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
find_package(Qt6 6.5.3 REQUIRED COMPONENTS Core)   # add more components if your code uses them

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

# Android system libs
find_library(log-lib log)

# Link order: your static lib first, then Qt modules it depends on.
# Add Qt6::Network, Qt6::Concurrent, etc., IF your static lib uses them.
target_link_libraries(qtservice-jni PRIVATE
  QtAndroidService
  Qt6::Core
  ${log-lib}
)

# Good hygiene
target_compile_definitions(qtservice-jni PRIVATE ANDROID)
set_target_properties(qtservice-jni PROPERTIES
  POSITION_INDEPENDENT_CODE ON
  CXX_VISIBILITY_PRESET hidden
  VISIBILITY_INLINES_HIDDEN YES
)
