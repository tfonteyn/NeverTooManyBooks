/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.Context;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Handles downloading, checking and cleanup of files.
 */
public class FileManager {

    /** Log tag. */
    private static final String TAG = "FileManager";

    /**
     * Downloaded files.
     * key = isbn
     */
    private final Map<String, ImageFileInfo> files =
            Collections.synchronizedMap(new HashMap<>());

    /** The Sites the user wants to search for cover images. */
    private final List<Site> siteList;

    /**
     * Constructor.
     *
     * @param sites site list
     */
    FileManager(@NonNull final List<Site> sites) {
        siteList = sites;
    }

    /**
     * Get the requested ImageFileInfo.
     *
     * @param isbn to search
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec,
     *         or {@code null} if there is no cached file at all
     */
    @Nullable
    @AnyThread
    ImageFileInfo getFileInfo(@NonNull final String isbn) {
        return files.get(isbn);
    }

    /**
     * Search for a file according to preference of {@link Size} and {@link Site}.
     * <p>
     * First checks the cache. If we already have a good image, abort the search and use it.
     * <p>
     * We loop on {@link Size} first, and for each, loop again on {@link Site}.
     * The for() loop will break/return <strong>as soon as a cover file is found.</strong>
     * The first Site which has an image is accepted.
     * <p>
     *
     * @param context Current context
     * @param caller  to check for any cancellations
     * @param isbn    to search for, <strong>must</strong> be valid.
     * @param cIdx    0..n image index
     * @param sizes   a list of images sizes in order of preference
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec.
     */
    @NonNull
    @WorkerThread
    public ImageFileInfo search(@NonNull final Context context,
                                @NonNull final Cancellable caller,
                                @NonNull final String isbn,
                                @IntRange(from = 0, to = 1) final int cIdx,
                                @NonNull final Size... sizes)
            throws StorageException, CredentialsException {

        final List<Site> enabledSites = Site.filterForEnabled(siteList);

        // We will disable sites on the fly for the *current* search without
        // modifying the list by using a simple bitmask.
        @SearchSites.EngineId
        int currentSearchSites = 0;
        for (final Site site : enabledSites) {
            currentSearchSites = currentSearchSites | site.engineId;
        }

        ImageFileInfo imageFileInfo;

        // We need to use the size as the outer loop.
        // The idea is to check all sites for the same size first.
        // If none respond with that size, try the next size inline.
        for (final Size size : sizes) {
            if (caller.isCancelled()) {
                return new ImageFileInfo(isbn);
            }

            // Do we already have a file previously downloaded?
            imageFileInfo = files.get(isbn);
            if (imageFileInfo != null && imageFileInfo.isUsable(size)) {
                return imageFileInfo;
            }

            for (final Site site : enabledSites) {
                // Should we search this site ?
                if ((currentSearchSites & site.engineId) != 0) {

                    if (caller.isCancelled()) {
                        return new ImageFileInfo(isbn);
                    }

                    // Is this Site's SearchEngine available and suitable?
                    final SearchEngine searchEngine = site.getSearchEngine();
                    if (searchEngine instanceof SearchEngine.CoverByIsbn
                        && searchEngine.isAvailable()) {

                        searchEngine.setCaller(caller);

                        final SearchEngineConfig seConfig = searchEngine.getConfig();

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                            Log.d(TAG, "search|SEARCHING"
                                       + "|searchEngine=" + seConfig.getName(context)
                                       + "|isbn=" + isbn
                                       + "|cIdx=" + cIdx
                                       + "|size=" + size);
                        }

                        String fileSpec = null;
                        try {
                            fileSpec = ((SearchEngine.CoverByIsbn) searchEngine)
                                    .searchCoverByIsbn(context, isbn, cIdx, size);

                        } catch (@NonNull final SearchException e) {
                            // ignore, don't let a single search break the loop.
                            // disable it for THIS search
                            currentSearchSites &= ~site.engineId;

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                                Log.d(TAG, "search|FAILED"
                                           + "|searchEngine=" + seConfig.getName(context)
                                           + "|imageFileInfo=" + imageFileInfo,
                                      e);
                            }

                        }

                        if (fileSpec != null) {
                            // we got a file
                            imageFileInfo = new ImageFileInfo(isbn, fileSpec, size,
                                                              seConfig.getEngineId());
                            files.put(isbn, imageFileInfo);

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                                Log.d(TAG, "search|SUCCESS"
                                           + "|searchEngine=" + seConfig.getName(context)
                                           + "|imageFileInfo=" + imageFileInfo);
                            }
                            // abort search, we got an image
                            return imageFileInfo;

                        } else {
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                                Log.d(TAG, "search|NO FILE"
                                           + "|searchEngine=" + seConfig.getName(context)
                                           + "|isbn=" + isbn
                                           + "|cIdx=" + cIdx
                                           + "|size=" + size);
                            }
                        }

                        // if the site we just searched only supports one image,
                        // disable it for THIS search
                        if (!seConfig.supportsMultipleCoverSizes()) {
                            currentSearchSites &= ~site.engineId;
                        }
                    } else {
                        // if the site we just searched was not available,
                        // disable it for THIS search
                        currentSearchSites &= ~site.engineId;
                    }
                }
                // loop for next site
            }
            // loop for next size
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Log.d(TAG, "search|FAILED|isbn=" + isbn);
        }

        // Failed to find any size on all sites, record the failure to prevent future attempt
        imageFileInfo = new ImageFileInfo(isbn);
        files.put(isbn, imageFileInfo);
        // and return the failure
        return imageFileInfo;
    }

    /**
     * Clean up all files we handled in this class.
     */
    public void purge() {
        files.values()
             .stream()
             .filter(Objects::nonNull)
             .map(ImageFileInfo::getFile)
             .forEach(file -> file.ifPresent(FileUtils::delete));

        // not strictly needed, but future-proof
        files.clear();
    }

}
