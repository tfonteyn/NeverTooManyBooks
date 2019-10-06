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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Global settings page.
 */
public class GlobalSettingsFragment
        extends BaseSettingsFragment {

    /** Fragment manager tag. */
    public static final String TAG = "GlobalSettingsFragment";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        setPreferencesFromResource(R.xml.preferences, rootKey);
        PreferenceScreen screen = getPreferenceScreen();
        setSummary(screen);
    }

    private boolean warnAfterChange = true;

    /**
     * Apply preference changes.
     */
    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(this, "GlobalSettingsFragment.onSharedPreferenceChanged",
                              "key=" + key);
        }

        //noinspection ConstantConditions
        @NonNull
        Context context = getContext();

        switch (key) {
            case Prefs.pk_scanner_beep_if_invalid:
                SoundManager.beepLow(context);
                break;

            case Prefs.pk_scanner_beep_if_valid:
                SoundManager.beepHigh(context);
                break;

            case Prefs.pk_scanner_preferred:
                //noinspection ConstantConditions
                ScannerManager.installScanner(getActivity(), success -> {
                    if (!success) {
                        ScannerManager.setDefaultScanner(context);
                    }
                });
                break;

            case Prefs.pk_reorder_titles_for_sorting:
                if (warnAfterChange) {
                    // prevent recursive loop
                    warnAfterChange = false;
                    handleReorderTitlesForSorting(context);
                } else {
                    // re-enable check
                    warnAfterChange = true;
                }
                break;

            default:
                break;
        }

        // set the result (and again and again...)
        // TODO: make the response conditional, not all changes warrant a recreate!
        Intent data = new Intent().putExtra(UniqueId.BKEY_RECREATE_ACTIVITY, true);
        //noinspection ConstantConditions
        getActivity().setResult(Activity.RESULT_OK, data);

        super.onSharedPreferenceChanged(sharedPreferences, key);
    }

    private void handleReorderTitlesForSorting(@NonNull final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean current = prefs.getBoolean(Prefs.pk_reorder_titles_for_sorting, true);

        new AlertDialog.Builder(context)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.warning_rebuild_orderby_columns)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
//                    prefs.edit()
//                         .putBoolean(Prefs.pk_reorder_titles_for_sorting, !current)
//                         .apply();
                    SwitchPreference sp = findPreference(Prefs.pk_reorder_titles_for_sorting);
                    //noinspection ConstantConditions
                    sp.setChecked(!current);
                    StartupViewModel.setScheduleOrderByRebuild(false);

                })
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    StartupViewModel.setScheduleOrderByRebuild(true);
                    // re-enable check
                    warnAfterChange = true;
                })
                .create()
                .show();
    }
}
