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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

/**
 * Base settings page.
 * <p>
 * Uses OnSharedPreferenceChangeListener to dynamically update the summary for each preference.
 */
public abstract class BasePreferenceFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "BasePreferenceFragment";

    /** Allows auto-scrolling on opening the preference screen to the desired key. */
    public static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":scrollTo";
    ResultDataModel mResultDataModel;
    @Nullable
    private String mAutoScrollToKey;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mAutoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);
    }

    @Override
    public void onResume() {
        super.onResume();

        PreferenceScreen screen = getPreferenceScreen();
        screen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // Set the summaries reflecting the current values for all Preferences.
        //TODO: use this for all prefs instead:
        // EditTextPreference np = screen.findPreference(Prefs.X));
        // np.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
        for (String key : screen.getSharedPreferences().getAll().keySet()) {
            updateSummary(key);
        }

        if (mAutoScrollToKey != null) {
            scrollToPreference(mAutoScrollToKey);
            mAutoScrollToKey = null;
        }
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                             .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @CallSuper
    void updateSummary(@NonNull final String key) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(getValueAsString(preference));
        }
    }

    /**
     * Get the current string value for a single Preference.
     *
     * @return the value string
     */
    @NonNull
    private CharSequence getValueAsString(@NonNull final Preference preference) {
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
                    //noinspection ConstantConditions
                    Logger.warnWithStackTrace(
                            getContext(), this,
                            "MultiSelectListPreference:"
                            + "\n s=" + s
                            + "\n key=" + msp.getKey()
                            + "\n entries="
                            + TextUtils.join(",", Arrays.asList(msp.getEntries()))
                            + "\n entryValues="
                            + TextUtils.join(",", Arrays.asList(msp.getEntryValues()))
                            + "\n values=" + msp.getValues());
                } else {
                    text.append(msp.getEntries()[index]).append('\n');
                }
            }
            return text;
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
        updateSummary(key);
    }
}
