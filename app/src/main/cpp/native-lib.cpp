#include <jni.h>
#include <android/log.h>
#include <string>

// OpenCV includes - these will work with the static OpenCV build
#ifdef OPENCV_STATIC
#include <opencv2/opencv.hpp>
using namespace cv;
#endif

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

// OpenCV test function
extern "C" JNIEXPORT jstring JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeTestOpenCV(JNIEnv *env, jobject thiz) {
    LOGI("Testing OpenCV functionality...");
    
#ifdef OPENCV_STATIC
    try {
        // Test 1: Create a simple matrix
        cv::Mat testMat = cv::Mat::zeros(100, 100, CV_8UC3);
        LOGI("OpenCV Test 1: Created %dx%d matrix successfully", testMat.rows, testMat.cols);
        
        // Test 2: Draw a simple shape
        cv::circle(testMat, cv::Point(50, 50), 30, cv::Scalar(0, 255, 0), 2);
        LOGI("OpenCV Test 2: Drew circle successfully");
        
        // Test 3: Get OpenCV version
        std::string version = cv::getVersionString();
        LOGI("OpenCV Test 3: Version info - %s", version.c_str());
        
        // Test 4: Basic image processing
        cv::Mat grayMat;
        // Replace cvtColor with a different operation to avoid version conflicts
        // Use threshold instead, which should be available in both versions
        cv::Mat singleChannel;
        cv::extractChannel(testMat, singleChannel, 0); // Extract blue channel
        cv::threshold(singleChannel, grayMat, 128, 255, cv::THRESH_BINARY);
        LOGI("OpenCV Test 4: Image processing successful (used threshold instead of cvtColor)");
        
        // Test 5: Matrix operations
        double meanVal = cv::mean(grayMat)[0];
        LOGI("OpenCV Test 5: Matrix mean calculation: %.2f", meanVal);
        
        std::string result = "OpenCV Test PASSED - All 5 tests successful!";
        LOGI("OpenCV Test: %s", result.c_str());
        return env->NewStringUTF(result.c_str());
        
    } catch (const cv::Exception& e) {
        std::string error = "OpenCV Exception: " + std::string(e.what());
        LOGE("OpenCV Test: %s", error.c_str());
        return env->NewStringUTF(error.c_str());
    } catch (const std::exception& e) {
        std::string error = "Standard Exception: " + std::string(e.what());
        LOGE("OpenCV Test: %s", error.c_str());
        return env->NewStringUTF(error.c_str());
    }
#else
    std::string result = "OpenCV Test SKIPPED - OpenCV not available";
    LOGI("OpenCV Test: %s", result.c_str());
    return env->NewStringUTF(result.c_str());
#endif
}