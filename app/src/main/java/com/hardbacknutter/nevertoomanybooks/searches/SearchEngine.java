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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.covers.CoverBrowserDialogFragment;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.FormattedMessageException;

/**
 * The interface a search engine for a {@link Site} needs to implement.
 * At least one of the sub-interfaces needs to be implemented:
 * <ul>
 *      <li>{@link ByNativeId}</li>
 *      <li>{@link ByIsbn}</li>
 *      <li>{@link ByText}</li>
 * </ul>
 * and if the site supports fetching images by ISBN: {@link CoverByIsbn}.
 * The latter is used to get covers for alternative editions in {@link CoverBrowserDialogFragment}.
 * <p>
 * ENHANCE: it seems most implementations can return multiple book bundles quite easily.
 */
public interface SearchEngine {

    /** Log tag. */
    String TAG = "SearchEngine";

    /**
     * Get the resource id for the human-readable name of the site.
     * Only to be used for displaying!
     *
     * @return the resource id of the name
     */
    @AnyThread
    @StringRes
    int getNameResId();

    /**
     * Get the root/website url.
     * This will be used e.g. for checking if the site is up.
     *
     * @param context Current context
     *
     * @return url, including scheme.
     */
    @AnyThread
    @NonNull
    String getUrl(@NonNull Context context);

    /**
     * Get the current/standard Locale for this engine.
     *
     * @param context Current context
     *
     * @return site locale
     */
    @NonNull
    Locale getLocale(@NonNull Context context);

    /**
     * Default timeout we allow for a connection to work.
     * <p>
     * Tests with WiFi 5GHz indoor, optimal placement, show that the sites we use,
     * connect in less than 200ms.
     * <p>
     * TODO: use different defaults depending on network capabilities?
     *
     * @return default of 5 second. Override as needed.
     */
    default int getConnectTimeoutMs() {
        return 5_000;
    }

    /**
     * Default timeout we allow for a response to a request.
     * <p>
     * TODO: use different defaults depending on network capabilities?
     *
     * @return default 10 second. Override as needed.
     */
    default int getReadTimeoutMs() {
        return 10_000;
    }

    /**
     * Get the default throttler for regulating network access.
     *
     * @return {@code null} no Throttler by default. Override as needed.
     */
    @Nullable
    default Throttler getThrottler() {
        return null;
    }

    /**
     * Get the human-readable name of the site.
     * Only used for displaying to the user.
     *
     * @param context Current context
     *
     * @return name of this engine
     */
    @AnyThread
    @NonNull
    default String getName(@NonNull final Context context) {
        return context.getString(getNameResId());
    }

    /**
     * Generic test to be implemented by individual site search managers to check if
     * this site can be consider for searching.
     * <p>
     * Implementations can for example check for developer keys, ...
     * It's not supposed to to check credentials.
     * <strong>Should not run network code.</strong>
     *
     * @param context Current context
     *
     * @return {@code true} if we can consider this site for searching.
     */
    @AnyThread
    default boolean isAvailable(@NonNull final Context context) {
        return true;
    }

    /**
     * Reset the engine, ready for a new search.
     * This is called by {@link Site#getSearchEngine()}.
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
     * @param context    Current context
     * @param required   {@code true} if we <strong>must</strong> have access to the site.
     *                   {@code false} if it would be beneficial but not mandatory.
     * @param prefSuffix String used to flag in preferences if we showed the alert from
     *                   that caller already or not yet.
     *
     * @return {@code true} if an alert is currently shown
     */
    @UiThread
    default boolean promptToRegister(@NonNull final Context context,
                                     final boolean required,
                                     @NonNull final String prefSuffix) {
        return false;
    }

