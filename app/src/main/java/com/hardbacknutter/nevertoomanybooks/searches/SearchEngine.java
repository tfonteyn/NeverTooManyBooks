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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.CoverBrowserFragment;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * The interface a search engine for a {@link Site} needs to implement.
 * At least one of the sub-interfaces needs to be implemented:
 * <ul>
 * <li>{@link ByNativeId}</li>
 * <li>{@link ByIsbn}</li>
 * <li>{@link ByText}</li>
 * </ul>
 * and if the site supports fetching images by ISBN: {@link CoverByIsbn}.
 * The latter is used to get covers for alternative editions in {@link CoverBrowserFragment}.
 * <p>
 * ENHANCE: it seems most implementations can return multiple book bundles quite easily.
 */
public interface SearchEngine {

    String TAG = "SearchEngine";

    /**
     * Get the resource id for the human-readable name of the site.
     *
     * @return the resource id
     */
    @AnyThread
    @StringRes
    int getNameResId();

    /**
     * Get the root/website url.
     *
     * @param appContext Application context
     *
     * @return url, including scheme.
     */
    @AnyThread
    @NonNull
    String getUrl(@NonNull Context appContext);


    /**
     * Generic test to be implemented by individual site search managers to check if
     * this site can be consider for searching.
     * <p>
     * Implementations can for example check for developer keys, ...
     * It's not supposed to to check credentials.
     * <strong>Should not run network code.</strong>
     *
     * @param appContext Application context
     *
     * @return {@code true} if we can consider this site for searching.
     */
    @AnyThread
    default boolean isAvailable(@NonNull final Context appContext) {
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
         * Called by the {@link SearchCoordinator#searchByText}.
         *
         * @param localizedAppContext Localised application context
         * @param nativeId            the native id (as a String) for this particular search site.
         * @param fetchThumbnail      Set to {@code true} if we want to get a thumbnail
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException for sites which require credentials
         * @throws IOException          on other failures
         */
        @WorkerThread
        @NonNull
        Bundle searchByNativeId(@NonNull final Context localizedAppContext,
                                @NonNull final String nativeId,
                                final boolean fetchThumbnail)
                throws CredentialsException, IOException;
    }

    /** Optional. */
    interface ByIsbn
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#searchByText}.
         *
         * @param localizedAppContext Localised application context
         * @param isbn                to search for, <strong>will</strong> be valid
         *                            unless the engine implements {@link ByBarcode},
         *                            in that case it <strong>may</strong> be generic/invalid.
         * @param fetchThumbnail      Set to {@code true} if we want to get a thumbnail
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException for sites which require credentials
         * @throws IOException          on other failures
         */
        @WorkerThread
        @NonNull
        Bundle searchByIsbn(@NonNull final Context localizedAppContext,
                            @NonNull final String isbn,
                            final boolean fetchThumbnail)
                throws CredentialsException, IOException;
    }

    /**
     * Optional.
     * Marker interface to indicate the engine can search generic bar codes,
     * aside of strict ISBN only.
     */
    interface ByBarcode
            extends ByIsbn {

//        /**
//         * Called by the {@link SearchCoordinator#searchByText}.
//         * The barcode should be <strong>valid</strong>; i.e. it's checksum must be correct.
//         *
//         * @param localizedAppContext Localised application context
//         * @param barcode             to search for
//         * @param fetchThumbnail      Set to {@code true} if we want to get a thumbnail
//         *
//         * @return bundle with book data. Can be empty, but never {@code null}.
//         *
//         * @throws CredentialsException for sites which require credentials
//         * @throws IOException          on other failures
//         */
//        @WorkerThread
//        @NonNull
//        default Bundle searchByBarcode(@NonNull final Context localizedAppContext,
//                                       @NonNull final String barcode,
//                                       final boolean fetchThumbnail)
//                throws CredentialsException, IOException {
//
//            return searchByIsbn(localizedAppContext, barcode, fetchThumbnail);
//        }
    }

