/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.editions;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.XmlTags;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.ElementContext;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * search.books   —   Find books by title, author, or ISBN.
 *
 * <a href="https://www.goodreads.com/api/index#search.books">search.books</a>
 * <p>
 * This class in fact returns a list of Goodreads "work" items.
 */
public class SearchWorksApiHandler
        extends ApiHandler {

    private static final String URL = GoodreadsManager.BASE_URL + "/search/index.xml";

    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    private final XmlFilter mRootFilter = new XmlFilter("");

    /** List of GoodreadsWork objects that result from a search. */
    @NonNull
    private final List<GoodreadsWork> mWorks = new ArrayList<>();
    /** Global data for the <b>current work</b> in search results. */
    @Nullable
    private GoodreadsWork mCurrentWork;
    /**
     * At the START of a "work" tag, we create a new work.
     */
    private final Consumer<ElementContext> mHandleWorkStart =
            elementContext -> mCurrentWork = new GoodreadsWork();
    /**
     * At the END of a "work" tag, we add it to list and reset the pointer.
     */
    private final Consumer<ElementContext> mHandleWorkEnd = elementContext -> {
        mWorks.add(mCurrentWork);
        mCurrentWork = null;
    };
    private final Consumer<ElementContext> mHandleWorkId =
            elementContext -> mCurrentWork.workId = Long.parseLong(elementContext.getBody());
    private final Consumer<ElementContext> mHandlePubDay = elementContext -> {
        try {
            mCurrentWork.pubDay = Long.parseLong(elementContext.getBody());
        } catch (@NonNull final NumberFormatException ignore) {
        }
    };
    private final Consumer<ElementContext> mHandlePubMonth = elementContext -> {
        try {
            mCurrentWork.pubMonth = Long.parseLong(elementContext.getBody());
        } catch (@NonNull final NumberFormatException ignore) {
        }
    };
    private final Consumer<ElementContext> mHandlePubYear = elementContext -> {
        try {
            mCurrentWork.pubYear = Long.parseLong(elementContext.getBody());
        } catch (@NonNull final NumberFormatException ignore) {
        }
    };
    private final Consumer<ElementContext> mHandleBookId =
            elementContext -> mCurrentWork.grBookId = Long.parseLong(elementContext.getBody());
    private final Consumer<ElementContext> mHandleBookTitle =
            elementContext -> mCurrentWork.title = elementContext.getBody();
    private final Consumer<ElementContext> mHandleAuthorId =
            elementContext -> mCurrentWork.authorId = Long.parseLong(elementContext.getBody());
    private final Consumer<ElementContext> mHandleAuthorName =
            elementContext -> mCurrentWork.authorName = elementContext.getBody();
    private final Consumer<ElementContext> mHandleAuthorRole =
            elementContext -> mCurrentWork.authorRole = elementContext.getBody();
    private final Consumer<ElementContext> mHandleImageUrl =
            elementContext -> mCurrentWork.imageUrl = elementContext.getBody();
    private final Consumer<ElementContext> mHandleSmallImageUrl =
            elementContext -> mCurrentWork.smallImageUrl = elementContext.getBody();
    /**
     * Starting result # (for multi-page result sets). We don't use it (yet).
     */
    private Long mResultsStart;
    private final Consumer<ElementContext> mHandleResultsStart =
            elementContext -> mResultsStart = Long.parseLong(elementContext.getBody());
    /**
     * Ending result # (for multi-page result sets). We don't use it (yet).
     */
    private Long mResultsEnd;
    private final Consumer<ElementContext> mHandleResultsEnd =
            elementContext -> mResultsEnd = Long.parseLong(elementContext.getBody());
    /**
     * Total results available, as opposed to number returned on first page.
     */
    private Long mTotalResults;
    private final Consumer<ElementContext> mHandleTotalResults =
            elementContext -> mTotalResults = Long.parseLong(elementContext.getBody());


    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     *
     * @throws CredentialsException with GoodReads
     */
    @WorkerThread
    SearchWorksApiHandler(@NonNull final Context appContext,
                          @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(appContext, grAuth);
        mGrAuth.hasValidCredentialsOrThrow(appContext);

        buildFilters();
    }

    /**
     * Perform a search and handle the results.
     *
     * @param query A GoodReads compatible query string ('q' parameter)
     *
     * @return the array of GoodreadsWork objects.
     *
     * @throws IOException          on failure
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     */
    @NonNull
    @WorkerThread
    public List<GoodreadsWork> search(@NonNull final String query)
            throws CredentialsException, Http404Exception, IOException {

        // clear for a new search
        mWorks.clear();

        final Map<String, String> parameters = new HashMap<>();
        parameters.put("q", query.trim());
        parameters.put("key", mGrAuth.getDevKey());

        final DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executeGet(URL, parameters, false, handler);

        return mWorks;
    }

    @SuppressWarnings("unused")
    public long getResultsStart() {
        return mResultsStart;
    }

    @SuppressWarnings("unused")
    public long getTotalResults() {
        return mTotalResults;
    }

    @SuppressWarnings("unused")
    public long getResultsEnd() {
        return mResultsEnd;
    }


    /**
     * Setup filters to process the XML parts we care about.
     * <p>
     * Typical result:
     * <pre>
     *  {@code
     *  <GoodreadsResponse>
     *    <Request>
     *      <authentication>true</authentication>
     *      <key><![CDATA[...]]></key>
     *      <method><![CDATA[ search_index ]]></method>
     *    </Request>
     *
     *    <search>
     *      <query>
     *        <![CDATA[ ender ]]>
     *      </query>
     *      <results-start>1</results-start>
     *      <results-end>20</results-end>
     *      <total-results>245</total-results>
     *      <source>Goodreads</source>
     *      <query-time-seconds>0.03</query-time-seconds>
     *      <results>
     *        <work>
     *          <books_count type="integer">91</books_count>
     *          <id type="integer">2422333</id>
     *          <original_publication_day type="integer">1</original_publication_day>
     *          <original_publication_month type="integer">1</original_publication_month>
     *          <original_publication_year type="integer">1985</original_publication_year>
     *          <ratings_count type="integer">208674</ratings_count>
     *          <text_reviews_count type="integer">11428</text_reviews_count>
     *          <average_rating>4.19</average_rating>
     *          <best_book>
     *            <id type="integer">375802</id>
     *            <title>Ender's Game (Ender's Saga, #1)</title>
     *            <author>
     *              <id type="integer">589</id>
     *              <name>Orson Scott Card</name>
     *            </author>
     *            <my_review>
     *              <id>154477749</id>
     *              <book>
     *                <id type="integer">375802</id>
     *                <isbn>0812550706</isbn>
     *                <isbn13>9780812550702</isbn13>
     *                <text_reviews_count type="integer">9861</text_reviews_count>
     *                <title>
     *                  <![CDATA[ Ender's Game (Ender's Saga, #1) ]]>
     *                </title>
     *                <image_url>
     *                  http://photo.goodreads.com/books/1316636769m/375802.jpg
     *                </image_url>
     *                <small_image_url>
     *                  http://photo.goodreads.com/books/1316636769s/375802.jpg
     *                </small_image_url>
     *                <link>
     *                  http://www.goodreads.com/book/show/375802.Ender_s_Game
     *                </link>
     *                <num_pages>324</num_pages>
     *                <publisher>Tor Science Fiction</publisher>
     *                <publication_day>15</publication_day>
     *                <publication_year>1994</publication_year>
     *                <publication_month>7</publication_month>
     *                <average_rating>4.19</average_rating>
     *                <ratings_count>208674</ratings_count>
     *                <description>
     *                  <![CDATA[blah blah...]]>
     *                </description>
     *                <authors>
     *                  <author>
     *                    <id>589</id>
     *                    <name>
     *                      <![CDATA[ Orson Scott Card ]]>
     *                    </name>
     *                    <image_url>
     *                      <![CDATA[
     *                        http://photo.goodreads.com/authors/1294099952p5/589.jpg
     *                      ]]>
     *                    </image_url>
     *                    <small_image_url>
     *                      <![CDATA[
     *                      http://photo.goodreads.com/authors/1294099952p2/589.jpg
     *                      ]]>
     *                    </small_image_url>
     *                    <link>
     *                      <![CDATA[
     *                      http://www.goodreads.com/author/show/589.Orson_Scott_Card
     *                      ]]>
     *                    </link>
     *                    <average_rating>3.93</average_rating>
     *                    <ratings_count>533747</ratings_count>
     *                    <text_reviews_count>30262</text_reviews_count>
     *                  </author>
     *                </authors>
     *                <published>1985</published>
     *              </book>
     *              <rating>4</rating>
     *              <votes>0</votes>
     *              <spoiler_flag>false</spoiler_flag>
     *              <spoilers_state>none</spoilers_state>
     *              <shelves>
     *                <shelf name="read"/>
     *                <shelf name="test"/>
     *                <shelf name="sci-fi-fantasy"/>
     *              </shelves>
     *              <recommended_for>
     *                <![CDATA[ ]]>
     *              </recommended_for>
     *              <recommended_by>
     *                <![CDATA[ ]]>
     *              </recommended_by>
     *              <started_at/>
     *              <read_at>Wed May 01 00:00:00 -0700 1991</read_at>
     *              <date_added>Tue Mar 15 01:51:42 -0700 2011</date_added>
     *              <date_updated>Sun Jan 01 05:43:30 -0800 2012</date_updated>
     *              <read_count/>
     *              <body>
     *                <![CDATA[ ]]>
     *              </body>
     *              <comments_count>0</comments_count>
     *              <url>
     *                <![CDATA[ http://www.goodreads.com/review/show/154477749 ]]>
     *              </url>
     *              <link>
     *                <![CDATA[ http://www.goodreads.com/review/show/154477749 ]]>
     *              </link>
     *              <owned>1</owned>
     *            </my_review>
     *            <image_url>
     *              http://photo.goodreads.com/books/1316636769m/375802.jpg
     *            </image_url>
     *            <small_image_url>
     *              http://photo.goodreads.com/books/1316636769s/375802.jpg
     *            </small_image_url>
     *          </best_book>
     *        </work>
     *      </results>
     *    </search>
     * </GoodreadsResponse>
     *  }
     *  </pre>
     *
     * <strong>Note:</strong> the response does not contain the language code
     * (checked with a french book).
     */
    private void buildFilters() {
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS_START)
                 .setEndAction(mHandleResultsStart);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS_END)
                 .setEndAction(mHandleResultsEnd);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_TOTAL_RESULTS)
                 .setEndAction(mHandleTotalResults);


        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK)
                 .setStartAction(mHandleWorkStart)
                 .setEndAction(mHandleWorkEnd);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_ID)
                 .setEndAction(mHandleWorkId);

        // Original publication date
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_DAY)
                 .setEndAction(mHandlePubDay);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_MONTH)
                 .setEndAction(mHandlePubMonth);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_YEAR)
                 .setEndAction(mHandlePubYear);


        // "Best book"
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_BEST_BOOK, XmlTags.XML_ID)
                 .setEndAction(mHandleBookId);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_BEST_BOOK, XmlTags.XML_TITLE)
                 .setEndAction(mHandleBookTitle);

        /* Author is taken from the SINGLE entry in <best_book>:
         *
         *          <best_book>
         *            <id type="integer">375802</id>
         *            <title>Ender's Game (Ender's Saga, #1)</title>
         *            <author>
         *              <id type="integer">589</id>
         *              <name>Orson Scott Card</name>
         *            </author>
         *            <my_review>
         *
         * In <my_review>, there is a  <authors> section...
         * Need more sample data... we should probably take the latter,
         * and use an ArrayList<Author> instead of a single Author.
         */
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_BEST_BOOK, XmlTags.XML_AUTHOR, XmlTags.XML_ID)
                 .setEndAction(mHandleAuthorId);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_BEST_BOOK, XmlTags.XML_AUTHOR, XmlTags.XML_NAME)
                 .setEndAction(mHandleAuthorName);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_BEST_BOOK, XmlTags.XML_AUTHOR, XmlTags.XML_ROLE)
                 .setEndAction(mHandleAuthorRole);


        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_BEST_BOOK, XmlTags.XML_IMAGE_URL)
                 .setEndAction(mHandleImageUrl);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_SEARCH,
                              XmlTags.XML_RESULTS, XmlTags.XML_WORK,
                              XmlTags.XML_BEST_BOOK, XmlTags.XML_SMALL_IMAGE_URL)
                 .setEndAction(mHandleSmallImageUrl);
    }
}
