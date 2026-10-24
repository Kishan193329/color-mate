#include "cmcore/reveal_game.h"

#include <climits>
#include <cstdlib>

#include "cmcore/json.h"

namespace cm {

uint32_t parseColour(const std::string& text, uint32_t fallback) {
    std::string hex = (!text.empty() && text[0] == '#') ? text.substr(1) : text;
    if (hex.size() == 3) hex = {hex[0], hex[0], hex[1], hex[1], hex[2], hex[2]};
    if (hex.size() != 6) return fallback;
    for (char c : hex) {
        bool ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        if (!ok) return fallback;
    }
    return 0xFF000000u | static_cast<uint32_t>(std::strtoul(hex.c_str(), nullptr, 16));
}

bool RevealGame::load(const std::string& regionsJson, std::string& error) {
    Json meta;
    if (!Json::parse(regionsJson, meta, error)) return false;
    if (!meta.isObject() || !meta["w"].isNumber() || !meta["h"].isNumber()) {
        error = "regions.json has no w/h";
        return false;
    }
    width_ = meta["w"].integer();
    height_ = meta["h"].integer();
    look_ = meta["look"].isString() ? meta["look"].string() : "black";

    const Json& pal = meta["palette"];
    int maxNumber = 0;
    for (size_t i = 0; i < pal.size(); i++) {
        if (pal[i]["number"].integer() > maxNumber) maxNumber = pal[i]["number"].integer();
    }
    palette_.assign(maxNumber + 1, 0xFF808080u);
    for (size_t i = 0; i < pal.size(); i++) {
        int n = pal[i]["number"].integer();
        if (n > 0) palette_[n] = parseColour(pal[i]["color"].string());
    }

    const Json& list = meta["regions"];
    regions_.clear();
    indexById_.clear();
    regions_.reserve(list.size());
    for (size_t i = 0; i < list.size(); i++) {
        const Json& e = list[i];
        Region r;
        r.id = e["id"].integer();
        r.number = e["number"].integer();
        r.cx = e["cx"].integer();
        r.cy = e["cy"].integer();
        r.radius = static_cast<float>(e["r"].number(12));
        const Json& bbox = e["bbox"];
        if (bbox.size() >= 4) {
            r.left = bbox[0].integer();
            r.top = bbox[1].integer();
            r.right = r.left + bbox[2].integer();
            r.bottom = r.top + bbox[3].integer();
        }
        indexById_[r.id] = static_cast<int>(regions_.size());
        regions_.push_back(r);
    }

    totalPerNumber_.assign(palette_.size(), 0);
    revealedPerNumber_.assign(palette_.size(), 0);
    revealedCount_ = 0;
    for (const Region& r : regions_) {
        if (r.number > 0 && r.number < static_cast<int>(totalPerNumber_.size())) totalPerNumber_[r.number]++;
    }
    return true;
}

int RevealGame::indexOf(int id) const {
    auto it = indexById_.find(id);
    return it == indexById_.end() ? -1 : it->second;
}

int RevealGame::regionAt(const IdMapView& map, int x, int y) const {
    int id = map.at(x, y);
    return id == 0 ? -1 : indexOf(id);
}

int RevealGame::nearestUnfilled(const IdMapView& map, int x, int y, int tolerance, int number,
                                bool onlyNumber) const {
    if (!map.ids || tolerance < 0) return -1;
    int x0 = x - tolerance < 0 ? 0 : x - tolerance;
    int y0 = y - tolerance < 0 ? 0 : y - tolerance;
    int x1 = x + tolerance >= map.width ? map.width - 1 : x + tolerance;
    int y1 = y + tolerance >= map.height ? map.height - 1 : y + tolerance;

    int best = -1;
    long long bestDist = LLONG_MAX;
    int lastId = -1, lastIndex = -1;  // neighbouring pixels are usually the same region
    for (int yy = y0; yy <= y1; yy++) {
        const uint16_t* row = map.ids + static_cast<size_t>(yy) * map.width;
        long long dy = yy - y;
        for (int xx = x0; xx <= x1; xx++) {
            int id = row[xx];
            if (id == 0) continue;
            int index = id == lastId ? lastIndex : indexOf(id);
            lastId = id;
            lastIndex = index;
            if (index < 0) continue;
            const Region& r = regions_[index];
            if (r.revealed || (onlyNumber && r.number != number)) continue;
            long long dx = xx - x, dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = index;
            }
        }
    }
    return best;
}

int RevealGame::tapTarget(const IdMapView& map, int x, int y, int selected, int tolerance) const {
    int direct = regionAt(map, x, y);
    if (direct >= 0 && !regions_[direct].revealed && regions_[direct].number == selected) return direct;
    return nearestUnfilled(map, x, y, tolerance, selected, true);
}

bool RevealGame::reveal(int index) {
    if (index < 0 || index >= static_cast<int>(regions_.size())) return false;
    Region& r = regions_[index];
    if (r.revealed) return false;
    r.revealed = true;
    revealedCount_++;
    if (r.number > 0 && r.number < static_cast<int>(revealedPerNumber_.size())) revealedPerNumber_[r.number]++;
    return true;
}

int RevealGame::totalOf(int number) const {
    return number > 0 && number < static_cast<int>(totalPerNumber_.size()) ? totalPerNumber_[number] : 0;
}

int RevealGame::revealedOf(int number) const {
    return number > 0 && number < static_cast<int>(revealedPerNumber_.size()) ? revealedPerNumber_[number] : 0;
}

int RevealGame::progressPercent() const {
    if (regions_.empty()) return 0;
    // Same rounding as Java's Math.round(count * 100f / size), so saved "#pct" values agree.
    return static_cast<int>(revealedCount_ * 100.0f / regions_.size() + 0.5f);
}

int RevealGame::nextUnfinishedNumber(int after) const {
    int count = static_cast<int>(palette_.size());
    for (int step = 1; step < count; step++) {
        int n = (after + step - 1) % (count - 1) + 1;  // after+1 .. max, then wrap to 1
        if (totalOf(n) > 0 && !isNumberFinished(n)) return n;
    }
    return 0;
}

int RevealGame::hintRegion(int number) const {
    int best = -1;
    for (int i = 0; i < static_cast<int>(regions_.size()); i++) {
        const Region& r = regions_[i];
        if (r.revealed || (number != 0 && r.number != number)) continue;
        if (best < 0 || r.radius < regions_[best].radius) best = i;
    }
    return best;
}

std::string RevealGame::encodeProgress() const {
    std::string out;
    for (const Region& r : regions_) {
        if (!r.revealed) continue;
        if (!out.empty()) out += ',';
        out += std::to_string(r.id);
    }
    return out;
}

std::vector<int> RevealGame::restoreProgress(const std::string& encoded) {
    std::vector<int> filled;
    size_t pos = 0;
    while (pos <= encoded.size()) {
        size_t comma = encoded.find(',', pos);
        if (comma == std::string::npos) comma = encoded.size();
        std::string part = encoded.substr(pos, comma - pos);
        pos = comma + 1;

        size_t a = part.find_first_not_of(" \t"), b = part.find_last_not_of(" \t");
        if (a == std::string::npos) continue;
        part = part.substr(a, b - a + 1);
        char* end = nullptr;
        long id = std::strtol(part.c_str(), &end, 10);
        if (end == part.c_str() || *end != '\0') continue;  // corrupt entry

        int index = indexOf(static_cast<int>(id));
        if (index >= 0 && reveal(index)) filled.push_back(index);
    }
    return filled;
}

}  // namespace cm
