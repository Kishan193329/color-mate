// colorbynumber.cpp
#include <jni.h>

extern "C" {

// GetBUrl(): index.php/Number/
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetBUrl(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("index.php/Number/");
}

// GetGiftUrl(): https://srv1919471.hstgr.cloud/ColorbyNumberNewColoringBook/getimages.php
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetGiftUrl(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColorbyNumberNewColoringBook/getimages.php");
}

// GetMainUrl(): https://srv1919471.hstgr.cloud/ColoringBook/
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetMainUrl(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/");
}

// GetMusicUrl(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/d2number/Number/audios
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetMusicUrl(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/d2number/Number/audios");
}

// GetRestUrl(): index.php/Welcome/
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetRestUrl(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("index.php/Welcome/");
}

// AnimalURL(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/animals
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_AnimalURL(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/animals");
}

// FlowerURL(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/flowers
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_FlowerURL(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/flowers");
}

// GetExclusive(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/dhnumber/Number/gimg/c/Exclusive
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetExclusive(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/dhnumber/Number/gimg/c/Exclusive");
}

// GetExclusiveBanner(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/dhnumber/Number/gimg/c/
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetExclusiveBanner(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/dhnumber/Number/gimg/c/");
}

// GetStory(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/dhnumber/Number/gimg/c/Story
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetStory(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/dhnumber/Number/gimg/c/Story");
}

// KidURL(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/kids
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_KidURL(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/kids");
}

// LoveURL(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/love
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_LoveURL(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/love");
}

// MandalaURL(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/mandalas
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_MandalaURL(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/mandalas");
}

// NatureURL(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/nature
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_NatureURL(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/nature");
}

// PeopleURL(): https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/people
JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_PeopleURL(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://srv1919471.hstgr.cloud/ColoringBook/index.php/Number/gimg/c/people");
}

JNIEXPORT jstring JNICALL
Java_com_colourmate_paintbynumber_Utils_Manager_GetContentBaseUrl(JNIEnv *env, jclass /*clazz*/) {
    return env->NewStringUTF("https://s3.amazonaws.com/fungames-forfree/colornumber/public/");
}


} // extern "C"
