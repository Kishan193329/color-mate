// A small JSON reader — just enough for the ColorForge files (regions.json, vector.json).
// No dependencies, so the same code builds for Android (NDK) and iOS (Swift / Xcode).
#pragma once

#include <map>
#include <memory>
#include <string>
#include <vector>

namespace cm {

class Json {
public:
    enum class Type { Null, Bool, Number, String, Array, Object };

    Type type() const { return type_; }
    bool isNull() const { return type_ == Type::Null; }
    bool isNumber() const { return type_ == Type::Number; }
    bool isString() const { return type_ == Type::String; }
    bool isArray() const { return type_ == Type::Array; }
    bool isObject() const { return type_ == Type::Object; }

    double number(double fallback = 0) const { return type_ == Type::Number ? num_ : fallback; }
    int integer(int fallback = 0) const { return type_ == Type::Number ? static_cast<int>(num_) : fallback; }
    bool boolean(bool fallback = false) const { return type_ == Type::Bool ? bool_ : fallback; }
    const std::string& string() const { return str_; }

    size_t size() const { return items_.size(); }
    const Json& operator[](size_t i) const;            // array element, Null when out of range
    const Json& operator[](const std::string& key) const;  // object member, Null when absent
    bool has(const std::string& key) const { return members_.count(key) != 0; }

    /** Parses `text`; on failure returns false and fills `error`. */
    static bool parse(const std::string& text, Json& out, std::string& error);

private:
    friend class JsonParser;
    Type type_ = Type::Null;
    bool bool_ = false;
    double num_ = 0;
    std::string str_;
    std::vector<Json> items_;
    std::map<std::string, Json> members_;
};

}  // namespace cm
