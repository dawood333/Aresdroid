#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_org_aresdroid_app_MainActivity_nativeRuntimeInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF("AresDroid native core loaded (arm64); diagnostic-only build");
}
