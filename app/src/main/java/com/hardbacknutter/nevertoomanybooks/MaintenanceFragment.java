/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.FileFilter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForWritingContract;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNodeDao;
import com.hardbacknutter.nevertoomanybooks.core.storage.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentMaintenanceBinding;
import com.hardbacknutter.nevertoomanybooks.debug.SqliteShellFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceAlertDialogBuilder;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsViewModel;
import com.hardbacknutter.nevertoomanybooks.utils.FileSize;

@Keep
public class MaintenanceFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "MaintenanceFragment";

    /** The length of a UUID string. */
    private static final int UUID_LEN = 32;

    private SettingsViewModel settingsViewModel;
    private MaintenanceViewModel vm;
    /** The launcher for picking a Uri to write to. */
    private final ActivityResultLauncher<GetContentUriForWritingContract.Input>
            createDocumentLauncher =
            registerForActivityResult(new GetContentUriForWritingContract(),
                                      o -> o.ifPresent(this::createBugReport));
    /** View Binding. */
    private FragmentMaintenanceBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        settingsViewModel = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        vm = new ViewModelProvider(this).get(MaintenanceViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        vb = FragmentMaintenanceBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_settings);

        vb.btnResetTips.setOnClickListener(this::onResetTips);
        vb.btnPurgeFiles.setOnClickListener(this::onPurgeFiles);
        vb.btnPurgeBlns.setOnClickListener(this::onPurgeNodeStates);

        vb.btnSyncDeletedBooks.setOnClickListener(this::onSyncDeletedBooks);
        vb.btnClearDeletedBooks.setOnClickListener(this::onClearDeletedBooks);

        vb.btnRebuildFts.setOnClickListener(this::onRebuildFts);
        vb.btnRebuildIndex.setOnClickListener(this::onRebuildIndex);

        vb.btnCreateBugReport.setOnClickListener(this::onCreateBugReport);
        vb.btnDebugSqShell.setOnClickListener(this::onDebugSqShell);

        vb.btnDebug.setOnClickListener(v -> {
            vm.incDebugClicks();

            if (vm.isShowDbgOptions()) {
                vb.btnDebugSqShell.setVisibility(View.VISIBLE);
            }

            if (vm.isDebugSqLiteAllowsUpdates()) {
                // Sanity check
                if (vb.btnDebugSqShell instanceof MaterialButton) {
                    ((MaterialButton) (vb.btnDebugSqShell))
                            .setIconResource(R.drawable.ic_baseline_warning_24);
                } else {
                    // This SHOULD have worked, but doesn't on a MaterialButton
                    vb.btnDebugSqShell.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.ic_baseline_warning_24, 0, 0, 0);
                }
            }
        });
    }

    private void onResetTips(final View v) {
        TipManager.getInstance().reset(v.getContext());
        //noinspection DataFlowIssue
        Snackbar.make(getView(), R.string.tip_reset_done, Snackbar.LENGTH_LONG).show();
    }

    private void onPurgeFiles(@NonNull final View v) {
        final Context context = v.getContext();
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final CoverStorage coverStorage = serviceLocator.getCoverStorage();

        final List<String> bookUuidList = serviceLocator.getBookDao().getBookUuidList();

        // Filter to check for orphaned cover files
        final FileFilter coverFilter = file -> {
            if (file.getName().length() > UUID_LEN) {
                // not in the list? then we can purge it
                return !((Collection<String>) bookUuidList)
                        .contains(file.getName().substring(0, UUID_LEN));
            }
            // not a uuid base filename ? be careful and leave it.
            return false;
        };

        final long bytes;
        try {
            bytes = FileUtils.getUsedSpace(serviceLocator.getLogDir(), null)
                    + FileUtils.getUsedSpace(serviceLocator.getUpgradesDir(), null)
                    + FileUtils.getUsedSpace(coverStorage.getTempDir(), null)
                    + FileUtils.getUsedSpace(coverStorage.getDir(), coverFilter);

        } catch (@NonNull final CoverStorageException e) {
            ErrorDialog.show(context, TAG, e);
            return;
        } catch (@NonNull final SecurityException e) {
            ErrorDialog.show(context, TAG, e);
            return;
        }

        if (bytes > 0) {
            final String msg = getString(R.string.info_cleanup_files,
                                         FileSize.format(context, bytes),
                                         getString(R.string.option_bug_report));

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.option_purge_files)
                    .setMessage(msg)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        try {
                            FileUtils.deleteDirectory(serviceLocator.getLogDir(), null);
                            FileUtils.deleteDirectory(serviceLocator.getUpgradesDir(), null);
                            FileUtils.deleteDirectory(coverStorage.getTempDir(), null);
                            FileUtils.deleteDirectory(coverStorage.getDir(), coverFilter);

                        } catch (@NonNull final CoverStorageException e) {
                            ErrorDialog.show(context, TAG, e);
                        } catch (@NonNull final SecurityException e) {
                            ErrorDialog.show(context, TAG, e);
                        }
                    })
                    .create()
                    .show();
        } else {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.info_nothing_to_do, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void onSyncDeletedBooks(@NonNull final View v) {
        new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.option_sync_deleted_book_records)
                .setMessage(getString(R.string.info_maintenance_sync_deleted_book_records)
                            + "\n\n" + getString(R.string.confirm_continue))
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final int count = ServiceLocator.getInstance().getDeletedBooksDao().sync();
                    if (count > 0) {
                        settingsViewModel.setForceRebuildBooklist();
                    }
                    //noinspection DataFlowIssue
                    Snackbar.make(getView(), getString(R.string.info_books_deleted, count),
                                  Snackbar.LENGTH_LONG).show();
                })
                .create()
                .show();
    }

    private void onClearDeletedBooks(@NonNull final View v) {
        new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.option_clear_deleted_book_records)
                .setMessage(R.string.info_maintenance_clear_deleted_book_records)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    ServiceLocator.getInstance().getDeletedBooksDao().purge();
                    //noinspection DataFlowIssue
                    Snackbar.make(getView(), R.string.action_done, Snackbar.LENGTH_SHORT).show();
                })
                .create()
                .show();
    }

    private void onPurgeNodeStates(@NonNull final View v) {
        new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.lbl_purge_blns)
                .setMessage(R.string.info_purge_blns_all)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    BooklistNodeDao.clearAll(ServiceLocator.getInstance().getDb());
                    //noinspection DataFlowIssue
                    Snackbar.make(getView(), R.string.action_done, Snackbar.LENGTH_SHORT).show();
                })
                .create()
                .show();
    }

    private void onRebuildFts(@NonNull final View v) {
        new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_info_24)
                .setTitle(R.string.option_rebuild_fts)
                .setMessage(R.string.confirm_rebuild_fts)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    StartupViewModel.schedule(v.getContext(),
                                              StartupViewModel.PK_REBUILD_FTS, false);
                    vb.btnRebuildFts.setError(null);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    StartupViewModel.schedule(v.getContext(),
                                              StartupViewModel.PK_REBUILD_FTS, true);
                    vb.btnRebuildFts.setError(getString(R.string.info_rebuild_scheduled));
                })
                .create()
                .show();
    }

    private void onRebuildIndex(@NonNull final View v) {
        new MaterialAlertDialogBuilder(v.getContext())
                .setIcon(R.drawable.ic_baseline_info_24)
                .setTitle(R.string.option_rebuild_index)
                .setMessage(R.string.confirm_rebuild_index)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    StartupViewModel.schedule(v.getContext(),
                                              StartupViewModel.PK_REBUILD_INDEXES, false);
                    vb.btnRebuildIndex.setError(null);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    StartupViewModel.schedule(v.getContext(),
                                              StartupViewModel.PK_REBUILD_INDEXES, true);
                    vb.btnRebuildIndex.setError(getString(R.string.info_rebuild_scheduled));
                })
                .create()
                .show();
    }

    private void onCreateBugReport(@NonNull final View v) {
        final Context context = v.getContext();

        // We're keeping this as a Dialog:
        // - the user will/should very seldom need this (◔_◔)
        // - It's more explicit in offering a textual OK/Cancel choice.
        new MultiChoiceAlertDialogBuilder<Integer>(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.debug)
                .setMessage(R.string.debug_select_items)
                .setSelectedItems(Set.of(MaintenanceViewModel.DBG_SEND_LOGFILES,
                                         MaintenanceViewModel.DBG_SEND_PREFERENCES))
                .setItems(List.of(MaintenanceViewModel.DBG_SEND_DATABASE,
                                  MaintenanceViewModel.DBG_SEND_DATABASE_UPGRADE,
                                  MaintenanceViewModel.DBG_SEND_LOGFILES,
                                  MaintenanceViewModel.DBG_SEND_PREFERENCES),
                          List.of(context.getString(R.string.option_bug_report_database),
                                  context.getString(
                                          R.string.option_bug_report_database_upgrade),
                                  context.getString(R.string.option_bug_report_logfiles),
                                  context.getString(R.string.option_bug_report_settings)))

                .setPositiveButton(R.string.action_save, selection -> {
                    vm.setDebugSelection(selection);
                    final String fileName = "ntmb-debug-" + LocalDate
                            .now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                    final String mimeType = FileUtils.getMimeTypeFromExtension("zip");
                    createDocumentLauncher.launch(new GetContentUriForWritingContract
                            .Input(mimeType, fileName));
                })
                .build()
                .show();
    }

    private void createBugReport(@NonNull final Uri uri) {
        //noinspection CheckStyle
        try {
            //noinspection DataFlowIssue
            vm.sendDebug(getContext(), uri);

        } catch (@NonNull final RuntimeException | IOException e) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_export_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onDebugSqShell(@NonNull final View v) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .addToBackStack(SqliteShellFragment.TAG)
                .replace(R.id.main_fragment,
                         SqliteShellFragment.create(vm.isDebugSqLiteAllowsUpdates()),
                         SqliteShellFragment.TAG)
                .commit();
    }
}
