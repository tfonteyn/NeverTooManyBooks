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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * The interface a search engine for a {@link Site} needs to implement.
 * <p>
 * More details in {@link SearchSites}.
 * <p>
 * At least one of the sub-interfaces needs to be implemented:
 * <ul>
 *      <li>{@link ByExternalId}</li>
 *      <li>{@link ByIsbn}</li>
 *      <li>{@link ByText}</li>
 * </ul>
 * and if the site supports fetching images by ISBN: {@link CoverByIsbn}.
 * <p>
 * ENHANCE: it seems most implementations can return multiple book bundles quite easily.
 */
public interface SearchEngine {

    /** Log tag. */
    String TAG = "SearchEngine";

    /**
     * Get the engine id.
     * <p>
     * Default implementation in {@link SearchEngineBase}.
     *
     * @return engine id
     */
    @AnyThread
    @SearchSites.EngineId
    int getId();

    /**
     * The <strong>Application</strong> context. Should not be used for UI interactions.
     * <p>
     * Default implementation in {@link SearchEngineBase}.
     *
     * @return <strong>Application</strong> context.
     */
    @AnyThread
    @NonNull
    Context getAppContext();

    /**
     * Get the name for this engine.
     *
     * @return name
     */
    @AnyThread
    @NonNull
    default String getName() {
        //noinspection ConstantConditions
        return getAppContext().getString(SearchEngineRegistry.getByEngineId(getId())
                                                             .getNameResId());
    }

    /**
     * Get the site url.
     * <p>
     * Override if the URL needs to be user configurable.
     *
     * @return url, including scheme.
     */
    @AnyThread
    @NonNull
    default String getSiteUrl() {
        //noinspection ConstantConditions
        return SearchEngineRegistry.getByEngineId(getId()).getSiteUrl();
    }

    /**
     * Get the Locale for this engine.
     * <p>
     * Override if the Locale needs to be user configurable.
     * (Presumably depending on the site url: see {@link AmazonSearchEngine} for an example)
     *
     * @return site locale
     */
    @AnyThread
    @NonNull
    default Locale getLocale() {
        //noinspection ConstantConditions
        return SearchEngineRegistry.getByEngineId(getId()).getLocale();
    }

    /**
     * Generic test to be implemented by individual site search managers to check if
     * this site can be consider for searching.
     * <p>
     * Implementations can for example check for developer keys, ...
     * <strong>Should not run network code.</strong>
     *
     * @return {@code true} if we can consider this site for searching.
     */
    @AnyThread
    default boolean isAvailable() {
        return true;
    }

    /**
     * Reset the engine, ready for a new search.
     * This is called by {@link Site#getSearchEngine}.
     * <p>
     * The default implementation calls {@code setCaller(null)}.
     * Custom implementations should do the same.
     */
    default void reset() {
        setCaller(null);
    }

    default void setCaller(@Nullable Canceller caller) {

    }

    @AnyThread
    default boolean isCancelled() {
        return false;
    }

    /**
     * Optional to implement: sites which need a registration of some sorts.
     * <p>
     * Check if we have a key; if not alert the user.
     *
     * @param context      Current context; <strong>MUST</strong> be passed in
     *                     as this call might do UI interaction.
     * @param required     {@code true} if we <strong>must</strong> have access to the site.
     *                     {@code false} if it would be beneficial but not mandatory.
     * @param callerSuffix String used to flag in preferences if we showed the alert from
     *                     that caller already or not.
     *
     * @return {@code true} if an alert is currently shown
     */
    @UiThread
    default boolean promptToRegister(@NonNull final Context context,
                                     final boolean required,
                                     @NonNull final String callerSuffix) {
        return false;
    }

    /**
     * Get the throttler for regulating network access.
     *
     * @return {@code null} no Throttler by default. Override as needed.
     */
    @Nullable
    default Throttler getThrottler() {
        return null;
    }

    /**
     * Convenience method to create a connection using the engines specific network configuration.
     *
     * @param url             to connect to
     * @param followRedirects a {@code boolean} indicating
     *                        whether or not to follow HTTP redirects.
     *
     * @return the connection
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    default TerminatorConnection createConnection(@NonNull final String url,
                                                  final boolean followRedirects)
            throws IOException {
        final SearchEngineRegistry.Config config = SearchEngineRegistry.getByEngineId(getId());
        //noinspection ConstantConditions
        final TerminatorConnection con = new TerminatorConnection(getAppContext(), url,
                                                                  config.getConnectTimeoutMs(),
                                                                  config.getReadTimeoutMs(),
                                                                  getThrottler());
        con.setInstanceFollowRedirects(followRedirects);
        return con;
    }

    @NonNull
    default String createFilename(@Nullable final String bookId,
                                  @IntRange(from = 0) final int cIdx,
                                  @Nullable final ImageFileInfo.Size size) {
        final SearchEngineRegistry.Config config = SearchEngineRegistry.getByEngineId(getId());
        //noinspection ConstantConditions
        return ImageUtils.createFilename(config.getFilenameSuffix(), bookId, cIdx, size);
    }

    /**
     * Convenience method to save an image using the engines specific network configuration.
     *
     * @param url      Image file URL
     * @param filename to use
     *
     * @return Downloaded fileSpec, or {@code null} on failure
     */
    @WorkerThread
    @Nullable
    default String saveImage(@NonNull final String url,
                             @NonNull final String filename) {
        final SearchEngineRegistry.Config config = SearchEngineRegistry.getByEngineId(getId());

        //noinspection ConstantConditions
        return ImageUtils.saveImage(getAppContext(), url, filename,
                                    config.getConnectTimeoutMs(),
                                    config.getReadTimeoutMs(),
                                    getThrottler());
    }

