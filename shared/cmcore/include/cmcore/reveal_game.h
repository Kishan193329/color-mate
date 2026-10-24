// The colour-by-number "reveal" game, without any drawing — the part Android and iOS share.
//
// It knows the regions of a picture (regions.json), which ones are filled, how a tap maps to a
// region, progress, saving, and hints. Drawing stays in each platform (Canvas / Core Graphics),
// because that is where the two genuinely differ.
//
// The id map (one region id per pixel, id = R + G*256 from id_map.png) is NOT owned here: it is
// the biggest array in the game (3 MB at 1254x1254) and the platform already holds it for its own
// drawing, so calls that need it are handed a view of the caller's copy.
#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace cm {

struct Region {
    int id = 0;
    int number = 0;
    int cx = 0, cy = 0;
    float radius = 12;
    // Inclusive corners. regions.json stores bbox as [x, y, width, height].
    int left = 0, top = 0, right = 0, bottom = 0;
    bool revealed = false;
};

/** A non-owning view of the caller's id map. */
struct IdMapView {
    const uint16_t* ids = nullptr;
    int width = 0;
    int height = 0;

    int at(int x, int y) const {
        return (x < 0 || y < 0 || x >= width || y >= height) ? 0 : ids[y * width + x];
    }
};

class RevealGame {
public:
    /** Parses regions.json. Returns false (and fills `error`) when the file is not usable. */
    bool load(const std::string& regionsJson, std::string& error);

    int width() const { return width_; }
    int height() const { return height_; }
    /** Outline look the admin picked for an SVG picture: "black" (default) or "soft". */
    const std::string& look() const { return look_; }

    const std::vector<Region>& regions() const { return regions_; }
    /** Palette colour per number as 0xAARRGGBB; index 0 unused, so palette()[n] is number n. */
    const std::vector<uint32_t>& palette() const { return palette_; }

    /** Index into regions() for a region id, or -1. */
    int indexOf(int id) const;

    // ─── Taps ───

    /** Region index under a picture pixel, or -1 (line art / outside). */
    int regionAt(const IdMapView& map, int x, int y) const;

    /**
     * The nearest unfilled region within `tolerance` px of (x, y) — of `number` when
     * `onlyNumber` is true, of any colour otherwise. -1 when none. Distance is to the nearest
     * pixel of the region, so a tap just outside a small spot still lands on it.
     */
    int nearestUnfilled(const IdMapView& map, int x, int y, int tolerance, int number,
                        bool onlyNumber) const;

    /**
     * What a tap at (x, y) fills while `selected` is the chosen number: the exact region when
     * it is an unfilled one of that number, else the nearest within `tolerance`. -1 = nothing.
     */
    int tapTarget(const IdMapView& map, int x, int y, int selected, int tolerance) const;

    // ─── Filling ───

    /** Marks a region filled. Returns false when it already was (or the index is bad). */
    bool reveal(int index);

    int totalOf(int number) const;
    int revealedOf(int number) const;
    bool isNumberFinished(int number) const { return totalOf(number) > 0 && revealedOf(number) >= totalOf(number); }
    int revealedCount() const { return revealedCount_; }
    int progressPercent() const;
    bool isFinished() const { return revealedCount_ >= static_cast<int>(regions_.size()); }

    /** The first number (1..) that still has unfilled regions, starting after `after`; 0 = none. */
    int nextUnfinishedNumber(int after) const;

    /**
     * The region worth pointing a hint at: the SMALLEST unfilled one of `number`, or of any
     * colour when `number` is 0. A player asking for a hint has already found the big shapes.
     */
    int hintRegion(int number) const;

    // ─── Save / restore ───

    /** The filled region ids as "12,40,7" — small enough for preferences / UserDefaults. */
    std::string encodeProgress() const;

    /**
     * Re-fills the saved regions and returns the indices it filled, so the caller can paint
     * them. Ids this picture does not have (a save from another picture) and corrupt entries
     * are skipped rather than losing the whole save.
     */
    std::vector<int> restoreProgress(const std::string& encoded);

private:
    int width_ = 0, height_ = 0;
    std::string look_ = "black";
    std::vector<Region> regions_;
    std::vector<uint32_t> palette_;
    std::unordered_map<int, int> indexById_;
    std::vector<int> totalPerNumber_;
    std::vector<int> revealedPerNumber_;
    int revealedCount_ = 0;
};

/** Parses "#rrggbb" / "rrggbb" (and #rgb) into 0xFFRRGGBB; `fallback` when it is not a colour. */
uint32_t parseColour(const std::string& text, uint32_t fallback = 0xFF808080u);

}  // namespace cm
