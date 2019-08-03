/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertomanybooks.backup.RestoreTask;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertomanybooks.viewmodels.ImportOptionsTaskModel;

import java.io.File;

/**
 * Lets the user choose an archive file to import from.
 */
public class RestoreActivity
        extends BRBaseActivity {

    private static final String TAG = "RestoreActivity";

    /** The ViewModel. */
    private ImportOptionsTaskModel mModel;

    private final ImportOptionsDialogFragment.OptionsListener mOptionsListener = this::onOptionsSet;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = ViewModelProviders.of(this).get(ImportOptionsTaskModel.class);
        mModel.getTaskFinishedMessage().observe(this, this::onTaskFinishedMessage);
        mModel.getTaskProgressMessage().observe(this, this::onTaskProgressMessage);
        mModel.getTaskCancelledMessage().observe(this, this::onTaskCancelledMessage);

        FragmentManager fm = getSupportFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTask(mModel.getTask());
        }

        setTitle(R.string.title_import);

        setupList(savedInstanceState);
    }

    private void onTaskFinishedMessage(final TaskListener.TaskFinishedMessage<ImportOptions> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (message.taskId) {
            case R.id.TASK_ID_READ_FROM_ARCHIVE:
                if (message.success) {
                    // see if there are any pre-200 preferences that need migrating.
                    if ((message.result.what & ImportOptions.PREFERENCES) != 0) {
                        Prefs.migratePreV200preferences(this,
                                Prefs.PREF_LEGACY_BOOK_CATALOGUE);
                    }
                    String importMsg;
                    if (message.result.results != null) {
                        importMsg = '\n'
                                + getString(R.string.progress_msg_n_created_m_updated,
                                message.result.results.booksCreated,
                                message.result.results.booksUpdated);
                        if (message.result.results.booksFailed > 0) {
                            importMsg += '\n'
                                    + getString(R.string.error_failed_x,
                                    message.result.results.booksFailed);
                        }
                        importMsg += '\n'
                                + getString(R.string.progress_msg_covers_handled,
                                message.result.results.coversImported);
                    } else {
                        importMsg = getString(R.string.progress_end_import_complete);
                    }

                    new AlertDialog.Builder(RestoreActivity.this)
                            .setTitle(R.string.lbl_import_from_archive)
                            .setMessage(importMsg)
                            .setPositiveButton(android.R.string.ok, (d, which) -> {
                                d.dismiss();
                                Intent data = new Intent().putExtra(
                                        UniqueId.BKEY_IMPORT_RESULT,
                                        message.result.what);
                                setResult(Activity.RESULT_OK, data);
                                finish();
                            })
                            .create()
                            .show();
                } else {
                    String msg = getString(R.string.error_import_failed)
                            + ' ' + getString(R.string.error_storage_not_readable)
                            + "\n\n"
                            + getString(R.string.error_if_the_problem_persists);

                    new AlertDialog.Builder(RestoreActivity.this)
                            .setTitle(R.string.lbl_import_from_archive)
                            .setMessage(msg)
                            .setPositiveButton(android.R.string.ok,
                                    (d, which) -> d.dismiss())
                            .create()
                            .show();
                }
                break;

            default:
                Logger.warnWithStackTrace(this, "Unknown taskId=" + message.taskId);
                break;
        }
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (ImportOptionsDialogFragment.TAG.equals(fragment.getTag())) {
            ((ImportOptionsDialogFragment) fragment).setListener(mOptionsListener);
        }
    }

    /**
     * The user selected a file.
     *
     * @param file selected
     */
    protected void onFileSelected(@NonNull final File file) {
        // sanity check
        if (!file.exists() || !file.isFile()) {
            UserMessage.show(mListView, R.string.warning_select_an_existing_file);
            return;
        }

        final ImportOptions options = new ImportOptions();
        options.file = file;

        new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(R.string.import_option_info_all_books)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.btn_options, (d, which) -> {
                    // ask user what options they want
                    FragmentManager fm = getSupportFragmentManager();
                    if (fm.findFragmentByTag(ImportOptionsDialogFragment.TAG) == null) {
                        ImportOptionsDialogFragment.newInstance(options).show(fm,
                                ImportOptionsDialogFragment.TAG);
                    }
                })
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    // User wants to import all.
                    options.what = ImportOptions.ALL;
                    onOptionsSet(options);
                })
                .create()
                .show();

    }

    /**
     * kick of the restore task.
     */
    public void onOptionsSet(@NonNull final ImportOptions options) {
        // sanity check
        if (options.what == ImportOptions.NOTHING) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_importing, false, 0);
            mProgressDialog.show(fm, TAG);

            RestoreTask task = new RestoreTask(options, mModel.getTaskListener());
            mModel.setTask(task);
            task.execute();
        }
        mProgressDialog.setTask(mModel.getTask());
    }
}
