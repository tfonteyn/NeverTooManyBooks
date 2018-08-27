package com.eleybourn.bookcatalogue;

import android.Manifest;
import android.os.Bundle;

public class StartupWithSplashActivity extends SplashPermissionsActivity {

    @Override
    public Class getNextActivityClass() {
        return StartupActivity.class;
    }

    /** no timeout; no 'splash' on pre Android 6 */
    @Override
    protected int getTimeoutMillis() {
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splashscreen);
    }

    /**
     * Minimally needed.
     * - WRITE_EXTERNAL_STORAGE
     *
     * Other permissions fail gracefully.
     * - READ_CONTACTS
     */
    @Override
    protected String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }
}
