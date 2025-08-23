# OpenCV Static Build Guide

This document explains how to build OpenCV statically for Android and how to integrate it with the Qt service.

## Table of Contents

1. [Building OpenCV Statically](#building-opencv-statically)
2. [Integration with Qt Service](#integration-with-qt-service)
3. [Current Setup](#current-setup)
4. [Usage Examples](#usage-examples)

## Building OpenCV Statically

### Step 1: Clone OpenCV Source

```bash
# Clone OpenCV 4.8.0
git clone --branch 4.8.0 https://github.com/opencv/opencv.git opencv-4.8.0
cd opencv-4.8.0
```

### Step 2: Create Build Directory

```bash
# Create build directory
mkdir opencv_static
cd opencv_static
```

### Step 3: Configure CMake

```bash
# Configure for Android ARM64 with static build
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-23 \
      -DANDROID_NDK=$ANDROID_NDK \
      -DANDROID_SDK_ROOT=$ANDROID_SDK \
      -DBUILD_SHARED_LIBS=OFF \
      -DBUILD_TESTS=OFF \
      -DBUILD_PERF_TESTS=OFF \
      -DBUILD_EXAMPLES=OFF \
      -DBUILD_DOCS=OFF \
      -DBUILD_ANDROID_EXAMPLES=ON \
      -DBUILD_ANDROID_PROJECTS=ON \
      -DBUILD_JAVA=ON \
      -DBUILD_FAT_JAVA_LIB=ON \
      -DBUILD_KOTLIN_EXTENSIONS=ON \
      -DWITH_CAROTENE=ON \
      -DWITH_TEGRA_OPTIMIZATIONS=ON \
      -DWITH_OPENCL=OFF \
      -DWITH_CUDA=OFF \
      -DWITH_IPP=OFF \
      -DWITH_1394=OFF \
      -DWITH_EIGEN=OFF \
      -DWITH_FFMPEG=OFF \
      -DWITH_GSTREAMER=OFF \
      -DWITH_GTK=OFF \
      -DWITH_OPENEXR=OFF \
      -DWITH_OPENGL=OFF \
      -DWITH_QT=OFF \
      -DWITH_VTK=OFF \
      -DWITH_WEBP=ON \
      -DWITH_JPEG=ON \
      -DWITH_PNG=ON \
      -DWITH_TIFF=ON \
      -DWITH_OPENJPEG=ON \
      -DWITH_JASPER=ON \
      -DWITH_ITT=ON \
      -DWITH_ADE=ON \
      -DWITH_OPENCV_DNN=ON \
      -DWITH_OPENCV_GAPI=ON \
      -DWITH_OPENCV_ML=ON \
      -DWITH_OPENCV_OBJDETECT=ON \
      -DWITH_OPENCV_PHOTO=ON \
      -DWITH_OPENCV_STITCHING=ON \
      -DWITH_OPENCV_VIDEO=ON \
      -DWITH_OPENCV_VIDEOIO=ON \
      -DWITH_OPENCV_HIGHGUI=ON \
      -DWITH_OPENCV_IMGPROC=ON \
      -DWITH_OPENCV_IMGCODECS=ON \
      -DWITH_OPENCV_FEATURES2D=ON \
      -DWITH_OPENCV_CALIB3D=ON \
      -DWITH_OPENCV_FLANN=ON \
      -DWITH_OPENCV_CORE=ON \
      -DCMAKE_INSTALL_PREFIX=./install \
      -DCMAKE_BUILD_TYPE=Release \
      ../opencv-4.8.0
```

### Step 4: Build and Install

```bash
# Build OpenCV (this may take 1-2 hours)
cmake --build . --config Release --parallel

# Install headers and libraries
cmake --build . --target install
```

### Step 5: Verify Build

After successful build, you should have:

```
opencv_static/
├── install/
│   └── sdk/
│       └── native/
│           ├── jni/
│           │   ├── include/opencv2/     # Headers
│           │   ├── libs/arm64-v8a/      # Static libraries
│           │   └── 3rdparty/libs/arm64-v8a/  # 3rdparty libraries
│           └── OpenCV.mk                 # Build configuration
└── lib/
    └── arm64-v8a/                       # Static libraries
```

## Integration with Qt Service

### Current Integration Approach

```cmake
# In app/CMakeLists.txt
set(OpenCV_STATIC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../opencv_static")
set(OpenCV_STATIC_LIB_DIR "${OpenCV_STATIC_DIR}/lib/${ANDROID_ABI}")
set(OpenCV_STATIC_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/opencv/${ANDROID_ABI}/sdk/native/jni/include")

# Link OpenCV libraries separately
target_link_libraries(qtservice-jni PRIVATE ${OPENCV_LIBS_ORDERED})
```

### Recommended Integration Approach

For a **packaged Qt service**, integrate OpenCV directly into Qt service build:

```cmake
# In Qt service CMakeLists.txt
find_package(OpenCV REQUIRED)

target_link_libraries(QtService
    ${OpenCV_LIBS}
    Qt6::Core
    Qt6::Positioning
    Qt6::HttpServer
)
```

## Current Setup

### What We Have

✅ **OpenCV 4.8.0 static libraries** in `opencv_static/lib/arm64-v8a/`
✅ **3rdparty libraries** (libpng, libjpeg, libtiff, etc.)
✅ **Tegra optimizations** (carotene, tegra_hal)
✅ **CMake configuration** for Android


```
opencv_static/
├── lib/arm64-v8a/
│   ├── libopencv_core.a (39MB)
│   ├── libopencv_imgproc.a (43MB)
│   ├── libopencv_imgcodecs.a (7.8MB)
│   ├── libopencv_videoio.a (6.5MB)
│   ├── libopencv_highgui.a (2.8MB)
│   ├── libopencv_features2d.a (12MB)
│   ├── libopencv_calib3d.a (34MB)
│   ├── libopencv_video.a (8.7MB)
│   ├── libopencv_photo.a (8.2MB)
│   ├── libopencv_ml.a (9.1MB)
│   ├── libopencv_flann.a (8.9MB)
│   ├── libopencv_objdetect.a (20MB)
│   ├── libopencv_stitching.a (10MB)
│   ├── libopencv_dnn.a (72MB)
│   └── libopencv_gapi.a (158MB)
├── 3rdparty/lib/arm64-v8a/
│   ├── libade.a (7.8MB)
│   ├── liblibpng.a (1.7MB)
│   ├── liblibwebp.a (5.5MB)
│   ├── liblibjpeg-turbo.a (3.8MB)
│   ├── liblibtiff.a (2.8MB)
│   ├── libittnotify.a (360KB)
│   ├── libcpufeatures.a (30KB)
│   ├── libtegra_hal.a (10MB)
│   └── liblibopenjp2.a (2.5MB)
└── [Build artifacts - excluded by .gitignore]
```

## Usage Examples

### Basic OpenCV Operations

```cpp
#include <opencv2/opencv.hpp>

// Image loading
cv::Mat image = cv::imread("image.jpg");
if (image.empty()) {
    // Handle error
    return false;
}

// Color conversion
cv::Mat gray;
cv::cvtColor(image, gray, cv::COLOR_BGR2GRAY);

// Image processing
cv::Mat blurred;
cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);

// Save result
cv::imwrite("output.jpg", blurred);
```

### Integration with Qt Service

```cpp
class ImageProcessor {
public:
    bool processImage(const std::string& inputPath, const std::string& outputPath) {
        try {
            cv::Mat image = cv::imread(inputPath);
            if (image.empty()) return false;
            
            cv::Mat processed = processWithOpenCV(image);
            
            return cv::imwrite(outputPath, processed);
        } catch (const cv::Exception& e) {
            qWarning() << "OpenCV error:" << e.what();
            return false;
        }
    }
    
private:
    cv::Mat processWithOpenCV(const cv::Mat& input) {
        cv::Mat output;
        cv::cvtColor(input, output, cv::COLOR_BGR2GRAY);
        return output;
    }
};
```

### JNI Interface

```cpp
extern "C" JNIEXPORT jboolean JNICALL
Java_com_yourcompany_qtservice_QtServiceWrapper_processImage(
    JNIEnv *env, jobject thiz, jstring inputPath, jstring outputPath) {
    
    const char* input = env->GetStringUTFChars(inputPath, 0);
    const char* output = env->GetStringUTFChars(outputPath, 0);
    
    ImageProcessor processor;
    bool result = processor.processImage(input, output);
    
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    
    return result ? JNI_TRUE : JNI_FALSE;
}
