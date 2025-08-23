cmake_minimum_required(VERSION 3.21)  # 3.21+ recommended for Qt6+Android
project(androidservicerunnerapp LANGUAGES CXX)

# ---- Toolchain/ABI basics ----------------------------------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android must use the shared STL with Qt
# (also ensure you are NOT adding -static-libstdc++ anywhere else)
set(CMAKE_ANDROID_STL_TYPE c++_shared)

# prints to confirm args arrived
message(STATUS "CMAKE_PREFIX_PATH = ${CMAKE_PREFIX_PATH}")
message(STATUS "Qt6_DIR          = ${Qt6_DIR}")
message(STATUS "QT_HOST_PATH     = ${QT_HOST_PATH}")

# ---- Find Qt built for Android (6.5.3) --------------------------------------
# Use qt-cmake to configure, OR set CMAKE_PREFIX_PATH to your Qt 6.5.3 Android install
# e.g. /opt/Qt/6.5.3/android_arm64
find_package(Qt6 6.5.3 REQUIRED COMPONENTS Core Positioning HttpServer)   # Available Qt components

# ---- Static OpenCV Configuration ---------------------------------------------
# Use your statically built OpenCV libraries
set(OpenCV_STATIC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../opencv_static")
set(OpenCV_STATIC_LIB_DIR "${OpenCV_STATIC_DIR}/lib/${ANDROID_ABI}")
set(OpenCV_STATIC_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/opencv/${ANDROID_ABI}/sdk/native/jni/include")

# Check if static OpenCV libraries exist
if(EXISTS "${OpenCV_STATIC_LIB_DIR}")
  message(STATUS "Static OpenCV libraries found at: ${OpenCV_STATIC_LIB_DIR}")
  
  # List all available OpenCV static libraries
  file(GLOB OpenCV_STATIC_LIBS "${OpenCV_STATIC_LIB_DIR}/libopencv_*.a")
  list(LENGTH OpenCV_STATIC_LIBS OpenCV_STATIC_LIBS_COUNT)
  message(STATUS "Found ${OpenCV_STATIC_LIBS_COUNT} OpenCV static libraries")
  
  # Check if include directory exists
  if(EXISTS "${OpenCV_STATIC_INCLUDE_DIR}")
    message(STATUS "OpenCV headers found at: ${OpenCV_STATIC_INCLUDE_DIR}")
    set(OpenCV_STATIC_FOUND TRUE)
  else()
    message(WARNING "OpenCV headers not found at: ${OpenCV_STATIC_INCLUDE_DIR}")
    set(OpenCV_STATIC_FOUND FALSE)
  endif()
else()
  message(WARNING "Static OpenCV libraries not found at: ${OpenCV_STATIC_LIB_DIR}")
  set(OpenCV_STATIC_FOUND FALSE)
endif()

# ---- 3rdparty Library Configuration ----------------------------------------------
# OpenCV static build requires 3rdparty libraries
set(THIRDPARTY_LIB_DIR "${OpenCV_STATIC_DIR}/3rdparty/lib/${ANDROID_ABI}")
if(EXISTS "${THIRDPARTY_LIB_DIR}")
  message(STATUS "3rdparty libraries found at: ${THIRDPARTY_LIB_DIR}")
  set(THIRDPARTY_FOUND TRUE)
else()
  message(STATUS "3rdparty libraries not found at: ${THIRDPARTY_LIB_DIR}")
  set(THIRDPARTY_FOUND FALSE)
endif()

# ---- LicenseSpring Configuration ---------------------------------------------
# Set LicenseSpring paths - you'll need to download LicenseSpring for Android
set(LicenseSpring_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/licensespring/${ANDROID_ABI}")
if(EXISTS "${LicenseSpring_DIR}/libLicenseSpring.so")
  message(STATUS "LicenseSpring found at: ${LicenseSpring_DIR}")
  set(LicenseSpring_FOUND TRUE)
else()
  message(WARNING "LicenseSpring not found at ${LicenseSpring_DIR}. Please download LicenseSpring for Android.")
  set(LicenseSpring_FOUND FALSE)
endif()

# ---- Your prebuilt static library -------------------------------------------
# Path to your static library (built with -fPIC, same NDK/ABI/Qt)
set(QT_STATIC_LIB ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/libs/${ANDROID_ABI}/libQtAndroidService_${ANDROID_ABI}.a)
if(NOT EXISTS "${QT_STATIC_LIB}")
  message(FATAL_ERROR "Static service lib not found: ${QT_STATIC_LIB}")
else()
  message(STATUS "Using static service lib: ${QT_STATIC_LIB}")
endif()

add_library(QtAndroidService STATIC IMPORTED GLOBAL)
set_target_properties(QtAndroidService PROPERTIES
  IMPORTED_LOCATION "${QT_STATIC_LIB}"
  # If you have public headers for that lib:
  # INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/include"
)

# ---- JNI wrapper shared library ---------------------------------------------
add_library(qtservice-jni SHARED
  src/main/cpp/native-lib.cpp   # JNI entrypoints calling into your service
)

# Set output directory to ensure Android can find the library
# Use standard Android NDK output paths
set_target_properties(qtservice-jni PROPERTIES
  LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
  RUNTIME_OUTPUT_DIRECTORY "${CMAKE_RUNTIME_OUTPUT_DIRECTORY}"
)

# Android system libs
find_library(log-lib log)
find_library(z-lib z)

if(z-lib)
  message(STATUS "zlib library found: ${z-lib}")
else()
  message(WARNING "zlib library not found - OpenCV persistence operations may fail")
endif()

# Link order: your static lib first, then Qt modules it depends on.
# Add Qt6::Network, Qt6::Concurrent, etc., IF your static lib uses them.
target_link_libraries(qtservice-jni PRIVATE
  QtAndroidService
  Qt6::Core
  Qt6::Positioning
  Qt6::HttpServer
  ${log-lib}
  ${z-lib}
)

# Link static OpenCV libraries if found
if(OpenCV_STATIC_FOUND)
  # Add OpenCV include directories
  target_include_directories(qtservice-jni PRIVATE ${OpenCV_STATIC_INCLUDE_DIR})
  
  # Check if all 3rdparty libraries exist
  set(THIRDPARTY_LIBS "")
  set(REQUIRED_3RDPARTY_LIBS
    "libade.a"
    "liblibpng.a"
    "liblibwebp.a"
    "liblibjpeg-turbo.a"
    "liblibtiff.a"
    "libcpufeatures.a"
    "liblibopenjp2.a"
    "libittnotify.a"
    "libtegra_hal.a"
  )
  
  foreach(LIB ${REQUIRED_3RDPARTY_LIBS})
    set(LIB_PATH "${THIRDPARTY_LIB_DIR}/${LIB}")
    if(EXISTS "${LIB_PATH}")
      list(APPEND THIRDPARTY_LIBS "${LIB_PATH}")
      message(STATUS "Found 3rdparty library: ${LIB}")
    else()
      message(WARNING "Missing 3rdparty library: ${LIB_PATH}")
    endif()
  endforeach()
  
  # Create a list of OpenCV libraries in the correct order
  set(OPENCV_LIBS_ORDERED
    "${OpenCV_STATIC_LIB_DIR}/libopencv_core.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_imgproc.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_imgcodecs.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_videoio.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_highgui.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_features2d.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_calib3d.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_video.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_photo.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_ml.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_flann.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_objdetect.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_stitching.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_dnn.a"
    "${OpenCV_STATIC_LIB_DIR}/libopencv_gapi.a"
  )
  
  # Link 3rdparty libraries FIRST (dependencies)
  if(THIRDPARTY_LIBS)
    target_link_libraries(qtservice-jni PRIVATE ${THIRDPARTY_LIBS})
    message(STATUS "3rdparty libraries linked: ${THIRDPARTY_LIBS}")
  else()
    message(WARNING "No 3rdparty libraries found - linking may fail")
  endif()
  
  # Link OpenCV libraries in the correct order AFTER 3rdparty libraries
  target_link_libraries(qtservice-jni PRIVATE ${OPENCV_LIBS_ORDERED})
  message(STATUS "OpenCV static libraries linked in order: ${OPENCV_LIBS_ORDERED}")
  
  # Link system libraries LAST (only math and atomic, no pthread)
  target_link_libraries(qtservice-jni PRIVATE m atomic ${z-lib} dl)
  message(STATUS "System libraries linked: m atomic zlib dl")
  
  # Add additional system libraries that OpenCV might need
  target_link_libraries(qtservice-jni PRIVATE android)
  
  # Add OpenCV compile definitions
  target_compile_definitions(qtservice-jni PRIVATE 
    OPENCV_FOUND
    OPENCV_STATIC
    OPENCV_VERSION_MAJOR=4
    OPENCV_VERSION_MINOR=8
    OPENCV_DISABLE_CAROTENE=1
    CV_DISABLE_OPTIMIZATION=1
    WITH_CAROTENE=OFF
    CAROTENE_DISABLE=1
    OPENCV_DISABLE_DEPRECATED=1
    OPENCV_DISABLE_LEGACY_COMPATIBILITY=1
    OPENCV_DISABLE_VERSION_CHECK=1
    OPENCV_DISABLE_ABI_CHECK=1
    OPENCV_DISABLE_OPENCL=1
    OPENCV_DISABLE_CUDA=1
    OPENCV_DISABLE_IPP=1
    OPENCV_DISABLE_NEON=1
    OPENCV_DISABLE_AVX=1
    OPENCV_DISABLE_SSE=1
    OPENCV_DISABLE_SIMD=1
    OPENCV_DISABLE_ALGORITHM_HINT=1
    OPENCV_FORCE_4_8_API=1
    OPENCV_DISABLE_NEW_API=1
  )
  
  message(STATUS "Static OpenCV linked successfully")
  message(STATUS "OpenCV include directory: ${OpenCV_STATIC_INCLUDE_DIR}")
else()
  message(WARNING "Static OpenCV not linked - libraries or headers not found")
  message(WARNING "OpenCV functionality will not be available")
endif()

# Conditionally link LicenseSpring if found
if(LicenseSpring_FOUND)
  target_link_libraries(qtservice-jni PRIVATE "${LicenseSpring_DIR}/libLicenseSpring.so")
  message(STATUS "LicenseSpring linked successfully")
endif()

# Good hygiene
target_compile_definitions(qtservice-jni PRIVATE ANDROID)
set_target_properties(qtservice-jni PROPERTIES
  POSITION_INDEPENDENT_CODE ON
  CXX_VISIBILITY_PRESET default
  VISIBILITY_INLINES_HIDDEN NO
)

# Add OpenCV-specific compile options
if(OpenCV_STATIC_FOUND)
  target_compile_options(qtservice-jni PRIVATE
    -fvisibility=default
    -DOPENCV_ABI_COMPATIBILITY=480
    -DCAROTENE_DISABLE=1
    -DWITH_CAROTENE=OFF
    -DOPENCV_DISABLE_DEPRECATED=1
    -DOPENCV_DISABLE_LEGACY_COMPATIBILITY=1
    -DOPENCV_DISABLE_VERSION_CHECK=1
    -DOPENCV_DISABLE_ABI_CHECK=1
    -DOPENCV_DISABLE_OPENCL=1
    -DOPENCV_DISABLE_CUDA=1
    -DOPENCV_DISABLE_IPP=1
    -DOPENCV_DISABLE_NEON=1
    -DOPENCV_DISABLE_AVX=1
    -DOPENCV_DISABLE_SSE=1
    -DOPENCV_DISABLE_SIMD=1
    -DOPENCV_DISABLE_ALGORITHM_HINT=1
    -DOPENCV_FORCE_4_8_API=1
    -DOPENCV_DISABLE_NEW_API=1
    -DOPENCV_DISABLE_ALGORITHM_HINT_OVERLOADS=1
    -DOPENCV_FORCE_LEGACY_API=1
  )
  
  # Ensure OpenCV symbols are visible
  target_compile_definitions(qtservice-jni PRIVATE
    OPENCV_ABI_COMPATIBILITY=480
    OPENCV_ENABLE_NONFREE=ON
    OPENCV_DISABLE_CAROTENE=1
    CV_DISABLE_OPTIMIZATION=1
    WITH_CAROTENE=OFF
    CAROTENE_DISABLE=1
    OPENCV_DISABLE_DEPRECATED=1
    OPENCV_DISABLE_LEGACY_COMPATIBILITY=1
    OPENCV_DISABLE_VERSION_CHECK=1
    OPENCV_DISABLE_ABI_CHECK=1
    OPENCV_DISABLE_OPENCL=1
    OPENCV_DISABLE_CUDA=1
    OPENCV_DISABLE_IPP=1
    OPENCV_DISABLE_NEON=1
    OPENCV_DISABLE_AVX=1
    OPENCV_DISABLE_SSE=1
    OPENCV_DISABLE_SIMD=1
    OPENCV_DISABLE_ALGORITHM_HINT=1
    OPENCV_FORCE_4_8_API=1
    OPENCV_DISABLE_NEW_API=1
  )
endif()
