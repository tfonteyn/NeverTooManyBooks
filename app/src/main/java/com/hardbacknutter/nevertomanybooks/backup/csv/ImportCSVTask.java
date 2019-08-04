package com.hardbacknutter.nevertomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.backup.ImportException;
import com.hardbacknutter.nevertomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertomanybooks.backup.Importer;
import com.hardbacknutter.nevertomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

public class ImportCSVTask
        extends TaskBase<Integer> {

    @NonNull
    private final ImportOptions mSettings;
    @NonNull
    private final Importer mImporter;

    /**
     * Constructor.
     *
     * @param context      Current context for accessing resources.
     * @param settings     the import settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public ImportCSVTask(@NonNull final Context context,
                         @NonNull final ImportOptions settings,
                         @NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_CSV_IMPORT, taskListener);
        mSettings = settings;
        mImporter = new CsvImporter(context, settings);
    }

    @Override
    @WorkerThread
    @Nullable
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("ImportCSVTask");

        //TODO: should be using a user context.
        Context context = App.getAppContext();
        Locale userLocale = LocaleUtils.getPreferredLocale();

        try (FileInputStream is = new FileInputStream(mSettings.file)) {
            //noinspection ConstantConditions
            mImporter.doBooks(context, userLocale,
                              is, new LocalCoverFinder(mSettings.file.getParent()),
                              new ProgressListener() {

                                  private int mMaxPosition;

                                  @Override
                                  public void setMax(final int maxPosition) {
                                      mMaxPosition = maxPosition;
                                  }

                                  @Override
                                  public void incMax(final int delta) {
                                      mMaxPosition += delta;
                                  }

                                  @Override
                                  public void onProgress(final int absPosition,
                                                         @Nullable final Object message) {
                                      Object[] values = {message};
                                      publishProgress(new TaskProgressMessage(mTaskId, mMaxPosition,
                                                                              absPosition, values));
                                  }

                                  @Override
                                  public boolean isCancelled() {
                                      return ImportCSVTask.this.isCancelled();
                                  }
                              }
            );

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
            Logger.error(this, e);
            mException = e;

        } catch (@NonNull final ImportException e) {
            Logger.error(this, e);
            mException = e;

        } finally {
            try {
                mImporter.close();
            } catch (@NonNull final IOException ignore) {
            }
        }
        return null;
    }
}
