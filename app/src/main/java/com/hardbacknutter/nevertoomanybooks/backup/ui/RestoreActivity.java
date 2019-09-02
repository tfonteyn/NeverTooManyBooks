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
package com.hardbacknutter.nevertoomanybooks.backup.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertoomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.backup.RestoreTask;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Lets the user choose an archive file to import from.
 */
public class RestoreActivity
        extends BRBaseActivity {

    private static final String TAG = "RestoreActivity";

    /** The ViewModel. */
    private ImportOptionsTaskModel mOptionsModel;

    private final OptionsDialogBase.OptionsListener<ImportOptions> mOptionsListener
            = this::onOptionsSet;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOptionsModel = new ViewModelProvider(this).get(ImportOptionsTaskModel.class);
        mOptionsModel.getTaskFinishedMessage().observe(this, this::onTaskFinishedMessage);
        mOptionsModel.getTaskProgressMessage().observe(this, this::onTaskProgressMessage);
        mOptionsModel.getTaskCancelledMessage().observe(this, this::onTaskCancelledMessage);

        FragmentManager fm = getSupportFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTask(mOptionsModel.getTask());
        }

        setTitle(R.string.title_import);
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
        mOptionsModel.setFile(file);

        ImportOptions options = new ImportOptions(ImportOptions.ALL);

        new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(R.string.import_option_info_all_books)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.btn_options, (d, which) -> {
                    // ask user what options they want
                    FragmentManager fm = getSupportFragmentManager();
                    if (fm.findFragmentByTag(ImportOptionsDialogFragment.TAG) == null) {
                        ImportOptionsDialogFragment.newInstance(options, archiveHasValidDates(file))
                                                   .show(fm, ImportOptionsDialogFragment.TAG);
                    }
                })
                .setPositiveButton(android.R.string.ok, (d, which) -> onOptionsSet(options))
                .create()
                .show();
    }

    private void onTaskCancelledMessage(
            @NonNull final TaskListener.TaskFinishedMessage<ImportOptions> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

        if (message.taskId == R.id.TASK_ID_READ_FROM_ARCHIVE) {
            if (message.wasSuccessful) {
                // see if there are any pre-200 preferences that need migrating.
                if ((message.result.what & Options.PREFERENCES) != 0) {
                    Prefs.migratePreV200preferences(this, Prefs.PREF_LEGACY_BOOK_CATALOGUE);
                }

                String reportMsg = getString(R.string.progress_end_import_partially_complete)
                                   + createImportReport(message.result.what,
                                                        message.result.results);
                reportResults(reportMsg, message.result.what);
            } else {
                reportFailure();
            }
        } else {
            Logger.warnWithStackTrace(this, "taskId=" + message.taskId);
        }
    }

    private void onTaskFinishedMessage(
            @NonNull final TaskListener.TaskFinishedMessage<ImportOptions> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

        if (message.taskId == R.id.TASK_ID_READ_FROM_ARCHIVE) {
            if (message.wasSuccessful) {
                // see if there are any pre-200 preferences that need migrating.
                if ((message.result.what & Options.PREFERENCES) != 0) {
                    Prefs.migratePreV200preferences(this, Prefs.PREF_LEGACY_BOOK_CATALOGUE);
                }
                String reportMsg = getString(R.string.progress_end_import_complete)
                                   + createImportReport(message.result.what,
                                                        message.result.results);
                reportResults(reportMsg, message.result.what);
            } else {
                reportFailure();
            }
        } else {
            Logger.warnWithStackTrace(this, "taskId=" + message.taskId);
        }
    }

    /**
     * Transform the result data into a user friendly report.
     */
    private String createImportReport(final int what,
                                      @Nullable final Importer.Results results) {
        StringBuilder msg = new StringBuilder("\n");

        if (results != null) {
            msg.append(getString(R.string.progress_msg_n_created_m_updated,
                                 results.booksCreated, results.booksUpdated)).append("\n");

            if (results.booksFailed > 0) {
                msg.append(getString(R.string.error_failed_x, results.booksFailed)).append("\n");
            }

            msg.append(getString(R.string.progress_msg_covers_handled, results.coversImported));
        }
        if ((what & Options.PREFERENCES) != 0) {
            msg.append("\n").append(getString(R.string.lbl_settings));
        }
        if ((what & Options.BOOK_LIST_STYLES) != 0) {
            msg.append("\n").append(getString(R.string.lbl_styles));
        }
        return msg.toString();
    }

    /**
     * All's well that end's well.
     * <p>
     * Finish the activity after the user confirms. We return the 'what' result.
     */
    private void reportResults(@NonNull final String message,
                               final int what) {
        new AlertDialog.Builder(RestoreActivity.this)
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    d.dismiss();
                    Intent data = new Intent().putExtra(UniqueId.BKEY_IMPORT_RESULT, what);
                    setResult(Activity.RESULT_OK, data);
                    finish();
                })
                .create()
                .show();
    }

    /**
     * It all went horribly wrong.
     */
    private void reportFailure() {
        String msg = getString(R.string.error_import_failed)
                     + ' ' + getString(R.string.error_storage_not_readable) + "\n\n"
                     + getString(R.string.error_if_the_problem_persists);

        new AlertDialog.Builder(RestoreActivity.this)
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                .create()
                .show();
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (ImportOptionsDialogFragment.TAG.equals(fragment.getTag())) {
            ((ImportOptionsDialogFragment) fragment).setListener(mOptionsListener);
        }
    }

    /**
     * kick of the restore task.
     */
    private void onOptionsSet(@NonNull final ImportOptions options) {
        // sanity check
        if (options.what == Options.NOTHING) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_importing, false, 0);
            mProgressDialog.show(fm, TAG);

            //noinspection ConstantConditions
            RestoreTask task = new RestoreTask(mOptionsModel.getFile(), options,
                                               mOptionsModel.getTaskListener());
            mOptionsModel.setTask(task);
            task.execute();
        }
        mProgressDialog.setTask(mOptionsModel.getTask());
    }

    /**
     * read the info block and check if we have valid dates.
     *
     * @param file to check
     *
     * @return {@code true} if the archive has (or is supposed to have) valid dates
     */
    public boolean archiveHasValidDates(@NonNull final File file) {
        boolean hasValidDates;
        try (BackupReader reader = BackupManager.getReader(this, file)) {
            BackupInfo info = reader.getInfo();
            reader.close();
            hasValidDates = info.getAppVersionCode() >= 152;
        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            hasValidDates = false;
        }
        return hasValidDates;
    }
}
