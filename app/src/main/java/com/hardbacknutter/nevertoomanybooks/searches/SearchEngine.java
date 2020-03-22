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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.CoverBrowserDialogFragment;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * The interface a search engine for a {@link Site} needs to implement.
 * At least one of the sub-interfaces needs to be implemented:
 * <ul>
 * <li>{@link ByNativeId}</li>
 * <li>{@link ByIsbn}</li>
 * <li>{@link ByText}</li>
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
     * Default timeout we allow for a connection to work.
     * Initial tests show that the sites we use, connect in less than 200ms.
     * <p>
     * Override {@link #getConnectTimeoutMs} if you want a SearchEngine to be configurable.
     */
    int SOCKET_TIMEOUT_MS = 1500;

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
     * Get the root/website url.
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

    default int getConnectTimeoutMs() {
        return SOCKET_TIMEOUT_MS;
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
     * @param bookData to read/write data
     */
    default void checkForSeriesNameInTitle(@NonNull final Bundle bookData) {
        String fullTitle = bookData.getString(DBDefinitions.KEY_TITLE);
        if (fullTitle != null) {
            Matcher matcher = Series.TEXT1_BR_TEXT2_BR_PATTERN.matcher(fullTitle);
            if (matcher.find()) {
                // the cleaned title
                String bookTitle = matcher.group(1);
                // the series title/number
                String seriesTitleWithNumber = matcher.group(2);

                if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                    // we'll add to, or create the Series list
                    ArrayList<Series> seriesList =
                            bookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                    if (seriesList == null) {
                        seriesList = new ArrayList<>();
                    }

                    // add to the TOP of the list. This is based on translated books/comics
                    // on Goodreads where the Series is in the original language, but the
                    // Series name embedded in the title is in the same language as the title.
                    seriesList.add(0, Series.fromString(seriesTitleWithNumber));

                    // store Series back
                    bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, seriesList);
                    // and store cleaned book title back
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
         * @param context        Localised application context
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
                throws CredentialsException, IOException;
    }

    /** Optional. */
    interface ByIsbn
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context        Localised application context
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
                throws CredentialsException, IOException;
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
         * @param context        Localised application context
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
                throws CredentialsException, IOException {
            return searchByIsbn(context, barcode, fetchThumbnail);
        }
    }

    /** Optional. */
    interface ByText
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         * <p>
         * The code might or might not be valid.
         * Checking the arguments <strong>MUST</strong> be done inside the implementation,
         * as they generally will depend on what the engine can do with them.
         *
         * @param context        Localised application context
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
                throws CredentialsException, IOException;
    }

    /** Optional. */
    interface CoverByIsbn
            extends SearchEngine {

        /**
         * A site can support a single (default) or multiple sizes.
         *
         * @return {@code true} if multiple sizes are supported.
         */
        @AnyThread
        default boolean supportsMultipleSizes() {
            return false;
        }

        /**
         * Get a cover image of the specified size.
         * <br><br>
         * <strong>Override</strong> this method if the site support a specific/faster api.
         *
         * @param context Application context
         * @param isbn    to search for, <strong>must</strong> be valid.
         * @param cIdx    0..n image index
         * @param size    of image to get.
         *
         * @return fileSpec, or {@code null} when none found (or any other failure)
         */
        @Nullable
        @WorkerThread
        default String getCoverImage(@NonNull final Context context,
                                     @NonNull final String isbn,
                                     final int cIdx,
                                     @Nullable final ImageSize size) {
            return getCoverImageFallback(context, isbn, cIdx);
        }

        /**
         * <strong>DO NOT OVERRIDE</strong>
         * <br><br>
         * Get a cover image. Will try in order of large, medium, small depending on the site
         * supporting multiple sizes.
         *
         * @param appContext Application context
         * @param isbn       to search for, <strong>must</strong> be valid.
         * @param cIdx       0..n image index
         * @param bookData   Bundle to populate
         */
        @WorkerThread
        default void getCoverImage(@NonNull final Context appContext,
                                   @NonNull final String isbn,
                                   final int cIdx,
                                   @NonNull final Bundle bookData) {

            String fileSpec = getCoverImage(appContext, isbn, cIdx, ImageSize.Large);
            if (supportsMultipleSizes()) {
                if (fileSpec == null) {
                    fileSpec = getCoverImage(appContext, isbn, cIdx, ImageSize.Medium);
                    if (fileSpec == null) {
                        fileSpec = getCoverImage(appContext, isbn, cIdx, ImageSize.Small);
                    }
                }
            }
            if (fileSpec != null) {
                ArrayList<String> imageList =
                        bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
                if (imageList == null) {
                    imageList = new ArrayList<>();
                }
                imageList.add(fileSpec);
                bookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
            }
        }

        /**
         * <strong>DO NOT OVERRIDE</strong>
         * <br><br>
         * There is normally no need to call this method directly, except when you
         * override {@link #getCoverImage(Context, String, int, ImageSize)}.
         * <br><br>
         * Do NOT use if the site either does not support returning images during search.
         * <br><br>
         * A search for the book is done, with the 'fetchThumbnail' flag set to true.
         * Any {@link IOException} or {@link CredentialsException} thrown are ignored and
         * {@code null} returned.
         *
         * @param appContext Application context
         * @param isbnStr    to search for, <strong>must</strong> be valid.
         * @param cIdx       0..n image index
         *
         * @return fileSpec, or {@code null} when none found (or any other failure)
         */
        @Nullable
        @WorkerThread
        default String getCoverImageFallback(@NonNull final Context appContext,
                                             @NonNull final String isbnStr,
                                             final int cIdx) {

            boolean[] fetchThumbnail = new boolean[2];
            fetchThumbnail[cIdx] = true;

            try {
                Bundle bookData;
                // If we have a valid ISBN, and the engine supports it, search.
                if (ISBN.isValidIsbn(isbnStr) && this instanceof SearchEngine.ByIsbn) {
                    bookData = ((SearchEngine.ByIsbn) this)
                            .searchByIsbn(appContext, isbnStr, fetchThumbnail);

                    // If we have a generic barcode, ...
                } else if (ISBN.isValid(isbnStr) && this instanceof SearchEngine.ByBarcode) {
                    bookData = ((SearchEngine.ByBarcode) this)
                            .searchByBarcode(appContext, isbnStr, fetchThumbnail);

                    // otherwise we pass a non-empty (but invalid) code on regardless.
                    // if the engine supports text mode.
                } else if (!isbnStr.isEmpty() && this instanceof SearchEngine.ByText) {
                    bookData = ((SearchEngine.ByText) this)
                            .search(appContext, isbnStr, "", "", "", fetchThumbnail);

                } else {
                    // don't throw here; this is a fallback method allowed to fail.
                    return null;
                }

                ArrayList<String> imageList =
                        bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
                if (imageList != null && !imageList.isEmpty()) {
                    // This is from a single site, no need to get the 'best'.
                    // Just use the first one found.
                    File downloadedFile = new File(imageList.get(0));
                    // let the system resolve any path variations
                    File destination = new File(downloadedFile.getAbsolutePath());
                    FileUtils.rename(downloadedFile, destination);
                    return destination.getAbsolutePath();
                }

            } catch (@NonNull final CredentialsException | IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "", e);
                }
            }

            return null;
        }

        /**
         * Sizes of thumbnails.
         * These are open to interpretation (or not used at all) by individual {@link SearchEngine}.
         */
        enum ImageSize {
            Large, Medium, Small
        }
    }

    /**
     * Optional.
     * ENHANCE: implement for multiple sites (e.g. WorldCat)
     */
    interface AlternativeEditions {

        /**
         * Find alternative editions (their ISBN) for the given ISBN.
         *
         * <strong>Note:</strong> all exceptions will be ignored.
         *
         * @param appContext Application context
         * @param isbn       to search for, <strong>must</strong> be valid.
         *
         * @return a list of isbn's of alternative editions of our original isbn, can be empty.
         */
        @WorkerThread
        @NonNull
        List<String> getAlternativeEditions(@NonNull Context appContext,
                                            @NonNull String isbn);
    }
}
