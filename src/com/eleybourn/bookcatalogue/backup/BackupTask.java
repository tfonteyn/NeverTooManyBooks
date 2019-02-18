package com.eleybourn.bookcatalogue.backup;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter;
import com.eleybourn.bookcatalogue.backup.tararchive.TarBackupContainer;
import com.eleybourn.bookcatalogue.backup.ui.BackupFileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;

public class BackupTask
        extends TaskWithProgress<ExportSettings> {

    private final String mBackupDate = DateUtils.utcSqlDateTimeForToday();

    @NonNull
    private final ExportSettings mSettings;
    @NonNull
    private final File mTmpFile;

    public BackupTask(final int taskId,
                      @NonNull final FragmentActivity context,
                      @NonNull final ExportSettings settings) {
        super(taskId, context, R.string.progress_msg_backing_up, false);
        mSettings = settings;

        // sanity checks
        if ((mSettings.file == null) || ((mSettings.what & ExportSettings.MASK) == 0)) {
            throw new IllegalArgumentException("Options must be specified: " + mSettings);
        }

        // Ensure the file key extension is what we want
        if (!BackupFileDetails.isArchive(mSettings.file)) {
            mSettings.file = new File(
                    mSettings.file.getAbsoluteFile() + BackupFileDetails.ARCHIVE_EXTENSION);
        }

        // we write to the temp file, and rename it upon success
        mTmpFile = new File(mSettings.file.getAbsolutePath() + ".tmp");
    }

    @Override
    @NonNull
    protected ExportSettings doInBackground(final Void... params) {
        BackupContainer bkp = new TarBackupContainer(mTmpFile);
        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(this, "backup",
                        "starting|file=" + mTmpFile.getAbsolutePath());
        }
        try (BackupWriter wrt = bkp.newWriter()) {
            // go go go...
            wrt.backup(mSettings, new BackupWriter.BackupWriterListener() {

                private int mProgress;

                @Override
                public void setMax(final int max) {
                    mFragment.setMax(max);
                }

                @Override
                public void onProgressStep(@Nullable final String message,
                                           final int delta) {
                    mProgress += delta;
                    publishProgress(message, mProgress);
                }

                @Override
                public boolean isCancelled() {
                    return mFragment.isCancelled();
                }
            });

            if (!mFragment.isCancelled()) {
                // success
                StorageUtils.deleteFile(mSettings.file);
                //noinspection ConstantConditions
                StorageUtils.renameFile(mTmpFile, mSettings.file);

                SharedPreferences.Editor ed = Prefs.getPrefs().edit();
                // if the backup was a full one (not a 'since') remember that.
                if ((mSettings.what & ExportSettings.ALL) != 0) {
                    ed.putString(BackupManager.PREF_LAST_BACKUP_DATE, mBackupDate);
                }
                //noinspection ConstantConditions
                ed.putString(BackupManager.PREF_LAST_BACKUP_FILE, mSettings.file.getAbsolutePath());
                ed.apply();

                if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                    //noinspection ConstantConditions
                    Logger.info(this,
                                "backup", "finished|file="
                                        + mSettings.file.getAbsolutePath()
                                        + ", size = " + mSettings.file.length());
                }
            }
        } catch (IOException e) {
            Logger.error(e);
            mException = e;
            // cleanup
            StorageUtils.deleteFile(mTmpFile);
        } finally {
            if (mFragment.isCancelled() || mException != null) {
                // cleanup
                StorageUtils.deleteFile(mTmpFile);
            }
        }

        return mSettings;
    }
}
