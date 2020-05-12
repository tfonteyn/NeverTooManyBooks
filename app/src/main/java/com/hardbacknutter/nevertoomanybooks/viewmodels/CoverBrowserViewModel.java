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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.searches.ImageSize;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.tasks.AlternativeExecutor;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
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
    private FetchImageTask mSelectedImageTask;
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

    public int getImageIndex() {
        return mCIdx;
    }

    /**
     * Start a task to fetch a Gallery image.
     *
     * @param isbn to search for, <strong>must</strong> be valid.
     */
    public void fetchGalleryImage(@NonNull final String isbn) {
        final FetchImageTask task =
                new FetchImageTask(mTaskIdCounter.getAndIncrement(), isbn, mCIdx,
                                   mFileManager, mGalleryImageTaskListener,
                                   ImageSize.smallFirst);
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
        mSelectedImageTask =
                new FetchImageTask(mTaskIdCounter.getAndIncrement(), imageFileInfo.isbn, mCIdx,
                                   mFileManager, mSelectedImageTaskListener,
                                   ImageSize.largeFirst);
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

    @Nullable
    public String getSelectedFilePath() {
        return mSelectedFilePath;
    }

    public void setSelectedFilePath(@Nullable final String filePath) {
        mSelectedFilePath = filePath;
    }

    /** wrapper for {@link FileManager#getFileInfo}. */
    public ImageFileInfo getFileInfo(@NonNull final String isbn,
                                     @NonNull final ImageSize... sizes) {
        return mFileManager.getFileInfo(isbn, sizes);
    }

    /**
     * Handles downloading, checking and cleanup of files.
     */
    private static class FileManager {

        /** The minimum side (height/width) and image has to be to be considered valid. */
        static final int MIN_IMAGE_SIDE = 10;

        /**
         * Downloaded files.
         * key = isbn + '_' + size.
         */
        private final Map<String, ImageFileInfo> mFiles =
                Collections.synchronizedMap(new HashMap<>());

        /** Sites the user wants to search for cover images. */
        private final SiteList mSiteList;

        /**
         * Constructor.
         *
         * @param siteList site list
         */
        FileManager(@NonNull final SiteList siteList) {
            mSiteList = siteList;
        }

        /**
         * Check if a file is an image with an acceptable size.
         *
         * @param context  Current context
         * @param fileSpec to check
         *
         * @return {@code true} if file is acceptable.
         */
        private boolean isGood(@NonNull final Context context,
                               @Nullable final String fileSpec) {
            if (fileSpec == null || fileSpec.isEmpty()) {
                return false;
            }

            boolean ok = false;
            final File file = new File(fileSpec);
            if (file.exists() && file.length() != 0) {
                try {
                    // Just read the image files to get file size
                    final BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
                    // If too small, it's no good
                    ok = opt.outHeight >= MIN_IMAGE_SIDE && opt.outWidth >= MIN_IMAGE_SIDE;
                } catch (@NonNull final RuntimeException e) {
                    // Failed to decode; probably not an image
                    ok = false;
                    Logger.error(context, TAG, e, "Unable to decode file");
                }
            }

            // cleanup bad files.
            if (!ok) {
                FileUtils.delete(file);
            }
            return ok;
        }

        /**
         * Search for a file according to preference of ImageSize and Site..
         * <p>
         * First checks the cache. If we already have a good image, abort the search and use it.
         * <p>
         * We loop on ImageSize first, and then for each ImageSize we loop again on Site.<br>
         * The for() loop will break/return <strong>as soon as a cover file is found.</strong>
         * The first Site which has an image is accepted.
         * <p>
         *
         * @param context    Current context
         * @param isbn       to search for, <strong>must</strong> be valid.
         * @param cIdx       0..n image index
         * @param imageSizes a list of images sizes in order of preference
         *
         * @return a {@link ImageFileInfo} object with or without a valid fileSpec.
         */
        @NonNull
        @WorkerThread
        ImageFileInfo search(@NonNull final Context context,
                             @NonNull final String isbn,
                             @IntRange(from = 0) final int cIdx,
                             @NonNull final ImageSize... imageSizes) {

            // we will disable sites on the fly for the *current* search without modifying the list.
            @SearchSites.Id
            int currentSearchSites = mSiteList.getEnabledSites();

            // We need to use the size as the outer loop.
            // The idea is to check all sites for the same size first.
            // If none respond with that size, try the next size inline.
            for (ImageSize size : imageSizes) {
                final String key = isbn + '_' + size;
                ImageFileInfo imageFileInfo = mFiles.get(key);

                // Do we already have a file and is it good ?
                if ((imageFileInfo != null) && isGood(context, imageFileInfo.fileSpec)) {

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                        Log.d(TAG, "download|PRESENT"
                                   + "|imageFileInfo=" + imageFileInfo);
                    }
                    // abort search and use the file we already have
                    return imageFileInfo;

                } else {
                    // it was present but bad, remove it.
                    mFiles.remove(key);
                }

                for (Site site : mSiteList.getSites(true)) {
                    // Should we search this site ?
                    if ((currentSearchSites & site.id) != 0) {
                        SearchEngine engine = site.getSearchEngine();

                        if (engine instanceof SearchEngine.CoverByIsbn
                            && engine.isAvailable(context)) {
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                                Log.d(TAG, "download|TRYING"
                                           + "|isbn=" + isbn
                                           + "|size=" + size
                                           + "|engine=" + engine.getName(context)
                                     );
                            }

                            @Nullable
                            final String fileSpec = ((SearchEngine.CoverByIsbn) engine)
                                    .searchCoverImageByIsbn(context, isbn, cIdx, size);

                            if (isGood(context, fileSpec)) {
                                imageFileInfo = new ImageFileInfo(isbn, fileSpec, size);
                                mFiles.put(key, imageFileInfo);

                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                                    Log.d(TAG, "download|SUCCESS"
                                               + "|isbn=" + isbn
                                               + "|size=" + size
                                               + "|engine=" + engine.getName(context)
                                               + "|fileSpec=" + fileSpec);
                                }
                                // abort search, we got an image
                                return imageFileInfo;

                            } else if (BuildConfig.DEBUG
                                       && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                                Log.d(TAG, "download|NO GOOD"
                                           + "|isbn=" + isbn
                                           + "|size=" + size
                                           + "|engine=" + engine.getName(context)
                                     );
                            }

                            // if the site we just searched only supports one image,
                            // disable it for THIS search
                            if (!((SearchEngine.CoverByIsbn) engine).supportsMultipleSizes()) {
                                currentSearchSites &= ~site.id;
                            }
                        } else {
                            // if the site we just searched was not available,
                            // disable it for THIS search
                            currentSearchSites &= ~site.id;
                        }
                    }
                    // loop for next site
                }

                // give up for this size; remove (if any) bad file
                mFiles.remove(key);
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                Log.d(TAG, "download|FAILED "
                           + "|isbn=" + isbn);
            }

            // Failed to find any size on all sites.
            return new ImageFileInfo(isbn);
        }

        /**
         * Get the requested ImageFileInfo.
         * The fileSpec member will be {@code null} if there is no available file.
         *
         * @param isbn  to search
         * @param sizes required sizes in order to look for. First found is used.
         *
         * @return the ImageFileInfo
         */
        @NonNull
        ImageFileInfo getFileInfo(@NonNull final String isbn,
                                  @NonNull final ImageSize... sizes) {
            for (ImageSize size : sizes) {
                final ImageFileInfo imageFileInfo = mFiles.get(isbn + '_' + size);
                if (imageFileInfo != null
                    && imageFileInfo.fileSpec != null
                    && !imageFileInfo.fileSpec.isEmpty()) {
                    return imageFileInfo;
                }
            }
            // Failed to find.
            return new ImageFileInfo(isbn);
        }

        /**
         * Clean up all files.
         */
        void purge() {
            for (ImageFileInfo imageFileInfo : mFiles.values()) {
                if (imageFileInfo != null) {
                    final File file = imageFileInfo.getFile();
                    if (file != null) {
                        FileUtils.delete(file);
                    }
                }
            }
            mFiles.clear();
        }
    }

    /**
     * Fetch a image from the file manager.
     */
    private static class FetchImageTask
            extends TaskBase<ImageFileInfo> {

        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "FetchImageTask";

        @NonNull
        private final String mIsbn;

        @NonNull
        private final FileManager mFileManager;
        /** Image index we're handling. */
        private final int mCIdx;
        @NonNull
        private final ImageSize[] mSizes;

        /**
         * Constructor.
         *
         * @param isbn         to search for, <strong>must</strong> be valid.
         * @param cIdx         0..n image index
         * @param fileManager  for downloads
         * @param taskListener to send results to
         * @param sizes        try to get a picture in this order of size.
         *                     Stops at first one found.
         */
        @UiThread
        FetchImageTask(final int taskId,
                       @NonNull final String isbn,
                       @IntRange(from = 0) final int cIdx,
                       @NonNull final FileManager fileManager,
                       @NonNull final TaskListener<ImageFileInfo> taskListener,
                       @NonNull final ImageSize... sizes) {
            super(taskId, taskListener);
            mCIdx = cIdx;
            mSizes = sizes;

            // sanity check
            if (BuildConfig.DEBUG /* always */) {
                if (!ISBN.isValidIsbn(isbn)) {
                    throw new IllegalStateException(ErrorMsg.ISBN_MUST_BE_VALID);
                }
            }

            mIsbn = isbn;
            mFileManager = fileManager;
        }

        @Override
        @NonNull
        @WorkerThread
        protected ImageFileInfo doInBackground(@Nullable final Void... voids) {
            Thread.currentThread().setName(TAG + mIsbn);
            final Context context = App.getTaskContext();

            try {
                return mFileManager.search(context, mIsbn, mCIdx, mSizes);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            // we failed, but we still need to return the isbn.
            return new ImageFileInfo(mIsbn);
        }
    }
}
