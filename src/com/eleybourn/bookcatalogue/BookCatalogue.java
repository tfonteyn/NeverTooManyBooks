package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.debug.Tracker;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * This is a placeholder class to deal with the surprising number of old shortcuts that
 * have not been updated from version 3.x.
 *
 * This activity just forwards to the StartupActivity.
 *
 * In retrospect, this should have been done in the first place, but since we now
 * have users with shortcuts that point to 'StartupActivity', it is too late to fix.
 *
 * @author Philip Warner
 */
public class BookCatalogue
    extends AppCompatActivity {

    @CallSuper
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, StartupActivity.class));
        finish();
        Tracker.exitOnCreate(this);
    }
}
