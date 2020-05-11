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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
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
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

public class CoverBrowserViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "CoverBrowserViewModel";
    public static final String BKEY_FILE_INDEX = TAG + ":cIdx";

    /** Holder for all active tasks, so we can cancel them if needed. */
    private final SparseArray<AsyncTask> mAllTasks = new SparseArray<>();

    /** List of all alternative editions/isbn for the given ISBN. */
    private final MutableLiveData<Collection<String>> mEditions = new MutableLiveData<>();

    private final MutableLiveData<FileInfo> mGalleryImage = new MutableLiveData<>();

    private final MutableLiveData<FileInfo> mSwitcherImage = new MutableLiveData<>();
    private final TaskListener<Collection<String>> mEditionTaskListener = message -> {
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

    /**
     * The selected (i.e. displayed in the switcher) file.
     * This is the absolute/resolved path for the file
     */
    @Nullable
    private String mSelectedFilePath;

    /** Handles downloading, checking and cleanup of files. */
    private FileManager mFileManager;

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
        }
    }

    /**
     * Start a search for alternative editions of the book (using the isbn).
     */
    public void fetchEditions() {
        final SearchEditionsTask task = new SearchEditionsTask(mBaseIsbn, mEditionTaskListener);
        synchronized (mAllTasks) {
            mAllTasks.put(task.getTaskId(), task);
        }
        task.execute();
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
        final GetGalleryImageTask task = new GetGalleryImageTask(isbn, mCIdx, mFileManager,
                                                                 mImageTaskListener);
        synchronized (mAllTasks) {
            mAllTasks.put(task.getTaskId(), task);
        }
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<FileInfo> onGalleryImage() {
        return mGalleryImage;
    }

    /**
     * Start a task to get the ImageSwitcher image; i.e. the full size image.
     *
     * @param fileInfo of the selected image
     */
    public void fetchSelectedImage(@NonNull final FileInfo fileInfo) {
        final GetSwitcherImageTask task = new GetSwitcherImageTask(fileInfo, mCIdx, mFileManager,
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
    public MutableLiveData<FileInfo> onGalleryImageSelected() {
        return mSwitcherImage;
    }

    @Nullable
    public String getSelectedFilePath() {
        return mSelectedFilePath;
    }

    public void setSelectedFilePath(@NonNull final String filePath) {
        mSelectedFilePath = filePath;
    }

    /** wrapper for {@link FileManager#getFileInfo}. */
    public FileInfo getFileInfo(@NonNull final String isbn,
                                @NonNull final SearchEngine.CoverByIsbn.ImageSize... sizes) {
        return mFileManager.getFileInfo(isbn, sizes);
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
        FileInfo(@NonNull final String isbn) {
            this.isbn = isbn;
        }

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
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private FileInfo(@NonNull final Parcel in) {
            //noinspection ConstantConditions
            isbn = in.readString();
            fileSpec = in.readString();
        }

        @Nullable
        public File getFile() {
            if (fileSpec != null && !fileSpec.isEmpty()) {
                return new File(fileSpec);
            }
            return null;
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
                   + "isbn=`" + isbn + '`'
                   + ", size=" + size
                   + ", fileSpec=`" + fileSpec + '`'
                   + '}';
        }
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
                          @IntRange(from = 0) final int cIdx,
                          @NonNull final SearchEngine.CoverByIsbn.ImageSize... imageSizes) {

            // we will disable sites on the fly for the *current* search without modifying the list.
            @SearchSites.Id
            int currentSearchSites = mSiteList.getEnabledSites();

            // We need to use the size as the outer loop.
            // The idea is to check all sites for the same size first.
            // If none respond with that size, try the next size inline.
            for (SearchEngine.CoverByIsbn.ImageSize size : imageSizes) {
                final String key = isbn + '_' + size;
                FileInfo fileInfo = mFiles.get(key);

                // Do we already have a file and is it good ?
                if ((fileInfo != null) && isGood(context, fileInfo.fileSpec)) {

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                        Log.d(TAG, "download|PRESENT"
                                   + "|fileInfo=" + fileInfo);
                    }
                    // abort search and use the file we already have
                    return fileInfo;

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
                                fileInfo = new FileInfo(isbn, size, fileSpec);
                                mFiles.put(key, fileInfo);

                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                                    Log.d(TAG, "download|SUCCESS"
                                               + "|isbn=" + isbn
                                               + "|size=" + size
                                               + "|engine=" + engine.getName(context)
                                               + "|fileSpec=" + fileSpec);
                                }
                                // abort search, we got an image
                                return fileInfo;

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
            return new FileInfo(isbn);
        }

        /**
         * Get the requested FileInfo.
         * The fileSpec member will be {@code null} if there is no available file.
         *
         * @param isbn  to search
         * @param sizes required sizes in order to look for. First found is used.
         *
         * @return the FileInfo
         */
        @NonNull
        FileInfo getFileInfo(@NonNull final String isbn,
                             @NonNull final SearchEngine.CoverByIsbn.ImageSize... sizes) {
            for (SearchEngine.CoverByIsbn.ImageSize size : sizes) {
                final FileInfo fileInfo = mFiles.get(isbn + '_' + size);
                if (fileInfo != null && fileInfo.fileSpec != null && !fileInfo.fileSpec.isEmpty()) {
                    return fileInfo;
                }
            }
            // Failed to find.
            return new FileInfo(isbn);
        }

        /**
         * Clean up all files.
         */
        void purge() {
            for (FileInfo fileInfo : mFiles.values()) {
                if (fileInfo != null) {
                    final File file = fileInfo.getFile();
                    if (file != null) {
                        FileUtils.delete(file);
                    }
                }
            }
            mFiles.clear();
        }
    }

    /**
     * Fetch a thumbnail and stick it into the gallery.
     */
    private static class GetGalleryImageTask
            extends TaskBase<FileInfo> {

        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "GetGalleryImageTask";
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
                            @IntRange(from = 0) final int cIdx,
                            @NonNull final FileManager fileManager,
                            @NonNull final TaskListener<FileInfo> taskListener) {
            super(R.id.TASK_ID_GALLERY_IMAGE, taskListener);
            mCIdx = cIdx;

            // sanity check
            if (BuildConfig.DEBUG /* always */) {
                if (!ISBN.isValidIsbn(isbnStr)) {
                    throw new IllegalStateException(ErrorMsg.ISBN_MUST_BE_VALID);
                }
            }

            mIsbn = isbnStr;
            mFileManager = fileManager;
        }

        @Override
        @NonNull
        @WorkerThread
        protected FileInfo doInBackground(final Void... params) {
            Thread.currentThread().setName(TAG + mIsbn);
            final Context context = App.getTaskContext();

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
    private static class GetSwitcherImageTask
            extends TaskBase<FileInfo> {

        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "GetSwitcherImageTask";

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
                             @IntRange(from = 0) final int cIdx,
                             @NonNull final FileManager fileManager,
                             @NonNull final TaskListener<FileInfo> taskListener) {
            super(R.id.TASK_ID_SWITCHER_IMAGE, taskListener);
            mCIdx = cIdx;

            // sanity check
            if (BuildConfig.DEBUG /* always */) {
                if (!ISBN.isValidIsbn(fileInfo.isbn)) {
                    throw new IllegalStateException(ErrorMsg.ISBN_MUST_BE_VALID);
                }
            }

            mFileInfo = fileInfo;
            mFileManager = fileManager;
        }

        @Override
        @NonNull
        @WorkerThread
        protected FileInfo doInBackground(final Void... params) {
            Thread.currentThread().setName(TAG + mFileInfo.isbn);
            final Context context = App.getTaskContext();

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
