package com.eleybourn.bookcatalogue;


import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Origin:
 *    https://github.com/pcess/tutorials/tree/master/SplashPermissions
 *
 * This {@link AppCompatActivity} functions as a base class for a splash screen. This
 * class will function differently on pre- and post-Android 6.0 devices,
 * although in both cases, the {@link #getNextActivityClass()} method must be
 * overridden, since {@link #getNextActivityClass()} returns the
 * {@link AppCompatActivity} to start once the splash screen times out.
 * <p>
 * On pre-Android 6.0 devices, this {@link AppCompatActivity} will display a
 * random color for {@link #getTimeoutMillis()} milliseconds, before
 * starting the {@link AppCompatActivity} specified by {@link #getNextActivityClass()}.
 * <p>
 * On post-Android 6.0 devices, this app will additionally force the user to
 * grant all of the currently missing app permissions before timing out and
 * starting the next {@link AppCompatActivity} specified by
 * {@link #getNextActivityClass()} (see
 * <a href="http://developer.android.com/training/permissions/requesting.html">
 * Requesting Android Permissions</a>). In pre-Android 6.0 devices, app
 * permissions were granted during installation and could not be revoked.
 * However, since Android 6.0, users can revoke app permissions after
 * installation. This {@link AppCompatActivity} will gather all of the required app
 * permissions from the manifest, and check that this app has been granted all
 * of those permissions. The user will then be forced to granted all missing
 * permissions before continuing. Note, however, that the user may still revoke
 * permissions while the app is running, and this {@link AppCompatActivity} does nothing
 * to protect your app from such occurrences. Specifically, this
 * {@link AppCompatActivity} only does a check at start up.
 * <p>
 * You can change the timeout duration (in milliseconds) and the permissions
 * required by your app by extending this class and overriding
 * {@link #getTimeoutMillis()} and {@link #getRequiredPermissions()} methods.
 */

abstract public class SplashPermissionsActivity extends AppCompatActivity {

    /** The result code used when requesting permissions */
    private static final int PERMISSIONS_REQUEST = 1234;

    /** The time when this {@link AppCompatActivity} was created */
    private long startTimeMillis = 0;

    /**
     * Get the time (in milliseconds) that the splash screen will be on the
     * screen before starting the {@link Activity} who's class is returned by
     * {@link #getNextActivityClass()}.
     */
    protected int getTimeoutMillis() {
        return 5000;
    }

    /**
     * Get the {@link Activity} to start when the splash screen times out.
     */
    abstract protected Class getNextActivityClass();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Used to determine when the splash screen should timeout
        startTimeMillis = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= 23) {
            // Android 6+ : check if the required permissions have been granted
            checkPermissions();
        } else {
            // older Android, simply move forward
            startNextActivity();
        }
    }

    /**
     * See if we now have all of the required dangerous permissions. Otherwise,
     * tell the user that they cannot continue without granting the permissions,
     * and then request the permissions again.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            checkPermissions();
        }
    }

     /**
     * After the timeout, start the {@link AppCompatActivity} as specified by
     * {@link #getNextActivityClass()}, and remove the splash screen.
     */
    private void startNextActivity() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
            }
        });
        long delayMillis = getTimeoutMillis() - (System.currentTimeMillis() - startTimeMillis);
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashPermissionsActivity.this, getNextActivityClass()));
                finish();
            }
        }, delayMillis);
    }

    /**
     * Get the list of required permissions by searching the manifest. If you
     * don't think the default behavior is working, then you could try
     * overriding this function to return something like:
     * <p>
     * <pre>
     * <code>
     * return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
     * </code>
     * </pre>
     */
    protected String[] getRequiredPermissions() {
        String[] permissions = null;
        try {
            permissions = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_PERMISSIONS).requestedPermissions;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        if (permissions == null) {
            return new String[0];
        } else {
            return permissions.clone();
        }
    }
    /**
     * Check if the required permissions have been granted, and
     * {@link #startNextActivity()} if they have. Otherwise
     * {@link #requestPermissions(String[], int)}.
     */
    private void checkPermissions() {
        String[] missing = requiredPermissionsStillNeeded();
        if (missing.length == 0) {
            startNextActivity();
        } else {
            ActivityCompat.requestPermissions(this, missing, PERMISSIONS_REQUEST);
            //TODO: when going to API 23+, use native call
            //requestPermissions(missing, PERMISSIONS_REQUEST);
        }
    }

    /**
     * Convert the array of required permissions to a {@link Set} to remove
     * redundant elements. Then remove already granted permissions, and return
     * an array of missing permissions.
     */
    private String[] requiredPermissionsStillNeeded() {

        Set<String> permissions = new HashSet<>();
        Collections.addAll(permissions, getRequiredPermissions());

        for (Iterator<String> i = permissions.iterator(); i.hasNext(); ) {
            //TODO: when going to API 23+, use native call
            if (ContextCompat.checkSelfPermission(this, i.next()) == PackageManager.PERMISSION_GRANTED) {
                i.remove();
            }
        }
        return permissions.toArray(new String[permissions.size()]);
    }
}
