// JNI bridge for the shared C++ game core (shared/cmcore) — Java side: model/reveal/RevealCore.
//
// The core keeps the game state; Java keeps drawing. Handles are a RevealGame* cast to jlong.

#include <jni.h>

#include <string>
#include <vector>

#include "cmcore/reveal_game.h"
#include "cmcore/vector_art.h"

namespace {

cm::RevealGame* game(jlong handle) { return reinterpret_cast<cm::RevealGame*>(handle); }

std::string utf(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) env->ThrowNew(cls, message.c_str());
}

jintArray intArray(JNIEnv* env, const int* data, size_t n) {
    jintArray arr = env->NewIntArray(static_cast<jsize>(n));
    if (arr && n) env->SetIntArrayRegion(arr, 0, static_cast<jsize>(n), reinterpret_cast<const jint*>(data));
    return arr;
}

/** Pins the caller's char[] id map for the duration of one call. Java char == uint16_t. */
class PinnedIdMap {
public:
    PinnedIdMap(JNIEnv* env, jcharArray ids, cm::RevealGame* g) : env_(env), ids_(ids) {
        if (!ids || !g) return;
        jsize length = env->GetArrayLength(ids);
        if (length < static_cast<jsize>(g->width()) * g->height()) return;
        data_ = static_cast<jchar*>(env->GetPrimitiveArrayCritical(ids, nullptr));
        view.ids = reinterpret_cast<const uint16_t*>(data_);
        view.width = g->width();
        view.height = g->height();
    }
    ~PinnedIdMap() {
        if (data_) env_->ReleasePrimitiveArrayCritical(ids_, data_, JNI_ABORT);  // read-only
    }
    cm::IdMapView view;

private:
    JNIEnv* env_;
    jcharArray ids_;
    jchar* data_ = nullptr;
};

}  // namespace

#define CORE(name) Java_com_colourmate_paintbynumber_model_reveal_RevealCore_##name

extern "C" {

JNIEXPORT jlong JNICALL CORE(nativeLoad)(JNIEnv* env, jclass, jstring regionsJson) {
    auto* g = new cm::RevealGame();
    std::string error;
    if (!g->load(utf(env, regionsJson), error)) {
        delete g;
        throwIllegalState(env, "regions.json: " + error);
        return 0;
    }
    return reinterpret_cast<jlong>(g);
}

JNIEXPORT void JNICALL CORE(nativeFree)(JNIEnv*, jclass, jlong h) { delete game(h); }

JNIEXPORT jint JNICALL CORE(nativeWidth)(JNIEnv*, jclass, jlong h) { return game(h)->width(); }
JNIEXPORT jint JNICALL CORE(nativeHeight)(JNIEnv*, jclass, jlong h) { return game(h)->height(); }

JNIEXPORT jstring JNICALL CORE(nativeLook)(JNIEnv* env, jclass, jlong h) {
    return env->NewStringUTF(game(h)->look().c_str());
}

/** 8 ints per region, in regions.json order: id, number, cx, cy, left, top, right, bottom. */
JNIEXPORT jintArray JNICALL CORE(nativeRegionInts)(JNIEnv* env, jclass, jlong h) {
    const auto& regions = game(h)->regions();
    std::vector<int> out;
    out.reserve(regions.size() * 8);
    for (const auto& r : regions) {
        int row[8] = {r.id, r.number, r.cx, r.cy, r.left, r.top, r.right, r.bottom};
        out.insert(out.end(), row, row + 8);
    }
    return intArray(env, out.data(), out.size());
}

JNIEXPORT jfloatArray JNICALL CORE(nativeRegionRadii)(JNIEnv* env, jclass, jlong h) {
    const auto& regions = game(h)->regions();
    std::vector<float> out;
    out.reserve(regions.size());
    for (const auto& r : regions) out.push_back(r.radius);
    jfloatArray arr = env->NewFloatArray(static_cast<jsize>(out.size()));
    if (arr && !out.empty()) env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(out.size()), out.data());
    return arr;
}

JNIEXPORT jintArray JNICALL CORE(nativePalette)(JNIEnv* env, jclass, jlong h) {
    const auto& p = game(h)->palette();
    return intArray(env, reinterpret_cast<const int*>(p.data()), p.size());
}

