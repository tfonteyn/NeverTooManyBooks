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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hardbacknutter.nevertomanybooks.MenuHandler;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertomanybooks.backup.BackupTask;
import com.hardbacknutter.nevertomanybooks.backup.ExportOptions;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertomanybooks.utils.Utils;
import com.hardbacknutter.nevertomanybooks.viewmodels.ExportOptionsTaskModel;

import java.io.File;

/**
 * Lets the user choose an archive file to backup to.
 */
public class BackupActivity
        extends BRBaseActivity {

    private static final String TAG = "BackupActivity";

    private EditText mFilenameView;
    /** The ViewModel. */
    private ExportOptionsTaskModel mModel;

    private final ExportOptionsDialogFragment.OptionsListener mOptionsListener = this::onOptionsSet;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = ViewModelProviders.of(this).get(ExportOptionsTaskModel.class);
        mModel.getTaskFinishedMessage().observe(this, this::onTaskFinishedMessage);
        mModel.getTaskProgressMessage().observe(this, this::onTaskProgressMessage);
        mModel.getTaskCancelledMessage().observe(this, this::onTaskCancelledMessage);

        FragmentManager fm = getSupportFragmentManager();

        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTask(mModel.getTask());
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

        setupList(savedInstanceState);
    }

    private void onTaskFinishedMessage(final TaskListener.TaskFinishedMessage<ExportOptions> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (message.taskId) {
            case R.id.TASK_ID_WRITE_TO_ARCHIVE:
                if (message.success) {
                    //noinspection ConstantConditions
                    String msg = getString(R.string.export_info_success_archive_details,
                            message.result.file.getParent(),
                            message.result.file.getName(),
                            StorageUtils.formatFileSize(this,
                                    message.result.file.length()));

                    new AlertDialog.Builder(BackupActivity.this)
                            .setTitle(R.string.lbl_backup_to_archive)
                            .setMessage(msg)
                            .setPositiveButton(android.R.string.ok, (d, which) -> {
                                d.dismiss();
                                Intent data = new Intent().putExtra(
                                        UniqueId.BKEY_EXPORT_RESULT,
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
                Utils.hideKeyboard(getWindow().getDecorView());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
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

    /**
     * Local handler for 'Save'. Perform basic validation, and pass on.
     */
    private void doBackup() {
        File file = new File(mRootDir.getAbsolutePath()
                + File.separator + mFilenameView.getText().toString().trim());
        if (file.exists() && !file.isFile()) {
            UserMessage.show(mListView, R.string.warning_enter_valid_filename);
            return;
        }

        final ExportOptions options = new ExportOptions();
        options.file = file;

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
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    options.what = ExportOptions.ALL;
                    onOptionsSet(options);
                })
                .create()
                .show();
    }

    /**
     * kick of the backup task.
     */
    public void onOptionsSet(@NonNull final ExportOptions options) {
        // sanity check
        if (options.what == ExportOptions.NOTHING) {
            return;
        }

        // backup 'since'
        if ((options.what & ExportOptions.EXPORT_SINCE) != 0) {
            // no date set, use "since last backup."
            if (options.dateFrom == null) {
                String lastBackup = PreferenceManager.getDefaultSharedPreferences(this)
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
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_backing_up, false, 0);
            mProgressDialog.show(fm, TAG);

            BackupTask task = new BackupTask(options, mModel.getTaskListener());
            mModel.setTask(task);
            task.execute();
        }
        mProgressDialog.setTask(mModel.getTask());
    }
}
