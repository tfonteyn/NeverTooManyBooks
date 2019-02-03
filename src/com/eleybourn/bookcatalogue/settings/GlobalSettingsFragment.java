package com.eleybourn.bookcatalogue.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceScreen;

import com.eleybourn.bookcatalogue.R;

/**
 * Global settings page.
 */
public class GlobalSettingsFragment
        extends BaseSettingsFragment
        implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "GlobalSettingsFragment";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        setPreferencesFromResource(R.xml.preferences, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        screen.setTitle(R.string.lbl_preferences);
        setSummary(screen);
    }
}
