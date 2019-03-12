/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.goodreads.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import com.eleybourn.bookcatalogue.goodreads.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsWork;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.xml.ElementContext;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

/**
 * Class to query and response to search.books api call.
 * <p>
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
 * @author Philip Warner
 */
public class SearchBooksApiHandler
        extends ApiHandler {

    /** List of GoodreadsWork objects that result from a search. */
    @Nullable
    private List<GoodreadsWork> mWorks;
    /** Transient global data for current work in search results. */
    @Nullable
    private GoodreadsWork mCurrentWork;
    /**
     * At the START of a "work" tag, we create a new work.
     */
    private final XmlHandler mHandleWorkStart = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mCurrentWork = new GoodreadsWork();
        }
    };
    /**
     * At the END of a "work" tag, we add it to list and reset the pointer.
     */
    private final XmlHandler mHandleWorkEnd = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            //mCurrentWork.requestImage();
            mWorks.add(mCurrentWork);
            mCurrentWork = null;
        }
    };
    private final XmlHandler mHandleWorkId = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mCurrentWork.workId = Long.parseLong(context.getBody());
        }
    };
    private final XmlHandler mHandlePubDay = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            try {
                mCurrentWork.pubDay = Long.parseLong(context.getBody());
            } catch (NumberFormatException ignored) {
            }
        }
    };
    private final XmlHandler mHandlePubMonth = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            try {
                mCurrentWork.pubMonth = Long.parseLong(context.getBody());
            } catch (NumberFormatException ignored) {
            }
        }
    };
    private final XmlHandler mHandlePubYear = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            try {
                mCurrentWork.pubYear = Long.parseLong(context.getBody());
            } catch (NumberFormatException ignored) {
            }
        }
    };
    private final XmlHandler mHandleBookId = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mCurrentWork.bookId = Long.parseLong(context.getBody());
        }
    };
    private final XmlHandler mHandleBookTitle = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mCurrentWork.title = context.getBody();
        }
    };
    private final XmlHandler mHandleAuthorId = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mCurrentWork.authorId = Long.parseLong(context.getBody());
        }
    };
    private final XmlHandler mHandleAuthorName = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mCurrentWork.authorName = context.getBody();
        }
    };
    private final XmlHandler mHandleImageUrl = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mCurrentWork.imageUrl = context.getBody();
        }
    };
    private final XmlHandler mHandleSmallImageUrl = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mCurrentWork.smallImageUrl = context.getBody();
        }
    };
    /**
     * Starting result # (for multi-page result sets). We don't use it (yet).
     */
    private Long mResultsStart;
    private final XmlHandler mHandleResultsStart = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mResultsStart = Long.parseLong(context.getBody());
        }
    };
    /**
     * Ending result # (for multi-page result sets). We don't use it (yet).
     */
    private Long mResultsEnd;
    private final XmlHandler mHandleResultsEnd = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mResultsEnd = Long.parseLong(context.getBody());
        }
    };
    /**
     * Total results available, as opposed to number returned on first page.
     */
    private Long mTotalResults;
    private final XmlHandler mHandleTotalResults = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            mTotalResults = Long.parseLong(context.getBody());
        }
    };


    /**
     * Constructor.
     */
    public SearchBooksApiHandler(@NonNull final GoodreadsManager manager) {
        super(manager);
        buildFilters();
    }

    /**
     * Perform a search and handle the results.
     *
     * @return the array of GoodreadsWork objects.
     */
    @NonNull
    public List<GoodreadsWork> search(@NonNull final String query)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        // Setup API call
        HttpPost post = new HttpPost(GoodreadsManager.BASE_URL + "/search/index.xml");
        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("q", query.trim()));
        parameters.add(new BasicNameValuePair("key", mManager.getDevKey()));

        post.setEntity(new UrlEncodedFormEntity(parameters));
        mWorks = new ArrayList<>();

        // Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        mManager.execute(post, handler, false);

        // Return parsed results.
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
     */
    private void buildFilters() {
    /*
       Stuff we care about

        <GoodreadsResponse>
            ...
            <search>
                ...
                <results-start>1</results-start>
                <results-end>20</results-end>
                <total-results>245</total-results>
                ...
                <results>
                    <work>
                        <books_count type="integer">91</books_count>
                        <id type="integer">2422333</id>
                        <original_publication_day type="integer">1</original_publication_day>
                        <original_publication_month type="integer">1</original_publication_month>
                        <original_publication_year type="integer">1985</original_publication_year>
                        <ratings_count type="integer">208674</ratings_count>
                        ...
                        <average_rating>4.19</average_rating>
                        <best_book>
                            <id type="integer">375802</id>
                            <title>Ender's Game (Ender's Saga, #1)</title>
                            <author>
                                <id type="integer">589</id>
                                <name>Orson Scott Card</name>
                            </author>
                            ...
                            <image_url>
                                http://photo.goodreads.com/books/1316636769m/375802.jpg
                            </image_url>
                            <small_image_url>
                                http://photo.goodreads.com/books/1316636769s/375802.jpg
                            </small_image_url>
                        </best_book>
                    </work>
                </results>
            </search>
        </GoodreadsResponse>
        */

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULTS_START)
                 .setEndAction(mHandleResultsStart);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULTS_END)
                 .setEndAction(mHandleResultsEnd);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_TOTAL_RESULTS)
                 .setEndAction(mHandleTotalResults);


        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK)
                 .setStartAction(mHandleWorkStart)
                 .setEndAction(mHandleWorkEnd);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_ID)
                 .setEndAction(mHandleWorkId);

        // Original publication date
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_ORIGINAL_PUBLICATION_DAY)
                 .setEndAction(mHandlePubDay);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_ORIGINAL_PUBLICATION_MONTH)
                 .setEndAction(mHandlePubMonth);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_ORIGINAL_PUBLICATION_YEAR)
                 .setEndAction(mHandlePubYear);

        // "Best book"
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_BEST_BOOK,
                              XML_ID)
                 .setEndAction(mHandleBookId);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_BEST_BOOK,
                              XML_TITLE)
                 .setEndAction(mHandleBookTitle);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_BEST_BOOK,
                              XML_AUTHOR, XML_ID)
                 .setEndAction(mHandleAuthorId);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_BEST_BOOK,
                              XML_AUTHOR, XML_NAME)
                 .setEndAction(mHandleAuthorName);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_BEST_BOOK,
                              XML_IMAGE_URL)
                 .setEndAction(mHandleImageUrl);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_SEARCH, XML_RESULT, XML_WORK,
                              XML_BEST_BOOK,
                              XML_SMALL_IMAGE_URL)
                 .setEndAction(mHandleSmallImageUrl);
    }

}
