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
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * Handles downloading, checking and cleanup of files.
 */
public class FileManager {

    /** Log tag. */
    private static final String TAG = "FileManager";

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
    public FileManager(@NonNull final SiteList siteList) {
        mSiteList = siteList;
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
    public ImageFileInfo search(@NonNull final Context context,
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
            if ((imageFileInfo != null) && ImageUtils.isFileGood(imageFileInfo.getFile())) {

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
                        imageFileInfo = new ImageFileInfo(isbn, fileSpec, size);

                        if (ImageUtils.isFileGood(imageFileInfo.getFile())) {
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
    public ImageFileInfo getFileInfo(@NonNull final String isbn,
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
    public void purge() {
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

    /**
     * Fetch a image from the file manager.
     */
    public static class FetchImageTask
            extends TaskBase<ImageFileInfo> {

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
        public FetchImageTask(final int taskId,
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
