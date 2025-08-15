package com.licensespring.android;

import android.content.Context;
import android.util.Log;

public class LicenseSpring {

    // This method declaration exactly matches the signature declared in the LicenseSpring SDK. Don't remove it.
    public static native void setAndroidContextAndIDs(Context context);

    static {
        try {
            System.loadLibrary("LicenseSpring");
            Log.d("LicenseSpring", "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e("LicenseSpring", "Failed to load native library: " + e.getMessage());
        }
    }

    public static void initialize(Context appContext) {
        try {
            setAndroidContextAndIDs(appContext.getApplicationContext());
            Log.d("LicenseSpring", "Native context and IDs set successfully");
        } catch (Exception e) {
            Log.e("LicenseSpring", "Error calling native method: " + e.getMessage());
        }
    }
}
