package com.eleybourn.bookcatalogue.searches;

import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * The API a search engine for a site needs to implement.
 */
public interface SearchEngine {

    /**
     * If a {@link SearchEngine} does not support a specific (and faster) way/api
     * to fetch a cover image, then {@link SearchEngine#getCoverImage(String, SearchEngine.ImageSizes)}
     * can call this fallback method.
     * Do NOT use if the site either does not support returning images during search,
     * or does not support isbn searches.
     * <p>
     * A search for the book is done, with the 'fetchThumbnail' flag set to true.
     * Any {@link IOException} or {@link AuthorizationException} thrown are ignored and
     * {@code null} returned.
     *
     * @param isbn to search for
     *
     * @return found/saved File, or {@code null} when none found (or any other failure)
     */
    @Nullable
    @WorkerThread
    static File getCoverImageFallback(@NonNull final SearchEngine site,
                                      @NonNull final String isbn) {
        // sanity check
        if (!ISBN.isValid(isbn)) {
            return null;
        }

        try {
            // ENHANCE/FIXME: its seems most (all?) implementations can return multiple book data bundles quite easily.
            Bundle bookData = site.search(isbn, "", "", "", true);

            ArrayList<String> imageList =
                    bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
            if (imageList != null && !imageList.isEmpty()) {
                File found = new File(imageList.get(0));
                File coverFile = new File(found.getAbsolutePath() + '_' + isbn);
                StorageUtils.renameFile(found, coverFile);
                return coverFile;
            }
        } catch (@NonNull final IOException | AuthorizationException e) {
            Logger.error(SearchSites.class, e);
        }

        return null;
    }

    /**
     * Start a search using the passed criteria.
     *
     * Checking the arguments should really be done inside the implementation,
     * as they generally will depend on what the object can do with them.
     *
     * The implementation will/should give preference to using the ISBN if present,
     * and only fall back to using author/title if needed.
     *
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     *
     * @return bundle with book data. Can be empty, but never {@code null}.
     * ENHANCE/FIXME: its seems most (all?) implementations can return multiple book data bundles quite easily.
     *
     * @throws AuthorizationException with GoodReads
     * @throws IOException            on other failures
     */
    @WorkerThread
    @NonNull
    Bundle search(@Nullable String isbn,
                  @Nullable String author,
                  @Nullable String title,
                  final String publisher,
                  boolean fetchThumbnail)
            throws IOException, AuthorizationException;

    /**
     * Get a cover image.
     *
     * @param isbn to search for
     * @param size of image to get.
     *
     * @return found/saved File, or {@code null} when none found (or any other failure)
     * <p>
     * If a {@link SearchEngine} does not support a specific (and faster) way/api
     * to fetch a cover image, then this default method is used, delegating to the
     * {@link #search(String, String, String, String, boolean)} method.
     * <p>
     * A search for the book is done, with the 'fetchThumbnail' flag set to true.
     * Any {@link IOException} or {@link AuthorizationException} thrown are ignored and
     * {@code null} returned.
     */
    @Nullable
    @WorkerThread
    default File getCoverImage(@NonNull final String isbn,
                               @Nullable final ImageSizes size) {
        return getCoverImageFallback(this, isbn);
    }

    /**
     * Generic test to be implemented by individual site search managers to check if
     * this site is available for search.
     * e.g. check for developer keys, site is up/down, authorization, ...
     * <p>
     * Runs in a background task, so can run network code.
     *
     * @return {@code true} if we can use this site for searching.
     */
    @WorkerThread
    boolean isAvailable();

    /**
     * @return {@code true} if the site can only be searched with a valid ISBN
     */
    @AnyThread
    default boolean isIsbnOnly() {
        return false;
    }

    /**
     * A site can support a single or multiple sizes.
     *
     * @return {@code true} if multiple sizes are supported.
     */
    @AnyThread
    default boolean siteSupportsMultipleSizes() {
        return false;
    }

    /**
     * @return the resource id for the human-readable name of the site
     */
    @AnyThread
    @StringRes
    int getNameResId();


    /**
     * Sizes of thumbnails.
     * These are open to interpretation (or not used) by individual {@link SearchEngine}.
     */
    enum ImageSizes {
        SMALL,
        MEDIUM,
        LARGE
    }

}
