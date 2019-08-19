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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertoomanybooks.backup.BackupTask;
import com.hardbacknutter.nevertoomanybooks.backup.ExportOptions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Lets the user choose an archive file to backup to.
 */
public class BackupActivity
        extends BRBaseActivity {

    private static final String TAG = "BackupActivity";

    private EditText mFilenameView;

    /** The ViewModel. */
    private ExportOptionsTaskModel mOptionsModel;

    private final ExportOptionsDialogFragment.OptionsListener mOptionsListener = this::onOptionsSet;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOptionsModel = new ViewModelProvider(this).get(ExportOptionsTaskModel.class);
        mOptionsModel.getTaskFinishedMessage().observe(this, this::onTaskFinishedMessage);
        mOptionsModel.getTaskProgressMessage().observe(this, this::onTaskProgressMessage);
        mOptionsModel.getTaskCancelledMessage().observe(this, this::onTaskCancelledMessage);

        FragmentManager fm = getSupportFragmentManager();

        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTask(mOptionsModel.getTask());
        }

        setTitle(R.string.title_backup);

        String defaultFilename = getString(R.string.app_name) + '-'
                                 + DateUtils.localSqlDateForToday()
                                            .replace(" ", "-")
                                            .replace(":", "")
                                 + BackupManager.ARCHIVE_EXTENSION;

        mFilenameView = findViewById(R.id.file_name);
        mFilenameView.setVisibility(View.VISIBLE);
        mFilenameView.setText(defaultFilename);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_save);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> doBackup());
    }

    /**
     * The user selected a file.
     *
     * @param file selected
     */
    protected void onFileSelected(@NonNull final File file) {
        // Put the name of the selected file into the filename field
        mFilenameView.setText(file.getName());
    }

    private void onTaskFinishedMessage(
            final TaskListener.TaskFinishedMessage<ExportOptions> message) {

        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (message.taskId) {
            case R.id.TASK_ID_WRITE_TO_ARCHIVE:
                if (message.success) {
                    File file = mOptionsModel.getFile();
                    //noinspection ConstantConditions
                    String msg = getString(R.string.export_info_success_archive_details,
                                           file.getParent(), file.getName(),
                                           StorageUtils.formatFileSize(this, file.length()));

                    new AlertDialog.Builder(BackupActivity.this)
                            .setTitle(R.string.lbl_backup_to_archive)
                            .setMessage(msg)
                            .setPositiveButton(android.R.string.ok, (d, which) -> {
                                d.dismiss();
                                Intent data = new Intent().putExtra(UniqueId.BKEY_EXPORT_RESULT,
                                                                    message.result.what);
                                setResult(Activity.RESULT_OK, data);
                                finish();
                            })
                            .create()
                            .show();
                } else {
                    String msg = getString(R.string.error_backup_failed)
                                 + ' ' + getString(R.string.error_storage_not_writable)
                                 + "\n\n"
                                 + getString(R.string.error_if_the_problem_persists);

                    new AlertDialog.Builder(BackupActivity.this)
                            .setTitle(R.string.lbl_backup_to_archive)
                            .setMessage(msg)
                            .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                            .create()
                            .show();
                }
                break;

            default:
                Logger.warnWithStackTrace(this, "taskId=" + message.taskId);
                break;
        }
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (ExportOptionsDialogFragment.TAG.equals(fragment.getTag())) {
            ((ExportOptionsDialogFragment) fragment).setListener(mOptionsListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {

        menu.add(Menu.NONE, R.id.MENU_HIDE_KEYBOARD,
                 MenuHandler.ORDER_HIDE_KEYBOARD, R.string.menu_hide_keyboard)
            .setIcon(R.drawable.ic_keyboard_hide)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_HIDE_KEYBOARD:
                App.hideKeyboard(getWindow().getDecorView());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Local handler for 'Save'. Perform basic validation, and pass on.
     */
    private void doBackup() {
        //noinspection ConstantConditions
        File file = new File(mModel.getRootDir().getAbsolutePath()
                             + File.separator + mFilenameView.getText().toString().trim());
        if (file.exists() && !file.isFile()) {
            UserMessage.show(mListView, R.string.warning_enter_valid_filename);
            return;
        }
        mOptionsModel.setFile(file);

        ExportOptions options = new ExportOptions();

        new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_backup_to_archive)
                .setMessage(R.string.export_info_backup_all)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.btn_options, (d, which) -> {
                    // ask user what options they want
                    FragmentManager fm = getSupportFragmentManager();
                    if (fm.findFragmentByTag(ExportOptionsDialogFragment.TAG) == null) {
                        ExportOptionsDialogFragment.newInstance(options)
                                                   .show(fm, ExportOptionsDialogFragment.TAG);
                    }
                })
                .setPositiveButton(android.R.string.ok, (d, which) -> onOptionsSet(options))
                .create()
                .show();
    }


    /**
     * kick of the backup task.
     */
    private void onOptionsSet(@NonNull final ExportOptions options) {
        // sanity check
        if (options.what == ExportOptions.NOTHING) {
            return;
        }

        // backup 'since'
        if ((options.what & ExportOptions.EXPORT_SINCE) != 0) {
            // no date set, use "since last backup."
            if (options.dateFrom == null) {
                String lastBackup = PreferenceManager
                                            .getDefaultSharedPreferences(this)
                                            .getString(BackupManager.PREF_LAST_BACKUP_DATE, null);
                if (lastBackup != null && !lastBackup.isEmpty()) {
                    options.dateFrom = DateUtils.parseDate(lastBackup);
                }
            }
        } else {
            // cannot have a dateFrom when not asking for a time limited export
            options.dateFrom = null;
        }

        FragmentManager fm = getSupportFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment
                                      .newInstance(R.string.progress_msg_backing_up, false, 0);
            mProgressDialog.show(fm, TAG);

            //noinspection ConstantConditions
            BackupTask task = new BackupTask(mOptionsModel.getFile(), options,
                                             mOptionsModel.getTaskListener());
            mOptionsModel.setTask(task);
            task.execute();
        }
        mProgressDialog.setTask(mOptionsModel.getTask());
    }
}
