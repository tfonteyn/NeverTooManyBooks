/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNodeDao;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentMaintenanceBinding;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SqliteShellFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

@Keep
public class MaintenanceFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "MaintenanceFragment";

    /** The length of a UUID string. */
    private static final int UUID_LEN = 32;

    /**
     * After clicking the debug category header 3 times, we display the debug options.
     * SQLite shell updates are not allowed.
     */
    private static final int DEBUG_CLICKS = 3;
    /** After clicking the header 3 more times, the SQLite shell will allow updates. */
    private static final int DEBUG_CLICKS_ALLOW_SQL_UPDATES = 6;
    /** After clicking the header another 3 times, the option to delete all data becomes visible. */
    private static final int DEBUG_CLICKS_ALLOW_DELETE_ALL = 9;
    private int mDebugClicks;
    private boolean mDebugSqLiteAllowsUpdates;

    /** View Binding. */
    private FragmentMaintenanceBinding mVb;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        mVb = FragmentMaintenanceBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.lbl_settings,
                 R.string.pt_maintenance);

        mVb.btnDebug.setOnClickListener(v -> {
            mDebugClicks++;
            if (mDebugClicks >= DEBUG_CLICKS) {
                mVb.btnDebugDumpPrefs.setVisibility(View.VISIBLE);
                mVb.btnDebugSqShell.setVisibility(View.VISIBLE);
            }

            if (mDebugClicks >= DEBUG_CLICKS_ALLOW_SQL_UPDATES) {
                mVb.btnDebugSqShell.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_baseline_warning_24, 0, 0, 0);
                mDebugSqLiteAllowsUpdates = true;
            }

            if (mDebugClicks >= DEBUG_CLICKS_ALLOW_DELETE_ALL) {
                // show the button, it's red...
                //URGENT: re-enable once the #onDeleteAll functionality is complete
//                mVb.btnDebugClearDb.setVisibility(View.VISIBLE);
            }
        });

        mVb.btnResetTips.setOnClickListener(v -> {
            TipManager.getInstance().reset();
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.tip_reset_done, Snackbar.LENGTH_LONG).show();
        });

        mVb.btnPurgeFiles.setOnClickListener(v -> {
            final Context context = v.getContext();
            final ArrayList<String> bookUuidList =
                    ServiceLocator.getInstance().getBookDao().getBookUuidList();

            final long bytes;
            try {
                bytes = purge(bookUuidList, false);

            } catch (@NonNull final CoverStorageException | SecurityException e) {
                StandardDialogs.showError(context, ExMsg
                        .map(context, e)
                        .orElse(getString(R.string.error_storage_not_accessible)));
                return;
            }

            final String msg = getString(R.string.txt_cleanup_files,
                                         FileUtils.formatFileSize(context, bytes),
                                         getString(R.string.lbl_send_debug));

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.lbl_purge_files)
                    .setMessage(msg)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        try {
                            purge(bookUuidList, true);

                        } catch (@NonNull final CoverStorageException | SecurityException e) {
                            StandardDialogs.showError(context, ExMsg
                                    .map(context, e)
                                    .orElse(getString(R.string.error_storage_not_accessible)));
                        }
                    })
                    .create()
                    .show();
        });

        mVb.btnPurgeBlns.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.lbl_purge_blns)
                .setMessage(R.string.info_purge_blns_all)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> BooklistNodeDao.clearAll())
                .create()
                .show());

        mVb.btnRebuildFts.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.menu_rebuild_fts)
                .setMessage(R.string.confirm_rebuild_fts)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    StartupViewModel.schedule(StartupViewModel.PK_REBUILD_FTS, false);
                    mVb.btnRebuildFts.setError(null);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    StartupViewModel.schedule(StartupViewModel.PK_REBUILD_FTS, true);
                    mVb.btnRebuildFts.setError(getString(R.string.txt_rebuild_scheduled));
                })
                .create()
                .show());

        mVb.btnRebuildIndex.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.menu_rebuild_index)
                .setMessage(R.string.confirm_rebuild_index)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    StartupViewModel.schedule(StartupViewModel.PK_REBUILD_INDEXES, false);
                    mVb.btnRebuildIndex.setError(null);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    StartupViewModel.schedule(StartupViewModel.PK_REBUILD_INDEXES, true);
                    mVb.btnRebuildIndex.setError(getString(R.string.txt_rebuild_scheduled));
                })
                .create()
                .show());

        mVb.btnDebugSendMail.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.debug)
                .setMessage(R.string.debug_send_info_text)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (!DebugReport.sendDebugInfo(v.getContext())) {
                        //noinspection ConstantConditions
                        Snackbar.make(getView(), R.string.error_email_failed,
                                      Snackbar.LENGTH_LONG).show();
                    }
                })
                .create()
                .show());

        mVb.btnDebugDumpPrefs.setOnClickListener(v -> logPreferences());

        mVb.btnDebugSqShell.setOnClickListener(v -> {
            final Bundle args = new Bundle();
            args.putBoolean(SqliteShellFragment.BKEY_ALLOW_UPDATES, mDebugSqLiteAllowsUpdates);
            final Fragment fragment = new SqliteShellFragment();
            fragment.setArguments(args);

            final FragmentManager fm = getParentFragmentManager();
            fm.beginTransaction()
              .setReorderingAllowed(true)
              .addToBackStack(SqliteShellFragment.TAG)
              .replace(R.id.main_fragment, fragment, SqliteShellFragment.TAG)
              .commit();
        });

        mVb.btnDebugClearDb.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
                .setTitle(R.string.action_clear_all_data)
                .setIcon(R.drawable.ic_baseline_delete_24)
                .setMessage(R.string.confirm_clear_all_data)
                .setNegativeButton(R.string.no, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_delete, (d, w) -> onDeleteAll())
                .create()
                .show());
    }

    /**
     * Write the global preferences to the log file.
     */
    private void logPreferences() {
        //noinspection ConstantConditions
        final Map<String, ?> map = PreferenceManager
                .getDefaultSharedPreferences(getContext()).getAll();
        final List<String> keyList = new ArrayList<>(map.keySet());
        Collections.sort(keyList);

        final StringBuilder sb = new StringBuilder("dumpPreferences|\n\nSharedPreferences:");
        for (final String key : keyList) {
            sb.append('\n').append(key).append('=').append(map.get(key));
        }
        sb.append("\n\n");

        Logger.warn(TAG, sb.toString());
    }


    /**
     * Count size / Cleanup any purgeable files.
     *
     * @param bookUuidList a list of book uuid to check for orphaned covers
     * @param reallyDelete {@code true} to actually delete files,
     *                     {@code false} to only sum file sizes in bytes
     *
     * @return the total size in bytes of purgeable/purged files.
     *
     * @throws CoverStorageException The covers directory is not available
     */
    private long purge(@NonNull final Collection<String> bookUuidList,
                       final boolean reallyDelete)
            throws CoverStorageException {

        // check for orphaned cover files
        final FileFilter coverFilter = file -> {
            if (file.getName().length() > UUID_LEN) {
                // not in the list? then we can purge it
                return !bookUuidList.contains(file.getName().substring(0, UUID_LEN));
            }
            // not a uuid base filename ? be careful and leave it.
            return false;
        };

        if (reallyDelete) {
            return delete(coverFilter);
        } else {
            return count(coverFilter);
        }
    }

    private long count(@Nullable final FileFilter coverFilter)
            throws CoverStorageException {
        final Context context = getContext();
        //noinspection ConstantConditions
        return FileUtils.getUsedSpace(ServiceLocator.getLogDir(), null)
               + FileUtils.getUsedSpace(ServiceLocator.getUpgradesDir(), null)
               + FileUtils.getUsedSpace(CoverDir.getTemp(context), null)
               + FileUtils.getUsedSpace(CoverDir.getDir(context), coverFilter);
    }

    private long delete(@Nullable final FileFilter coverFilter)
            throws CoverStorageException {
        final Context context = getContext();
        //noinspection ConstantConditions
        return FileUtils.deleteDirectory(ServiceLocator.getLogDir(), null, null)
               + FileUtils.deleteDirectory(ServiceLocator.getUpgradesDir(), null, null)
               + FileUtils.deleteDirectory(CoverDir.getTemp(context), null, null)
               + FileUtils.deleteDirectory(CoverDir.getDir(context), coverFilter, null);
    }

    private void onDeleteAll() {
        final Context context = getContext();
        try {
            //FIXME: delete all style xml files
            ServiceLocator.getInstance().getStyles().clearCache();

            //noinspection ConstantConditions
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply();

            ServiceLocator.getInstance().deleteDatabases(context);

            delete(null);

            Snackbar.make(mVb.getRoot(), "Now quit the app", Snackbar.LENGTH_LONG).show();

        } catch (@NonNull final CoverStorageException | SecurityException e) {
            //noinspection ConstantConditions
            StandardDialogs.showError(context, ExMsg
                    .map(context, e).orElse(getString(R.string.error_storage_not_accessible)));
        }
    }
}
