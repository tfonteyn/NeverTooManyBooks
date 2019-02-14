package com.eleybourn.bookcatalogue.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;

import java.io.IOException;
import java.util.Objects;

public class RestoreTask
        extends TaskWithProgress<ImportSettings> {

    @NonNull
    private final ImportSettings mSettings;

    public RestoreTask(final int taskId,
                       @NonNull final FragmentActivity context,
                       @NonNull final ImportSettings /* in/out */settings) {
        super(taskId, context, R.string.progress_msg_importing, false);
        mSettings = settings;

        if ((mSettings.what & ImportSettings.MASK) == 0) {
            throw new IllegalArgumentException("Options must be specified");
        }
    }

    @Override
    @NonNull
    protected ImportSettings doInBackground(final Void... params) {
        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(this, "restore",
                        "starting|file=" + mSettings.file.getAbsolutePath());
        }
        try (BackupReader reader = BackupManager.readFrom(mSettings.file)) {
            Objects.requireNonNull(reader);
            reader.restore(mSettings, new BackupReader.BackupReaderListener() {

                private int mProgress;

                @Override
                public void setMax(final int max) {
                    mFragment.setMax(max);
                }

                @Override
                public void onProgressStep(@NonNull final String message,
                                           final int delta) {
                    mProgress += delta;
                    publishProgress(message, mProgress);
                }

                @Override
                public boolean isCancelled() {
                    return mFragment.isCancelled();
                }
            });

            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(BackupManager.class,
                            "restore|finishing|file=" + mSettings.file.getAbsolutePath()
                                    + ", size = " + mSettings.file.length());
            }
        } catch (IOException e) {
            Logger.error(e);
            mException = e;
        }

        return mSettings;
    }
}
