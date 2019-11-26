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
import androidx.preference.SwitchPreference;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
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
                handleReorderTitlesForSorting();
                return false;
            });
        }

        // Purge BLNS database table.
        preference = findPreference(Prefs.psk_purge_blns);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                purgeBLNS();
                return true;
            });
        }

        preference = findPreference(Prefs.psk_search_site_order);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), SearchAdminActivity.class);
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;
            });
        }

        preference = findPreference(Prefs.psk_credentials_goodreads);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), GoodreadsRegistrationActivity.class);
                startActivity(intent);
                return true;
            });
        }

        preference = findPreference(Prefs.psk_credentials_library_thing);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), LibraryThingRegistrationActivity.class);
                startActivity(intent);
                return true;
            });
        }

        preference = findPreference(Prefs.psk_purge_files);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                purgeFiles();
                return true;
            });
        }

        preference = findPreference(Prefs.psk_tip_reset_all);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                TipManager.reset(getContext());
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.tip_reset_done, Snackbar.LENGTH_LONG).show();
                return true;
            });
        }

        preference = findPreference(Prefs.psk_send_debug_info);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new AlertDialog.Builder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.debug)
                        .setMessage(R.string.debug_send_info_text)
                        .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                                dialog.dismiss())
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (!DebugReport.sendDebugInfo(getContext())) {
                                //noinspection ConstantConditions
                                Snackbar.make(getView(), R.string.error_email_failed,
                                              Snackbar.LENGTH_LONG).show();
                            }
                        })
                        .create()
                        .show();
                return true;
            });
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mResultDataModel.putExtras(data);
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
                if (sharedPreferences.getBoolean(key, false)) {
                    SoundManager.beepLow(context);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_valid:
                if (sharedPreferences.getBoolean(key, false)) {
                    SoundManager.beepHigh(context);
                }
                break;

            case Prefs.pk_scanner_preferred:
                //noinspection ConstantConditions
                ScannerManager.installScanner(getActivity(), success -> {
                    if (!success) {
                        ScannerManager.setDefaultScanner(context);
                    }
                });
                break;

            default:
                // TODO: make the response conditional, not all changes warrant a recreate!
                mResultDataModel.putExtra(UniqueId.BKEY_RECREATE_ACTIVITY, true);
                break;
        }

        super.onSharedPreferenceChanged(sharedPreferences, key);
    }

    private void handleReorderTitlesForSorting() {
        //noinspection ConstantConditions
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean current = prefs.getBoolean(Prefs.pk_reformat_titles_sort, true);

        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.warning_rebuild_orderby_columns)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) ->
                        StartupViewModel.setScheduleOrderByRebuild(getContext(), false))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    //prefs.edit().putBoolean(Prefs.pk_reformat_titles_sort, !current).apply();
                    SwitchPreference sp = findPreference(Prefs.pk_reformat_titles_sort);
                    //noinspection ConstantConditions
                    sp.setChecked(!current);

                    StartupViewModel.setScheduleOrderByRebuild(getContext(), true);
                })
                .create()
                .show();
    }

    private void purgeFiles() {
        //noinspection ConstantConditions
        long bytes = StorageUtils.purgeFiles(getContext(), false);
        String formattedSize = StorageUtils.formatFileSize(getContext(), bytes);
        String msg = getString(R.string.info_cleanup_files_text, formattedSize,
                               getString(R.string.lbl_send_debug_info));

        new AlertDialog.Builder(getContext())
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_purge_files)
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        StorageUtils.purgeFiles(getContext(), true))
                .create()
                .show();
    }

    private void purgeBLNS() {
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.lbl_purge_blns)
                .setMessage(R.string.info_purge_blns_all)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try (DAO db = new DAO()) {
                        db.purgeNodeStates();
                    }
                })
                .create()
                .show();
    }
}
