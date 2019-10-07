/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * The interface a search engine for a site needs to implement.
 */
public interface SearchEngine {

    /**
     * If an implementation does not support a specific (and faster) way/api
     * to fetch a cover image, then {@link #getCoverImage(String, ImageSize)}
     * can call this fallback method.
     * Do NOT use if the site either does not support returning images during search,
     * or does not support isbn searches.
     * <p>
     * A search for the book is done, with the 'fetchThumbnail' flag set to true.
     * Any {@link IOException} or {@link CredentialsException} thrown are ignored and
     * {@code null} returned.
     *
     * @param searchEngine engine to use
     * @param isbn         to search for
     *
     * @return found/saved File, or {@code null} when none found (or any other failure)
     */
    @Nullable
    @WorkerThread
    static File getCoverImageFallback(@NonNull final SearchEngine searchEngine,
                                      @NonNull final String isbn) {
        // sanity check
        if (!ISBN.isValid(isbn)) {
            return null;
        }

        try {
            //ENHANCE: it seems most implementations can return multiple book bundles quite easily.
            Bundle bookData = searchEngine.search(isbn, "", "", "", true);

            ArrayList<String> imageList =
                    bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
            if (imageList != null && !imageList.isEmpty()) {
                // simply get the first one found.
                File downloadedFile = new File(imageList.get(0));
                // let the system resolve any path variations
                File destination = new File(downloadedFile.getAbsolutePath());
                StorageUtils.renameFile(downloadedFile, destination);
                return destination;
            }
        } catch (@NonNull final CredentialsException | IOException e) {
            Logger.error(SearchSites.class, e);
        }

        return null;
    }

    /**
     * Syntax sugar.
     *
     * @return preferences
     */
    static SharedPreferences getPref() {
        return PreferenceManager.getDefaultSharedPreferences(App.getAppContext());
    }

    /**
     * Start a search using the passed criteria.
     * <p>
     * Checking the arguments should really be done inside the implementation,
     * as they generally will depend on what the object can do with them.
     * <p>
     * The implementation will/should give preference to using the ISBN if present,
     * and only fall back to using author/title if needed.
     *
     * @param isbn           to search for
     * @param author         to search for
     * @param title          to search for
     * @param publisher      optional and in addition to author/title.
     *                       i.e. author and/or title must be valid;
     *                       only then the publisher is taken into account.
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     *
     * @return bundle with book data. Can be empty, but never {@code null}.
     * ENHANCE: it seems most implementations can return multiple book bundles quite easily.
     *
     * @throws CredentialsException with GoodReads
     * @throws IOException          on other failures
     */
    @WorkerThread
    @NonNull
    Bundle search(@Nullable String isbn,
                  @Nullable String author,
                  @Nullable String title,
                  @Nullable String publisher,
                  boolean fetchThumbnail)
            throws CredentialsException, IOException;

    /**
     * A site can support a single (default) or multiple sizes.
     *
     * @return {@code true} if multiple sizes are supported.
     */
    @AnyThread
    default boolean siteSupportsMultipleSizes() {
        return false;
    }

    /**
     * Get a cover image.
     *
     * @param isbn to search for
     * @param size of image to get.
     *
     * @return found/saved File, or {@code null} when none found (or any other failure)
     */
    @Nullable
    @WorkerThread
    File getCoverImage(@NonNull String isbn,
                       @Nullable ImageSize size);

    /**
     * Get a cover image. Try in order of large, medium, small depending on the site supporting
     * multiple sizes.
     *
     * @param isbn     book to get an image for
     * @param bookData bundle to populate with the image file spec
     */
    default void getCoverImage(@NonNull final String isbn,
                               @NonNull final Bundle bookData) {
        File file = getCoverImage(isbn, ImageSize.Large);
        if (siteSupportsMultipleSizes()) {
            if (file == null) {
                file = getCoverImage(isbn, ImageSize.Medium);
                if (file == null) {
                    file = getCoverImage(isbn, ImageSize.Small);
                }
            }
        }
        if (file != null) {
            ArrayList<String> imageList =
                    bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
            if (imageList == null) {
                imageList = new ArrayList<>();
            }
            imageList.add(file.getAbsolutePath());
            bookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
        }
    }

    /**
     * Generic test to be implemented by individual site search managers to check if
     * this site is available for search.
     * e.g. check for developer keys, site is up/down, authorization, ...
     * <p>
     * Runs in a background task, so can run network code.
     *
     * @return {@code true} if we can contact this site for searching.
     */
    @WorkerThread
    boolean isAvailable();

    /**
     * Check if the site is ISBN based only.
     *
     * The default implementation assumes that the site can also use other criteria.
     * Override and return {@code true} if the actual implementation cannot do this.
     *
     * @return {@code true} if the site can only be searched with a valid ISBN
     */
    @AnyThread
    default boolean isIsbnOnly() {
        return false;
    }

    /**
     * Get the resource id for the human-readable name of the site.
     *
     * @return the resource id
     */
    @AnyThread
    @StringRes
    int getNameResId();


    /**
     * Sizes of thumbnails.
     * These are open to interpretation (or not used at all) by individual {@link SearchEngine}.
     */
    enum ImageSize {
        Large, Medium, Small
    }
}
