OpenCV 4.8.0 for Android - Expected Directory Structure

This directory should contain the OpenCV Android SDK files.

Expected structure:
opencv/arm64-v8a/
└── sdk/
    └── native/
        └── jni/
            ├── OpenCVConfig.cmake
            ├── libs/
            │   └── arm64-v8a/
            │       ├── libopencv_java4.so
            │       ├── libopencv_core.so
            │       ├── libopencv_imgproc.so
            │       └── (other OpenCV libraries)
            └── include/
                └── opencv4/
                    └── opencv2/
                        ├── core.hpp
                        ├── imgproc.hpp
                        └── (other OpenCV headers)

To install OpenCV:
1. Download opencv-4.8.0-android-sdk.zip from https://github.com/opencv/opencv/releases
2. Extract the contents here
3. Ensure the jni/ directory contains OpenCVConfig.cmake

