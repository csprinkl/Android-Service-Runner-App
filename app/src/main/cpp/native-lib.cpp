#include <jni.h>
#include <android/log.h>

#define LOG_TAG "QtServiceJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// External C functions from your Qt static library
extern "C" {
    const char* qt_service_get_version();
    bool qt_service_initialize();
    bool qt_service_start();
    bool qt_service_stop();
    bool qt_service_is_running();
    void qt_service_cleanup();
}

// JNI functions - MUST match your Java class package exactly
// For org.qtproject.qtservice.QtServiceWrapper:

extern "C" JNIEXPORT jstring JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeGetVersion(JNIEnv *env, jobject thiz) {
    LOGI("Getting Qt service version");
    const char* version = qt_service_get_version();
    return env->NewStringUTF(version);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeInitializeService(JNIEnv *env, jobject thiz) {
    LOGI("Initializing Qt service");
    bool result = qt_service_initialize();
    LOGI("Qt service initialize result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeStartService(JNIEnv *env, jobject thiz) {
    LOGI("Starting Qt service");
    bool result = qt_service_start();
    LOGI("Qt service start result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeStopService(JNIEnv *env, jobject thiz) {
    LOGI("Stopping Qt service");
    bool result = qt_service_stop();
    LOGI("Qt service stop result: %s", result ? "SUCCESS" : "FAILED");
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeIsServiceRunning(JNIEnv *env, jobject thiz) {
    return qt_service_is_running() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeCleanupService(JNIEnv *env, jobject thiz) {
    LOGI("Cleaning up Qt service");
    qt_service_cleanup();
    LOGI("Qt service cleanup complete");
}