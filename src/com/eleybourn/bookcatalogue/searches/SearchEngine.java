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
            Bundle bookData = site.search(isbn, "", "", true);

            ArrayList<String> imageList =
                    bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
            if (imageList != null && !imageList.isEmpty()) {
                File found = new File(imageList.get(0));
                File coverFile = new File(found.getAbsolutePath() + '_' + isbn);
                StorageUtils.renameFile(found, coverFile);
                return coverFile;
            }
        } catch (IOException | AuthorizationException e) {
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
     * @throws IOException            on failure
     * @throws AuthorizationException if the site rejects our credentials (if any)
     */
    @WorkerThread
    @NonNull
    Bundle search(@Nullable String isbn,
                  @Nullable String author,
                  @Nullable String title,
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
     * {@link #search(String, String, String, boolean)} method.
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
     * Warning: the use of this method should be limited to places
     * where we search for multiple sizes!
     * <p>
     * Do NOT use this check if you just do a single search for some size.
     * <p>
     * TODO: the above warning could be phrased better...
     * try again:
     * CoverBrowserFragment loops through all sizes
     * --> must use this method check to avoid calling sites to many times.
     * Other places, where just a single image is fetched, don't use this method.
     * <p>
     * Would it be better if this method was more "siteSupportsMultipleSizes()" ?
     *
     * @param size the image size we want to check support on.
     *
     * @return {@code true} if the site supports the passed image size
     */
    @AnyThread
    boolean supportsImageSize(@NonNull final ImageSizes size);

    /**
     * @return the resource id for the text "Searching {site}"
     */
    @AnyThread
    @StringRes
    int getSearchingResId();

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
