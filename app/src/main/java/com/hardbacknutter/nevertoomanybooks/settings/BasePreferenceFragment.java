/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.widgets.TriStateMultiSelectListPreference;

/**
 * Base settings page. This handles:
 * <ul>
 *     <li>Summaries</li>
 *     <li>custom preference dialogs</li>
 *     <li>auto-scroll key</li>
 * </ul>
 */
public abstract class BasePreferenceFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Log tag. */
    private static final String TAG = "BasePreferenceFragment";

    /** Allows auto-scrolling on opening the preference screen to the desired key. */
    @SuppressWarnings("WeakerAccess")
    public static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":scrollTo";

    @Nullable
    private String mAutoScrollToKey;

    @Override
    @CallSuper
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        final Bundle args = getArguments();
        if (args != null) {
            mAutoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final PreferenceScreen screen = getPreferenceScreen();
        screen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // Set the summaries reflecting the current values.
        updateSummaries(screen);

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

    /**
     * Recursively update the summary for all preferences in the given group.
     *
     * @param group to update
     */
    private void updateSummaries(@NonNull final PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            final Preference p = group.getPreference(i);
            if (p instanceof PreferenceGroup) {
                updateSummaries((PreferenceGroup) p);
            } else {
                final String key = p.getKey();
                if (key != null) {
                    updateSummary(key);
                }
            }
        }
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        // Update the summary after a change.
        updateSummary(key);
    }


    /**
     * TriStateMultiSelectListPreference get a custom Dialog were the neutral button displays
     * the "unused" option.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void onDisplayPreferenceDialog(@NonNull final Preference preference) {
        if (preference instanceof TriStateMultiSelectListPreference) {
            // getParentFragmentManager is required by PreferenceDialogFragmentCompat
            // as the latter insists on using setTargetFragment to communicate back.

            // check if dialog is already showing
            if (getParentFragmentManager().findFragmentByTag(
                    TriStateMultiSelectListPreference.TSMSLPreferenceDialogFragment.TAG) == null) {
                TriStateMultiSelectListPreference.TSMSLPreferenceDialogFragment
                        .launch(this, (TriStateMultiSelectListPreference) preference);
            }
            return;
        }

        super.onDisplayPreferenceDialog(preference);
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
        final Preference preference = findPreference(key);
        if (preference != null) {
            final CharSequence summary = getValueAsString(preference);
            if (summary != null) {
                preference.setSummary(summary);
            }
        }
    }

    /**
     * Get the current string value for a single Preference.
     *
     * @param preference to get the value of
     *
     * @return the value string, or {@code null} if the preference had no values
     */
    @Nullable
    private CharSequence getValueAsString(@NonNull final Preference preference) {
        if (preference instanceof ListPreference) {
            final CharSequence value = ((ListPreference) preference).getEntry();
            return value != null ? value : getString(R.string.hint_not_set);
        }

        if (preference instanceof EditTextPreference) {
            return ((EditTextPreference) preference).getText();
        }

        if (preference instanceof TriStateMultiSelectListPreference) {
            final TriStateMultiSelectListPreference p =
                    (TriStateMultiSelectListPreference) preference;
            if (!p.isActive()) {
                return p.getDisregardSummaryText();
            }
            // if it is active, drop through to MultiSelectListPreference
        }

        if (preference instanceof MultiSelectListPreference) {
            final MultiSelectListPreference p = (MultiSelectListPreference) preference;
            final StringBuilder text = new StringBuilder();
            for (final String s : p.getValues()) {
                final int index = p.findIndexOfValue(s);
                if (index >= 0) {
                    text.append(p.getEntries()[index]).append('\n');

                } else {
                    // This re-surfaces sometimes after a careless dev. change.
                    //noinspection ConstantConditions
                    Logger.error(getContext(), TAG, new Throwable(),
                                 "MultiSelectListPreference:"
                                 + "\n s=" + s
                                 + "\n key=" + p.getKey()
                                 + "\n entries=" + TextUtils.join(",", p.getEntries())
                                 + "\n entryValues=" + TextUtils.join(",", p.getEntryValues())
                                 + "\n values=" + p.getValues());
                }
            }

            if (text.length() > 0) {
                return text;
            } else {
                // the preference has no values set, but that is a VALID setting and will be used.
                return getString(R.string.none);
            }
        }

        return null;
    }
}