    /**
     * Look for a book title; if present try to get a Series from it and clean the book title.
     * <p>
     * This default implementation is fine for most engines, but override when needed.
     *
     * @param bookData Bundle to update
     */
    default void checkForSeriesNameInTitle(@NonNull final Bundle bookData) {
        final String fullTitle = bookData.getString(DBDefinitions.KEY_TITLE);
        if (fullTitle != null) {
            final Matcher matcher = Series.TEXT1_BR_TEXT2_BR_PATTERN.matcher(fullTitle);
            if (matcher.find()) {
                // the cleansed title
                final String bookTitle = matcher.group(1);
                // the series title/number
                final String seriesTitleWithNumber = matcher.group(2);

                if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                    // we'll add to, or create the Series list
                    ArrayList<Series> seriesList =
                            bookData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
                    if (seriesList == null) {
                        seriesList = new ArrayList<>();
                    }

                    // add to the TOP of the list. This is based on translated books/comics
                    // on Goodreads where the Series is in the original language, but the
                    // Series name embedded in the title is in the same language as the title.
                    seriesList.add(0, Series.from(seriesTitleWithNumber));

                    // store Series back
                    bookData.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, seriesList);
                    // and store cleansed book title back
                    bookData.putString(DBDefinitions.KEY_TITLE, bookTitle);
                }
            }
        }
    }

    /** Optional. */
    interface ByNativeId
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context        Current context
         * @param nativeId       the native id (as a String) for this particular search site.
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
        Bundle searchByNativeId(@NonNull Context context,
                                @NonNull String nativeId,
                                @NonNull boolean[] fetchThumbnail)
                throws CredentialsException, IOException, SearchException;


        /**
         * By default we assume the native id is {@code long}.
         * Override this method, and return {@code true} if this engine uses
         * {@code String} based identifiers.
         *
         * @return {code false} for numeric ID's, {@code true} for string ID's
         */
        default boolean hasStringId() {
            return false;
        }
    }

    /** Optional. */
    interface ByIsbn
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context        Current context
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
        Bundle searchByIsbn(@NonNull Context context,
                            @NonNull String validIsbn,
                            @NonNull boolean[] fetchThumbnail)
                throws CredentialsException, IOException, SearchException;

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
         * @param context Current context
         *
         * @return {@code true} if ISBN10 should be preferred.
         */
        default boolean isPreferIsbn10(@NonNull final Context context) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return prefs.getBoolean(Prefs.pk_search_isbn_prefer_10, false);
        }
    }

    /**
     * Optional.
     * The engine can search generic bar codes, aside of strict ISBN only.
     * <p>
     * The default implementation provided uses the {@link #searchByIsbn}.
     * Override when needed.
     */
    interface ByBarcode
            extends ByIsbn {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context        Current context
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
        default Bundle searchByBarcode(@NonNull Context context,
                                       @NonNull String barcode,
                                       @NonNull boolean[] fetchThumbnail)
                throws CredentialsException, IOException, SearchException {
            return searchByIsbn(context, barcode, fetchThumbnail);
        }
    }

    /** Optional. */
    interface ByText
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         * <p>
         * The code parameter might or might not be valid.
         * Checking the arguments <strong>MUST</strong> be done inside the implementation,
         * as they generally will depend on what the engine can do with them.
         *
         * @param context        Current context
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
        Bundle search(@NonNull Context context,
                      @Nullable String code,
                      @Nullable String author,
                      @Nullable String title,
                      @Nullable String publisher,
                      @NonNull boolean[] fetchThumbnail)
                throws CredentialsException, IOException, SearchException;
    }

    /**
     * Optional.
     * <p>
     * ENHANCE: CoverByIsbn Return a struct with file name AND size.
     * ENHANCE: if a site only supports 1 image size, it should be assumed to be Large.
     */
    interface CoverByIsbn
            extends SearchEngine {

        /**
         * Helper method.
         * Get the resolved file path for the first cover file found (for the given index).
         *
         * @param bookData with file-spec data
         * @param cIdx     0..n image index
         *
         * @return resolved path, or {@code null} if none found
         */
        @Nullable
        static String getFirstCoverFileFoundPath(@NonNull final Bundle bookData,
                                                 @IntRange(from = 0) final int cIdx) {
            final ArrayList<String> imageList =
                    bookData.getStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[cIdx]);
            if (imageList != null && !imageList.isEmpty()) {
                File downloadedFile = new File(imageList.get(0));
                // let the system resolve any path variations
                File destination = new File(downloadedFile.getAbsolutePath());
                FileUtils.rename(downloadedFile, destination);
                return destination.getAbsolutePath();
            }
            return null;
        }

        /**
         * Helper method.
         * There is normally no need to call this method directly, except when you
         * override {@link #searchCoverImageByIsbn}.
         * <br><br>
         * Do NOT use if the site either does not support returning images during search.
         * <br><br>
         * A search for the book is done, with the 'fetchThumbnail' flag set to true.
         * Any {@link IOException} or {@link CredentialsException} thrown are ignored and
         * {@code null} returned.
         *
         * @param context Current context
         * @param isbnStr to search for, <strong>must</strong> be valid.
         * @param cIdx    0..n image index
         *
         * @return fileSpec, or {@code null} when none found (or any other failure)
         */
        @Nullable
        @WorkerThread
        static String getCoverImageFallback(@NonNull final Context context,
                                            @NonNull final SearchEngine searchEngine,
                                            @NonNull final String isbnStr,
                                            @IntRange(from = 0) final int cIdx) {

            final boolean[] fetchThumbnail = new boolean[2];
            fetchThumbnail[cIdx] = true;

            try {
                final Bundle bookData;
                // If we have a valid ISBN, and the engine supports it, search.
                if (ISBN.isValidIsbn(isbnStr)
                    && searchEngine instanceof SearchEngine.ByIsbn) {
                    bookData = ((SearchEngine.ByIsbn) searchEngine)
                            .searchByIsbn(context, isbnStr, fetchThumbnail);

                    // If we have a generic barcode, ...
                } else if (ISBN.isValid(isbnStr)
                           && searchEngine instanceof SearchEngine.ByBarcode) {
                    bookData = ((SearchEngine.ByBarcode) searchEngine)
                            .searchByBarcode(context, isbnStr, fetchThumbnail);

                    // otherwise we pass a non-empty (but invalid) code on regardless.
                    // if the engine supports text mode.
                } else if (!isbnStr.isEmpty()
                           && searchEngine instanceof SearchEngine.ByText) {
                    bookData = ((SearchEngine.ByText) searchEngine)
                            .search(context, isbnStr, "", "", "", fetchThumbnail);

                } else {
                    // don't throw here; this is a fallback method allowed to fail.
                    return null;
                }

                return getFirstCoverFileFoundPath(bookData, cIdx);

            } catch (@NonNull final CredentialsException | IOException | SearchException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "getCoverImageFallback", e);
                }
            }

            return null;
        }

        /**
         * Helper method. A wrapper around {@link #searchCoverImageByIsbn}.
         * Will try in order of large, medium, small depending on the site
         * supporting multiple sizes.
         *
         * @param context  Current context
         * @param isbn     to search for, <strong>must</strong> be valid.
         * @param cIdx     0..n image index
         * @param bookData Bundle to update
         */
        @WorkerThread
        static void getCoverImage(@NonNull final Context context,
                                  @NonNull final SearchEngine.CoverByIsbn searchEngine,
                                  @NonNull final String isbn,
                                  @IntRange(from = 0) final int cIdx,
                                  @NonNull final Bundle bookData) {

            String fileSpec = searchEngine.searchCoverImageByIsbn(context,
                                                                  isbn, cIdx,
                                                                  ImageFileInfo.Size.Large);
            if (searchEngine.supportsMultipleSizes()) {
                if (fileSpec == null) {
                    fileSpec = searchEngine
                            .searchCoverImageByIsbn(context, isbn, cIdx,
                                                    ImageFileInfo.Size.Medium);
                    if (fileSpec == null) {
                        fileSpec = searchEngine
                                .searchCoverImageByIsbn(context, isbn, cIdx,
                                                        ImageFileInfo.Size.Small);
                    }
                }
            }
            if (fileSpec != null) {
                ArrayList<String> imageList =
                        bookData.getStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[cIdx]);
                if (imageList == null) {
                    imageList = new ArrayList<>();
                }
                imageList.add(fileSpec);
                bookData.putStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[cIdx], imageList);
            }
        }

        /**
         * Get a single cover image of the specified size.
         * <br><br>
         * <strong>Override</strong> this method if the site support a specific/faster api.
         *
         * @param context   Application context
         * @param validIsbn to search for, <strong>must</strong> be valid.
         * @param cIdx      0..n image index
         * @param size      of image to get.
         *
         * @return fileSpec, or {@code null} when none found (or any other failure)
         */
        @Nullable
        @WorkerThread
        String searchCoverImageByIsbn(@NonNull Context context,
                                      @NonNull String validIsbn,
                                      @IntRange(from = 0) int cIdx,
                                      @Nullable ImageFileInfo.Size size);

        /**
         * A site can support a single (default) or multiple sizes.
         *
         * @return {@code true} if multiple sizes are supported.
         */
        @AnyThread
        default boolean supportsMultipleSizes() {
            return false;
        }
    }

    /** Optional. */
    interface AlternativeEditions {

        /**
         * Find alternative editions (their ISBN) for the given ISBN.
         *
         * <strong>Note:</strong> all exceptions will be ignored.
         *
         * @param context Current context
         * @param isbn    to search for, <strong>must</strong> be valid.
         *
         * @return a list of isbn's of alternative editions of our original isbn, can be empty.
         */
        @WorkerThread
        @NonNull
        List<String> getAlternativeEditions(@NonNull Context context,
                                            @NonNull String isbn);
    }

    /**
     * If possible, an engine should mask a recognised exception with this
     * user friendly SearchException with a message suited for displaying to the user .
     */
    class SearchException
            extends FormattedMessageException {

        private static final long serialVersionUID = 915010765930247859L;

        public SearchException(@StringRes final int stringId,
                               @Nullable final Object... args) {
            super(stringId, args);
        }
    }
}
