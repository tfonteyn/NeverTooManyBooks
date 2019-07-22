package com.eleybourn.bookcatalogue.viewmodels;

import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.Site;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.tasks.AlternativeExecutor;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class CoverBrowserViewModel
        extends ViewModel {

    /** Holder for all active tasks, so we can cancel them if needed. */
    private final Set<AsyncTask> mAllTasks = new HashSet<>();

    /** List of all alternative editions/isbn for the given ISBN. */
    private final MutableLiveData<ArrayList<String>> mEditions = new MutableLiveData<>();

    private final MutableLiveData<FileInfo> mGalleryImage = new MutableLiveData<>();

    private final MutableLiveData<FileInfo> mSwitcherImage = new MutableLiveData<>();
    /** ISBN of book to fetch other editions of. */
    private String mBaseIsbn;
    /** Handles downloading, checking and cleanup of files. */
    private CoverBrowserViewModel.FileManager mFileManager;

    /**
     * Pseudo constructor.
     *
     * @param args Bundle
     */
    public void init(@NonNull final Bundle args) {
        if (mBaseIsbn != null) {
            return;
        }

        mBaseIsbn = Objects.requireNonNull(args.getString(DBDefinitions.KEY_ISBN));
        int initialSearchSites = args.getInt(UniqueId.BKEY_SEARCH_SITES, SearchSites.SEARCH_ALL);
        // Create an object to manage the downloaded files
        mFileManager = new FileManager(initialSearchSites);
    }

    @Override
    protected void onCleared() {
        cancelAllTasks();

        if (mFileManager != null) {
            mFileManager.purge();
        }
    }

    public FileManager getFileManager() {
        return mFileManager;
    }

    /**
     * Keep track of all tasks so we can remove/cancel at will.
     *
     * @param task to add
     * @param <T>  type of task
     *
     * @return task for chaining
     */
    private <T extends AsyncTask> T addTask(@NonNull final T task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task);
        }
        return task;
    }

    /**
     * Remove a specific task from the set (NOT the queue).
     * This method should be called *after* a task has notified us it finished.
     *
     * @param task to remove
     * @param <T>  type of task
     */
    private <T extends AsyncTask> void removeTask(@Nullable final T task) {
        if (task != null) {
            synchronized (mAllTasks) {
                mAllTasks.remove(task);
            }
        }
    }

    /**
     * Cancel all active tasks.
     */
    private void cancelAllTasks() {
        // cancel any active tasks.
        synchronized (mAllTasks) {
            for (AsyncTask task : mAllTasks) {
                task.cancel(true);
            }
        }
    }


    /**
     * Start a search for alternative editions of the book (using the isbn).
     */
    public void fetchEditions() {
        addTask(new GetEditionsTask(mBaseIsbn, this)).execute();
    }

    public MutableLiveData<ArrayList<String>> getEditions() {
        return mEditions;
    }

    private void onGetEditionsTaskFinished(@NonNull final GetEditionsTask task,
                                           @Nullable final ArrayList<String> editions) {
        removeTask(task);
        mEditions.setValue(editions);
    }


    public void fetchGalleryImage(@NonNull final String isbn) {
        addTask(new GetGalleryImageTask(isbn, mFileManager, this))
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public MutableLiveData<FileInfo> getGalleryImage() {
        return mGalleryImage;
    }

    private void onGetGalleryImageTaskFinished(@NonNull final GetGalleryImageTask task,
                                               @NonNull final FileInfo fileInfo) {
        removeTask(task);
        mGalleryImage.setValue(fileInfo);
    }


    public void fetchSwitcherImage(@NonNull final CoverBrowserViewModel.FileInfo fileInfo) {
        // use the alternative executor, so we get a result back without
        // waiting on the gallery tasks.
        addTask(new GetSwitcherImageTask(fileInfo, mFileManager, this))
                .executeOnExecutor(AlternativeExecutor.THREAD_POOL_EXECUTOR);
    }

    public MutableLiveData<FileInfo> getSwitcherImage() {
        return mSwitcherImage;
    }

    private void onGetSwitcherImageTaskFinished(@NonNull final GetSwitcherImageTask task,
                                                @NonNull final FileInfo fileInfo) {
        removeTask(task);
        mSwitcherImage.setValue(fileInfo);
    }

    /**
     * Value class to return info about a file.
     */
    public static class FileInfo
            implements Parcelable {

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
        public SearchEngine.ImageSize size;
        public String fileSpec;
        public Site site;

        /**
         * Constructor.
         */
        FileInfo(@NonNull final String isbn,
                 @NonNull final SearchEngine.ImageSize size,
                 @NonNull final String fileSpec,
                 @NonNull final Site site) {
            this.isbn = isbn;
            this.size = size;
            this.fileSpec = fileSpec;
            this.site = site;
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

        /** {@link Parcelable}. */
        private FileInfo(@NonNull final Parcel in) {
            //noinspection ConstantConditions
            isbn = in.readString();
            fileSpec = in.readString();
            site = in.readParcelable(Site.class.getClassLoader());
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(isbn);
            dest.writeString(fileSpec);
            dest.writeParcelable(site, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public boolean hasFileSpec() {
            return fileSpec != null && !fileSpec.isEmpty();
        }

        @NonNull
        @Override
        public String toString() {
            return "FileInfo{"
                    + "isbn='" + isbn + '\''
                    + ", size=" + size
                    + ", fileSpec='" + fileSpec + '\''
                    + ", site=" + (site != null ? site.getName() : null)
                    + '}';
        }
    }

    /**
     * Handles downloading, checking and cleanup of files.
     */
    public static class FileManager {

        /**
         * Downloaded files.
         * key = isbn + '_' + size.
         */
        private final Map<String, FileInfo> mFiles = Collections.synchronizedMap(new HashMap<>());

        /** Sites the user wants to search. */
        private final int mSearchSites;

        /**
         * Constructor.
         *
         * @param initialSearchSites bitmask with sites to search,
         *                           see {@link SearchSites#SEARCH_ALL} and individual flags
         */
        FileManager(final int initialSearchSites) {
            mSearchSites = initialSearchSites;
        }

        /**
         * Check if a file is an image with an acceptable size.
         *
         * @param file to check
         *
         * @return {@code true} if file is acceptable.
         */
        private boolean isGood(@NonNull final File file) {
            boolean ok = false;

            if (file.exists() && file.length() != 0) {
                try {
                    // Just read the image files to get file size
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
                    // If too small, it's no good
                    ok = opt.outHeight >= 10 && opt.outWidth >= 10;
                } catch (@NonNull final RuntimeException e) {
                    // Failed to decode; probably not an image
                    ok = false;
                    Logger.error(this, e, "Unable to decode thumbnail");
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
         * ENHANCE: use {@link SearchEngine#isAvailable()}.
         * <p>
         * We loop on ImageSize first, and then for each ImageSize we loop again on Site.<br>
         * The for() loop will break/return <strong><as soon as a cover file is found./strong>
         * The first Site which has an image is accepted.
         * <p>
         * ENHANCE: allow the user to prioritize the Site order on the fly.
         *
         * @param isbn        ISBN of file
         * @param primarySite try this site first
         * @param imageSizes  a list of images sizes in order of preference
         *
         * @return a {@link FileInfo} object with or without a valid fileSpec.
         */
        @NonNull
        @WorkerThread
        FileInfo download(@NonNull final String isbn,
                          @Nullable final Site primarySite,
                          @NonNull final SearchEngine.ImageSize... imageSizes) {

            // use a local copy so we can disable sites on the fly.
            int currentSearchSites = mSearchSites;

            // we need to use the size as the outer loop (and not inside of getCoverImage itself).
            // the idea is to check all sites for the same size first.
            // if none respond with that size, try the next size inline.
            // The other way around we could get a site/small-size instead of otherSite/better-size.
            for (SearchEngine.ImageSize size : imageSizes) {
                String key = isbn + '_' + size;
                FileInfo fileInfo = mFiles.get(key);

                // Do we already have a file and is it good ?
                if ((fileInfo != null) && fileInfo.hasFileSpec() && isGood(
                        new File(fileInfo.fileSpec))) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                        Logger.debug(this, "download", "FILESYSTEM", fileInfo);
                    }
                    // use it
                    return fileInfo;

                } else {
                    // it was present but bad, remove it.
                    mFiles.remove(key);
                }

                for (Site site : SearchSites.getSitesForCoverSearches()) {
                    // Are we allowed to search this site ? and should we search this site ?
                    if (site.isEnabled() && ((currentSearchSites & site.id) != 0)) {

                        fileInfo = downloadFromSite(site, isbn, size);
                        if (fileInfo != null) {
                            return fileInfo;
                        }

                        // if the site we just searched only supports one image, disable it
                        // for THIS search
                        if (!site.getSearchEngine().siteSupportsMultipleSizes()) {
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
         * Try to get an image from the specified site.
         *
         * @param site to try
         * @param isbn to get
         * @param size to get
         *
         * @return a FileInfo object with a valid fileSpec, or {@code null} if not found.
         */
        @Nullable
        private FileInfo downloadFromSite(@NonNull final Site site,
                                          @NonNull final String isbn,
                                          @NonNull final SearchEngine.ImageSize size) {

            @Nullable
            File file = site.getSearchEngine().getCoverImage(isbn, size);
            if (file != null && isGood(file)) {
                String key = isbn + '_' + size;
                FileInfo fileInfo = new FileInfo(isbn, size, file.getAbsolutePath(), site);
                mFiles.put(key, fileInfo);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                    Logger.debug(this, "downloadFromSite",
                                 "FOUND", fileInfo);
                }
                return fileInfo;

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                    Logger.debug(this, "downloadFromSite",
                                 "MISSING",
                                 "site=" + site.getName(),
                                 "isbn=" + isbn,
                                 "size=" + size);
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
                                @NonNull final SearchEngine.ImageSize... sizes) {
            for (SearchEngine.ImageSize size : sizes) {
                FileInfo fileInfo = mFiles.get(isbn + '_' + size);
                if (fileInfo != null && fileInfo.hasFileSpec()) {
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
                if (fileInfo != null && fileInfo.hasFileSpec()) {
                    StorageUtils.deleteFile(new File(fileInfo.fileSpec));
                }
            }
            mFiles.clear();
        }
    }

    /**
     * Fetch all alternative edition isbn's from LibraryThing.
     */
    static class GetEditionsTask
            extends AsyncTask<Void, Void, ArrayList<String>> {

        @NonNull
        private final String mIsbn;
        @NonNull
        private final WeakReference<CoverBrowserViewModel> mTaskListener;

        /**
         * Constructor.
         *
         * @param isbn         to search for
         * @param taskListener to send results to
         */
        @UiThread
        GetEditionsTask(@NonNull final String isbn,
                        @NonNull final CoverBrowserViewModel taskListener) {
            mIsbn = isbn;
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @Nullable
        @WorkerThread
        protected ArrayList<String> doInBackground(final Void... params) {
            Thread.currentThread().setName("GetEditionsTask " + mIsbn);

            // Get some editions
            // ENHANCE: the list of editions should be expanded to include other sites
            // As well as the alternate user-contributed images from LibraryThing. The latter are
            // often the best source but at present could only be obtained by HTML scraping.
            try {
                return LibraryThingManager.searchEditions(mIsbn);
            } catch (@NonNull final RuntimeException e) {
                return null;
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final ArrayList<String> result) {
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetEditionsTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    /**
     * Fetch a thumbnail and stick it into the gallery.
     */
    static class GetGalleryImageTask
            extends AsyncTask<Void, Void, FileInfo> {

        @NonNull
        private final String mIsbn;
        @NonNull
        private final FileManager mFileManager;
        @NonNull
        private final WeakReference<CoverBrowserViewModel> mTaskListener;

        /**
         * Constructor.
         *
         * @param isbn         to get image for
         * @param fileManager  for downloads
         * @param taskListener to send results to
         */
        @UiThread
        GetGalleryImageTask(@NonNull final String isbn,
                            @NonNull final FileManager fileManager,
                            @NonNull final CoverBrowserViewModel taskListener) {
            mIsbn = isbn;
            mFileManager = fileManager;
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @NonNull
        @WorkerThread
        protected FileInfo doInBackground(final Void... params) {
            Thread.currentThread().setName("GetGalleryImageTask " + mIsbn);
            try {
                return mFileManager.download(mIsbn, null,
                                             // try to get a picture in this order of size.
                                             // Stops at first one found.
                                             SearchEngine.ImageSize.SMALL,
                                             SearchEngine.ImageSize.MEDIUM,
                                             SearchEngine.ImageSize.LARGE);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(mIsbn);
        }

        @Override
        protected void onCancelled(@NonNull final FileInfo result) {
            // let the caller clean up.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetGalleryImageTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onCancelled",
                                 "WeakReference to listener was dead");
                }
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final FileInfo result) {
            // always callback; even with a bad result.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetGalleryImageTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    /**
     * Fetch a full-size image and stick it into the ImageSwitcher.
     */
    static class GetSwitcherImageTask
            extends AsyncTask<Void, Void, FileInfo> {

        @NonNull
        private final FileInfo mFileInfo;
        @NonNull
        private final FileManager mFileManager;
        @NonNull
        private final WeakReference<CoverBrowserViewModel> mTaskListener;


        /**
         * Constructor.
         *
         * @param fileInfo     book to search
         * @param fileManager  for downloads
         * @param taskListener to send results to
         */
        @UiThread
        GetSwitcherImageTask(@NonNull final CoverBrowserViewModel.FileInfo fileInfo,
                             @NonNull final FileManager fileManager,
                             @NonNull final CoverBrowserViewModel taskListener) {
            mFileInfo = fileInfo;
            mFileManager = fileManager;
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @NonNull
        @WorkerThread
        protected FileInfo doInBackground(final Void... params) {
            Thread.currentThread().setName("GetSwitcherImageTask " + mFileInfo.isbn);
            try {
                return mFileManager.download(mFileInfo.isbn,
                                             // try this site first.
                                             mFileInfo.site,
                                             // try to get a picture in this order of size.
                                             // Stops at first one found.
                                             SearchEngine.ImageSize.LARGE,
                                             SearchEngine.ImageSize.MEDIUM,
                                             SearchEngine.ImageSize.SMALL);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(mFileInfo.isbn);
        }

        @Override
        protected void onCancelled(@NonNull final FileInfo result) {
            // let the caller clean up.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetSwitcherImageTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onCancelled",
                                 "WeakReference to listener was dead");
                }
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final FileInfo result) {
            // always callback; even with a bad result.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetSwitcherImageTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }
}
