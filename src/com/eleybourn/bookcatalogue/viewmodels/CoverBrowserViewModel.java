package com.eleybourn.bookcatalogue.viewmodels;

import android.graphics.BitmapFactory;
import android.os.AsyncTask;

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
import java.util.Set;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.CoverBrowserFragment;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
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
    /**
     * List of all alternative editions/isbn for the given ISBN.
     */
    private final MutableLiveData<ArrayList<String>> mEditions = new MutableLiveData<>();
    private final MutableLiveData<String> mSwitcherImageFileSpec = new MutableLiveData<>();
    /** ISBN of book to fetch other editions of. */
    private String mBaseIsbn;
    /** Handles downloading, checking and cleanup of files. */
    private CoverBrowserViewModel.FileManager mFileManager;

    public void init(@NonNull final String isbn,
                     final int initialSearchSites) {
        if (mBaseIsbn != null) {
            return;
        }

        mBaseIsbn = isbn;

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
     */
    private <T extends AsyncTask> void addTask(@NonNull final T task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task);
        }
    }

    /**
     * Remove a specific task from the set (NOT the queue).
     * This method should be called *after* a task has notified us it finished.
     *
     * @param task to remove
     * @param <T>  type of task
     */
    public <T extends AsyncTask> void removeTask(@Nullable final T task) {
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
     * Start a search for alternative editions of the book (isbn).
     */
    public void fetchEditions() {
        // Start a search for alternative editions of the book (isbn).
        GetEditionsTask task = new GetEditionsTask(mBaseIsbn, this);
        addTask(task);
        task.execute();
    }

    public MutableLiveData<ArrayList<String>> getEditions() {
        return mEditions;
    }

    private void onGetEditionsTaskFinished(@NonNull final GetEditionsTask task,
                                           @Nullable final ArrayList<String> editions) {
        removeTask(task);
        mEditions.setValue(editions);
    }

    public void fetchSwitcherImage(@NonNull final String isbn) {

        GetSwitcherImageTask task = new GetSwitcherImageTask(isbn, mFileManager, this);
        addTask(task);
        // use the alternative executor, so we get a result back without
        // waiting on the gallery tasks.
        task.executeOnExecutor(AlternativeExecutor.THREAD_POOL_EXECUTOR);
    }

    public MutableLiveData<String> getSwitcherImageFileSpec() {
        return mSwitcherImageFileSpec;
    }

    private void onGetSwitcherImageTaskFinished(@NonNull final GetSwitcherImageTask task,
                                                @Nullable final String fileSpec) {
        removeTask(task);
        mSwitcherImageFileSpec.setValue(fileSpec);
    }

    public void fetchGalleryImages(@NonNull final CoverBrowserFragment taskListener,
                                   final int position,
                                   @NonNull final String isbn) {
        GetGalleryImageTask task = new GetGalleryImageTask(taskListener, position, isbn,
                                                           mFileManager);

        addTask(task);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Handles downloading, checking and cleanup of files.
     */
    public static class FileManager {

        /**
         * Downloaded files.
         * key = isbn + "_ + size.
         */
        private final Map<String, String> mFiles = Collections.synchronizedMap(new HashMap<>());

        /** Flags applicable to *current* search. */
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
                } catch (RuntimeException e) {
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
         * Download a file if not present and keep a record of it.
         * <p>
         * ENHANCE: use {@link SearchEngine#isAvailable()}.
         * <p>
         * Reminder: the for() loop will bailout (return) as soon as a cover file is found.
         * i.e. for each edition of the isbn, we try to get an image from the search-sites.
         * The first search site which has an image is accepted for *that* edition.
         * Repeat for all editions.
         * <p>
         * ENHANCE: allow the user to prioritize the order on the fly.
         *
         * @param isbn       ISBN of file
         * @param imageSizes a list of images sizes in order of preference
         *
         * @return the fileSpec, or {@code null} if not found
         */
        @Nullable
        @WorkerThread
        public String download(@NonNull final String isbn,
                               @NonNull final SearchEngine.ImageSizes... imageSizes) {

            // we need to use the size as the outer loop (and not inside of getCoverImage itself).
            // the idea is to check all sites for the same size first.
            // if none respond with that size, try the next size inline.
            // The other way around we could get a site/size instead of other0site/better-size.
            for (SearchEngine.ImageSizes size : imageSizes) {
                String key = isbn + '_' + size;
                String fileSpec = mFiles.get(key);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                    Logger.debug(this, "download",
                                 "isbn=" + isbn,
                                 "size=" + size,
                                 "fileSpec=" + fileSpec);
                }

                // Is the file present && good ?
                if ((fileSpec != null) && !fileSpec.isEmpty() && isGood(new File(fileSpec))) {
                    return fileSpec;

                } else {
                    mFiles.remove(key);
                }

                for (Site site : SearchSites.getSitesForCoverSearches()) {
                    // Are we allowed to search this site ?
                    if (site.isEnabled()
                            // and should we search this site ?
                            && ((mSearchSites & site.id) != 0)
                    ) {
                        SearchEngine searchEngine = site.getSearchEngine();
                        // don't search the same site for all sizes if it only supports one size.
                        if (searchEngine.supportsImageSize(size)) {
                            File file = searchEngine.getCoverImage(isbn, size);
                            if (file != null && isGood(file)) {
                                fileSpec = file.getAbsolutePath();
                                mFiles.put(key, fileSpec);
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                                    Logger.debug(this, "download",
                                                 "FOUND",
                                                 "isbn=" + isbn,
                                                 "size=" + size,
                                                 "fileSpec=" + fileSpec);
                                }
                                return fileSpec;

                            }
//                            else {
//                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
//                                    Logger.debug(this, "download",
//                                                "MISSING",
//                                                "isbn=" + isbn,
//                                                "size=" + size,
//                                                "fileSpec=" + fileSpec);
//                                }
//                            }
                        }
                    }
                }

                // give up
                mFiles.remove(key);
            }
            return null;
        }

        /**
         * Get the requested file, if available, otherwise return {@code null}.
         *
         * @param isbn  to search
         * @param sizes required sizes in order to look for. First found is used.
         *
         * @return the file, or {@code null} if not found
         */
        @Nullable
        public File getFile(@NonNull final String isbn,
                            @NonNull final SearchEngine.ImageSizes... sizes) {
            for (SearchEngine.ImageSizes size : sizes) {
                String fileSpec = mFiles.get(isbn + '_' + size);
                if (fileSpec != null && !fileSpec.isEmpty()) {
                    return new File(fileSpec);
                }
            }
            return null;
        }

        /**
         * Clean up all files.
         */
        void purge() {
            for (String fileSpec : mFiles.values()) {
                if (fileSpec != null) {
                    StorageUtils.deleteFile(new File(fileSpec));
                }
            }
            mFiles.clear();
        }
    }

    /**
     * Fetch all alternative edition isbn's from LibraryThing.
     */
    public static class GetEditionsTask
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
            } catch (RuntimeException e) {
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
    public static class GetGalleryImageTask
            extends AsyncTask<Void, Void, String> {

        @NonNull
        private final WeakReference<CoverBrowserFragment> mTaskListener;
        private final int mPosition;

        @NonNull
        private final FileManager mFileManager;

        @NonNull
        private final String mIsbn;

        /**
         * Constructor.
         *
         * @param taskListener to send results to
         * @param isbn         to get image for
         * @param fileManager  for downloads
         */
        @UiThread
        GetGalleryImageTask(@NonNull final CoverBrowserFragment taskListener,
                            final int position,
                            @NonNull final String isbn,
                            @NonNull final FileManager fileManager) {
            mTaskListener = new WeakReference<>(taskListener);
            mPosition = position;
            mIsbn = isbn;
            mFileManager = fileManager;
        }

        @Override
        @Nullable
        @WorkerThread
        protected String doInBackground(final Void... params) {
            Thread.currentThread().setName("GetGalleryImageTask " + mIsbn);
            try {
                return mFileManager.download(mIsbn,
                                             SearchEngine.ImageSizes.SMALL,
                                             SearchEngine.ImageSizes.MEDIUM,
                                             SearchEngine.ImageSizes.LARGE);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            return null;
        }

        @Override
        protected void onCancelled(@Nullable final String result) {
            // let the caller clean up.
            if (mTaskListener.get() != null) {
                mTaskListener.get().updateGallery(this, mPosition, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onCancelled",
                                 "WeakReference to listener was dead");
                }
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final String result) {
            // always callback; even with a bad result.
            if (mTaskListener.get() != null) {
                mTaskListener.get().updateGallery(this, mPosition, result);
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
    public static class GetSwitcherImageTask
            extends AsyncTask<Void, Void, String> {

        @NonNull
        private final WeakReference<CoverBrowserViewModel> mTaskListener;
        @NonNull
        private final FileManager mFileManager;
        @NonNull
        private final String mIsbn;

        /**
         * Constructor.
         *
         * @param isbn         book to search
         * @param fileManager  for downloads
         * @param taskListener to send results to
         */
        @UiThread
        GetSwitcherImageTask(@NonNull final String isbn,
                             @NonNull final FileManager fileManager,
                             @NonNull final CoverBrowserViewModel taskListener) {
            mTaskListener = new WeakReference<>(taskListener);

            mFileManager = fileManager;
            mIsbn = isbn;
        }

        @Override
        @Nullable
        @WorkerThread
        protected String doInBackground(final Void... params) {
            Thread.currentThread().setName("GetSwitcherImageTask " + mIsbn);
            try {
                return mFileManager.download(mIsbn, SearchEngine.ImageSizes.LARGE,
                                             SearchEngine.ImageSizes.MEDIUM,
                                             SearchEngine.ImageSizes.SMALL);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            return null;
        }

        @Override
        protected void onCancelled(final String result) {
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
        protected void onPostExecute(@Nullable final String result) {
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
