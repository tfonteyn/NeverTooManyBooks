/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * The interface a search engine for an {@link EngineId} needs to implement.
 * <p>
 * More details in {@link EngineId}.
 * <p>
 * At least one of the sub-interfaces needs to be implemented:
 * <ul>
 *      <li>{@link ByExternalId}</li>
 *      <li>{@link ByIsbn}</li>
 *      <li>{@link ByText}</li>
 * </ul>
 * and if the site supports fetching images by {@link AltEdition}: {@link CoverByEdition}.
 * <p>
 * ENHANCE: most implementations can return multiple book bundles quite easily.
 * <p>
 * The searches can throw 3 Exceptions:
 * <ul>
 *     <li>{@link CredentialsException}: We cannot authenticate to the site,
 *                                       the user MUST take action on it NOW.</li>
 *     <li>{@link StorageException}:     Specific local storage issues,
 *                                       the user MUST take action on it NOW.</li>
 *     <li>{@link SearchException}:      The embedded Exception has the details,
 *                                       should be reported to the user,
 *                                       but action is optional.</li>
 * </ul>
 */
public interface SearchEngine
        extends Cancellable {

    /** Log tag. */
    String TAG = "SearchEngine";

    /**
     * Get the engine id.
     *
     * @return engine id
     */
    @NonNull
    EngineId getEngineId();

    /**
     * Get the name for this engine.
     *
     * @param context Current context
     *
     * @return name
     */
    @NonNull
    String getName(@NonNull Context context);

    /**
     * Get the host url.
     *
     * @param context Current context
     *
     * @return url, including scheme.
     */
    @AnyThread
    @NonNull
    String getHostUrl(@NonNull Context context);

    /**
     * Get the Locale for this engine.
     *
     * @param context Current context
     *
     * @return site locale
     */
    @AnyThread
    @NonNull
    Locale getLocale(@NonNull Context context);

    /**
     * {@link CoverByEdition} only.
     * <p>
     * A site can support a single (default) or multiple sizes.
     *
     * @return {@code true} if multiple sizes are supported.
     */
    boolean supportsMultipleCoverSizes();

    /**
     * Reset the engine, ready for a new search.
     */
    void reset();

    /**
     * Set the caller to allow <strong>PULL</strong> checks if we should cancel the search.
     * i.e. the engine will ask the caller at semi-regular intervals if it should quit.
     *
     * @param caller to check with
     */
    void setCaller(@Nullable Cancellable caller);

    /**
     * Ping the website for this SearchEngine.
     *
     * @param context Current context
     *
     * @throws UnknownHostException   the IP address of a host could not be determined.
     * @throws IOException            if we cannot reach the site
     * @throws SocketTimeoutException on timeouts (both DNS and host itself)
     * @throws MalformedURLException  if the URL does not start with {@code http} or {@code https}
     */
    @WorkerThread
    void ping(@NonNull Context context)
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException;

    /**
     * Maps the {@link Site.Type#Data} search interface classes to an enum.
     * <p>
     * This allows us to use them in for example a switch() statement
     * and other places where using the interface class itself is not possible.
     * <p>
     * Dev. note: this is really a kludge... there must be a better way of doing this...
     *
     * @see ByExternalId
     * @see ByIsbn
     * @see ByBarcode
     * @see ByText
     */
    enum SearchBy {
        ExternalId(ByExternalId.class),
        Isbn(ByIsbn.class),
        Barcode(ByBarcode.class),
        Text(ByText.class);

        @NonNull
        private final Class<? extends SearchEngine> clazz;

        SearchBy(@NonNull final Class<? extends SearchEngine> clazz) {
            this.clazz = clazz;
        }

        @NonNull
        Class<? extends SearchEngine> getSearchEngineClass() {
            return clazz;
        }
    }

    /**
     * Optional.
     *
     * @see SearchBy#ExternalId
     */
    interface ByExternalId
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context     Current context
         * @param externalId  the external id (as a String) for this particular search site.
         * @param fetchCovers Set to {@code true} if we want to get covers
         *                    The array is guaranteed to have 2 elements.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException on authentication/login failures
         * @throws StorageException     on storage related failures
         * @throws SearchException      on generic exceptions (wrapped) during search
         */
        @WorkerThread
        @NonNull
        Book searchByExternalId(@NonNull Context context,
                                @NonNull String externalId,
                                @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;
    }

    /** Optional. */
    interface ViewBookByExternalId
            extends SearchEngine {

        /**
         * Create a url to open a book on the website with the external id.
         *
         * @param context    Current context
         * @param externalId to open
         *
         * @return url
         */
        @AnyThread
        @NonNull
        String createBrowserUrl(@NonNull Context context,
                                @NonNull String externalId);
    }

    /**
     * Optional. But every engine should really implement this.
     *
     * @see SearchBy#Isbn
     */
    interface ByIsbn
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context     Current context
         * @param validIsbn   to search for, <strong>will</strong> be valid.
         * @param fetchCovers Set to {@code true} if we want to get covers
         *                    The array is guaranteed to have 2 elements.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException on authentication/login failures
         * @throws StorageException     on storage related failures
         * @throws SearchException      on generic exceptions (wrapped) during search
         */
        @WorkerThread
        @NonNull
        Book searchByIsbn(@NonNull Context context,
                          @NonNull String validIsbn,
                          @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;
    }

    /**
     * Optional.
     * Implement if the engine can search generic bar codes, aside of strict ISBN only.
     *
     * @see SearchBy#Barcode
     */
    interface ByBarcode
            extends ByIsbn {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context     Current context
         * @param barcode     to search for, <strong>will</strong> be valid.
         * @param fetchCovers Set to {@code true} if we want to get covers
         *                    The array is guaranteed to have 2 elements.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException on authentication/login failures
         * @throws StorageException     on storage related failures
         * @throws SearchException      on generic exceptions (wrapped) during search
         */
        @WorkerThread
        @NonNull
        Book searchByBarcode(@NonNull Context context,
                             @NonNull String barcode,
                             @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;
    }

    /**
     * Optional.
     * The engine can search by author/title/... without a valid ISBN.
     *
     * @see SearchBy#Text
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
         * @param context     Current context
         * @param code        isbn, barcode or generic code to search for
         * @param author      to search for
         * @param title       to search for
         * @param publisher   optional and in addition to author/title.
         *                    i.e. author and/or title must be valid;
         *                    only then the publisher is taken into account.
         * @param fetchCovers Set to {@code true} if we want to get covers
         *                    The array is guaranteed to have 2 elements.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         *
         * @throws CredentialsException on authentication/login failures
         * @throws StorageException     on storage related failures
         * @throws SearchException      on generic exceptions (wrapped) during search
         */
        @WorkerThread
        @NonNull
        Book search(@NonNull Context context,
                    @Nullable String code,
                    @Nullable String author,
                    @Nullable String title,
                    @Nullable String publisher,
                    @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;
    }

    /**
     * Optional.
     *
     * @param <T> SearchEngine specific implementations.
     */
    interface AlternativeEditions<T extends AltEdition>
            extends SearchEngine {

        /**
         * Find alternative editions for the given ISBN.
         *
         * @param context   Current context
         * @param validIsbn to search for, <strong>must</strong> be valid.
         *
         * @return a list of {@link T} alternative editions, can be empty.
         *
         * @throws CredentialsException on authentication/login failures
         * @throws SearchException      on generic exceptions (wrapped) during search
         */
        @WorkerThread
        @NonNull
        List<T> searchAlternativeEditions(@NonNull Context context,
                                          @NonNull String validIsbn)
                throws SearchException,
                       CredentialsException;
    }

    /** Optional. */
    interface CoverByEdition
            extends SearchEngine {

        /**
         * Get a single cover image of the specified size.
         * <p>
         * If the given {@link AltEdition} type is not supported, this method
         * <strong>MUST</strong> return {@code Optional.empty()}.
         * <p>
         * <strong>Important</strong> this method should never throw any {@link RuntimeException}.
         * For the latter, simply return {@code Optional.empty()} when an error occurs
         * after logging the error.
         * <p>
         * See {@link #searchBestCoverByEdition} for sites with support for multiple cover sizes.
         *
         * @param context Current context
         * @param edition to search for
         * @param cIdx    0..n image index
         * @param size    of image to get.
         *
         * @return fileSpec
         *
         * @throws CredentialsException on authentication/login failures
         * @throws StorageException     on storage related failures
         * @throws SearchException      on generic exceptions (wrapped) during search
         */
        @WorkerThread
        @NonNull
        Optional<String> searchCoverByEdition(@NonNull Context context,
                                              @NonNull AltEdition edition,
                                              @IntRange(from = 0, to = 1) int cIdx,
                                              @Nullable Size size)
                throws StorageException,
                       SearchException,
                       CredentialsException;

        /**
         * Helper method for sites which support multiple image sizes.
         * It's a wrapper around {@link #searchCoverByEdition} which
         * will try to get an image in order of large, medium, small.
         * i.e. the 'best' image being the largest we can find.
         *
         * @param context Current context
         * @param edition to search for
         * @param cIdx    0..n image index
         *
         * @return fileSpec
         *
         * @throws CredentialsException on authentication/login failures
         * @throws StorageException     on storage related failures
         * @throws SearchException      on generic exceptions (wrapped) during search
         */
        @WorkerThread
        @NonNull
        default Optional<String> searchBestCoverByEdition(
                @NonNull final Context context,
                @NonNull final AltEdition edition,
                @IntRange(from = 0, to = 1) final int cIdx)
                throws StorageException,
                       SearchException,
                       CredentialsException {

            Optional<String> oFileSpec = searchCoverByEdition(context, edition, cIdx, Size.Large);
            if (oFileSpec.isEmpty() && supportsMultipleCoverSizes()) {
                oFileSpec = searchCoverByEdition(context, edition, cIdx, Size.Medium);
                if (oFileSpec.isEmpty()) {
                    oFileSpec = searchCoverByEdition(context, edition, cIdx, Size.Small);
                }
            }
            return oFileSpec;
        }
    }
}
