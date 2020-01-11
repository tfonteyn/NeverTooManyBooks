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
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.tasks.AlternativeExecutor;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

public class CoverBrowserViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "CoverBrowserViewModel";
    public static final String BKEY_FILE_INDEX = TAG + ":cIdx";

    /** Holder for all active tasks, so we can cancel them if needed. */
    private final SparseArray<AsyncTask> mAllTasks = new SparseArray<>();

    /** List of all alternative editions/isbn for the given ISBN. */
    private final MutableLiveData<ArrayList<String>> mEditions = new MutableLiveData<>();

    private final MutableLiveData<FileInfo> mGalleryImage = new MutableLiveData<>();

    private final MutableLiveData<FileInfo> mSwitcherImage = new MutableLiveData<>();
    private final TaskListener<ArrayList<String>> mEditionTaskListener = message -> {
        synchronized (mAllTasks) {
            mAllTasks.remove(message.taskId);
        }
        mEditions.setValue(message.result);
    };

    private final TaskListener<FileInfo> mImageTaskListener = message -> {
        synchronized (mAllTasks) {
            mAllTasks.remove(message.taskId);
        }
        switch (message.taskId) {
            case R.id.TASK_ID_SWITCHER_IMAGE:
                mSwitcherImage.setValue(message.result);
                break;

            case R.id.TASK_ID_GALLERY_IMAGE:
                mGalleryImage.setValue(message.result);
                break;

            default:
                throw new UnexpectedValueException(message.taskId);
        }
    };

    /** ISBN of book to fetch other editions of. */
    private String mBaseIsbn;
    /** Index of the image we're handling. */
    private int mCIdx;
    /** Handles downloading, checking and cleanup of files. */
    private CoverBrowserViewModel.FileManager mFileManager;

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
            Objects.requireNonNull(mBaseIsbn, "ISBN must be passed in args");
            // optional
            SiteList siteList = args.getParcelable(SiteList.Type.Covers.getBundleKey());
            if (siteList == null) {
                siteList = SiteList.getList(context, SiteList.Type.Covers);
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
        }
    }

    /**
     * Start a search for alternative editions of the book (using the isbn).
     */
    public void fetchEditions() {
        SearchEditionsTask task = new SearchEditionsTask(mBaseIsbn, mEditionTaskListener);
        synchronized (mAllTasks) {
            mAllTasks.put(task.getTaskId(), task);
        }
        task.execute();
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<ArrayList<String>> getEditions() {
        return mEditions;
    }

    public int getImageIndex() {
        return mCIdx;
    }

    @NonNull
    public FileManager getFileManager() {
        return mFileManager;
    }

    /**
     * Start a task to fetch a Gallery image.
     *
     * @param isbn to search for, <strong>must</strong> be valid.
     */
    public void fetchGalleryImage(@NonNull final String isbn) {
        GetGalleryImageTask task = new GetGalleryImageTask(isbn, mCIdx,
                                                           mFileManager, mImageTaskListener);
        synchronized (mAllTasks) {
            mAllTasks.put(task.getTaskId(), task);
        }
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<FileInfo> getGalleryImage() {
        return mGalleryImage;
    }

    /**
     * Start a task to get the ImageSwitcher image; i.e. the full size image.
     *
     * @param fileInfo of the selected image
     */
    public void fetchSelectedImage(@NonNull final CoverBrowserViewModel.FileInfo fileInfo) {
        GetSwitcherImageTask task = new GetSwitcherImageTask(fileInfo, mCIdx, mFileManager,
                                                             mImageTaskListener);
        synchronized (mAllTasks) {
            mAllTasks.put(task.getTaskId(), task);
        }
        // use the alternative executor, so we get a result back without
        // waiting on the gallery tasks.
        task.executeOnExecutor(AlternativeExecutor.THREAD_POOL_EXECUTOR);
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<FileInfo> getSelectedImage() {
        return mSwitcherImage;
    }

    /**
     * Value class to return info about a file.
     */
    public static class FileInfo
            implements Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<FileInfo> CREATOR = new Creator<FileInfo>() {
            @Override
            public FileInfo createFromParcel(@NonNull final Parcel in) {
                return new FileInfo(in);
            }

            @Override
            public FileInfo[] newArray(final int size) {
                return new FileInfo[size];
            }
        };
        @NonNull
        public final String isbn;
        @Nullable
        public SearchEngine.CoverByIsbn.ImageSize size;
        @Nullable
        public String fileSpec;

        /**
         * Constructor.
         */
        FileInfo(@NonNull final String isbn,
                 @NonNull final SearchEngine.CoverByIsbn.ImageSize size,
                 @NonNull final String fileSpec) {
            this.isbn = isbn;
            this.size = size;
            this.fileSpec = fileSpec;
        }

        /**
         * Failure constructor.
         * <p>
         * Some functions need to return a @NonNull FileInfo with a valid isbn while the fileSpec
         * can be null. Use this constructor to do that.
         */
        FileInfo(@NonNull final String isbn) {
            this.isbn = isbn;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private FileInfo(@NonNull final Parcel in) {
            //noinspection ConstantConditions
            isbn = in.readString();
            fileSpec = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(isbn);
            dest.writeString(fileSpec);
        }

        @NonNull
        @Override
        public String toString() {
            return "FileInfo{"
                   + "isbn='" + isbn + '\''
                   + ", size=" + size
                   + ", fileSpec='" + fileSpec + '\''
                   + '}';
        }
    }

    /**
     * Handles downloading, checking and cleanup of files.
     */
    public static class FileManager {

        /** The minimum side (height/width) and image has to be to be considered valid. */
        static final int MIN_IMAGE_SIDE = 10;

        /**
         * Downloaded files.
         * key = isbn + '_' + size.
         */
        private final Map<String, FileInfo> mFiles = Collections.synchronizedMap(new HashMap<>());

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
         * @param fileSpec to check
         *
         * @return {@code true} if file is acceptable.
         */
        private boolean isGood(@Nullable final String fileSpec) {
            if (fileSpec == null || fileSpec.isEmpty()) {
                return false;
            }

            boolean ok = false;
            File file = new File(fileSpec);
            if (file.exists() && file.length() != 0) {
                try {
                    // Just read the image files to get file size
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
                    // If too small, it's no good
                    ok = opt.outHeight >= MIN_IMAGE_SIDE && opt.outWidth >= MIN_IMAGE_SIDE;
                } catch (@NonNull final RuntimeException e) {
                    // Failed to decode; probably not an image
                    ok = false;
                    Logger.error(TAG, e, "Unable to decode file");
                }
            }

            // cleanup bad files.
            if (!ok) {
                StorageUtils.deleteFile(file);
            }
            return ok;
        }

        /**
         * Download a file according to preference of ImageSize and Site..
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
         * @return a {@link FileInfo} object with or without a valid fileSpec.
         */
        @NonNull
        @WorkerThread
        FileInfo download(@NonNull final Context context,
                          @NonNull final String isbn,
                          final int cIdx,
                          @NonNull final SearchEngine.CoverByIsbn.ImageSize... imageSizes) {

            // we will disable sites on the fly for the *current* search without modifying the list.
            @SearchSites.Id
            int currentSearchSites = mSiteList.getEnabledSites();

            // we need to use the size as the outer loop (and not inside of getCoverImage itself).
            // the idea is to check all sites for the same size first.
            // if none respond with that size, try the next size inline.
            // The other way around we might get a site/small-size instead of otherSite/better-size.
            for (SearchEngine.CoverByIsbn.ImageSize size : imageSizes) {
                String key = isbn + '_' + size;
                FileInfo fileInfo = mFiles.get(key);

                // Do we already have a file and is it good ?
                if ((fileInfo != null) && isGood(fileInfo.fileSpec)) {

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                        Log.d(TAG, "download|FILESYSTEM|fileInfo=" + fileInfo);
                    }
                    // use it
                    return fileInfo;

                } else {
                    // it was present but bad, remove it.
                    mFiles.remove(key);
                }

                for (Site site : mSiteList.getSites(true)) {
                    // Should we search this site ?
                    if ((currentSearchSites & site.id) != 0) {
                        SearchEngine engine = site.getSearchEngine();

                        boolean isAvailable = engine instanceof SearchEngine.CoverByIsbn
                                              && engine.isAvailable(context);
                        if (isAvailable) {
                            fileInfo = download(context, (SearchEngine.CoverByIsbn) engine,
                                                isbn, cIdx, size);
                            if (fileInfo != null) {
                                return fileInfo;
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
                }

                // give up
                mFiles.remove(key);
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(isbn);
        }

        /**
         * Try to get an image from the specified engine.
         *
         * @param appContext   Application context
         * @param searchEngine to use
         * @param isbn         to search for, <strong>must</strong> be valid.
         * @param cIdx         0..n image index
         * @param size         to get
         *
         * @return a FileInfo object with a valid fileSpec, or {@code null} if not found.
         */
        @Nullable
        private FileInfo download(@NonNull final Context appContext,
                                  @NonNull final SearchEngine.CoverByIsbn searchEngine,
                                  @NonNull final String isbn,
                                  final int cIdx,
                                  @NonNull final SearchEngine.CoverByIsbn.ImageSize size) {
            @Nullable
            String fileSpec = searchEngine.getCoverImage(appContext, isbn, cIdx, size);
            if (isGood(fileSpec)) {
                String key = isbn + '_' + size;
                FileInfo fileInfo = new FileInfo(isbn, size, fileSpec);
                mFiles.put(key, fileInfo);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                    Log.d(TAG, "download|FOUND|fileInfo=" + fileInfo);
                }
                return fileInfo;

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                    Log.d(TAG, "download|MISSING"
                               + "|engine=" + appContext.getString(searchEngine.getNameResId())
                               + "|isbn=" + isbn
                               + "|size=" + size);
                }
            }
            return null;
        }

        /**
         * Get the requested FileInfo, if available, otherwise return {@code null}.
         *
         * @param isbn  to search
         * @param sizes required sizes in order to look for. First found is used.
         *
         * @return the FileInfo
         */
        @NonNull
        public FileInfo getFile(@NonNull final String isbn,
                                @NonNull final SearchEngine.CoverByIsbn.ImageSize... sizes) {
            for (SearchEngine.CoverByIsbn.ImageSize size : sizes) {
                FileInfo fileInfo = mFiles.get(isbn + '_' + size);
                if (fileInfo != null && fileInfo.fileSpec != null && !fileInfo.fileSpec.isEmpty()) {
                    return fileInfo;
                }
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(isbn);
        }

        /**
         * Clean up all files.
         */
        void purge() {
            for (FileInfo fileInfo : mFiles.values()) {
                if (fileInfo != null
                    && fileInfo.fileSpec != null
                    && !fileInfo.fileSpec.isEmpty()) {
                    StorageUtils.deleteFile(new File(fileInfo.fileSpec));
                }
            }
            mFiles.clear();
        }
    }

    /**
     * Fetch a thumbnail and stick it into the gallery.
     */
    static class GetGalleryImageTask
            extends TaskBase<Void, FileInfo> {

        @NonNull
        private final String mIsbn;
        @NonNull
        private final FileManager mFileManager;
        /** Image index we're handling. */
        private final int mCIdx;

        /**
         * Constructor.
         *
         * @param isbnStr      to search for, <strong>must</strong> be valid.
         * @param cIdx         0..n image index
         * @param fileManager  for downloads
         * @param taskListener to send results to
         */
        @UiThread
        GetGalleryImageTask(@NonNull final String isbnStr,
                            final int cIdx,
                            @NonNull final FileManager fileManager,
                            @NonNull final TaskListener<FileInfo> taskListener) {
            super(R.id.TASK_ID_GALLERY_IMAGE, taskListener);
            mCIdx = cIdx;

            // sanity check
            if (BuildConfig.DEBUG /* always */) {
                if (!ISBN.isValidIsbn(isbnStr)) {
                    throw new IllegalStateException("isbn must be valid");
                }
            }

            mIsbn = isbnStr;
            mFileManager = fileManager;
        }

        @Override
        @NonNull
        @WorkerThread
        protected FileInfo doInBackground(final Void... params) {
            Thread.currentThread().setName("GetGalleryImageTask " + mIsbn);
            Context context = App.getAppContext();
            try {
                return mFileManager.download(context, mIsbn, mCIdx,
                                             // try to get a picture in this order of size.
                                             // Stops at first one found.
                                             SearchEngine.CoverByIsbn.ImageSize.Small,
                                             SearchEngine.CoverByIsbn.ImageSize.Medium,
                                             SearchEngine.CoverByIsbn.ImageSize.Large);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(mIsbn);
        }
    }

    /**
     * Fetch a full-size image and stick it into the ImageSwitcher.
     */
    static class GetSwitcherImageTask
            extends TaskBase<Void, FileInfo> {

        @NonNull
        private final FileInfo mFileInfo;
        @NonNull
        private final FileManager mFileManager;
        /** Image index we're handling. */
        private final int mCIdx;

        /**
         * Constructor.
         *
         * @param fileInfo     book to search
         * @param cIdx         0..n image index
         * @param fileManager  for downloads
         * @param taskListener to send results to
         */
        @UiThread
        GetSwitcherImageTask(@NonNull final FileInfo fileInfo,
                             final int cIdx,
                             @NonNull final FileManager fileManager,
                             @NonNull final TaskListener<FileInfo> taskListener) {
            super(R.id.TASK_ID_SWITCHER_IMAGE, taskListener);
            mCIdx = cIdx;

            // sanity check
            if (BuildConfig.DEBUG /* always */) {
                if (!ISBN.isValidIsbn(fileInfo.isbn)) {
                    throw new IllegalStateException("isbn must be valid");
                }
            }

            mFileInfo = fileInfo;
            mFileManager = fileManager;
        }

        @Override
        @NonNull
        @WorkerThread
        protected FileInfo doInBackground(final Void... params) {
            Thread.currentThread().setName("GetSwitcherImageTask " + mFileInfo.isbn);
            Context context = App.getAppContext();
            try {
                return mFileManager.download(context, mFileInfo.isbn, mCIdx,
                                             // try to get a picture in this order of size.
                                             // Stops at first one found.
                                             SearchEngine.CoverByIsbn.ImageSize.Large,
                                             SearchEngine.CoverByIsbn.ImageSize.Medium,
                                             SearchEngine.CoverByIsbn.ImageSize.Small);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(mFileInfo.isbn);
        }
    }
}