    /** Optional. */
    interface ByText
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#searchByText}.
         * The isbn is <strong>not tested</strong>.
         * <p>
         * Checking the arguments must be done inside the implementation,
         * as they generally will depend on what the engine can do with them.
         *
         * @param localizedAppContext Localised application context
         * @param isbn                to search for
         * @param author              to search for
         * @param title               to search for
         * @param publisher           optional and in addition to author/title.
         *                            i.e. author and/or title must be valid;
         *                            only then the publisher is taken into account.
         * @param fetchThumbnail      Set to {@code true} if we want to get a thumbnail
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException for sites which require credentials
         * @throws IOException          on other failures
         */
        @WorkerThread
        @NonNull
        Bundle search(@NonNull Context localizedAppContext,
                      @Nullable String isbn,
                      @Nullable String author,
                      @Nullable String title,
                      @Nullable String publisher,
                      boolean fetchThumbnail)
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
        default boolean hasMultipleSizes() {
            return false;
        }

        /**
         * Get a cover image.
         *
         * @param appContext Application context
         * @param isbn       to search for, <strong>must</strong> be valid.
         * @param size       of image to get.
         *
         * @return found/saved File, or {@code null} when none found (or any other failure)
         */
        @Nullable
        @WorkerThread
        default File getCoverImage(@NonNull Context appContext,
                                   @NonNull String isbn,
                                   @Nullable ImageSize size) {
            return getCoverImageFallback(appContext, isbn);
        }

        /**
         * Get a cover image. Will try in order of large, medium, small depending on the site
         * supporting multiple sizes.
         *
         * @param appContext Application context
         * @param isbn       to search for, <strong>must</strong> be valid.
         * @param bookData   bundle to populate with the image file spec
         */
        @WorkerThread
        default void getCoverImage(@NonNull final Context appContext,
                                   @NonNull final String isbn,
                                   @NonNull final Bundle bookData) {

            File file = getCoverImage(appContext, isbn, ImageSize.Large);
            if (hasMultipleSizes()) {
                if (file == null) {
                    file = getCoverImage(appContext, isbn, ImageSize.Medium);
                    if (file == null) {
                        file = getCoverImage(appContext, isbn, ImageSize.Small);
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
         * If an implementation does not support a specific (and faster) way/api
         * to fetch a cover image, then {@link #getCoverImage(Context, String, ImageSize)}
         * can call this fallback method.
         * Do NOT use if the site either does not support returning images during search,
         * or does not support isbn searches.
         * <p>
         * A search for the book is done, with the 'fetchThumbnail' flag set to true.
         * Any {@link IOException} or {@link CredentialsException} thrown are ignored and
         * {@code null} returned.
         *
         * @param appContext Application context
         * @param isbnStr       to search for, <strong>must</strong> be valid.
         *
         * @return found/saved File, or {@code null} when none found (or any other failure)
         */
        @Nullable
        @WorkerThread
        default File getCoverImageFallback(@NonNull final Context appContext,
                                           @NonNull final String isbnStr) {
            try {
                Bundle bookData;
                // If we have a valid ISBN, and the engine supports it, we can search.
                if (ISBN.isValidIsbn(isbnStr) && this instanceof SearchEngine.ByIsbn) {
                    bookData = ((SearchEngine.ByIsbn) this).searchByIsbn(appContext, isbnStr, true);

                    // If we have a generic barcode, ...
                } else if (!isbnStr.isEmpty() && this instanceof SearchEngine.ByBarcode) {
                    bookData = ((SearchEngine.ByIsbn) this).searchByIsbn(appContext, isbnStr, true);

                    // If we have valid text to search on, ...
                } else if (!isbnStr.isEmpty() && this instanceof SearchEngine.ByText) {
                    bookData = ((SearchEngine.ByText) this)
                            .search(appContext, isbnStr, "", "", "", true);
                } else {
                    // don't throw here; this is a fallback method allowed to fail.
                    return null;
                }

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
                Logger.error(appContext, TAG, e);
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
        ArrayList<String> getAlternativeEditions(@NonNull Context appContext,
                                                 @NonNull String isbn);
    }
}
