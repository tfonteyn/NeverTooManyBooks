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
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

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
    FileManager(@NonNull final SiteList siteList) {
        mSiteList = siteList;
    }

    /**
     * Search for a file according to preference of {@link ImageFileInfo.Size} and {@link Site}.
     * <p>
     * First checks the cache. If we already have a good image, abort the search and use it.
     * <p>
     * We loop on {@link ImageFileInfo.Size} first, and for each, loop again on {@link Site}.
     * The for() loop will break/return <strong>as soon as a cover file is found.</strong>
     * The first Site which has an image is accepted.
     * <p>
     *
     * @param context Current context
     * @param isbn    to search for, <strong>must</strong> be valid.
     * @param cIdx    0..n image index
     * @param sizes   a list of images sizes in order of preference
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec.
     */
    @NonNull
    @WorkerThread
    public ImageFileInfo search(@NonNull final Canceller caller,
                                @NonNull final Context context,
                                @NonNull final String isbn,
                                @IntRange(from = 0) final int cIdx,
                                @NonNull final ImageFileInfo.Size... sizes) {

        // we will disable sites on the fly for the *current* search without modifying the list.
        @SearchSites.Id
        int currentSearchSites = mSiteList.getEnabledSites();

        // We need to use the size as the outer loop.
        // The idea is to check all sites for the same size first.
        // If none respond with that size, try the next size inline.
        for (ImageFileInfo.Size size : sizes) {
            if (caller.isCancelled()) {
                return new ImageFileInfo(isbn);
            }

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

                    if (caller.isCancelled()) {
                        return new ImageFileInfo(isbn);
                    }

                    final SearchEngine searchEngine = site.getSearchEngine(caller);
                    if (searchEngine instanceof SearchEngine.CoverByIsbn
                        && searchEngine.isAvailable(context)) {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                            Log.d(TAG, "download|TRYING"
                                       + "|isbn=" + isbn
                                       + "|size=" + size
                                       + "|engine=" + searchEngine.getName(context)
                                 );
                        }

                        @Nullable
                        final String fileSpec = ((SearchEngine.CoverByIsbn) searchEngine)
                                .searchCoverImageByIsbn(context, isbn, cIdx, size);

                        imageFileInfo = new ImageFileInfo(isbn, fileSpec, size);

                        if (ImageUtils.isFileGood(imageFileInfo.getFile())) {
                            mFiles.put(key, imageFileInfo);

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                                Log.d(TAG, "download|SUCCESS"
                                           + "|isbn=" + isbn
                                           + "|size=" + size
                                           + "|engine=" + searchEngine.getName(context)
                                           + "|fileSpec=" + fileSpec);
                            }
                            // abort search, we got an image
                            return imageFileInfo;

                        } else if (BuildConfig.DEBUG
                                   && DEBUG_SWITCHES.COVER_BROWSER_DOWNLOADS) {
                            Log.d(TAG, "download|NO GOOD"
                                       + "|isbn=" + isbn
                                       + "|size=" + size
                                       + "|engine=" + searchEngine.getName(context)
                                 );
                        }

                        // if the site we just searched only supports one image,
                        // disable it for THIS search
                        if (!((SearchEngine.CoverByIsbn) searchEngine).supportsMultipleSizes()) {
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

            // remove (if any) bad file
            mFiles.remove(key);
            // loop for next size
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
                              @NonNull final ImageFileInfo.Size... sizes) {
        for (ImageFileInfo.Size size : sizes) {
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
    static class FetchImageTask
            extends TaskBase<ImageFileInfo> {

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
            final Context context = LocaleUtils.applyLocale(App.getTaskContext());

            try {
                return mFileManager.search(this, context, mIsbn, mCIdx, mSizes);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            // we failed, but we still need to return the isbn.
            return new ImageFileInfo(mIsbn);
        }
    }
}
