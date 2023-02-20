/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

/**
 * Fetch an image from the {@link FileManager}.
 */
class FetchImageTask
        extends LTask<ImageFileInfo> {

    /** Log tag. */
    private static final String TAG = "FetchImageTask";

    @NonNull
    private final String isbn;

    @NonNull
    private final FileManager fileManager;
    /** Image index we're handling. */
    private final int cIdx;
    @NonNull
    private final Size[] sizes;

    /**
     * Constructor.
     *
     * @param taskId       a task identifier, will be returned in the task listener.
     * @param validIsbn    to search for, <strong>must</strong> be valid.
     * @param cIdx         0..n image index
     * @param fileManager  for downloads
     * @param taskListener to send results to
     * @param sizes        try to get a picture in this order of size.
     *                     Stops at first one found.
     */
    @UiThread
    FetchImageTask(final int taskId,
                   @NonNull final String validIsbn,
                   @IntRange(from = 0, to = 1) final int cIdx,
                   @NonNull final FileManager fileManager,
                   @NonNull final TaskListener<ImageFileInfo> taskListener,
                   @NonNull final Size... sizes) {
        super(taskId, TAG, taskListener);
        this.cIdx = cIdx;
        this.sizes = sizes;

        // sanity check
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValidIsbn(validIsbn);
        }

        isbn = validIsbn;
        this.fileManager = fileManager;
    }

    public void start() {
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected ImageFileInfo doWork(@NonNull final Context context)
            throws StorageException {
        // We need to catch the exceptions we really want to thrown, but catch all others.
        try {
            return fileManager.search(context, this, isbn, cIdx, sizes);

        } catch (@NonNull final StorageException e) {
            throw e;

        } catch (@NonNull final Exception ignore) {
            // failing is ok, but we need to return the isbn + null fileSpec
            return new ImageFileInfo(isbn);
        }
    }
}
