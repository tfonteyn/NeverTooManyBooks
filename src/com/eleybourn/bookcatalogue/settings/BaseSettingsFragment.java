package com.eleybourn.bookcatalogue.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.Map;
import java.util.Set;

/**
 * Base settings page.
 * <p>
 * Uses OnSharedPreferenceChangeListener to dynamically update the summary for each preference.
 */
public abstract class BaseSettingsFragment
        extends PreferenceFragmentCompat
        implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Copy all preferences from source to destination.
     */
    protected void copyPrefs(@NonNull final String source,
                             @NonNull final String destination,
                             final boolean clearSource) {
        Context context = getPreferenceManager().getContext();
        SharedPreferences sourcePrefs =
                context.getSharedPreferences(source, Context.MODE_PRIVATE);
        SharedPreferences destinationPrefs =
                context.getSharedPreferences(destination, Context.MODE_PRIVATE);

        SharedPreferences.Editor ed = destinationPrefs.edit();
        Map<String, ?> all = sourcePrefs.getAll();
        for (Map.Entry<String, ?> x : all.entrySet()) {

            if (x.getValue() instanceof Boolean) {
                ed.putBoolean(x.getKey(), (Boolean) x.getValue());
            } else if (x.getValue() instanceof Float) {
                ed.putFloat(x.getKey(), (Float) x.getValue());
            } else if (x.getValue() instanceof Integer) {
                ed.putInt(x.getKey(), (Integer) x.getValue());
            } else if (x.getValue() instanceof Long) {
                ed.putLong(x.getKey(), (Long) x.getValue());
            } else if (x.getValue() instanceof String) {
                ed.putString(x.getKey(), (String) x.getValue());
            } else if (x.getValue() instanceof Set) {
                //noinspection unchecked
                ed.putStringSet(x.getKey(), (Set<String>) x.getValue());
            } else {
                Logger.error(x.getValue().getClass().getCanonicalName());
            }
        }
        ed.apply();
        if (clearSource) {
            // API: 24 -> BookCatalogueApp.getAppContext().deleteSharedPreferences(source);
            sourcePrefs.edit().clear().apply();
        }
    }

    /**
     * Set the summaries reflecting the current values for all Preferences.
     */
    void setSummary(@NonNull final PreferenceScreen screen) {
        for (String key : screen.getSharedPreferences().getAll().keySet()) {
            setSummary(key);
        }
    }

    private void setSummary(@NonNull final String key) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(getSummary(preference));
        }
    }

    /**
     * @return the current string value for a single Preference.
     */
    @NonNull
    private CharSequence getSummary(@NonNull final Preference preference) {
        if (preference instanceof ListPreference) {
            return ((ListPreference) preference).getEntry();

        } else if (preference instanceof EditTextPreference) {
            return ((EditTextPreference) preference).getText();

        } else if (preference instanceof MultiSelectListPreference) {
            MultiSelectListPreference msp = (MultiSelectListPreference) preference;
            StringBuilder text = new StringBuilder();
            for (String s : msp.getValues()) {
                text.append(msp.getEntries()[msp.findIndexOfValue(s)]).append('\n');
            }
            return text;
        } else if (preference instanceof PreferenceScreen) {
            //ENHANCE: collect the summaries from all its prefs ?
            return "";
        } else {
            return "";
        }
    }

    /**
     * Update the summary after a change.
     */
    @Override
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        setSummary(key);
    }

    /**
     * reload our fragment, but with the new root key.
     *
     * @param preferenceScreen to load.
     */
    @Override
    public void onNavigateToScreen(@NonNull final PreferenceScreen preferenceScreen) {
        Fragment frag;
        try {
            frag = this.getClass().newInstance();
        } catch (java.lang.InstantiationException | IllegalAccessException ignore) {
            throw new IllegalStateException();
        }

        Bundle args = new Bundle();
        args.putAll(getArguments());
        args.putString(ARG_PREFERENCE_ROOT, preferenceScreen.getKey());

        frag.setArguments(args);
        getFragmentManager()
                .beginTransaction()
                .replace(getId(), frag)
                .addToBackStack(preferenceScreen.getKey())
                .commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                             .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                             .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
}
