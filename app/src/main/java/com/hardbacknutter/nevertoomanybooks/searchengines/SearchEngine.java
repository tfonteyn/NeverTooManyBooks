/*
 * @Copyright 2018-2025 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

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
        /**
         * Search by a <strong>LOCALLY STORED</strong> external/website id.
         * See the various {@code DBKey#SID_*} keys.
         */
        ExternalId(ByExternalId.class),
        /**
         * Search with a <strong>VALID</strong> ISBN number.
         */
        Isbn(ByIsbn.class),
        /**
         * Search with an <strong>INVALID</strong> ISBN number or actual barcode.
         * i.e. a code which is specifically supported by the site.
         */
        Barcode(ByBarcode.class),
        /**
         * Generic text search using (at least) title/author fields.
         */
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
     * Search by a <strong>LOCALLY STORED</strong> external/website id.
     * See the various {@code DBKey#SID_*} keys.
     *
     * @see SearchBy#ExternalId
     */
    interface ByExternalId
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         * <p>
         * If applicable, {@link Login} will be called upon before this method is called.
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
    @Deprecated(forRemoval = true)
    interface ViewBookByExternalId
            extends SearchEngine {

        /**
         * Create a url to open a book on the website with the external id.
         * <p>
         * {@link SearchEngine.Login} will NOT be called upon.
         * <p>
         * ViewBookByExternalId functionality should be moved to the EngineId class
         *  This will eliminate having to create a SearchEngine instance just to open
         *  a browser link. See {@link ViewBookOnSiteMenuHandler#onMenuItemSelected}.
         *
         * @param context    Current context
         * @param externalId to open
         *
         * @return url
         */
        @Deprecated
        @AnyThread
        @NonNull
        String createViewOnSiteUrl(@NonNull Context context,
                                   @NonNull String externalId);
    }

    /** Optional. */
    interface SearchOnSite
            extends SearchEngine {

        /**
         * Should the menu be visible.
         *
         * @param context Current context
         *
         * @return {@code true} to show
         */
        boolean isShowSearchOnSiteMenu(@NonNull Context context);

        /**
         * Create a url to search on the website with Author/Series.
         * At least one of the Author/Series parameters must not be {@code null}.
         * <p>
         * {@link SearchEngine.Login} will NOT be called upon.
         *
         * @param context Current context
         * @param author  to search for
         * @param series  to search for
         *
         * @return url
         *
         * @throws IllegalArgumentException if both Author and Series are {@code null}
         */
        @AnyThread
        @NonNull
        String createSearchOnSiteUrl(@NonNull Context context,
                                     @Nullable Author author,
                                     @Nullable Series series)
                throws IllegalArgumentException;
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
         * <p>
         * If applicable, {@link Login} will be called upon before this method is called.
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
     * Implement if the engine can search generic bar codes,
     * or is known to store/support invalid ISBN numbers.
     * <p>
     * <strong>IMPORTANT</strong>: only use the default implementation
     * if the engine's implementation of {@link ByIsbn} supports searching for non-valid
     * ISBN codes as generic codes!
     * Otherwise {@link #searchByBarcode(Context, String, boolean[])} <strong>MUST</strong>
     * be properly implemented.
     *
     * @see SearchBy#Barcode
     */
    interface ByBarcode
            extends ByIsbn {

        /**
         * Called by the {@link SearchCoordinator#search}.
         * <p>
         * The default implementation redirect to
         * {@link ByIsbn#searchByIsbn(Context, String, boolean[])}
         * <p>
         * If applicable, {@link Login} will be called upon before this method is called.
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
        default Book searchByBarcode(@NonNull final Context context,
                                     @NonNull final String barcode,
                                     @NonNull final boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException {
            return searchByIsbn(context, barcode, fetchCovers);
        }
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
         * Checking the criteria <strong>MUST</strong> be done inside the implementation,
         * as they generally will depend on what the engine can do with them.
         * <p>
         * The engine can simply return an empty {@link Book} if it deems
         * the criteria not usable. It <strong>MUST NOT</strong> throw in such a situation.
         * <p>
         * If applicable, {@link Login} will be called upon before this method is called.
         *
         * @param context     Current context
         * @param criteria    text strings to search for
         * @param code        isbn, barcode or generic code to search for.
         *                    The interpretation depends on the engine.
         *                    Optional / not supported by all engines
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
                    @NonNull SearchCoordinatorCriteria criteria,
                    @Nullable String code,
                    @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;
    }

    interface Login
            extends SearchEngine {

        /**
         * Check whether the user should be logged in to the website
         * before starting a <strong>search</strong>.
         *
         * @param context Current context
         *
         * @return {@code true} if we should perform a login
         */
        boolean isLoginToSearch(@NonNull Context context);

        /**
         * For use by synchronization if implemented to avoid multiple logins.
         *
         * @param authModule to use
         */
        void setAuthModule(@NonNull SiteAuthModule authModule);

        /**
         * Request a login. This is an <strong>attempt</strong>.
         * Failing or disregarding this request is an actual error.
         *
         * @param context Current context
         *
         * @throws CredentialsException on authentication/login failures
         * @throws SearchException      on generic exceptions (wrapped) during search
         */
        void login(@NonNull Context context)
                throws CredentialsException, SearchException;
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
         * <p>
         * {@link Login} will NOT be called upon.
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
         * The {@link AltEdition} to be passed in will typically (always?) be coming from
         * {@link AlternativeEditions#searchAlternativeEditions(Context, String)}.
         * i.o.w.:
         * Engines which implement {@link AlternativeEditions} will collect a list of
         * potential {@link AltEdition}.
         * These will then be passed to engines which implement {@code CoverByEdition}
         * to fetch the covers if possible.
         * <p>
         * If the given {@link AltEdition} type is not supported, implementations of this method
         * <strong>MUST</strong> return {@code Optional.empty()}.
         * <p>
         * If applicable, {@link SearchEngine.Login} will be called upon
         * before this method is called.
         * <p>
         * <strong>Important</strong> this method should never throw any {@link RuntimeException}.
         * For the latter, simply return {@code Optional.empty()} when an error occurs
         * after logging the error.
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
    }
}
