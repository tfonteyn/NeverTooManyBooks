/*
 * @Copyright 2020 HardBackNutter
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
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.widgets.BitmaskPreference;

/**
 * Base settings page.
 * <p>
 * Uses OnSharedPreferenceChangeListener to dynamically update the summary for each preference.
 */
public abstract class BasePreferenceFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Log tag. */
    private static final String TAG = "BasePreferenceFragment";
    private static final String DIALOG_FRAGMENT_TAG = TAG + ":dialog";

    private static final int REQ_BITMASK_DIALOG = 0;

    /** Allows auto-scrolling on opening the preference screen to the desired key. */
    public static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":scrollTo";
    protected ResultDataModel mResultDataModel;
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
        for (String key : screen.getSharedPreferences().getAll().keySet()) {
            updateSummary(key);
        }

        if (mAutoScrollToKey != null) {
            scrollToPreference(mAutoScrollToKey);
            mAutoScrollToKey = null;
        }
    }

    /**
     * BitmaskPreference get a custom Dialog were the neutral button displays
     * the "unused" option.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onDisplayPreferenceDialog(@NonNull final Preference preference) {
        if (preference instanceof BitmaskPreference) {
            // check if dialog is already showing
            if (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
                return;
            }

            final DialogFragment f = BitmaskPreference.BitmaskPreferenceDialogFragment
                    .newInstance((BitmaskPreference) preference);

            f.setTargetFragment(this, REQ_BITMASK_DIALOG);
            f.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
            return;
        }

        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                             .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * Update the summary for the given key.
     * <p>
     * TODO: maybe implement {@link Preference.SummaryProvider}
     *
     * @param key for preference.
     */
    @CallSuper
    protected void updateSummary(@NonNull final String key) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(getValueAsString(preference));
        }
    }

    /**
     * Get the current string value for a single Preference.
     *
     * @param preference to get the value of
     *
     * @return the value string
     */
    @NonNull
    private CharSequence getValueAsString(@NonNull final Preference preference) {
        if (preference instanceof ListPreference) {
            CharSequence value = ((ListPreference) preference).getEntry();
            return value != null ? value : getString(R.string.hint_empty_field);
        }
        if (preference instanceof EditTextPreference) {
            return ((EditTextPreference) preference).getText();
        }

        if (preference instanceof BitmaskPreference) {
            BitmaskPreference bmp = (BitmaskPreference) preference;
            if (!bmp.isUsed()) {
                return bmp.getNotInUseSummary();
            }
            // if it is in use, drop through to MultiSelectListPreference
        }

        if (preference instanceof MultiSelectListPreference) {
            MultiSelectListPreference msp = (MultiSelectListPreference) preference;
            StringBuilder text = new StringBuilder();
            for (String s : msp.getValues()) {
                int index = msp.findIndexOfValue(s);
                if (index >= 0) {
                    text.append(msp.getEntries()[index]).append('\n');

                } else {
                    // This re-surfaces sometimes after a careless dev. change.
                    Logger.warnWithStackTrace(
                            preference.getContext(), TAG,
                            "MultiSelectListPreference:"
                            + "\n s=" + s
                            + "\n key=" + msp.getKey()
                            + "\n entries="
                            + TextUtils.join(",", Arrays.asList(msp.getEntries()))
                            + "\n entryValues="
                            + TextUtils.join(",", Arrays.asList(msp.getEntryValues()))
                            + "\n values=" + msp.getValues());
                }

            }
            if (text.length() > 0) {
                return text;
            } else {
                // the preference has no values set, but that is a VALID setting and will be used.
                return preference.getContext().getString(R.string.none);
            }
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
