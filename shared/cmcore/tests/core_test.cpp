// Tests for the shared core. No framework: each CHECK prints the line that failed.
//
//   core_test                      synthetic checks only
//   core_test regions.json [vector.json.gz]
//                                  also loads a real published picture and checks it holds up
#include <zlib.h>

#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include "cmcore/json.h"
#include "cmcore/reveal_game.h"
#include "cmcore/vector_art.h"

static int failures = 0;
#define CHECK(cond)                                                     \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #cond); \
            failures++;                                                 \
        }                                                               \
    } while (0)

static std::string gzip(const std::string& text) {
    z_stream zs;
    std::memset(&zs, 0, sizeof(zs));
    deflateInit2(&zs, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 16 + MAX_WBITS, 8, Z_DEFAULT_STRATEGY);
    std::string out(deflateBound(&zs, text.size()) + 32, '\0');
    zs.next_in = reinterpret_cast<Bytef*>(const_cast<char*>(text.data()));
    zs.avail_in = static_cast<uInt>(text.size());
    zs.next_out = reinterpret_cast<Bytef*>(&out[0]);
    zs.avail_out = static_cast<uInt>(out.size());
    deflate(&zs, Z_FINISH);
    out.resize(zs.total_out);
    deflateEnd(&zs);
    return out;
}

static std::string readFile(const char* path) {
    std::ifstream in(path, std::ios::binary);
    std::stringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

static void testJson() {
    cm::Json j;
    std::string err;
    CHECK(cm::Json::parse(R"({"a":[1,2.5,-3e2],"b":"x\"yé","c":true,"d":null,"e":{}})", j, err));
    CHECK(j["a"].size() == 3);
    CHECK(j["a"][1].number() == 2.5);
    CHECK(j["a"][2].integer() == -300);
    CHECK(j["b"].string() == "x\"y\xc3\xa9");
    CHECK(j["c"].boolean());
    CHECK(j["d"].isNull());
    CHECK(j["missing"].isNull());
    CHECK(j["a"][9].isNull());
    CHECK(!cm::Json::parse("{\"a\":1", j, err));
    CHECK(!cm::Json::parse("[1,2] x", j, err));
}

// A 6x4 picture:   1 1 0 2 2 2
//                  1 1 0 2 2 2
//                  3 3 0 0 0 0
//                  3 3 0 4 4 4
// regions 1 and 4 are number 1, region 2 is number 2, region 3 is number 3.
static const char* kRegions = R"({
  "w": 6, "h": 4, "look": "soft",
  "palette": [{"number":1,"color":"#ff0000"},{"number":2,"color":"00ff00"},{"number":3,"color":"#00f"}],
  "regions": [
    {"id":1,"number":1,"cx":0,"cy":0,"r":5,"bbox":[0,0,2,2]},
    {"id":2,"number":2,"cx":4,"cy":0,"r":9,"bbox":[3,0,3,2]},
    {"id":3,"number":3,"cx":0,"cy":2,"r":3,"bbox":[0,2,2,2]},
    {"id":4,"number":1,"cx":4,"cy":3,"r":2,"bbox":[3,3,3,1]}
  ]})";

static const uint16_t kIds[] = {
    1, 1, 0, 2, 2, 2,
    1, 1, 0, 2, 2, 2,
    3, 3, 0, 0, 0, 0,
    3, 3, 0, 4, 4, 4,
};

