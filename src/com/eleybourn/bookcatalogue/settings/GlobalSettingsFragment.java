package com.eleybourn.bookcatalogue.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.R;

import androidx.preference.PreferenceScreen;

/**
 * Global settings page.
 */
public class GlobalSettingsFragment extends BaseSettingsFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {

        setPreferencesFromResource(R.xml.preferences, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        screen.setTitle(R.string.lbl_preferences);
        setSummary(screen);
    }
}
