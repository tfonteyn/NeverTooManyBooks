/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.covers;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * Fetch an image from the {@link FileManager}.
 */
class FetchImageTask
        extends LTask<ImageFileInfo> {

    /** Log tag. */
    private static final String TAG = "FetchImageTask";

    @NonNull
    private final String mIsbn;

    @NonNull
    private final FileManager mFileManager;
    /** Image index we're handling. */
    private final int mCIdx;
    @NonNull
    private final ImageFileInfo.Size[] mSizes;

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
                   @IntRange(from = 0) final int cIdx,
                   @NonNull final FileManager fileManager,
                   @NonNull final TaskListener<ImageFileInfo> taskListener,
                   @NonNull final ImageFileInfo.Size... sizes) {
        super(taskId, taskListener);
        mCIdx = cIdx;
        mSizes = sizes;

        // sanity check
        if (BuildConfig.DEBUG /* always */) {
            if (!ISBN.isValidIsbn(validIsbn)) {
                throw new IllegalStateException(ErrorMsg.INVALID_ISBN);
            }
        }

        mIsbn = validIsbn;
        mFileManager = fileManager;
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImageFileInfo doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG + mIsbn);

        try {
            return mFileManager.search(this, mIsbn, mCIdx, mSizes);

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
            // tad annoying... java.io.InterruptedIOException: thread interrupted
            // can be thrown, but for some reason javac does not think so.
        }
        // we failed, but we still need to return the isbn + null fileSpec
        return new ImageFileInfo(mIsbn);
    }
}