static void testGame() {
    cm::RevealGame g;
    std::string err;
    CHECK(g.load(kRegions, err));
    CHECK(g.width() == 6 && g.height() == 4);
    CHECK(g.look() == "soft");
    CHECK(g.regions().size() == 4);
    CHECK(g.palette().size() == 4);
    CHECK(g.palette()[1] == 0xFFFF0000u);
    CHECK(g.palette()[2] == 0xFF00FF00u);
    CHECK(g.palette()[3] == 0xFF0000FFu);
    // bbox [x, y, w, h] becomes inclusive corners.
    CHECK(g.regions()[1].left == 3 && g.regions()[1].right == 6 && g.regions()[1].bottom == 2);

    cm::IdMapView map{kIds, 6, 4};
    CHECK(g.regionAt(map, 4, 1) == 1);
    CHECK(g.regionAt(map, 2, 1) == -1);   // line art
    CHECK(g.regionAt(map, -1, 0) == -1);  // outside

    // Exact hit on the selected number.
    CHECK(g.tapTarget(map, 0, 0, 1, 0) == 0);
    // A tap on the line next to region 1 snaps to it with tolerance, not without.
    CHECK(g.tapTarget(map, 2, 0, 1, 0) == -1);
    CHECK(g.tapTarget(map, 2, 0, 1, 1) == 0);
    // A tap on a region of another number reaches the selected one only within tolerance.
    CHECK(g.tapTarget(map, 4, 2, 1, 1) == 3);
    CHECK(g.tapTarget(map, 4, 0, 1, 1) == -1);
    // Nearest of any colour.
    CHECK(g.nearestUnfilled(map, 2, 2, 1, 0, false) >= 0);

    CHECK(g.totalOf(1) == 2 && g.totalOf(2) == 1 && g.totalOf(9) == 0);
    CHECK(g.hintRegion(0) == 3);  // smallest radius overall
    CHECK(g.hintRegion(2) == 1);
    CHECK(g.nextUnfinishedNumber(0) == 1);

    CHECK(g.reveal(0));
    CHECK(!g.reveal(0));          // twice does not count twice
    CHECK(!g.reveal(42));
    CHECK(g.revealedOf(1) == 1);
    CHECK(!g.isNumberFinished(1));
    CHECK(g.progressPercent() == 25);
    // A filled region is no longer a target.
    CHECK(g.tapTarget(map, 0, 0, 1, 0) == -1);

    CHECK(g.reveal(3));
    CHECK(g.isNumberFinished(1));
    CHECK(g.nextUnfinishedNumber(0) == 2);
    CHECK(g.nextUnfinishedNumber(2) == 3);
    CHECK(g.hintRegion(1) == -1);
    CHECK(g.encodeProgress() == "1,4");

    // Restore into a fresh game: unknown ids and junk are skipped, nothing counts twice.
    cm::RevealGame r;
    CHECK(r.load(kRegions, err));
    std::vector<int> filled = r.restoreProgress(" 4, 99,abc,,1,4");
    CHECK(filled.size() == 2 && filled[0] == 3 && filled[1] == 0);
    CHECK(r.revealedCount() == 2);
    CHECK(r.encodeProgress() == "1,4");
    CHECK(r.restoreProgress("").empty());

    CHECK(r.reveal(1) && r.reveal(2));
    CHECK(r.isFinished());
    CHECK(r.progressPercent() == 100);
    CHECK(r.nextUnfinishedNumber(0) == 0);
    CHECK(r.hintRegion(0) == -1);

    // Rounding matches Java's Math.round(count * 100f / size): 1 of 3 = 33, 2 of 3 = 67.
    cm::RevealGame three;
    CHECK(three.load(R"({"w":1,"h":1,"palette":[],"regions":[{"id":1,"number":1},{"id":2,"number":1},{"id":3,"number":1}]})", err));
    three.reveal(0);
    CHECK(three.progressPercent() == 33);
    three.reveal(1);
    CHECK(three.progressPercent() == 67);

    CHECK(!g.load("{}", err));
    CHECK(!g.load("not json", err));
}

static const char* kVector =
    R"({"v":1,"w":100,"h":50,"s":[[0,"000000",[10,20,30,0,0,40]],)"
    R"([7,"FF8800",[0,0,100,0,0,100,-100,0],[20,20,10,0]],[3,"a0b0c0",[]]]})";

