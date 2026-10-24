#include "cmcore/json.h"

#include <cstdlib>

namespace cm {

namespace {
const Json& nullJson() {
    static const Json kNull;
    return kNull;
}
}  // namespace

const Json& Json::operator[](size_t i) const {
    return (type_ == Type::Array && i < items_.size()) ? items_[i] : nullJson();
}

const Json& Json::operator[](const std::string& key) const {
    if (type_ != Type::Object) return nullJson();
    auto it = members_.find(key);
    return it == members_.end() ? nullJson() : it->second;
}

class JsonParser {
public:
    explicit JsonParser(const std::string& s) : s_(s) {}

    bool parse(Json& out, std::string& error) {
        if (!value(out, 0)) {
            error = error_.empty() ? "invalid JSON" : error_;
            return false;
        }
        skipSpace();
        if (pos_ != s_.size()) {
            error = "unexpected text after JSON at " + std::to_string(pos_);
            return false;
        }
        return true;
    }

private:
    static constexpr int kMaxDepth = 64;
    const std::string& s_;
    size_t pos_ = 0;
    std::string error_;

    bool fail(const char* what) {
        if (error_.empty()) error_ = std::string(what) + " at " + std::to_string(pos_);
        return false;
    }

    void skipSpace() {
        while (pos_ < s_.size() && (s_[pos_] == ' ' || s_[pos_] == '\n' || s_[pos_] == '\r' || s_[pos_] == '\t')) pos_++;
    }

    bool literal(const char* word) {
        size_t n = 0;
        while (word[n]) n++;
        if (s_.compare(pos_, n, word) != 0) return fail("bad literal");
        pos_ += n;
        return true;
    }

    bool value(Json& out, int depth) {
        if (depth > kMaxDepth) return fail("too deeply nested");
        skipSpace();
        if (pos_ >= s_.size()) return fail("unexpected end");
        char c = s_[pos_];
        if (c == '{') return object(out, depth);
        if (c == '[') return array(out, depth);
        if (c == '"') { out.type_ = Json::Type::String; return string(out.str_); }
        if (c == 't') { out.type_ = Json::Type::Bool; out.bool_ = true; return literal("true"); }
        if (c == 'f') { out.type_ = Json::Type::Bool; out.bool_ = false; return literal("false"); }
        if (c == 'n') { out.type_ = Json::Type::Null; return literal("null"); }
        return number(out);
    }

    bool number(Json& out) {
        const char* start = s_.c_str() + pos_;
        char* end = nullptr;
        double v = std::strtod(start, &end);
        if (end == start) return fail("bad number");
        pos_ += static_cast<size_t>(end - start);
        out.type_ = Json::Type::Number;
        out.num_ = v;
        return true;
    }

    static void appendUtf8(std::string& out, unsigned cp) {
        if (cp < 0x80) out += static_cast<char>(cp);
        else if (cp < 0x800) { out += static_cast<char>(0xC0 | (cp >> 6)); out += static_cast<char>(0x80 | (cp & 0x3F)); }
        else if (cp < 0x10000) {
            out += static_cast<char>(0xE0 | (cp >> 12));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (cp >> 18));
            out += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        }
    }

    bool hex4(unsigned& cp) {
        if (pos_ + 4 > s_.size()) return fail("bad \\u escape");
        cp = 0;
        for (int i = 0; i < 4; i++) {
            char h = s_[pos_++];
            cp <<= 4;
            if (h >= '0' && h <= '9') cp |= h - '0';
            else if (h >= 'a' && h <= 'f') cp |= h - 'a' + 10;
            else if (h >= 'A' && h <= 'F') cp |= h - 'A' + 10;
            else return fail("bad \\u escape");
        }
        return true;
    }

    bool string(std::string& out) {
        pos_++;  // opening quote
        out.clear();
        while (pos_ < s_.size()) {
            char c = s_[pos_++];
            if (c == '"') return true;
            if (c != '\\') { out += c; continue; }
            if (pos_ >= s_.size()) break;
            char e = s_[pos_++];
            switch (e) {
                case '"': out += '"'; break;
                case '\\': out += '\\'; break;
                case '/': out += '/'; break;
                case 'b': out += '\b'; break;
                case 'f': out += '\f'; break;
                case 'n': out += '\n'; break;
                case 'r': out += '\r'; break;
                case 't': out += '\t'; break;
                case 'u': {
                    unsigned cp;
                    if (!hex4(cp)) return false;
                    if (cp >= 0xD800 && cp <= 0xDBFF && pos_ + 6 <= s_.size() && s_[pos_] == '\\' && s_[pos_ + 1] == 'u') {
                        pos_ += 2;
                        unsigned lo;
                        if (!hex4(lo)) return false;
                        cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                    }
                    appendUtf8(out, cp);
                    break;
                }
                default: return fail("bad escape");
            }
        }
        return fail("unterminated string");
    }

    bool array(Json& out, int depth) {
        pos_++;
        out.type_ = Json::Type::Array;
        skipSpace();
        if (pos_ < s_.size() && s_[pos_] == ']') { pos_++; return true; }
        while (true) {
            out.items_.emplace_back();
            if (!value(out.items_.back(), depth + 1)) return false;
            skipSpace();
            if (pos_ >= s_.size()) return fail("unterminated array");
            char c = s_[pos_++];
            if (c == ']') return true;
            if (c != ',') return fail("expected , or ]");
        }
    }

    bool object(Json& out, int depth) {
        pos_++;
        out.type_ = Json::Type::Object;
        skipSpace();
        if (pos_ < s_.size() && s_[pos_] == '}') { pos_++; return true; }
        while (true) {
            skipSpace();
            if (pos_ >= s_.size() || s_[pos_] != '"') return fail("expected key");
            std::string key;
            if (!string(key)) return false;
            skipSpace();
            if (pos_ >= s_.size() || s_[pos_++] != ':') return fail("expected :");
            Json v;
            if (!value(v, depth + 1)) return false;
            out.members_[key] = std::move(v);
            skipSpace();
            if (pos_ >= s_.size()) return fail("unterminated object");
            char c = s_[pos_++];
            if (c == '}') return true;
            if (c != ',') return fail("expected , or }");
        }
    }
};

bool Json::parse(const std::string& text, Json& out, std::string& error) {
    out = Json();
    return JsonParser(text).parse(out, error);
}

}  // namespace cm
