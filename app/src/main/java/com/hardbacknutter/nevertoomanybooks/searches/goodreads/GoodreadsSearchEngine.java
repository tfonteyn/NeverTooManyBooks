/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.goodreads;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.SearchBookApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShowBookByIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShowBookByIsbnApiHandler;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * <a href="https://www.goodreads.com">https://www.goodreads.com</a>
 */
public class GoodreadsSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ByText,
                   SearchEngine.CoverByIsbn {

    /** Can only send requests at a throttled speed. */
    @NonNull
    public static final Throttler THROTTLER = new Throttler();

    @NonNull
    private final GoodreadsAuth mGoodreadsAuth;

    @Nullable
    private ShowBookByIsbnApiHandler mByIsbnApi;
    @Nullable
    private ShowBookByIdApiHandler mByIdApi;
    @Nullable
    private SearchBookApiHandler mSearchApi;


    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public GoodreadsSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
        mGoodreadsAuth = new GoodreadsAuth();
    }

    public static SearchEngineConfig createConfig() {
        return new SearchEngineConfig.Builder(GoodreadsSearchEngine.class,
                                              SearchSites.GOODREADS,
                                              R.string.site_goodreads,
                                              GoodreadsManager.PREF_KEY,
                                              GoodreadsManager.BASE_URL)
                .setFilenameSuffix(GoodreadsManager.FILENAME_SUFFIX)

                .setDomainKey(DBKeys.KEY_ESID_GOODREADS_BOOK)
                .setDomainViewId(R.id.site_goodreads)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_GOODREADS)

                .setConnectTimeoutMs(GoodreadsManager.CONNECTION_TIMEOUT_MS)
                .setReadTimeoutMs(GoodreadsManager.READ_TIMEOUT_MS)
                .build();
    }

    @NonNull
    @Override
    public String createUrl(@NonNull final String externalId) {
        return getSiteUrl() + "/book/show/" + externalId;
    }

    @Override
    public boolean isAvailable() {
        // makes sure we *have* credentials, but does not check them.
        return !mGoodreadsAuth.getDevKey().isEmpty()
               && mGoodreadsAuth.hasCredentials();
    }

    @Nullable
    @Override
    public Throttler getThrottler() {
        return THROTTLER;
    }


    @Override
    public boolean promptToRegister(@NonNull final Context context,
                                    final boolean required,
                                    @Nullable final String callerIdString,
                                    @Nullable final Consumer<RegistrationAction> onResult) {
        // sanity check
        if (isAvailable()) {
            return false;
        }

        return showRegistrationDialog(context, required, callerIdString, action -> {
            if (action == RegistrationAction.Register) {
                context.startActivity(new Intent(context, GoodreadsRegistrationActivity.class));

            } else if (onResult != null) {
                onResult.accept(action);
            }
        });
    }

    @NonNull
    @Override
    public Bundle searchByExternalId(@NonNull final String externalId,
                                     @NonNull final boolean[] fetchThumbnail)
            throws GeneralParsingException, IOException {

        final Bundle bookData = new Bundle();

        try {
            if (mByIdApi == null) {
                mByIdApi = new ShowBookByIdApiHandler(getContext(), mGoodreadsAuth);
            }
            final long grBookId = Long.parseLong(externalId);
            return mByIdApi.searchByExternalId(grBookId, fetchThumbnail, bookData);

        } catch (@NonNull final HttpNotFoundException | NumberFormatException e) {
            // ignore
        }
        return bookData;
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws GeneralParsingException, IOException {

        final Bundle bookData = new Bundle();

        try {
            if (mByIsbnApi == null) {
                mByIsbnApi = new ShowBookByIsbnApiHandler(getContext(), mGoodreadsAuth);
            }
            return mByIsbnApi.searchByIsbn(validIsbn, fetchThumbnail, bookData);

        } catch (@NonNull final HttpNotFoundException ignore) {
            // ignore
        }
        return bookData;
    }

    /**
     * Search for a book.
     * <p>
     * The search will in fact search/find a list of 'works' but we only return the first one.
     *
     * @return first book found, or an empty bundle if none found.
     */
    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@Nullable final /* not supported */ String code,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         @NonNull final boolean[] fetchThumbnail)
            throws GeneralParsingException, IOException {

        final Bundle bookData = new Bundle();

        if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
            try {
                if (mSearchApi == null) {
                    mSearchApi = new SearchBookApiHandler(getContext(),
                                                          mGoodreadsAuth);
                }
                final List<Long> grIdList = mSearchApi.searchBookIds(author + ' ' + title);
                // return the first one found
                if (!grIdList.isEmpty()) {
                    if (mByIdApi == null) {
                        mByIdApi = new ShowBookByIdApiHandler(getContext(),
                                                              mGoodreadsAuth);
                    }
                    return mByIdApi.searchByExternalId(grIdList.get(0), fetchThumbnail, bookData);
                }
            } catch (@NonNull final HttpNotFoundException ignore) {
                // ignore
            }
        }
        return bookData;
    }

    @Nullable
    @Override
    @WorkerThread
    public String searchCoverImageByIsbn(@NonNull final String validIsbn,
                                         @IntRange(from = 0, to = 1) final int cIdx,
                                         @Nullable final ImageFileInfo.Size size) {
        if (!mGoodreadsAuth.hasValidCredentials(getContext())) {
            return null;
        }
        try {
            if (mByIsbnApi == null) {
                mByIsbnApi = new ShowBookByIsbnApiHandler(getContext(), mGoodreadsAuth);
            }
            return mByIsbnApi.searchCoverImageByIsbn(validIsbn, new Bundle());

        } catch (@NonNull final IOException | GeneralParsingException ignore) {
            // ignore
        }
        return null;
    }
}

