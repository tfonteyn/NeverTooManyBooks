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
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
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
    private boolean warnAfterChange = true;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        setPreferencesFromResource(R.xml.preferences, rootKey);

        PreferenceScreen screen = getPreferenceScreen();
        // Some PreferenceScreen use a click listener.
        initClickListeners();
        // Set the summaries reflecting the current values for all basic Preferences.
        updateSummaries(screen);
    }

    /**
     * Hook up a {@link Preference.OnPreferenceClickListener} to start a dedicated activity.
     */
    private void initClickListeners() {
        Preference preference;

        preference = findPreference(Prefs.psk_search_site_order);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), SearchAdminActivity.class);
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;
            });
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mResultDataModel.putAll(data);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {

        //noinspection ConstantConditions
        @NonNull
        Context context = getContext();

        switch (key) {
            case Prefs.pk_sounds_scan_isbn_invalid:
                SoundManager.beepLow(context);
                break;

            case Prefs.pk_sounds_scan_isbn_valid:
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

            case Prefs.pk_reformat_titles_sort:
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
                // TODO: make the response conditional, not all changes warrant a recreate!
                mResultDataModel.putExtra(UniqueId.BKEY_RECREATE_ACTIVITY, true);
                break;
        }

        super.onSharedPreferenceChanged(sharedPreferences, key);
    }

    private void handleReorderTitlesForSorting(@NonNull final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean current = prefs.getBoolean(Prefs.pk_reformat_titles_sort, true);

        new AlertDialog.Builder(context)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.warning_rebuild_orderby_columns)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    //TEST: should handleReorderTitlesForSorting modify the pref?
//                    prefs.edit()
//                         .putBoolean(Prefs.pk_reformat_titles_sort, !current)
//                         .apply();
                    SwitchPreference sp = findPreference(Prefs.pk_reformat_titles_sort);
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
