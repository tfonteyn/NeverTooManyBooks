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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

public class CoverBrowserViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "CoverBrowserViewModel";
    static final String BKEY_FILE_INDEX = TAG + ":cIdx";

    /** Progressbar for the gallery. */
    private final MutableLiveData<Boolean> mShowGalleryProgress = new MutableLiveData<>();
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
    /** Executor for displaying preview images. */
    private final Executor mPreviewDisplayExecutor = ASyncExecutor.MAIN;
    /** Executor for fetching preview images. */
    private final Executor mPreviewNetworkExecutor = ASyncExecutor.MAIN;

    /**
     * Holder for all active tasks, so we can cancel them if needed.
     * key: isbn.
     */
    private final Map<String, FetchImageTask> mGalleryTasks = new HashMap<>();

    /** Editions. */
    private final SearchEditionsTask mSearchEditionsTask = new SearchEditionsTask();

    /** List of ISBN numbers for alternative editions. The base list for the gallery adapter. */
    @NonNull
    private final ArrayList<String> mEditions = new ArrayList<>();
    /** SelectedImage. */
    @Nullable
    private FetchImageTask mSelectedImageTask;
    /** FetchImageTask listener. */
    private final TaskListener<ImageFileInfo> mTaskListener = new TaskListener<>() {
        @Override
        public void onFinished(@NonNull final TaskResult<ImageFileInfo> message) {
            //TaskListener, don't check if (message.isNewEvent())
            final ImageFileInfo result = message.getResult();
            if (message.getTaskId() == R.id.TASK_ID_PREVIEW_IMAGE) {
                mSelectedImageTask = null;
                mSelectedImage.setValue(result);
            } else {
                removeTask(message.getTaskId());
                mGalleryImage.setValue(result);
            }
        }

        @Override
        public void onCancelled(@NonNull final TaskResult<ImageFileInfo> message) {
            //TaskListener, don't check if (message.isNewEvent())
            if (message.getTaskId() == R.id.TASK_ID_PREVIEW_IMAGE) {
                mSelectedImageTask = null;
                mSelectedImage.setValue(null);
            } else {
                removeTask(message.getTaskId());
                mGalleryImage.setValue(null);
            }
        }

        @Override
        public void onFailure(@NonNull final TaskResult<Exception> message) {
            //TaskListener, don't check if (message.isNewEvent())
            if (message.getTaskId() == R.id.TASK_ID_PREVIEW_IMAGE) {
                mSelectedImageTask = null;
                mSelectedImage.setValue(null);
            } else {
                removeTask(message.getTaskId());
                mGalleryImage.setValue(null);
            }
        }
    };
    /** Indicates cancel has been requested. */
    private boolean mIsCancelled;

    /**
     * The selected (i.e. displayed in the preview) file.
     * This is the absolute/resolved path for the file
     */
    @Nullable
    private String mSelectedFileAbsolutePath;
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
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (mBaseIsbn == null) {
            mBaseIsbn = SanityCheck.requireValue(args.getString(DBKey.KEY_ISBN),
                                                 "KEY_ISBN");
            mCIdx = args.getInt(BKEY_FILE_INDEX);

            // optional
            List<Site> sites = args.getParcelableArrayList(Site.Type.Covers.getBundleKey());
            if (sites == null) {
                sites = Site.Type.Covers.getSites();
            }
            mFileManager = new FileManager(sites);
        }
    }

    public boolean isCancelled() {
        return mIsCancelled;
    }

    /**
     * Cancel all active tasks; called before we're dismissed in any way.
     */
    void cancelAllTasks() {
        // prevent new tasks being started.
        mIsCancelled = true;

        if (mSelectedImageTask != null) {
            mSelectedImageTask.cancel();
        }

        synchronized (mGalleryTasks) {
            for (final FetchImageTask task : mGalleryTasks.values()) {
                task.cancel();
            }
            // not strictly needed, but future-proof
            mGalleryTasks.clear();
        }
    }

    /**
     * Remove the given task.
     *
     * @param taskId to remove
     */
    private void removeTask(final int taskId) {
        synchronized (mGalleryTasks) {
            String isbn = null;
            for (final Map.Entry<String, FetchImageTask> entry : mGalleryTasks.entrySet()) {
                if (entry.getValue().getTaskId() == taskId) {
                    isbn = entry.getKey();
                    break;
                }
            }
            if (isbn != null) {
                mGalleryTasks.remove(isbn);
            }

            if (mGalleryTasks.isEmpty()) {
                mShowGalleryProgress.setValue(false);
            }
        }
    }

    /**
     * Get the executor used for displaying the selected image.
     *
     * @return executor
     */
    @NonNull
    Executor getPreviewDisplayExecutor() {
        return mPreviewDisplayExecutor;
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

    @NonNull
    public ArrayList<String> getEditions() {
        return mEditions;
    }

    /**
     * Set the given list as the editions list.
     *
     * @param list to use
     */
    public void setEditions(@Nullable final Collection<String> list) {
        mEditions.clear();
        if (list != null && !list.isEmpty()) {
            mEditions.addAll(list);
        }
    }

    @Nullable
    String getSelectedFileAbsPath() {
        return mSelectedFileAbsolutePath;
    }

    void setSelectedFile(@Nullable final File file) {
        if (file != null) {
            mSelectedFileAbsolutePath = file.getAbsolutePath();
        } else {
            mSelectedFileAbsolutePath = null;
        }
    }

    /**
     * wrapper for {@link FileManager#getFileInfo}.
     *
     * @param isbn to search
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec,
     * or {@code null} if there is no cached file at all
     */
    @Nullable
    ImageFileInfo getFileInfo(@NonNull final String isbn) {
        return mFileManager.getFileInfo(isbn);
    }

    /**
     * Start a task to fetch a Gallery image.
     *
     * @param isbn to search for, <strong>must</strong> be valid.
     */
    void fetchGalleryImage(@NonNull final String isbn) {
        synchronized (mGalleryTasks) {
            if (!mGalleryTasks.containsKey(isbn)) {
                final FetchImageTask task =
                        new FetchImageTask(mTaskIdCounter.getAndIncrement(), isbn, mCIdx,
                                           mFileManager, mTaskListener,
                                           ImageFileInfo.Size.SMALL_FIRST);
                task.setExecutor(mGalleryNetworkExecutor);

                mGalleryTasks.put(isbn, task);
                task.start();

                final Boolean isShowing = mShowGalleryProgress.getValue();
                if (isShowing == null || !isShowing) {
                    mShowGalleryProgress.setValue(true);
                }
            }
        }
    }

    /**
     * Observable.
     *
     * @return boolean whether to show or hide the progress bar
     */
    @NonNull
    LiveData<Boolean> onShowGalleryProgress() {
        return mShowGalleryProgress;
    }

    /**
     * Observable.
     *
     * @return a gallery image file info; can be {@code null}.
     */
    @NonNull
    LiveData<ImageFileInfo> onGalleryImage() {
        return mGalleryImage;
    }

    /**
     * Start a task to get the preview image; i.e. the full size image.
     *
     * @param imageFileInfo of the selected image
     */
    void fetchSelectedImage(@NonNull final ImageFileInfo imageFileInfo) {
        if (mSelectedImageTask != null) {
            mSelectedImageTask.cancel();
        }
        mSelectedImageTask = new FetchImageTask(R.id.TASK_ID_PREVIEW_IMAGE,
                                                imageFileInfo.getIsbn(), mCIdx,
                                                mFileManager, mTaskListener,
                                                ImageFileInfo.Size.LARGE_FIRST);

        mSelectedImageTask.setExecutor(mPreviewNetworkExecutor);
        mSelectedImageTask.start();
    }

    /**
     * Observable.
     *
     * @return the preview image file info; can be {@code null}.
     */
    @NonNull
    LiveData<ImageFileInfo> onSelectedImage() {
        return mSelectedImage;
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Collection<String>>>> onSearchEditionsTaskFinished() {
        return mSearchEditionsTask.onFinished();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onSearchEditionsTaskFailure() {
        return mSearchEditionsTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Collection<String>>>> onSearchEditionsTaskCancelled() {
        return mSearchEditionsTask.onCancelled();
    }

    void searchEditions() {
        mSearchEditionsTask.search(mBaseIsbn);
    }

    boolean isSearchEditionsTaskRunning() {
        return mSearchEditionsTask.isRunning();
    }
}
