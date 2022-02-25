/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.common.DataWriterTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

/**
 * Coordinate between the UI and the {@link ExportHelper}.
 * Handle the export related background tasks.
 */
@SuppressWarnings("WeakerAccess")
public class ExportViewModel
        extends ViewModel {

    private static final ArchiveEncoding[] ENCODINGS = {
            ArchiveEncoding.Zip,
            ArchiveEncoding.Csv,
            ArchiveEncoding.Json,
            ArchiveEncoding.Xml,
            ArchiveEncoding.SqLiteDb};

    @NonNull
    private final ExportHelper mHelper = new ExportHelper();
    @NonNull
    private final DataWriterTask<ExportResults> mWriterTask = new DataWriterTask<>();

    /** UI helper. */
    private boolean mQuickOptionsAlreadyShown;

    @Override
    protected void onCleared() {
        mWriterTask.cancel();
        super.onCleared();
    }

    boolean isQuickOptionsAlreadyShown() {
        return mQuickOptionsAlreadyShown;
    }

    void setQuickOptionsAlreadyShown(
            @SuppressWarnings("SameParameterValue") final boolean quickOptionsAlreadyShown) {
        mQuickOptionsAlreadyShown = quickOptionsAlreadyShown;
    }

    @NonNull
    ExportHelper getExportHelper() {
        return mHelper;
    }

    /**
     * Get the {@link ArchiveEncoding} for the given position in the dropdown menu.
     *
     * @param position to get
     *
     * @return encoding
     */
    @NonNull
    ArchiveEncoding getEncoding(final int position) {
        return ENCODINGS[position];
    }

    /**
     * Get the list of options (and initial position) for the drop down menu
     * for the archive format.
     *
     * @param context Current context
     *
     * @return initial position + list
     */
    @NonNull
    Pair<Integer, ArrayList<String>> getFormatOptions(@NonNull final Context context) {
        final ArchiveEncoding currentEncoding = mHelper.getEncoding();
        int initialPos = 0;
        final ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < ENCODINGS.length; i++) {
            final ArchiveEncoding encoding = ENCODINGS[i];
            if (encoding == currentEncoding) {
                initialPos = i;
            }
            list.add(context.getString(encoding.getSelectorResId()));
        }

        return new Pair<>(initialPos, list);
    }

    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mWriterTask.onProgress();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<ExportResults>>> onExportCancelled() {
        return mWriterTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onExportFailure() {
        return mWriterTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<ExportResults>>> onExportFinished() {
        return mWriterTask.onFinished();
    }

    boolean isExportRunning() {
        return mWriterTask.isRunning();
    }

    void startExport(@NonNull final Uri uri) {
        mHelper.setUri(uri);
        mWriterTask.start(mHelper);
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mWriterTask.getTaskId()) {
            mWriterTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
