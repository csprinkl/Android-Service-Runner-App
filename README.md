# Qt Timer Service — Android Foreground Service Host for Qt

A minimal Android app + **foreground Service** that loads a **Qt-based native library** and runs a simple timer in the background.  
Use this project as a **drop-in host** for your Qt service library (e.g., `libQtAndroidService_arm64-v8a.a`).

---

## Contents
- [What is this?](#what-is-this)
- [Quick Start](#quick-start)
- [Project Layout](#project-layout)
- [How It Works](#how-it-works)
- [JNI Contract (Direct `Java_...` names)](#jni-contract-direct-java_-names)
- [Your Native C API (what your library must export)](#your-native-c-api-what-your-library-must-export)
- [CMake Wiring (link your lib + optional deps)](#cmake-wiring-link-your-lib--optional-deps)
- [Android Manifest & Permissions](#android-manifest--permissions)
- [Optional Dependencies & Notes](#optional-dependencies--notes)
  - [Qt modules](#qt-modules)
  - [OpenCV](#opencv)
  - [LicenseSpring](#licensespring)
- [Build & Run](#build--run)
- [Troubleshooting](#troubleshooting)

---

## What is this?

This is an Android host app that starts a **foreground Service** (`QtServiceWrapper`) which loads a **Qt-powered native library** and runs a simple timer (or any background task) on a Qt event loop.  
It gives you a clean separation:

- **Java/Kotlin side:** minimal UI + Android lifecycle + a Service.
- **C++ side (Qt):** your business logic with `QCoreApplication`, `QTimer`, threads, etc.

---

## Quick Start

1. **Drop your native library**  
   - Static: `app/src/main/cpp/libs/<ABI>/libQtAndroidService_arm64-v8a.a` (or your own name)
   - Headers: `app/src/main/cpp/include/`

2. **Expose the C API** (see **Your Native C API**).

3. **Wire it in CMake**  
   - Edit `app/CMakeLists.txt` to add/import your lib and link it in `target_link_libraries(...)`.

4. **Build & Run**  
   - `./gradlew :app:installDebug`
   - Launch the app → tap **Start Service** → check `logcat` for `QtService` messages.

---

## Project Layout

```text
app/
├─ CMakeLists.txt
├─ src/
│  └─ main/
│     ├─ java/
│     │  ├─ org/example/androidservicerunnerapp/
│     │  │  └─ MainActivity.java
│     │  └─ org/qtproject/qtservice/
│     │     └─ QtServiceWrapper.java   # Foreground Service; loads JNI; calls native init/start/stop
│     ├─ cpp/
│     │  ├─ native-lib.cpp             # JNI bridge (calls your C API)
│     │  ├─ include/                   # Put your public headers here
│     │  └─ libs/                      # Optional: prebuilt .a per ABI
│     ├─ res/
│     │  └─ layout/
│     │     └─ activity_main.xml       # Simple control UI
│     └─ AndroidManifest.xml           # Declares the foreground Service
├─ build.gradle                        # May reference absolute paths for Qt/OpenCV as needed
├─ settings.gradle
└─ gradle.properties
```

> Note: `CMakeLists.txt` lives under `app/` (not project root).

---

## How It Works

- **MainActivity** → simple buttons to **Start/Stop** the foreground service and show logs.
- **QtServiceWrapper (Service)**:
  - Creates a persistent notification (required by Android for long-running work).
  - Loads the JNI bridge: `System.loadLibrary("qtservice-jni")`.
  - Invokes native entry points: `nativeInitializeService()`, `nativeStartService()`, `nativeStopService()`, etc.
- **native-lib.cpp (JNI)** → calls your C API:
  - `qt_service_initialize()` should construct a `QCoreApplication` (preferably on its own thread) and set up your timers/work.
  - `qt_service_start()` kicks the timer/work off.
  - `qt_service_stop()` and `qt_service_cleanup()` shut everything down cleanly.

---

## JNI Contract (Direct `Java_...` names)

This project maps Java ↔ C++ using **direct JNI function names** so the native methods must be defined with the exact **mangled** names:

```cpp
extern "C" JNIEXPORT jstring JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeGetVersion(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeInitializeService(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeStartService(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeStopService(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jboolean JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeIsServiceRunning(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT void JNICALL
Java_org_qtproject_qtservice_QtServiceWrapper_nativeCleanupService(JNIEnv* env, jobject thiz);
```

> If you change the Java package/class (`org.qtproject.qtservice.QtServiceWrapper`), you must rename these C functions accordingly (or switch to `RegisterNatives` to avoid hardcoded names).

---

## Your Native C API (what your library must export)

Your Qt library **must** export these C functions (called by the JNI bridge):

```c
#ifdef __cplusplus
extern "C" {
#endif

const char* qt_service_get_version();   // Return a short version string
bool        qt_service_initialize();    // Create QCoreApplication, set up timers/threads
bool        qt_service_start();         // Start your work (QTimer, worker, etc.)
bool        qt_service_stop();          // Graceful stop
bool        qt_service_is_running();    // Health/heartbeat for UI
void        qt_service_cleanup();       // Destroy app state, free resources

#ifdef __cplusplus
}
#endif
```

**Threading tip:** run the Qt event loop on a dedicated thread; attach that thread to the JVM if you use any Qt Android JNI helpers.

---

## CMake Wiring (link your lib + optional deps)

Edit **`app/CMakeLists.txt`** and make sure your targets are added and linked:

```cmake
cmake_minimum_required(VERSION 3.22)
project(qtservice-jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)

# 1) Build the JNI bridge .so
add_library(qtservice-jni SHARED
    src/main/cpp/native-lib.cpp
)

target_include_directories(qtservice-jni PRIVATE
    src/main/cpp/include
)

# 2) Link your prebuilt Qt service library (static or shared)
#    Place per-ABI libs under: src/main/cpp/libs/<ABI>/<lib>.a
add_library(QtAndroidService STATIC IMPORTED)
set_target_properties(QtAndroidService PROPERTIES
    IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/src/main/cpp/libs/${ANDROID_ABI}/libQtAndroidService_arm64-v8a.a"
)

# 3) (Optional) Qt modules (static or shared) — see 'Qt modules' below for details
# find_package(Qt6 COMPONENTS Core Concurrent Multimedia Positioning SerialPort Sql HttpServer REQUIRED)

# 4) (Optional) OpenCV
# set(OpenCV_DIR "/absolute/path/to/OpenCV-android-sdk/sdk/native/jni")
# find_package(OpenCV QUIET)

# 5) (Optional) LicenseSpring (native lib)
# add_library(licensespring STATIC IMPORTED)
# set_target_properties(licensespring PROPERTIES
#     IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/src/main/cpp/libs/${ANDROID_ABI}/liblicensespring.a"
# )

target_link_libraries(qtservice-jni
    QtAndroidService
    # Qt6::Core Qt6::Concurrent Qt6::Multimedia Qt6::Positioning Qt6::SerialPort Qt6::Sql Qt6::HttpServer
    # ${OpenCV_LIBS}
    # licensespring
    log
    android
)
```

> If you use a full Qt-for-Android static toolchain, prefer `find_package(Qt6 ...)` with the official Android mkspec/toolchain instead of manual IMPORTED libs.

---

## Android Manifest & Permissions

```xml
<!-- app/src/main/AndroidManifest.xml -->

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- Targeting 33+: ask POST_NOTIFICATIONS at runtime -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Optional/feature-based permissions -->
<!-- Positioning -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!-- Multimedia (audio capture) -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<!-- Serial over USB (if applicable) -->
<uses-feature android:name="android.hardware.usb.host" android:required="false" />

<application ...>
    <service
        android:name="org.qtproject.qtservice.QtServiceWrapper"
        android:exported="false"
        android:foregroundServiceType="dataSync">
        <intent-filter>
            <action android:name="org.qtproject.qtservice.START" />
            <action android:name="org.qtproject.qtservice.STOP" />
        </intent-filter>
    </service>
</application>
```

> Add or remove permissions based on the modules you actually use.

---

## Dependencies that need integration (preferably in the library instead of here, but this would be how to add them here)

### Qt modules

Module Support to be added:

- **Concurrent**: thread pools & futures (`Qt6::Concurrent`)
- **Multimedia**: audio/video (`Qt6::Multimedia`), may need codecs/plugins
- **Positioning**: GPS/network location (`Qt6::Positioning`), requires location permissions
- **SerialPort**: serial comms (`Qt6::SerialPort`), often with USB host features
- **Sql**: database drivers (`Qt6::Sql`), include the driver plugin you use (e.g., SQLite)
- **HttpServer**: lightweight HTTP server (`Qt6::HttpServer`)

Link them in `target_link_libraries(...)`. For static builds, ensure required **plugins** are also linked/bundled (e.g., `qsqlite`, `qmediaplayer` backends).

### OpenCV

- Drop the Android SDK, set `OpenCV_DIR` to the `sdk/native/jni` folder, run `find_package(OpenCV)` and link `${OpenCV_LIBS}`.
- If using **shared** OpenCV `.so`, make sure they’re packaged in your APK (`jniLibs/<ABI>` or via CMake `target_link_libraries` + Gradle packaging).

### LicenseSpring

- **Java/AAR route:** put the AAR in `app/libs/` and add in `app/build.gradle`:
  ```gradle
  dependencies {
      implementation files("libs/licensespring.aar")
  }
  ```
  Initialize per LS docs on app/service start.

- **Native route:** place per-ABI static or shared LS libs under `src/main/cpp/libs/<ABI>/` and link in CMake (see example above). Add `INTERNET` permission if your flow requires network calls.

---

## Build & Run

```bash
# From project root
./gradlew :app:assembleDebug
./gradlew :app:installDebug

# Then launch the app on device/emulator and tap "Start Service".
# Logs:
adb logcat | grep -E "QtService|QtAndroidService|QtServiceJNI"
```

**ABI note:** provide your `.a`/`.so` for each ABI you target (e.g., `arm64-v8a`, `armeabi-v7a`). Align Gradle’s `abiFilters` with what you ship.

---

## Troubleshooting

- **SIGSEGV in `QJniEnvironment` / `QCoreApplicationPrivate::init`**  
  Ensure:
  1) `System.loadLibrary("qtservice-jni")` runs before any native calls.  
  2) JNI functions use the **exact** `Java_org_qtproject_qtservice_QtServiceWrapper_*` names (or switch to `RegisterNatives`).  
  3) Your Qt event loop runs on a thread **attached** to the JVM before using Qt Android helpers.  
  4) All required Qt static libs (and plugins) are linked and available.

- **“Class not found”** when calling native functions  
  The class must be `org.qtproject.qtservice.QtServiceWrapper`. If you move it, update the JNI function names accordingly.

- **Service starts but no work happens**  
  Check that `qt_service_start()` actually arms your `QTimer` / starts your worker thread, and watch `logcat` for your own tags.