JNIEXPORT jint JNICALL CORE(nativeTapTarget)(JNIEnv* env, jclass, jlong h, jcharArray ids, jint x,
                                             jint y, jint selected, jint tolerance) {
    PinnedIdMap map(env, ids, game(h));
    return game(h)->tapTarget(map.view, x, y, selected, tolerance);
}

JNIEXPORT jint JNICALL CORE(nativeNearestUnfilled)(JNIEnv* env, jclass, jlong h, jcharArray ids,
                                                   jint x, jint y, jint tolerance, jint number,
                                                   jboolean onlyNumber) {
    PinnedIdMap map(env, ids, game(h));
    return game(h)->nearestUnfilled(map.view, x, y, tolerance, number, onlyNumber == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL CORE(nativeReveal)(JNIEnv*, jclass, jlong h, jint index) {
    return game(h)->reveal(index) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL CORE(nativeTotalOf)(JNIEnv*, jclass, jlong h, jint n) { return game(h)->totalOf(n); }
JNIEXPORT jint JNICALL CORE(nativeRevealedOf)(JNIEnv*, jclass, jlong h, jint n) { return game(h)->revealedOf(n); }
JNIEXPORT jint JNICALL CORE(nativeProgressPercent)(JNIEnv*, jclass, jlong h) { return game(h)->progressPercent(); }
JNIEXPORT jboolean JNICALL CORE(nativeIsFinished)(JNIEnv*, jclass, jlong h) {
    return game(h)->isFinished() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL CORE(nativeNextUnfinishedNumber)(JNIEnv*, jclass, jlong h, jint after) {
    return game(h)->nextUnfinishedNumber(after);
}
JNIEXPORT jint JNICALL CORE(nativeHintRegion)(JNIEnv*, jclass, jlong h, jint number) {
    return game(h)->hintRegion(number);
}

JNIEXPORT jstring JNICALL CORE(nativeEncodeProgress)(JNIEnv* env, jclass, jlong h) {
    return env->NewStringUTF(game(h)->encodeProgress().c_str());
}

JNIEXPORT jintArray JNICALL CORE(nativeRestoreProgress)(JNIEnv* env, jclass, jlong h, jstring s) {
    std::vector<int> filled = game(h)->restoreProgress(utf(env, s));
    return intArray(env, filled.data(), filled.size());
}

/**
 * Returns {int[]{w,h}, int[] region, int[] colour, int[] shapeStart, int[] pathStart,
 * float[] points} — see cmcore/vector_art.h. Throws IllegalStateException when malformed.
 */
JNIEXPORT jobjectArray JNICALL CORE(nativeParseVector)(JNIEnv* env, jclass, jbyteArray bytes) {
    jsize n = bytes ? env->GetArrayLength(bytes) : 0;
    std::vector<uint8_t> data(static_cast<size_t>(n));
    if (n) env->GetByteArrayRegion(bytes, 0, n, reinterpret_cast<jbyte*>(data.data()));

    cm::VectorArt art;
    std::string error;
    if (!cm::parseVectorArt(data.data(), data.size(), art, error)) {
        throwIllegalState(env, "vector.json: " + error);
        return nullptr;
    }

    jclass objectClass = env->FindClass("java/lang/Object");
    jobjectArray out = env->NewObjectArray(6, objectClass, nullptr);
    int size[2] = {art.width, art.height};
    env->SetObjectArrayElement(out, 0, intArray(env, size, 2));
    env->SetObjectArrayElement(out, 1, intArray(env, art.region.data(), art.region.size()));
    env->SetObjectArrayElement(out, 2, intArray(env, reinterpret_cast<const int*>(art.colour.data()), art.colour.size()));
    env->SetObjectArrayElement(out, 3, intArray(env, art.shapeStart.data(), art.shapeStart.size()));
    env->SetObjectArrayElement(out, 4, intArray(env, art.pathStart.data(), art.pathStart.size()));
    jfloatArray points = env->NewFloatArray(static_cast<jsize>(art.points.size()));
    if (points && !art.points.empty()) {
        env->SetFloatArrayRegion(points, 0, static_cast<jsize>(art.points.size()), art.points.data());
    }
    env->SetObjectArrayElement(out, 5, points);
    return out;
}

}  // extern "C"
