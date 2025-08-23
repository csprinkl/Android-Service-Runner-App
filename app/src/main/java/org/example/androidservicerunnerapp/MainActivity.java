package org.example.androidservicerunnerapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Consumer app that demonstrates using the QtAndroidService static library.
 * Provides start/stop controls and live logcat output display.
 */
public class MainActivity extends Activity {
    private static final String TAG = "QtServiceTestApplication";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 100;

    private Button serviceToggleButton;
    private TextView consoleOutput;
    private ScrollView scrollView;

    private boolean serviceRunning = false;
    private boolean wasServiceRunning = false; // Track if service was previously running
    private boolean loggingActive = false;
    private boolean notificationPermissionGranted = false;
    private Handler mainHandler;
    private Thread logcatThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize UI components
        initializeUI();

        // Check and request notification permission
        checkAndRequestNotificationPermission();

        // Start logcat monitoring
        startLogcatMonitoring();

        appendToConsole("=== Qt Service Test App Started ===");
        appendToConsole("Ready to start Qt Service Library");

        Log.i(TAG, "Qt Service Test App initialized");
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires POST_NOTIFICATIONS permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                // Permission already granted
                notificationPermissionGranted = true;
                appendToConsole("✓ POST_NOTIFICATIONS permission granted");
                Log.d(TAG, "✓ POST_NOTIFICATIONS permission already granted");
            } else {
                // Request the permission
                appendToConsole("Requesting POST_NOTIFICATIONS permission for foreground service...");
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission...");

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        } else {
            // Android < 13 doesn't need this permission
            notificationPermissionGranted = true;
            appendToConsole("✓ Running on Android < 13 - notification permission not required");
            Log.d(TAG, "✓ Running on Android < 13 - no permission needed");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                notificationPermissionGranted = true;
                appendToConsole("✓ POST_NOTIFICATIONS permission granted");
                Log.d(TAG, "✓ POST_NOTIFICATIONS permission granted by user");
                Toast.makeText(this, "Notification permission granted - service can run in foreground", Toast.LENGTH_LONG).show();
            } else {
                // Permission denied
                notificationPermissionGranted = false;
                appendToConsole("⚠ POST_NOTIFICATIONS permission denied");
                appendToConsole("Service will still work but may be limited by background restrictions");
                Log.w(TAG, "POST_NOTIFICATIONS permission denied by user");

                // Show explanation to user
                Toast.makeText(this,
                        "Notification permission denied. Service will run but may be killed by system.",
                        Toast.LENGTH_LONG).show();

                // You could show a more detailed explanation dialog here if needed
                showPermissionExplanation();
            }
        }
    }

    private void showPermissionExplanation() {
        // Optional: Show a dialog explaining why the permission is important
        appendToConsole("Note: Without notification permission, the Qt service may be stopped by Android");
        appendToConsole("to save battery. You can grant the permission later in Settings > Apps > Permissions.");
    }

    private void initializeUI() {
        serviceToggleButton = findViewById(R.id.button_toggle_service);
        consoleOutput = findViewById(R.id.console_output);
        scrollView = findViewById(R.id.scroll_view);

        // Set initial state
        updateToggleButton();

        serviceToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceRunning) {
                    stopQtService();
                } else {
                    startQtService();
                }
            }
        });

        // Clear console button
        Button clearButton = findViewById(R.id.button_clear_console);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearConsole();
            }
        });

        // OpenCV test button
        Button opencvTestButton = findViewById(R.id.button_test_opencv);
        opencvTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testOpenCV();
            }
        });

        // Reset service button
        Button resetServiceButton = findViewById(R.id.button_reset_service);
        resetServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetServiceState();
            }
        });

        // Test OpenCV in service button
        Button testOpenCVServiceButton = findViewById(R.id.button_test_opencv_service);
        testOpenCVServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testOpenCVInService();
            }
        });
    }

    private void startQtService() {
        if (serviceRunning) {
            Toast.makeText(this, "Service is already running", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check permission status before starting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
            appendToConsole("⚠ Warning: Starting service without notification permission");
            appendToConsole("Service may be killed by system due to background restrictions");
        }

        appendToConsole(">>> Starting Qt Service...");
        Log.i(TAG, "Starting Qt Service from test app");

        try {
            // If we're restarting, wait a bit for cleanup
            if (wasServiceRunning) {
                appendToConsole("Waiting for previous service cleanup...");
                Thread.sleep(2000); // Wait 2 seconds for cleanup
                wasServiceRunning = false; // Reset the flag
            }
            Intent serviceIntent = new Intent(this, org.qtproject.qtservice.QtServiceWrapper.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
                if (notificationPermissionGranted) {
                    appendToConsole("Started as foreground service with notification permission");
                } else {
                    appendToConsole("Started as foreground service (but notification permission missing)");
                }
            } else {
                startService(serviceIntent);
                appendToConsole("Started as background service");
            }

            serviceRunning = true;
            updateToggleButton();

            appendToConsole("Qt Service start request sent");
            appendToConsole("Watch for Qt initialization and outputs below...");

            Toast.makeText(this, "Qt Service Started", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            String errorMsg = "Failed to start Qt service: " + e.getMessage();
            appendToConsole("ERROR: " + errorMsg);
            Log.e(TAG, errorMsg, e);
            Toast.makeText(this, "Failed to start service", Toast.LENGTH_LONG).show();
        }
    }

    private void stopQtService() {
        if (!serviceRunning) {
            Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show();
            return;
        }

        appendToConsole(">>> Stopping Qt Service...");
        Log.i(TAG, "Stopping Qt Service from test app");

        try {
            Intent serviceIntent = new Intent(this, org.qtproject.qtservice.QtServiceWrapper.class);
            stopService(serviceIntent);

            wasServiceRunning = true; // Mark that service was running
            serviceRunning = false;
            updateToggleButton();

            appendToConsole("Qt Service stop request sent");
            appendToConsole("Waiting for service cleanup before restart...");
            Toast.makeText(this, "Qt Service Stopped", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            String errorMsg = "Failed to stop Qt service: " + e.getMessage();
            appendToConsole("ERROR: " + errorMsg);
            Log.e(TAG, errorMsg, e);
            Toast.makeText(this, "Failed to stop service", Toast.LENGTH_LONG).show();
        }
    }

    private void startLogcatMonitoring() {
        if (loggingActive) {
            return;
        }

        loggingActive = true;
        logcatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Monitor logcat for Qt service related messages
                    Process process = Runtime.getRuntime().exec(new String[]{
                            "logcat",
                            "-s",
                            "QtServiceWrapper:*",
                            "QtService:*",
                            "libQtAndroidService*:*",
                            "QtAndroidService"
                    });

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));

                    String line;
                    while (loggingActive && (line = reader.readLine()) != null) {
                        final String logLine = line;

                        // Filter and format interesting log lines
                        if (isRelevantLogLine(logLine)) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    appendToConsole(formatLogLine(logLine));
                                }
                            });
                        }
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Error reading logcat", e);
                }
            }
        });

        logcatThread.start();
        appendToConsole("Started monitoring Qt service logs...");
    }

    private boolean isRelevantLogLine(String line) {
        return line.contains("QtServiceWrapper") ||
                line.contains("QtService") ||
                line.contains("Timer") ||
                line.contains("Qt initialization") ||
                line.contains("Qt library") ||
                line.contains("Foreground service") ||
                line.contains("POST_NOTIFICATIONS") ||
                line.contains("notification permission") ||
                line.contains("libQtAndroidService") ||
                line.contains("OpenCV") ||
                line.contains("QtServiceJNI") ||
                line.contains("OpenCV Test") ||
                line.contains("cv::") ||
                line.contains("libcpufeatures");
    }

    private String formatLogLine(String rawLine) {
        // Extract timestamp and message for cleaner display
        try {
            // Format: MM-DD HH:MM:SS.mmm PID-TID TAG LEVEL: Message
            if (rawLine.length() > 31) {
                String timestamp = rawLine.substring(0, 18); // MM-DD HH:MM:SS.mmm
                String rest = rawLine.substring(31); // Skip PID-TID section

                // Find tag and message
                int colonIndex = rest.indexOf(':');
                if (colonIndex > 0) {
                    String tag = rest.substring(0, colonIndex).trim();
                    String message = rest.substring(colonIndex + 1).trim();
                    return String.format("[%s] %s: %s", timestamp, tag, message);
                }
            }
        } catch (Exception e) {
            // Fall back to original line if parsing fails
        }

        return rawLine;
    }

    private void appendToConsole(String message) {
        if (consoleOutput != null) {
            String timestamp = java.text.DateFormat.getTimeInstance().format(new java.util.Date());
            String formattedMessage = String.format("[%s] %s\n", timestamp, message);

            consoleOutput.append(formattedMessage);

            // Auto-scroll to bottom
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }

        // Also log to Android log
        Log.i(TAG, message);
    }

    private void clearConsole() {
        if (consoleOutput != null) {
            consoleOutput.setText("");
            appendToConsole("Console cleared");

            // Re-display permission status
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (notificationPermissionGranted) {
                    appendToConsole("✓ POST_NOTIFICATIONS permission: GRANTED");
                } else {
                    appendToConsole("⚠ POST_NOTIFICATIONS permission: DENIED");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop logcat monitoring
        loggingActive = false;
        if (logcatThread != null) {
            logcatThread.interrupt();
        }

        Log.i(TAG, "Qt Service Consumer App destroyed");
    }

    private void updateToggleButton() {
        if (serviceRunning) {
            serviceToggleButton.setText("STOP\nQt Service");
            serviceToggleButton.setBackgroundColor(0xFFf44336); // Red
        } else {
            serviceToggleButton.setText("START\nQt Service");
            serviceToggleButton.setBackgroundColor(0xFF4CAF50); // Green
        }
    }

    private void testOpenCV() {
        appendToConsole("=== OPENCV TEST STARTED ===");
        appendToConsole("Testing OpenCV functionality...");
        appendToConsole("Checking native library availability...");
        
        try {
            // Step 1: Check if the native library is available (SAFE MODE)
            appendToConsole("Step 1: Checking native library status...");
            
            // Don't try to load the library - just check if it exists
            checkNativeLibraryStatus();
            
            // Step 2: Check if OpenCV files exist
            appendToConsole("Step 2: Checking OpenCV installation...");
            checkOpenCVInstallation();
            
            // Step 3: Check if we can access native methods (SAFE MODE)
            appendToConsole("Step 3: Testing native method accessibility...");
            testNativeMethodAccess();
            
            appendToConsole("=== OPENCV TEST COMPLETED ===");
            appendToConsole("Note: Full OpenCV testing requires the service to be running");
            appendToConsole("Start the Qt service first, then check logcat for OpenCV logs");
            
        } catch (Exception e) {
            String errorMsg = "OpenCV test failed with exception: " + e.getMessage();
            appendToConsole("=== OPENCV TEST ERROR ===");
            appendToConsole("❌ " + errorMsg);
            appendToConsole("❌ Exception type: " + e.getClass().getSimpleName());
            
            Log.e(TAG, errorMsg, e);
            Toast.makeText(this, "OpenCV test failed", Toast.LENGTH_LONG).show();
        }
    }
    
    private void checkOpenCVInstallation() {
        try {
            // Check if OpenCV directory exists
            java.io.File opencvDir = new java.io.File(getApplicationInfo().nativeLibraryDir);
            appendToConsole("Native library directory: " + opencvDir.getAbsolutePath());
            
            // Check for OpenCV-related files
            java.io.File[] files = opencvDir.listFiles();
            if (files != null) {
                boolean foundOpenCV = false;
                for (java.io.File file : files) {
                    if (file.getName().contains("opencv") || file.getName().contains("cv")) {
                        appendToConsole("✓ Found OpenCV-related file: " + file.getName());
                        foundOpenCV = true;
                    }
                }
                if (!foundOpenCV) {
                    appendToConsole("⚠ No OpenCV-related files found in native library directory");
                }
            }
            
            // Check if OpenCV source directory exists
            java.io.File opencvSourceDir = new java.io.File(getApplicationInfo().sourceDir);
            java.io.File opencvCppDir = new java.io.File(opencvSourceDir.getParentFile(), "cpp/opencv");
            if (opencvCppDir.exists()) {
                appendToConsole("✓ OpenCV source directory exists: " + opencvCppDir.getAbsolutePath());
                
                // Check for OpenCVConfig.cmake
                java.io.File opencvConfig = new java.io.File(opencvCppDir, "arm64-v8a/sdk/native/jni/OpenCVConfig.cmake");
                if (opencvConfig.exists()) {
                    appendToConsole("✓ OpenCVConfig.cmake found");
                } else {
                    appendToConsole("⚠ OpenCVConfig.cmake not found - OpenCV may not be properly configured");
                }
            } else {
                appendToConsole("⚠ OpenCV source directory not found: " + opencvCppDir.getAbsolutePath());
                appendToConsole("⚠ This suggests OpenCV files are not in the expected location");
            }
            
        } catch (Exception e) {
            appendToConsole("⚠ Error checking OpenCV installation: " + e.getMessage());
        }
    }
    
    private void checkNativeLibraryStatus() {
        try {
            // Check if the native library file exists without loading it
            java.io.File nativeLibDir = new java.io.File(getApplicationInfo().nativeLibraryDir);
            appendToConsole("Native library directory: " + nativeLibDir.getAbsolutePath());
            
            java.io.File qtserviceLib = new java.io.File(nativeLibDir, "libqtservice-jni.so");
            if (qtserviceLib.exists()) {
                appendToConsole("✓ Native library file exists: libqtservice-jni.so");
                appendToConsole("✓ File size: " + (qtserviceLib.length() / 1024) + " KB");
            } else {
                appendToConsole("❌ Native library file not found: libqtservice-jni.so");
                appendToConsole("❌ This suggests the app hasn't been built with native code yet");
                appendToConsole("❌ Build the project first to generate the native library");
            }
            
            // Check for other native libraries
            java.io.File[] files = nativeLibDir.listFiles();
            if (files != null) {
                appendToConsole("Available native libraries:");
                for (java.io.File file : files) {
                    if (file.getName().endsWith(".so")) {
                        appendToConsole("  - " + file.getName() + " (" + (file.length() / 1024) + " KB)");
                    }
                }
            }
            
        } catch (Exception e) {
            appendToConsole("⚠ Error checking native library status: " + e.getMessage());
        }
    }
    
    private void testNativeMethodAccess() {
        try {
            // Try to access the QtServiceWrapper class without instantiating it
            Class<?> serviceClass = Class.forName("org.qtproject.qtservice.QtServiceWrapper");
            appendToConsole("✓ QtServiceWrapper class found");
            
            // Check if the testOpenCV method exists
            try {
                java.lang.reflect.Method testMethod = serviceClass.getMethod("testOpenCV");
                appendToConsole("✓ testOpenCV method found");
                
                // Check if it's a native method
                if (java.lang.reflect.Modifier.isNative(testMethod.getModifiers())) {
                    appendToConsole("✓ testOpenCV is a native method");
                } else {
                    appendToConsole("⚠ testOpenCV is a Java wrapper method (calls native method)");
                }
                
            } catch (NoSuchMethodException e) {
                appendToConsole("❌ testOpenCV method not found in QtServiceWrapper");
            }
            
            // Check if the nativeTestOpenCV method exists and is native
            try {
                java.lang.reflect.Method nativeMethod = serviceClass.getDeclaredMethod("nativeTestOpenCV");
                appendToConsole("✓ nativeTestOpenCV method found");
                
                // Check if it's a native method
                if (java.lang.reflect.Modifier.isNative(nativeMethod.getModifiers())) {
                    appendToConsole("✓ nativeTestOpenCV is a native method");
                } else {
                    appendToConsole("❌ nativeTestOpenCV is not a native method");
                }
                
            } catch (NoSuchMethodException e) {
                appendToConsole("❌ nativeTestOpenCV method not found in QtServiceWrapper");
            }
            
        } catch (ClassNotFoundException e) {
            appendToConsole("❌ QtServiceWrapper class not found: " + e.getMessage());
            appendToConsole("❌ Check your package structure and imports");
        } catch (Exception e) {
            appendToConsole("⚠ Error checking native method access: " + e.getMessage());
        }
    }
    
    /**
     * Reset the service state to handle restart issues
     */
    private void resetServiceState() {
        appendToConsole("=== RESETTING SERVICE STATE ===");
        
        try {
            // Stop the service if it's running
            if (serviceRunning) {
                appendToConsole("Stopping currently running service...");
                stopQtService();
                
                // Wait a bit more for cleanup
                Thread.sleep(3000);
            }
            
            // Reset all flags
            serviceRunning = false;
            wasServiceRunning = false;
            
            // Update UI
            updateToggleButton();
            
            appendToConsole("✓ Service state reset completed");
            appendToConsole("✓ Ready for fresh service start");
            appendToConsole("=== SERVICE STATE RESET COMPLETE ===");
            
            Toast.makeText(this, "Service state reset completed", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            String errorMsg = "Failed to reset service state: " + e.getMessage();
            appendToConsole("❌ ERROR: " + errorMsg);
            Log.e(TAG, errorMsg, e);
            Toast.makeText(this, "Failed to reset service state", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Test OpenCV functionality in the running service
     */
    private void testOpenCVInService() {
        appendToConsole("=== TESTING OPENCV IN SERVICE ===");
        
        if (!serviceRunning) {
            appendToConsole("❌ Service is not running. Start the service first.");
            Toast.makeText(this, "Start the service first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            appendToConsole("Testing OpenCV in running Qt service...");
            
            // Create a service intent to test OpenCV
            Intent serviceIntent = new Intent(this, org.qtproject.qtservice.QtServiceWrapper.class);
            serviceIntent.setAction("TEST_OPENCV");
            
            // Start the service with the test action
            startService(serviceIntent);
            
            appendToConsole("✓ OpenCV test request sent to service");
            appendToConsole("Check the service logs below for OpenCV test results");
            
            Toast.makeText(this, "OpenCV test sent to service", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            String errorMsg = "Failed to test OpenCV in service: " + e.getMessage();
            appendToConsole("❌ ERROR: " + errorMsg);
            Log.e(TAG, errorMsg, e);
            Toast.makeText(this, "OpenCV test failed", Toast.LENGTH_LONG).show();
        }
    }
}