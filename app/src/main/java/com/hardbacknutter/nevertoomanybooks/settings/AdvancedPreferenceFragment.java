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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.tasks.Scheduler;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Used/defined in xml/preferences.xml
 */
public class AdvancedPreferenceFragment
        extends BasePreferenceFragment {

    /** Log tag. */
    private static final String TAG = "AdvancedPreferenceFrag";

    private static final int REQ_PICK_FILE_FOR_EXPORT_DATABASE = 1;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState,
                                    final String rootKey) {

        setPreferencesFromResource(R.xml.preferences_advanced, rootKey);

        initListeners();
    }

    /**
     * Hook up specific listeners/preferences.
     */
    private void initListeners() {
        Preference preference;

        // Purge BLNS database table.
        preference = findPreference(Prefs.PSK_PURGE_BLNS);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.lbl_purge_blns)
                        .setMessage(R.string.info_purge_blns_all)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> RowStateDAO.clearAll())
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_TIP_RESET_ALL);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                TipManager.reset(getContext());
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.tip_reset_done, Snackbar.LENGTH_LONG).show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_REBUILD_FTS);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.menu_rebuild_fts)
                        .setMessage(R.string.confirm_rebuild_fts)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            Scheduler.scheduleFtsRebuild(getContext(), false);
                            p.setSummary(null);
                        })
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            Scheduler.scheduleFtsRebuild(getContext(), true);
                            p.setSummary(R.string.txt_rebuild_scheduled);
                        })
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_REBUILD_INDEX);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.menu_rebuild_index)
                        .setMessage(R.string.confirm_rebuild_index)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            Scheduler.scheduleIndexRebuild(getContext(), false);
                            p.setSummary(null);
                        })
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            Scheduler.scheduleIndexRebuild(getContext(), true);
                            p.setSummary(R.string.txt_rebuild_scheduled);
                        })
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_PURGE_FILES);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                long bytes = AppDir.purge(getContext(), false);
                String msg = getString(R.string.txt_cleanup_files,
                                       FileUtils.formatFileSize(getContext(), bytes),
                                       getString(R.string.lbl_send_debug_info));

                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.lbl_purge_files)
                        .setMessage(msg)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                AppDir.purge(getContext(), true))
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_SEND_DEBUG_INFO);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.debug)
                        .setMessage(R.string.debug_send_info_text)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
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

        preference = findPreference(Prefs.PSK_EXPORT_DATABASE);
        if (preference != null) {
            // Export database - Mainly meant for debug or external processing.
            preference.setOnPreferenceClickListener(p -> {
                final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_TITLE,
                                  getString(R.string.app_name) + '-'
                                  + DateUtils.localSqlDateForToday()
                                             .replace(" ", "-")
                                             .replace(":", "")
                                  + ".ntmb.db");
                startActivityForResult(intent, REQ_PICK_FILE_FOR_EXPORT_DATABASE);
                return true;
            });
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (requestCode == REQ_PICK_FILE_FOR_EXPORT_DATABASE) {
            if (resultCode == Activity.RESULT_OK) {
                Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                final Uri uri = data.getData();
                if (uri != null) {
                    @StringRes
                    int msgId;
                    try {
                        final Context context = getContext();
                        //noinspection ConstantConditions
                        FileUtils.copy(context, DBHelper.getDatabasePath(context), uri);
                        msgId = R.string.progress_end_backup_success;
                    } catch (@NonNull final IOException e) {
                        Logger.error(getContext(), TAG, e);
                        msgId = R.string.error_backup_failed;
                    }
                    //noinspection ConstantConditions
                    Snackbar.make(getView(), msgId, Snackbar.LENGTH_LONG).show();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
