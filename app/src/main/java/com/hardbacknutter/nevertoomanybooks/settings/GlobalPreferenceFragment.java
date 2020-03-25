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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.tasks.Scheduler;

/**
 * Global settings page.
 */
public class GlobalPreferenceFragment
        extends BasePreferenceFragment {

    /** Fragment manager tag. */
    public static final String TAG = "GlobalPreferenceFragment";
    private static final String BKEY_CURRENT_SORT_TITLE_REORDERED = TAG + ":cSTR";

    /** Used to be able to reset this pref to what it was when this fragment started. */
    private boolean mCurrentSortTitleReordered;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        setPreferencesFromResource(R.xml.preferences, rootKey);

        boolean storedSortTitleReordered = getPreferenceManager()
                .getSharedPreferences().getBoolean(Prefs.pk_sort_title_reordered, true);
        if (savedInstanceState == null) {
            mCurrentSortTitleReordered = storedSortTitleReordered;
        } else {
            mCurrentSortTitleReordered = savedInstanceState
                    .getBoolean(BKEY_CURRENT_SORT_TITLE_REORDERED, storedSortTitleReordered);
        }

        initListeners();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_CURRENT_SORT_TITLE_REORDERED, mCurrentSortTitleReordered);
    }

    /**
     * Hook up specific listeners/preferences.
     */
    private void initListeners() {
        Preference preference;

        preference = findPreference(Prefs.pk_sort_title_reordered);
        if (preference != null) {
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                final SwitchPreference sp = (SwitchPreference) pref;
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.confirm_rebuild_orderby_columns)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            sp.setChecked(mCurrentSortTitleReordered);
                            Scheduler.scheduleOrderByRebuild(getContext(), false);
                        })
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            sp.setChecked(!sp.isChecked());
                            Scheduler.scheduleOrderByRebuild(getContext(), true);
                            //URGENT: needs visual indicator when scheduled.
                        })
                        .create()
                        .show();
                // Do not let the system update the preference value.
                return false;
            });
        }
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        switch (key) {
            case Prefs.pk_ui_locale:
            case Prefs.pk_ui_theme:
            case Prefs.pk_sort_title_reordered:
            case Prefs.pk_show_title_reordered:
                mResultDataModel.putResultData(BaseActivity.BKEY_RECREATE, true);
                break;

            default:
                break;
        }

        super.onSharedPreferenceChanged(sharedPreferences, key);
    }
}
