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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public class CoverBrowserViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "CoverBrowserViewModel";
    static final String BKEY_FILE_INDEX = TAG + ":cIdx";

    /** GalleryImage. */
    private final MutableLiveData<ImageFileInfo> mGalleryImage = new MutableLiveData<>();
    /** SelectedImage. */
    private final MutableLiveData<ImageFileInfo> mSelectedImage = new MutableLiveData<>();

    /** Unique identifier generator for all tasks. */
    private final AtomicInteger mTaskIdCounter = new AtomicInteger();

    /** Executor for displaying gallery images. */
    private final Executor mGalleryDisplayExecutor = ASyncExecutor.create("gallery/d");
    /** Executor for fetching gallery images. */
    private final Executor mGalleryNetworkExecutor = ASyncExecutor.create("gallery/n");

    /** Holder for all active tasks, so we can cancel them if needed. */
    @SuppressWarnings("rawtypes")
    private final SparseArray<AsyncTask> mAllTasks = new SparseArray<>();
    /** SelectedImage. */
    @Nullable
    private FetchImageTask mSelectedImageTask;
    /** FetchImageTask listener. */
    private final TaskListener<ImageFileInfo> mTaskListener = new TaskListener<ImageFileInfo>() {
        @Override
        public void onFinished(@NonNull final FinishedMessage<ImageFileInfo> message) {
            removeTask(message.taskId);
            if (message.taskId == R.id.TASK_ID_PREVIEW_IMAGE) {
                mSelectedImageTask = null;
                mSelectedImage.setValue(message.result);
            } else {
                mGalleryImage.setValue(message.result);
            }
        }

        @Override
        public void onCancelled(@NonNull final FinishedMessage<ImageFileInfo> message) {
            removeTask(message.taskId);
        }

        @Override
        public void onFailure(@NonNull final FinishedMessage<Exception> message) {
            removeTask(message.taskId);
        }
    };
    /**
     * The selected (i.e. displayed in the preview) file.
     * This is the absolute/resolved path for the file
     */
    @Nullable
    private String mSelectedFilePath;
    /** Handles downloading, checking and cleanup of files. */
    private FileManager mFileManager;
    /** ISBN of book to fetch other editions of. */
    private String mBaseIsbn;
    /** Index of the image we're handling. */
    private int mCIdx;

    @Override
    protected void onCleared() {
        cancelAllTasks();

        if (mFileManager != null) {
            mFileManager.purge();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mBaseIsbn == null) {
            mBaseIsbn = args.getString(DBDefinitions.KEY_ISBN);
            mCIdx = args.getInt(BKEY_FILE_INDEX);
            Objects.requireNonNull(mBaseIsbn, ErrorMsg.NULL_ISBN);
            // optional
            SiteList siteList = args.getParcelable(SiteList.Type.Covers.getBundleKey());
            if (siteList == null) {
                final Locale locale = LocaleUtils.getUserLocale(context);
                siteList = SiteList.getList(context, locale, SiteList.Type.Covers);
            }
            mFileManager = new FileManager(siteList);
        }
    }

    /**
     * Cancel all active tasks.
     */
    void cancelAllTasks() {
        synchronized (mAllTasks) {
            for (int i = 0; i < mAllTasks.size(); i++) {
                //noinspection rawtypes
                final AsyncTask task = mAllTasks.valueAt(i);
                task.cancel(true);
            }
            mAllTasks.clear();
        }
    }

    /**
     * Remove the given task.
     *
     * @param taskId to remove
     */
    private void removeTask(final int taskId) {
        synchronized (mAllTasks) {
            mAllTasks.remove(taskId);
        }
    }

    /**
     * Get the executor used for displaying gallery images.
     *
     * @return executor
     */
    @NonNull
    Executor getGalleryDisplayExecutor() {
        return mGalleryDisplayExecutor;
    }

    public String getBaseIsbn() {
        return mBaseIsbn;
    }

    int getImageIndex() {
        return mCIdx;
    }

    @Nullable
    String getSelectedFilePath() {
        return mSelectedFilePath;
    }

    void setSelectedFilePath(@Nullable final String filePath) {
        mSelectedFilePath = filePath;
    }

    /**
     * wrapper for {@link FileManager#getFileInfo}.
     *
     * @param isbn  to search
     * @param sizes required sizes in order to look for. First found is used.
     *
     * @return the ImageFileInfo
     */
    ImageFileInfo getFileInfo(@NonNull final String isbn,
                              @NonNull final ImageFileInfo.Size... sizes) {
        return mFileManager.getFileInfo(isbn, sizes);
    }

    /**
     * Start a task to fetch a Gallery image.
     *
     * @param isbn to search for, <strong>must</strong> be valid.
     */
    void fetchGalleryImage(@NonNull final String isbn) {
        final FetchImageTask task =
                new FetchImageTask(mTaskIdCounter.getAndIncrement(), isbn, mCIdx,
                                   mFileManager, mTaskListener,
                                   ImageFileInfo.Size.SMALL_FIRST);
        synchronized (mAllTasks) {
            mAllTasks.put(task.getTaskId(), task);
        }
        task.executeOnExecutor(mGalleryNetworkExecutor);
    }

    /**
     * Observable.
     *
     * @return a gallery image file info; can be {@code null}.
     */
    @NonNull
    MutableLiveData<ImageFileInfo> onGalleryImage() {
        return mGalleryImage;
    }

    /**
     * Start a task to get the preview image; i.e. the full size image.
     *
     * @param imageFileInfo of the selected image
     */
    void fetchSelectedImage(@NonNull final ImageFileInfo imageFileInfo) {
        if (mSelectedImageTask != null) {
            mSelectedImageTask.cancel(true);
            removeTask(mSelectedImageTask.getTaskId());
        }
        mSelectedImageTask = new FetchImageTask(R.id.TASK_ID_PREVIEW_IMAGE,
                                                imageFileInfo.isbn, mCIdx,
                                                mFileManager, mTaskListener,
                                                ImageFileInfo.Size.LARGE_FIRST);
        synchronized (mAllTasks) {
            mAllTasks.put(mSelectedImageTask.getTaskId(), mSelectedImageTask);
        }
        // use the default executor which is free right now
        mSelectedImageTask.execute();
    }

    /**
     * Observable.
     *
     * @return the preview image file info; can be {@code null}.
     */
    @NonNull
    MutableLiveData<ImageFileInfo> onSelectedImage() {
        return mSelectedImage;
    }
}
