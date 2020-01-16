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
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Global settings page.
 */
public class GlobalPreferenceFragment
        extends BasePreferenceFragment {

    /** Fragment manager tag. */
    public static final String TAG = "GlobalPreferenceFragment";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        setPreferencesFromResource(R.xml.preferences, rootKey);

        initListeners();
    }

    /**
     * Hook up specific listeners/preferences.
     */
    private void initListeners() {
        Preference preference;

        preference = findPreference(Prefs.pk_reformat_titles_sort);
        if (preference != null) {
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                //noinspection ConstantConditions
                boolean current = PreferenceManager.getDefaultSharedPreferences(getContext())
                                                   .getBoolean(Prefs.pk_reformat_titles_sort, true);

                new AlertDialog.Builder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.warning_rebuild_orderby_columns)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        .setNegativeButton(android.R.string.cancel, (d, w) ->
                                StartupViewModel.setScheduleOrderByRebuild(getContext(), false))
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            //prefs.edit()
                            // .putBoolean(Prefs.pk_reformat_titles_sort, !current).apply();
                            SwitchPreference sp = findPreference(Prefs.pk_reformat_titles_sort);
                            //noinspection ConstantConditions
                            sp.setChecked(!current);

                            StartupViewModel.setScheduleOrderByRebuild(getContext(), true);
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
            case Prefs.pk_reformat_titles_sort:
            case Prefs.pk_reformat_titles_display:
                mResultDataModel.putExtra(BaseActivity.BKEY_RECREATE, true);
                break;

            default:
                break;
        }

        super.onSharedPreferenceChanged(sharedPreferences, key);
    }
}
