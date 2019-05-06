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
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import java.io.File;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.RestoreTask;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.OnTaskFinishedListener;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Lets the user choose an archive file to import from.
 *
 * @author pjw
 */
public class RestoreActivity
        extends BRBaseActivity
        implements
        ImportOptionsDialogFragment.OnOptionsListener,
        OnTaskFinishedListener<ImportSettings> {

    private ProgressDialogFragment<Object, ImportSettings> mProgressDialog;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();

        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, ImportSettings>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setOnTaskFinishedListener(this);
//            mProgressDialog.setOnProgressCancelledListener(this);
        }

        if (fm.findFragmentByTag(FileChooserFragment.TAG) == null) {
            createFileBrowser("");
        }

        Button confirm = findViewById(R.id.confirm);
        confirm.setText(R.string.btn_confirm_open);
        confirm.setOnClickListener(v -> doRestore());

        setTitle(R.string.lbl_import_from_archive);
    }

    /**
     * Local handler for 'Open'. Perform basic validation, and pass on.
     */
    private void doRestore() {
        FileChooserFragment frag = (FileChooserFragment)
                getSupportFragmentManager().findFragmentByTag(FileChooserFragment.TAG);
        if (frag != null) {
            File file = frag.getSelectedFile();
            if (!file.exists() || !file.isFile()) {
                //noinspection ConstantConditions
                UserMessage.showUserMessage(frag.getView(),
                                            R.string.warning_select_an_existing_file);
                return;
            }

            final ImportSettings settings = new ImportSettings();
            settings.file = file;

            new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_import_from_archive)
                    .setMessage(R.string.import_option_info_all_books)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setNeutralButton(R.string.btn_options, (d, which) ->
                            // ask user what options he they want
                            ImportOptionsDialogFragment.show(getSupportFragmentManager(), settings))
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        // User wants to import all.
                        settings.what = ImportSettings.ALL;
                        onOptionsSet(settings);
                    })
                    .create()
                    .show();
        }
    }

    /**
     * kick of the restore task.
     */
    @Override
    public void onOptionsSet(@NonNull final ImportSettings settings) {
        // sanity check
        if (settings.what == ImportSettings.NOTHING) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, ImportSettings>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_importing, false, 0);
            RestoreTask task = new RestoreTask(mProgressDialog, settings);
            mProgressDialog.show(fm, ProgressDialogFragment.TAG);
            task.execute();
        }
        mProgressDialog.setOnTaskFinishedListener(this);
//        mProgressDialog.setOnProgressCancelledListener(this);
    }

    /**
     * Listener for tasks.
     *
     * @param taskId  a task identifier
     * @param success {@code true} for success.
     * @param result  {@link ImportSettings}
     */
    @Override
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               @NonNull final ImportSettings result,
                               @Nullable final Exception e) {

        //noinspection SwitchStatementWithTooFewBranches
        switch (taskId) {
            case R.id.TASK_ID_READ_FROM_ARCHIVE:
                if (success) {
                    // see if there are any pre-200 preferences that need migrating.
                    if ((result.what & ImportSettings.PREFERENCES) != 0) {
                        Prefs.migratePreV200preferences(App.PREF_LEGACY_BOOK_CATALOGUE);
                    }

                    new AlertDialog.Builder(this)
                            .setTitle(R.string.lbl_import_from_archive)
                            .setMessage(R.string.progress_end_import_complete)
                            .setPositiveButton(android.R.string.ok, (d, which) -> {
                                d.dismiss();
                                Intent data = new Intent().putExtra(UniqueId.BKEY_IMPORT_RESULT,
                                                                    result.what);
                                setResult(Activity.RESULT_OK, data);
                                finish();
                            })
                            .create()
                            .show();
                } else {
                    String msg = getString(R.string.error_import_failed)
                            + ' ' + getString(R.string.error_storage_not_readable)
                            + "\n\n" + getString(R.string.error_if_the_problem_persists);

                    new AlertDialog.Builder(this)
                            .setTitle(R.string.lbl_import_from_archive)
                            .setMessage(msg)
                            .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                            .create()
                            .show();
                }
                break;

            default:
                Logger.warnWithStackTrace(this, "Unknown taskId=" + taskId);
                break;
        }
    }
}
