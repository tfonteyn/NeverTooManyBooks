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
package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ImportOptions;
import com.eleybourn.bookcatalogue.backup.RestoreTask;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.settings.Prefs;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Lets the user choose an archive file to import from.
 *
 * @author pjw
 */
public class RestoreActivity
        extends BRBaseActivity {

    private final TaskListener<Object, ImportOptions> mTaskListener =
            new TaskListener<Object, ImportOptions>() {

                @Override
                public void onTaskCancelled(@Nullable final Integer taskId) {
                    UserMessage.showUserMessage(RestoreActivity.this,
                                                R.string.progress_end_cancelled);
                    //TODO: add to the message that partial imports might have been done... blah blah
                }

                /**
                 * Listener for tasks.
                 *
                 * @param taskId  a task identifier
                 * @param success {@code true} for success.
                 * @param result  {@link ImportOptions}
                 */
                @Override
                public void onTaskFinished(final int taskId,
                                           final boolean success,
                                           @NonNull final ImportOptions result,
                                           @Nullable final Exception e) {

                    //noinspection SwitchStatementWithTooFewBranches
                    switch (taskId) {
                        case R.id.TASK_ID_READ_FROM_ARCHIVE:
                            if (success) {
                                // see if there are any pre-200 preferences that need migrating.
                                if ((result.what & ImportOptions.PREFERENCES) != 0) {
                                    Prefs.migratePreV200preferences(
                                            Prefs.PREF_LEGACY_BOOK_CATALOGUE);
                                }

                                new AlertDialog.Builder(RestoreActivity.this)
                                        .setTitle(R.string.lbl_import_from_archive)
                                        .setMessage(R.string.progress_end_import_complete)
                                        .setPositiveButton(android.R.string.ok, (d, which) -> {
                                            d.dismiss();
                                            Intent data = new Intent().putExtra(
                                                    UniqueId.BKEY_IMPORT_RESULT,
                                                    result.what);
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
                            Logger.warnWithStackTrace(this, "Unknown taskId=" + taskId);
                            break;
                    }
                }
            };

    private ProgressDialogFragment<Object, ImportOptions> mProgressDialog;

    private final ImportOptionsDialogFragment.OptionsListener mOptionsListener =
            this::onOptionsSet;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();
        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, ImportOptions>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTaskListener(mTaskListener);
        }

        setTitle(R.string.title_import);

        setupList(savedInstanceState);
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
            UserMessage.showUserMessage(mListView, R.string.warning_select_an_existing_file);
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
        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, ImportOptions>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_importing, false, 0);
            RestoreTask task = new RestoreTask(mProgressDialog, options);
            mProgressDialog.show(fm, ProgressDialogFragment.TAG);
            task.execute();
        }
        mProgressDialog.setTaskListener(mTaskListener);
    }
}