static void checkVector(const cm::VectorArt& v) {
    CHECK(v.width == 100 && v.height == 50);
    CHECK(v.shapeCount() == 3);
    CHECK(v.region[0] == 0 && v.region[1] == 7 && v.region[2] == 3);
    CHECK(v.colour[1] == 0xFFFF8800u);
    CHECK(v.shapeStart.size() == 4);
    CHECK(v.shapeStart[1] - v.shapeStart[0] == 1);  // ink: one sub-path
    CHECK(v.shapeStart[2] - v.shapeStart[1] == 2);  // two sub-paths
    CHECK(v.shapeStart[3] - v.shapeStart[2] == 0);  // empty sub-path dropped
    // Shape 0: (1,2) then +(3,0) then +(0,4) in pixels.
    int p = v.pathStart[v.shapeStart[0]];
    CHECK(v.pathStart[v.shapeStart[0] + 1] - p == 3);
    CHECK(v.points[p * 2] == 1.0f && v.points[p * 2 + 1] == 2.0f);
    CHECK(v.points[p * 2 + 2] == 4.0f && v.points[p * 2 + 3] == 2.0f);
    CHECK(v.points[p * 2 + 4] == 4.0f && v.points[p * 2 + 5] == 6.0f);
    // Shape 1, second sub-path: (2,2) then (3,2).
    int q = v.pathStart[v.shapeStart[1] + 1];
    CHECK(v.points[q * 2] == 2.0f && v.points[q * 2 + 2] == 3.0f);
}

static void testVector() {
    std::string text = kVector;
    cm::VectorArt v;
    std::string err;
    CHECK(cm::parseVectorArt(reinterpret_cast<const uint8_t*>(text.data()), text.size(), v, err));
    checkVector(v);

    std::string gz = gzip(text);
    cm::VectorArt g;
    CHECK(cm::parseVectorArt(reinterpret_cast<const uint8_t*>(gz.data()), gz.size(), g, err));
    checkVector(g);

    std::string broken = gz.substr(0, gz.size() / 2);
    CHECK(!cm::parseVectorArt(reinterpret_cast<const uint8_t*>(broken.data()), broken.size(), g, err));
    std::string bad = R"({"w":1,"h":1,"s":[[0,"000000",[1,2,]]]})";
    CHECK(!cm::parseVectorArt(reinterpret_cast<const uint8_t*>(bad.data()), bad.size(), g, err));
    std::string noSize = R"({"s":[]})";
    CHECK(!cm::parseVectorArt(reinterpret_cast<const uint8_t*>(noSize.data()), noSize.size(), g, err));
}

static void testRealPicture(const char* regionsPath, const char* vectorPath) {
    cm::RevealGame g;
    std::string err;
    std::string json = readFile(regionsPath);
    CHECK(g.load(json, err));
    if (!err.empty()) std::printf("  %s\n", err.c_str());
    std::printf("real picture: %dx%d, %zu regions, %zu numbers\n", g.width(), g.height(),
                g.regions().size(), g.palette().size() - 1);
    CHECK(!g.regions().empty());
    int sum = 0;
    for (size_t n = 1; n < g.palette().size(); n++) sum += g.totalOf(static_cast<int>(n));
    CHECK(sum == static_cast<int>(g.regions().size()));
    for (int i = 0; i < static_cast<int>(g.regions().size()); i++) g.reveal(i);
    CHECK(g.isFinished());
    cm::RevealGame again;
    CHECK(again.load(json, err));
    CHECK(again.restoreProgress(g.encodeProgress()).size() == g.regions().size());

    if (vectorPath) {
        std::string data = readFile(vectorPath);
        cm::VectorArt v;
        CHECK(cm::parseVectorArt(reinterpret_cast<const uint8_t*>(data.data()), data.size(), v, err));
        if (!err.empty()) std::printf("  %s\n", err.c_str());
        std::printf("real vector: %d shapes, %zu sub-paths, %zu points\n", v.shapeCount(),
                    v.pathStart.size() - 1, v.points.size() / 2);
        CHECK(v.width == g.width() && v.height == g.height());
        int unknown = 0;
        for (int r : v.region) if (r != 0 && g.indexOf(r) < 0) unknown++;
        CHECK(unknown == 0);
    }
}

int main(int argc, char** argv) {
    testJson();
    testGame();
    testVector();
    if (argc > 1) testRealPicture(argv[1], argc > 2 ? argv[2] : nullptr);
    if (failures) {
        std::printf("%d check(s) failed\n", failures);
        return 1;
    }
    std::printf("all core checks passed\n");
    return 0;
}
