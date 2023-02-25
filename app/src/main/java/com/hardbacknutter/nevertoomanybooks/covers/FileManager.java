/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

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
    @NonNull
    private final Map<String, ImageFileInfo> files = Collections.synchronizedMap(new HashMap<>());

    /** The sites the user wants to search for cover images. */
    private final List<EngineId> engineIds;

    /**
     * Constructor.
     *
     * @param engineIds to search on
     */
    FileManager(@NonNull final List<EngineId> engineIds) {
        this.engineIds = engineIds;
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
     * Search for a file according to preference of {@link Size} and {@link EngineId}.
     * <p>
     * First checks the cache. If we already have a good image, abort the search and use it.
     * <p>
     * We loop on {@link Size} first, and for each, loop again on {@link EngineId}.
     * The for() loop will break/return <strong>as soon as a cover file is found.</strong>
     * The first Site which has an image is accepted.
     * <p>
     *
     * @param context          Current context
     * @param progressListener to check for any cancellations
     * @param isbn             to search for, <strong>must</strong> be valid.
     * @param cIdx             0..n image index
     * @param sizes            a list of images sizes in order of preference
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec.
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     */
    @NonNull
    @WorkerThread
    public ImageFileInfo search(@NonNull final Context context,
                                @NonNull final ProgressListener progressListener,
                                @NonNull final String isbn,
                                @IntRange(from = 0, to = 1) final int cIdx,
                                @NonNull final Size... sizes)
            throws StorageException, CredentialsException {

        // We will disable sites on the fly for the *current* search without modifying the list
        final Set<EngineId> currentSearch = EnumSet.copyOf(engineIds);

        final Map<EngineId, SearchEngine> engineCache = new EnumMap<>(EngineId.class);

        ImageFileInfo imageFileInfo;

        // We need to use the size as the outer loop.
        // The idea is to check all sites for the same size first.
        // If none respond with that size, try the next size inline.
        for (final Size size : sizes) {
            if (progressListener.isCancelled()) {
                return new ImageFileInfo(isbn);
            }

            // Do we already have a file previously downloaded?
            imageFileInfo = files.get(isbn);
            if (imageFileInfo != null && imageFileInfo.isUsable(size)) {
                return imageFileInfo;
            }

            for (final EngineId engineId : engineIds) {
                // Should we search this site ?
                if (currentSearch.contains(engineId)) {

                    if (progressListener.isCancelled()) {
                        return new ImageFileInfo(isbn);
                    }

                    // Is this Site's SearchEngine available and suitable?
                    if (engineId.supports(SearchEngine.SearchBy.Isbn)) {

                        SearchEngine searchEngine = engineCache.get(engineId);
                        if (searchEngine == null) {
                            searchEngine = engineId.createSearchEngine(context);
                            // caller is the FetchImageTask
                            searchEngine.setCaller(progressListener);
                            engineCache.put(engineId, searchEngine);
                        }

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                            Log.d(TAG, "search|SEARCHING"
                                       + "|searchEngine=" + searchEngine.getName(context)
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
                            currentSearch.remove(engineId);

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                                Log.d(TAG, "search|FAILED"
                                           + "|searchEngine=" + searchEngine.getName(context)
                                           + "|imageFileInfo=" + imageFileInfo,
                                      e);
                            }

                        }

                        if (fileSpec != null) {
                            // we got a file
                            imageFileInfo = new ImageFileInfo(isbn, fileSpec, size,
                                                              searchEngine.getEngineId());
                            files.put(isbn, imageFileInfo);

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                                Log.d(TAG, "search|SUCCESS"
                                           + "|searchEngine=" + searchEngine.getName(context)
                                           + "|imageFileInfo=" + imageFileInfo);
                            }
                            // abort search, we got an image
                            return imageFileInfo;

                        } else {
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                                Log.d(TAG, "search|NO FILE"
                                           + "|searchEngine=" + searchEngine.getName(context)
                                           + "|isbn=" + isbn
                                           + "|cIdx=" + cIdx
                                           + "|size=" + size);
                            }
                        }

                        // if the site we just searched only supports one image,
                        // disable it for THIS search
                        if (!searchEngine.supportsMultipleCoverSizes()) {
                            currentSearch.remove(engineId);
                        }
                    } else {
                        // if the site we just searched was not available,
                        // disable it for THIS search
                        currentSearch.remove(engineId);
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
