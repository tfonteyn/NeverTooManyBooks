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

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.tasks.AlternativeExecutor;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public class CoverBrowserViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "CoverBrowserViewModel";
    public static final String BKEY_FILE_INDEX = TAG + ":cIdx";

    private final AtomicInteger mTaskIdCounter = new AtomicInteger();

    /** Holder for all active tasks, so we can cancel them if needed. */
    private final SparseArray<AsyncTask> mAllTasks = new SparseArray<>();

    /** Executor for getting the preview image, and for displaying images. */
    private final Executor mPriorityExecutor = AlternativeExecutor.create("preview", 32);

    /** List of all alternative editions/isbn for the given ISBN. */
    private final MutableLiveData<Collection<String>> mEditions = new MutableLiveData<>();

    /** GalleryImage. */
    private final MutableLiveData<ImageFileInfo> mGalleryImage = new MutableLiveData<>();
    /** GalleryImage. */
    private final TaskListener<ImageFileInfo> mGalleryImageTaskListener = message -> {
        synchronized (mAllTasks) {
            mAllTasks.remove(message.taskId);
        }
        mGalleryImage.setValue(message.result);
    };
    /** SelectedImage. */
    private final MutableLiveData<ImageFileInfo> mSelectedImage = new MutableLiveData<>();
    /** ISBN of book to fetch other editions of. */
    private String mBaseIsbn;
    /** Index of the image we're handling. */
    private int mCIdx;
    /**
     * The selected (i.e. displayed in the preview) file.
     * This is the absolute/resolved path for the file
     */
    @Nullable
    private String mSelectedFilePath;
    /** Handles downloading, checking and cleanup of files. */
    private FileManager mFileManager;

    /** Editions. */
    @Nullable
    private SearchEditionsTask mEditionsTask;
    /** Editions. */
    private final TaskListener<Collection<String>> mEditionTaskListener = message -> {
        synchronized (mAllTasks) {
            mAllTasks.remove(message.taskId);
            mEditionsTask = null;
        }
        mEditions.setValue(message.result);
    };
    /** SelectedImage. */
    @Nullable
    private FileManager.FetchImageTask mSelectedImageTask;
    /** SelectedImage. */
    private final TaskListener<ImageFileInfo> mSelectedImageTaskListener = message -> {
        synchronized (mAllTasks) {
            mAllTasks.remove(message.taskId);
            mSelectedImageTask = null;
        }
        mSelectedImage.setValue(message.result);
    };

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
            Objects.requireNonNull(mBaseIsbn, ErrorMsg.ARGS_MISSING_ISBN);
            // optional
            SiteList siteList = args.getParcelable(SiteList.Type.Covers.getBundleKey());
            if (siteList == null) {
                final Locale locale = LocaleUtils.getUserLocale(context);
                siteList = SiteList.getList(context, locale, SiteList.Type.Covers);
            }
            mFileManager = new FileManager(siteList);
        }
    }

    @Override
    protected void onCleared() {
        cancelAllTasks();

        if (mFileManager != null) {
            mFileManager.purge();
        }
    }

    /**
     * Cancel all active tasks.
     */
    private void cancelAllTasks() {
        synchronized (mAllTasks) {
            for (int i = 0; i < mAllTasks.size(); i++) {
                AsyncTask task = mAllTasks.valueAt(i);
                task.cancel(true);
            }
            mAllTasks.clear();
        }
    }

    @NonNull
    public Executor getPriorityExecutor() {
        return mPriorityExecutor;
    }

    public int getImageIndex() {
        return mCIdx;
    }

    @Nullable
    public String getSelectedFilePath() {
        return mSelectedFilePath;
    }

    public void setSelectedFilePath(@Nullable final String filePath) {
        mSelectedFilePath = filePath;
    }

    /** wrapper for {@link FileManager#getFileInfo}. */
    public ImageFileInfo getFileInfo(@NonNull final String isbn,
                                     @NonNull final ImageFileInfo.Size... sizes) {
        return mFileManager.getFileInfo(isbn, sizes);
    }


    /**
     * Start a search for alternative editions of the book (using the isbn).
     */
    public void fetchEditions() {
        if (mEditionsTask != null) {
            mEditionsTask.cancel(true);
            synchronized (mAllTasks) {
                mAllTasks.remove(mEditionsTask.getTaskId());
            }
        }
        mEditionsTask = new SearchEditionsTask(mTaskIdCounter.getAndIncrement(),
                                               mBaseIsbn, mEditionTaskListener);
        synchronized (mAllTasks) {
            mAllTasks.put(mEditionsTask.getTaskId(), mEditionsTask);
        }
        mEditionsTask.execute();
    }

    /**
     * Observable.
     *
     * @return list of ISBN numbers for alternative editions.
     */
    @NonNull
    public MutableLiveData<Collection<String>> onEditionsLoaded() {
        return mEditions;
    }


    /**
     * Start a task to fetch a Gallery image.
     *
     * @param isbn to search for, <strong>must</strong> be valid.
     */
    public void fetchGalleryImage(@NonNull final String isbn) {
        final FileManager.FetchImageTask task =
                new FileManager.FetchImageTask(mTaskIdCounter.getAndIncrement(), isbn, mCIdx,
                                               mFileManager, mGalleryImageTaskListener,
                                               ImageFileInfo.Size.smallFirst);
        synchronized (mAllTasks) {
            mAllTasks.put(task.getTaskId(), task);
        }
        // default parallel executor.
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<ImageFileInfo> onGalleryImage() {
        return mGalleryImage;
    }


    /**
     * Start a task to get the preview image; i.e. the full size image.
     *
     * @param imageFileInfo of the selected image
     */
    public void fetchSelectedImage(@NonNull final ImageFileInfo imageFileInfo) {
        if (mSelectedImageTask != null) {
            mSelectedImageTask.cancel(true);
            synchronized (mAllTasks) {
                mAllTasks.remove(mSelectedImageTask.getTaskId());
            }
        }
        mSelectedImageTask = new FileManager.FetchImageTask(mTaskIdCounter.getAndIncrement(),
                                                            imageFileInfo.isbn,
                                                            mCIdx,
                                                            mFileManager,
                                                            mSelectedImageTaskListener,
                                                            ImageFileInfo.Size.largeFirst);
        synchronized (mAllTasks) {
            mAllTasks.put(mSelectedImageTask.getTaskId(), mSelectedImageTask);
        }
        mSelectedImageTask.executeOnExecutor(mPriorityExecutor);
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<ImageFileInfo> onSelectedImage() {
        return mSelectedImage;
    }
}
