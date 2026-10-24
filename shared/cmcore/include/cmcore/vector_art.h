// An SVG picture as shapes — vector.json.gz, written by the backend's svg_builder.write_vector().
//
//   {"v":1, "w":W, "h":H, "s":[[regionId, "rrggbb", [x0,y0,dx1,dy1,...], ...], ...]}
//
// Coordinates are tenths of a pixel; after the first point of a sub-path every pair is a delta.
// Region id 0 is outline ink. Shapes are in the SVG's own drawing order, which matters: dark
// shapes often sit UNDER light ones, so drawing all colours first and all ink after is wrong.
//
// The result is flat arrays so it crosses JNI / Swift cheaply and each platform builds its own
// path objects (android.graphics.Path, CGPath) from it.
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace cm {

struct VectorArt {
    int width = 0;
    int height = 0;

    /** Per shape. */
    std::vector<int> region;
    std::vector<uint32_t> colour;      // 0xFFRRGGBB
    /** Shape i's sub-paths are [shapeStart[i], shapeStart[i+1]). Size = shapes + 1. */
    std::vector<int> shapeStart;

    /** Sub-path j's points are [pathStart[j], pathStart[j+1]) in `points`. Size = sub-paths + 1. */
    std::vector<int> pathStart;
    /** Absolute x, y pairs in picture pixels. A point index p is points[2p], points[2p+1]. */
    std::vector<float> points;

    int shapeCount() const { return static_cast<int>(region.size()); }
};

/**
 * Parses vector.json, gzipped or not — the magic bytes decide, because a server may or may not
 * undo the gzip in transit. Returns false and fills `error` on anything malformed.
 */
bool parseVectorArt(const uint8_t* data, size_t size, VectorArt& out, std::string& error);

/** Inflates gzip data. Returns false when it is not valid gzip. */
bool gunzip(const uint8_t* data, size_t size, std::string& out);

}  // namespace cm
