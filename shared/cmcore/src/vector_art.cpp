#include "cmcore/vector_art.h"

#include <zlib.h>

#include <cstdlib>
#include <cstring>

#include "cmcore/reveal_game.h"  // parseColour

namespace cm {

bool gunzip(const uint8_t* data, size_t size, std::string& out) {
    z_stream zs;
    std::memset(&zs, 0, sizeof(zs));
    if (inflateInit2(&zs, 16 + MAX_WBITS) != Z_OK) return false;
    zs.next_in = const_cast<Bytef*>(data);
    zs.avail_in = static_cast<uInt>(size);

    out.clear();
    char buf[65536];
    int rc;
    do {
        zs.next_out = reinterpret_cast<Bytef*>(buf);
        zs.avail_out = sizeof(buf);
        rc = inflate(&zs, Z_NO_FLUSH);
        if (rc != Z_OK && rc != Z_STREAM_END) {
            inflateEnd(&zs);
            return false;
        }
        out.append(buf, sizeof(buf) - zs.avail_out);
    } while (rc != Z_STREAM_END);
    inflateEnd(&zs);
    return true;
}

namespace {

// A streaming reader for exactly this file. A general JSON tree would cost a few hundred bytes
// per number, and the biggest picture has several hundred thousand of them.
class Reader {
public:
    Reader(const char* s, size_t n) : p_(s), end_(s + n) {}

    std::string error;

    bool parse(VectorArt& out) {
        out = VectorArt();
        out.shapeStart.push_back(0);
        out.pathStart.push_back(0);
        if (!expect('{')) return false;
        if (peek() == '}') { p_++; return finish(out); }
        while (true) {
            std::string key;
            if (!string(key) || !expect(':')) return false;
            if (key == "w") { if (!integer(out.width)) return false; }
            else if (key == "h") { if (!integer(out.height)) return false; }
            else if (key == "s") { if (!shapes(out)) return false; }
            else if (!skipValue(0)) return false;
            char c = next();
            if (c == '}') break;
            if (c != ',') return fail("expected , or }");
        }
        return finish(out);
    }

private:
    const char* p_;
    const char* end_;

    bool fail(const char* what) {
        if (error.empty()) error = what;
        return false;
    }

    void space() {
        while (p_ < end_ && (*p_ == ' ' || *p_ == '\n' || *p_ == '\r' || *p_ == '\t')) p_++;
    }
    char peek() { space(); return p_ < end_ ? *p_ : '\0'; }
    char next() { space(); return p_ < end_ ? *p_++ : '\0'; }
    bool expect(char c) { return next() == c ? true : fail("unexpected character"); }

    bool finish(VectorArt& out) {
        space();
        if (p_ != end_) return fail("text after JSON");
        if (out.width <= 0 || out.height <= 0) return fail("vector has no size");
        return true;
    }

    bool integer(int& v) {
        space();
        char* stop = nullptr;
        // strtol stops at the first non-digit, and the buffer always ends in ']' or '}' long
        // before its end, so this cannot read past it.
        long n = std::strtol(p_, &stop, 10);
        if (stop == p_) return fail("expected a number");
        // Accept "12.0" as well — a number written by a different encoder.
        if (stop < end_ && *stop == '.') {
            double d = std::strtod(p_, &stop);
            n = static_cast<long>(d);
        }
        p_ = stop;
        v = static_cast<int>(n);
        return true;
    }

    bool string(std::string& s) {
        if (next() != '"') return fail("expected a string");
        const char* start = p_;
        while (p_ < end_ && *p_ != '"') {
            if (*p_ == '\\') p_++;  // keys and colours never escape, but do not trip over one
            p_++;
        }
        if (p_ >= end_) return fail("unterminated string");
        s.assign(start, p_);
        p_++;
        return true;
    }

    bool skipValue(int depth) {
        if (depth > 64) return fail("too deeply nested");
        char c = peek();
        if (c == '"') { std::string ignored; return string(ignored); }
        if (c == '[' || c == '{') {
            char close = c == '[' ? ']' : '}';
            p_++;
            if (peek() == close) { p_++; return true; }
            while (true) {
                if (close == '}') {
                    std::string ignored;
                    if (!string(ignored) || !expect(':')) return false;
                }
                if (!skipValue(depth + 1)) return false;
                char n = next();
                if (n == close) return true;
                if (n != ',') return fail("bad container");
            }
        }
        // number / true / false / null
        const char* start = p_;
        while (p_ < end_ && *p_ != ',' && *p_ != ']' && *p_ != '}' && *p_ != ' ' && *p_ != '\n') p_++;
        return p_ > start ? true : fail("expected a value");
    }

    bool shapes(VectorArt& out) {
        if (!expect('[')) return false;
        if (peek() == ']') { p_++; return true; }
        while (true) {
            if (!shape(out)) return false;
            char c = next();
            if (c == ']') return true;
            if (c != ',') return fail("bad shape list");
        }
    }

    bool shape(VectorArt& out) {
        int region;
        std::string hex;
        if (!expect('[') || !integer(region) || !expect(',') || !string(hex)) return false;
        out.region.push_back(region);
        out.colour.push_back(parseColour(hex, 0xFF000000u));
        while (true) {
            char c = next();
            if (c == ']') break;
            if (c != ',') return fail("bad shape");
            if (!subPath(out)) return false;
        }
        out.shapeStart.push_back(static_cast<int>(out.pathStart.size()) - 1);
        return true;
    }

    bool subPath(VectorArt& out) {
        if (!expect('[')) return false;
        int x = 0, y = 0, k = 0, v;
        if (peek() == ']') { p_++; return true; }  // an empty sub-path is dropped
        while (true) {
            if (!integer(v)) return false;
            if (k % 2 == 0) x = k == 0 ? v : x + v;
            else {
                y = k == 1 ? v : y + v;
                out.points.push_back(x / 10.0f);
                out.points.push_back(y / 10.0f);
            }
            k++;
            char c = next();
            if (c == ']') break;
            if (c != ',') return fail("bad sub-path");
        }
        out.pathStart.push_back(static_cast<int>(out.points.size() / 2));
        return true;
    }
};

}  // namespace

bool parseVectorArt(const uint8_t* data, size_t size, VectorArt& out, std::string& error) {
    std::string inflated;
    const char* text = reinterpret_cast<const char*>(data);
    size_t length = size;
    if (size >= 2 && data[0] == 0x1f && data[1] == 0x8b) {
        if (!gunzip(data, size, inflated)) {
            error = "vector is not valid gzip";
            return false;
        }
        text = inflated.data();
        length = inflated.size();
    }
    // strtol needs a terminator it can stop at; std::string guarantees one, raw input may not.
    std::string own;
    if (inflated.empty()) {
        own.assign(text, length);
        text = own.data();
    }
    Reader reader(text, length);
    if (!reader.parse(out)) {
        error = reader.error.empty() ? "invalid vector" : reader.error;
        return false;
    }
    return true;
}

}  // namespace cm
