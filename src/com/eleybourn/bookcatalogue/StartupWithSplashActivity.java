package com.eleybourn.bookcatalogue;

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
}
