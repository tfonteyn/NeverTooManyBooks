/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Base settings page.
 * <p>
 * Uses OnSharedPreferenceChangeListener to dynamically update the summary for each preference.
 */
abstract class BaseSettingsFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

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
                int index = msp.findIndexOfValue(s);
                if (index == -1) {
                    // This re-surfaces sometimes after a careless dev. change.
                    Logger.warnWithStackTrace(
                            this, "MultiSelectListPreference:"
                                  + "\n s=" + s
                                  + "\n key=" + msp.getKey()
                                  + "\n entries="
                                  + TextUtils.join(",",
                                                   Arrays.asList(msp.getEntries()))
                                  + "\n entryValues="
                                  + TextUtils.join(",",
                                                   Arrays.asList(msp.getEntryValues()))
                                  + "\n values=" + msp.getValues());
                } else {
                    text.append(msp.getEntries()[index]).append('\n');
                }
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
     *
     * <p>
     * <br>{@inheritDoc}
     */
    @CallSuper
    @Override
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        setSummary(key);
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

    /**
     * Force children to adopt this pattern / to not forget to set a result.
     * THEY STILL MUST CALL IT THEMSELVES!
     */
    abstract void prepareResult();

    /**
     * Copy all preferences from source to destination.
     */
    @SuppressWarnings("unused")
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
        for (Map.Entry<String, ?> entry : all.entrySet()) {

            if (entry.getValue() instanceof Boolean) {
                ed.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Float) {
                ed.putFloat(entry.getKey(), (Float) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                ed.putInt(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                ed.putLong(entry.getKey(), (Long) entry.getValue());
            } else if (entry.getValue() instanceof String) {
                ed.putString(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Set) {
                //noinspection unchecked
                ed.putStringSet(entry.getKey(), (Set<String>) entry.getValue());
            } else {
                Logger.warnWithStackTrace(this, entry.getValue().getClass().getCanonicalName());
            }
        }
        ed.apply();
        if (clearSource) {
            // API: 24 -> context.deleteSharedPreferences(source);
            sourcePrefs.edit().clear().apply();
        }
    }
}