    /** Optional. */
    interface ByExternalId
            extends SearchEngine {

        /**
         * Create a url to search the website with the external id.
         *
         * @param externalId to search for
         *
         * @return url
         */
        @AnyThread
        @NonNull
        String createUrl(@NonNull String externalId);

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param externalId     the external id (as a String) for this particular search site.
         * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
         *                       The array is guaranteed to have at least one element.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException for sites which require credentials
         * @throws IOException          on other failures
         */
        @WorkerThread
        @NonNull
        Bundle searchByExternalId(@NonNull String externalId,
                                  @NonNull boolean[] fetchThumbnail)
                throws CredentialsException, IOException;
    }

    /** Optional. Every engine should really implement this. */
    interface ByIsbn
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param validIsbn      to search for, <strong>will</strong> be valid.
         * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
         *                       The array is guaranteed to have at least one element.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException for sites which require credentials
         * @throws IOException          on other failures
         */
        @WorkerThread
        @NonNull
        Bundle searchByIsbn(@NonNull String validIsbn,
                            @NonNull boolean[] fetchThumbnail)
                throws CredentialsException, IOException;

        /**
         * Indicates if ISBN code should be forced down to ISBN10 (if possible) before a search.
         * <p>
         * By default, we search on the ISBN entered by the user.
         * A preference setting per site can override this.
         * If set, and an ISBN13 is passed in, it will be translated to an ISBN10 before starting
         * the search.
         * <p>
         * This default implementation returns the global setting.
         *
         * @return {@code true} if ISBN10 should be preferred.
         */
        @AnyThread
        default boolean isPreferIsbn10() {
            return PreferenceManager.getDefaultSharedPreferences(getAppContext())
                                    .getBoolean(Prefs.pk_search_isbn_prefer_10, false);
        }
    }

    /**
     * Optional.
     * Implement if the engine can search generic bar codes, aside of strict ISBN only.
     */
    interface ByBarcode
            extends ByIsbn {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param barcode        to search for, <strong>will</strong> be valid.
         * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
         *                       The array is guaranteed to have at least one element.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException for sites which require credentials
         * @throws IOException          on other failures
         */
        @WorkerThread
        @NonNull
        Bundle searchByBarcode(@NonNull String barcode,
                               @NonNull boolean[] fetchThumbnail)
                throws CredentialsException, IOException;
    }

    /**
     * Optional.
     * The engine can search by author/title/... without a valid ISBN.
     */
    interface ByText
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         * <p>
         * The code parameter might or might not be valid.
         * Checking the arguments <strong>MUST</strong> be done inside the implementation,
         * as they generally will depend on what the engine can do with them.
         *
         * @param code           isbn, barcode or generic code to search for
         * @param author         to search for
         * @param title          to search for
         * @param publisher      optional and in addition to author/title.
         *                       i.e. author and/or title must be valid;
         *                       only then the publisher is taken into account.
         * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
         *                       The array is guaranteed to have at least one element.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException for sites which require credentials
         * @throws IOException          on other failures
         */
        @WorkerThread
        @NonNull
        Bundle search(@Nullable String code,
                      @Nullable String author,
                      @Nullable String title,
                      @Nullable String publisher,
                      @NonNull boolean[] fetchThumbnail)
                throws CredentialsException, IOException;
    }

    /** Optional. */
    interface CoverByIsbn
            extends SearchEngine {

        /**
         * Get a single cover image of the specified size.
         * <p>
         * <strong>Important</strong> this method should never throw any Exceptions.
         * Simply return {@code null} when an error occurs (but log the error).
         * <p>
         * See {@link #searchBestCoverImageByIsbn} for sites with
         * {@link #supportsMultipleCoverSizes()} == true
         *
         * @param validIsbn to search for, <strong>must</strong> be valid.
         * @param cIdx      0..n image index
         * @param size      of image to get.
         *
         * @return fileSpec, or {@code null} when none found (or any other failure)
         */
        @WorkerThread
        @Nullable
        String searchCoverImageByIsbn(@NonNull String validIsbn,
                                      @IntRange(from = 0) int cIdx,
                                      @Nullable ImageFileInfo.Size size);

        /**
         * A site can support a single (default) or multiple sizes.
         *
         * @return {@code true} if multiple sizes are supported.
         */
        @AnyThread
        default boolean supportsMultipleCoverSizes() {
            //noinspection ConstantConditions
            return SearchEngineRegistry.getByEngineId(getId()).supportsMultipleCoverSizes();
        }

        /**
         * Helper method for sites which support multiple image sizes.
         * It's a wrapper around {@link #searchCoverImageByIsbn} which
         * will try to get an image in order of large, medium, small.
         * i.e. the 'best' image being the largest we can find.
         *
         * @param validIsbn to search for, <strong>must</strong> be valid.
         * @param cIdx      0..n image index
         *
         * @return ArrayList with a single fileSpec (This is for convenience, as the result
         * is meant to be stored into the book-data as a parcelable array;
         * and it allows extending to multiple images at a future time)
         */
        @WorkerThread
        @NonNull
        default ArrayList<String> searchBestCoverImageByIsbn(@NonNull final String validIsbn,
                                                             @IntRange(from = 0) final int cIdx) {

            final ArrayList<String> imageList = new ArrayList<>();
            String fileSpec = searchCoverImageByIsbn(validIsbn, cIdx, ImageFileInfo.Size.Large);
            if (supportsMultipleCoverSizes()) {
                if (fileSpec == null) {
                    fileSpec = searchCoverImageByIsbn(validIsbn, cIdx, ImageFileInfo.Size.Medium);
                    if (fileSpec == null) {
                        fileSpec = searchCoverImageByIsbn(validIsbn, cIdx,
                                                          ImageFileInfo.Size.Small);
                    }
                }
            }
            if (fileSpec != null) {
                imageList.add(fileSpec);
            }
            return imageList;
        }

        /**
         * Helper method you can call from {@link #searchCoverImageByIsbn} if needed.
         * See the Goodreads SearchEngine for an example.
         * <p>
         * A search for the book is done, with the 'fetchThumbnail' flag set to true.
         * Any {@link IOException} or {@link CredentialsException}
         * thrown are ignored and {@code null} returned instead.
         * <p>
         * Do NOT use if the site does not support returning images during searches.
         *
         * @param isbn to search for
         * @param cIdx 0..n image index
         *
         * @return fileSpec, or {@code null} when none found (or any other failure)
         */
        @SuppressWarnings("unused")
        @WorkerThread
        @Nullable
        default String searchCoverImageByIsbnFallback(@NonNull final String isbn,
                                                      @IntRange(from = 0) final int cIdx) {

            final boolean[] fetchThumbnail = new boolean[2];
            fetchThumbnail[cIdx] = true;

            try {
                final Bundle bookData;
                // If we have a valid ISBN, and the engine supports it, search.
                if (ISBN.isValidIsbn(isbn)
                    && this instanceof SearchEngine.ByIsbn) {
                    bookData = ((SearchEngine.ByIsbn) this)
                            .searchByIsbn(isbn, fetchThumbnail);

                    // If we have a generic barcode, ...
                } else if (ISBN.isValid(isbn)
                           && this instanceof SearchEngine.ByBarcode) {
                    bookData = ((SearchEngine.ByBarcode) this)
                            .searchByBarcode(isbn, fetchThumbnail);

                    // otherwise we pass a non-empty (but invalid) code on regardless.
                    // if the engine supports text mode.
                } else if (!isbn.isEmpty()
                           && this instanceof SearchEngine.ByText) {
                    bookData = ((SearchEngine.ByText) this)
                            .search(isbn, "", "", "", fetchThumbnail);

                } else {
                    // don't throw here; this is a fallback method allowed to fail.
                    return null;
                }

                final ArrayList<String> imageList =
                        bookData.getStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[cIdx]);
                if (imageList != null && !imageList.isEmpty()) {
                    final File downloadedFile = new File(imageList.get(0));
                    // let the system resolve any path variations
                    final File destination = new File(downloadedFile.getAbsolutePath());
                    FileUtils.rename(downloadedFile, destination);
                    return destination.getAbsolutePath();
                }

            } catch (@NonNull final CredentialsException | IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "getCoverImageFallback", e);
                }
            }

            return null;
        }
    }

    /** Optional. */
    interface AlternativeEditions {

        /**
         * Find alternative editions (their ISBN) for the given ISBN.
         *
         * @param validIsbn to search for, <strong>must</strong> be valid.
         *
         * @return a list of isbn's of alternative editions of our original isbn, can be empty.
         *
         * @throws CredentialsException for sites which require credentials
         * @throws IOException          on other failures
         */
        @WorkerThread
        @NonNull
        List<String> searchAlternativeEditions(@NonNull String validIsbn)
                throws CredentialsException, IOException;
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Configuration {

        @SearchSites.EngineId int id();

        @StringRes int nameResId();

        @NonNull
        String prefKey();

        @NonNull
        String url();

        @NonNull
        String lang() default "en";

        @NonNull
        String country() default "";

        @NonNull
        String domainKey() default "";

        @IdRes int domainViewId() default 0;

        @IdRes int domainMenuId() default 0;

        int connectTimeoutMs() default 5_000;

        int readTimeoutMs() default 10_000;

        /** {@link CoverByIsbn} only. */
        boolean supportsMultipleCoverSizes() default false;

        /** file suffix for cover files. */
        @NonNull
        String filenameSuffix() default "";
    }
}
