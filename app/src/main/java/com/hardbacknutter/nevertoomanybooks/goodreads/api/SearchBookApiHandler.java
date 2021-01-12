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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.ElementContext;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * search.books   —   Find books by title, author, or ISBN.
 *
 * <a href="https://www.goodreads.com/api/index#search.books">search.books</a>
 * <p>
 * This class is a two-stage approach. A search here returns <strong>only</strong> the list
 * of Goodreads book-ids.
 * Another call to {@link ShowBookByIdApiHandler} must be made to get the full details
 * for each id in the list.
 */
public class SearchBookApiHandler
        extends ApiHandler {

    private static final String URL = GoodreadsManager.BASE_URL + "/search/index.xml";

    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    private final XmlFilter mRootFilter = new XmlFilter("");

    private final List<Long> mGrIdList = new ArrayList<>();

    private final Consumer<ElementContext> mHandleBookId = elementContext ->
            mGrIdList.add(Long.parseLong(elementContext.getBody()));

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     *
     * @throws CredentialsException if there are no valid credentials available
     */
    public SearchBookApiHandler(@NonNull final Context appContext,
                                @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(appContext, grAuth);
        mGrAuth.hasValidCredentialsOrThrow(appContext);

        buildFilters();
    }

    @NonNull
    @WorkerThread
    public List<Long> searchBookIds(@NonNull final String query)
            throws GeneralParsingException, IOException {

        // clear for a new search
        mGrIdList.clear();

        final Map<String, String> parameters = new HashMap<>();
        parameters.put("q", query.trim());
        parameters.put("key", mGrAuth.getDevKey());

        final DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executeGet(URL, parameters, false, handler);

        return mGrIdList;
    }

    private void buildFilters() {
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_BEST_BOOK, XmlTags.XML_ID)
                 .setEndAction(mHandleBookId);
    }
}
