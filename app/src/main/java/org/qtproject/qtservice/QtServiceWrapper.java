package org.qtproject.qtservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;

/**
 * Qt Service Wrapper - Static Library Version
 * Loads Qt service from statically linked native library
 */
public class QtServiceWrapper extends Service {
    private static final String TAG = "QtServiceWrapper";
    private static final String DEFAULT_CHANNEL_ID = "qt_service_channel";
    private static final int NOTIFICATION_ID = 1;

    private static boolean qtInitialized = false;
    private static boolean qtStarted = false;
    private Handler mainHandler;
    private boolean hasNotificationPermission = false;

    // Load the native library containing Qt static library
    static {
        try {
            System.loadLibrary("qtservice-jni");
            Log.d(TAG, "✓ qtservice-jni library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "✗ Failed to load qtservice-jni library", e);
        }
    }

    // Native function declarations
    private native String nativeGetVersion();
    private native boolean nativeInitializeService();
    private native boolean nativeStartService();
    private native boolean nativeStopService();
    private native boolean nativeIsServiceRunning();
    private native void nativeCleanupService();

    @Override
    public void onCreate() {
        Log.d(TAG, "=== QtServiceWrapper onCreate (STATIC LIBRARY) ===");
        Log.d(TAG, "Process ID: " + android.os.Process.myPid());

        mainHandler = new Handler(Looper.getMainLooper());
        checkNotificationPermission();

        // Start foreground service if we have permission
        if (hasNotificationPermission) {
            createNotificationChannel();
            Notification notification = createServiceNotification("Initializing Qt service...");
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "✓ Foreground service started");
        } else {
            Log.w(TAG, "Cannot start foreground service - missing POST_NOTIFICATIONS permission");
        }

        // Schedule Qt initialization
        mainHandler.post(() -> {
            if (!qtStarted) {
                qtStarted = true;
                startQtInitialization();
            }
        });

        Log.d(TAG, "onCreate completed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "=== QtServiceWrapper onStartCommand ===");

        checkNotificationPermission();

        if (hasNotificationPermission) {
            createNotificationChannel();
            String statusText = qtInitialized ?
                    "Qt service active - timers running" :
                    qtStarted ? "Qt service initializing..." : "Starting Qt service...";

            Notification notification = createServiceNotification(statusText);
            startForeground(NOTIFICATION_ID, notification);
        }

        if (!qtStarted) {
            qtStarted = true;
            mainHandler.post(this::startQtInitialization);
        }

        Log.d(TAG, "onStartCommand completed");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "=== QtServiceWrapper onDestroy ===");

        try {
            if (hasNotificationPermission) {
                stopForeground(true);
            }

            if (qtInitialized) {
                Log.d(TAG, "Cleaning up Qt service...");
                try {
                    nativeStopService();
                    nativeCleanupService();
                    Thread.sleep(500);
                } catch (Exception e) {
                    Log.w(TAG, "Exception during Qt cleanup: " + e.getMessage());
                }
            }

            qtInitialized = false;
            qtStarted = false;
            Log.d(TAG, "Qt service cleanup completed");

        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ActivityCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

            if (!hasNotificationPermission) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
            } else {
                Log.d(TAG, "✓ POST_NOTIFICATIONS permission granted");
            }
        } else {
            hasNotificationPermission = true;
            Log.d(TAG, "✓ Running on Android < 13 - notification permission not required");
        }
    }

    private void startQtInitialization() {
        Log.d(TAG, "Starting Qt service initialization in background thread...");

        new Thread(() -> initializeQt()).start();
    }

    private void initializeQt() {
        Log.d(TAG, "Qt service initialization thread started");

        try {
            updateNotification("Loading Qt service from static library...");

            // Get version info
            try {
                String version = nativeGetVersion();
                Log.d(TAG, "Qt Service Version: " + version);
                updateNotification("Loaded: " + version);
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Native functions not available: " + e.getMessage());
                updateNotification("ERROR: Native Qt functions not available");
                return;
            }

            updateNotification("Initializing Qt service...");

            // Initialize Qt service
            if (!nativeInitializeService()) {
                Log.e(TAG, "Failed to initialize Qt service");
                updateNotification("ERROR: Qt service initialization failed");
                return;
            }
            Log.d(TAG, "✓ Qt service initialized");

            updateNotification("Starting Qt service...");

            // Start Qt service
            if (!nativeStartService()) {
                Log.e(TAG, "Failed to start Qt service");
                updateNotification("ERROR: Qt service start failed");
                return;
            }
            Log.d(TAG, "✓ Qt service started");

            qtInitialized = true;
            Log.d(TAG, "✓ Qt service initialization completed successfully");

            updateNotification("Qt service active - timer threads running");

        } catch (Exception e) {
            Log.e(TAG, "Qt service initialization failed", e);
            updateNotification("ERROR: Qt service failed - " + e.getMessage());
        }
    }

    private Notification createServiceNotification(String statusText) {
        if (!hasNotificationPermission) {
            return null;
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, DEFAULT_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("Qt Timer Service")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String statusText) {
        if (!hasNotificationPermission) {
            Log.d(TAG, "Notification update skipped (no permission): " + statusText);
            return;
        }

        try {
            Notification notification = createServiceNotification(statusText);
            if (notification != null) {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, notification);
                    Log.d(TAG, "✓ Notification updated: " + statusText);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to update notification", e);
        }
    }

    private void createNotificationChannel() {
        if (!hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                DEFAULT_CHANNEL_ID,
                "Qt Background Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Qt-based background timer service");
        channel.setShowBadge(false);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
            Log.d(TAG, "✓ Notification channel created");
        }
    }
}