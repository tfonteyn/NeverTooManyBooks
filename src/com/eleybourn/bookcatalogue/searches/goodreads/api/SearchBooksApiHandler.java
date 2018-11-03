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

package com.eleybourn.bookcatalogue.searches.goodreads.api;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsWork;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.XmlHandler;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

/**
 * Class to query and response to search.books api call.
 *
 * @author Philip Warner
 */
public class SearchBooksApiHandler extends ApiHandler {
    private static final String GR_RESP = "GoodreadsResponse";
    private static final String GR_SEARCH = "search";
    private static final String GR_RESULT = "results";
    private static final String GR_WORK = "work";
    private static final String GR_BEST_BOOK = "best_book";
    /** List of GoodreadsWork objects that result from a search */
    @Nullable
    private List<GoodreadsWork> mWorks = null;
    /** Starting result # (for multi-page result sets). We don't use it (yet). */
    private Long mResultsStart;
    private final XmlHandler mHandleResultsStart = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mResultsStart = Long.parseLong(context.body);
        }
    };
    /** Ending result # (for multi-page result sets). We don't use it (yet). */
    private Long mResultsEnd;
    private final XmlHandler mHandleResultsEnd = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mResultsEnd = Long.parseLong(context.body);
        }
    };

	/*
	 * Typical result:

		<GoodreadsResponse>
			<Request>
				<authentication>true</authentication>
				<key>
					<![CDATA[ GJ59HZyvOM5KGm6Wn8GDzg ]]>
				</key>
				<method>
					<![CDATA[ search_index ]]>
				</method>
			</Request>
			<search>
				<query>
					<![CDATA[ ender ]]>
				</query>
				<results-start>1</results-start>
				<results-end>20</results-end>
				<total-results>245</total-results>
				<source>Goodreads</source>
				<query-time-seconds>0.03</query-time-seconds>
				<results>
					<work>
						<books_count type="integer">91</books_count>
						<id type="integer">2422333</id>
						<original_publication_day type="integer">1</original_publication_day>
						<original_publication_month type="integer">1</original_publication_month>
						<original_publication_year type="integer">1985</original_publication_year>
						<ratings_count type="integer">208674</ratings_count>
						<text_reviews_count type="integer">11428</text_reviews_count>
						<average_rating>4.19</average_rating>
						<best_book>
							<id type="integer">375802</id>
							<title>Ender's Game (Ender's Saga, #1)</title>
							<author>
								<id type="integer">589</id>
								<name>Orson Scott Card</name>
							</author>
							<my_review>
								<id>154477749</id>
								<book>
									<id type="integer">375802</id>
									<isbn>0812550706</isbn>
									<isbn13>9780812550702</isbn13>
									<text_reviews_count type="integer">9861</text_reviews_count>
									<title>
										<![CDATA[ Ender's Game (Ender's Saga, #1) ]]>
									</title>
									<image_url>
										http://photo.goodreads.com/books/1316636769m/375802.jpg
									</image_url>
									<small_image_url>
										http://photo.goodreads.com/books/1316636769s/375802.jpg
									</small_image_url>
									<link>
										http://www.goodreads.com/book/show/375802.Ender_s_Game
									</link>
									<num_pages>324</num_pages>
									<publisher>Tor Science Fiction</publisher>
									<publication_day>15</publication_day>
									<publication_year>1994</publication_year>
									<publication_month>7</publication_month>
									<average_rating>4.19</average_rating>
									<ratings_count>208674</ratings_count>
									<description>
										<![CDATA[
										<br/>&lt;div&gt;<strong>Winner of the Hugo and Nebula Awards</strong><br/><br/>In order to develop a secure defense against a hostile alien race's next attack, government agencies breed child geniuses and train them as soldiers. A brilliant young boy, Andrew &quot;Ender&quot; Wiggin lives with his kind but distant parents, his sadistic brother Peter, and the person he loves more than anyone else, his sister Valentine. Peter and Valentine were candidates for the soldier-training program but didn't make the cut--young Ender is the Wiggin drafted to the orbiting Battle School for rigorous military training.<br/><br/>Ender's skills make him a leader in school and respected in the Battle Room, where children play at mock battles in zero gravity. Yet growing up in an artificial community of young soldiers Ender suffers greatly from isolation, rivalry from his peers, pressure from the adult teachers, and an unsettling fear of the alien invaders. His psychological battles include loneliness, fear that he is becoming like the cruel brother he remembers, and fanning the flames of devotion to his beloved sister. <br/><br/>Is Ender the general Earth needs? But Ender is not the only result of the genetic experiments. The war with the Buggers has been raging for a hundred years, and the quest for the perfect general has been underway for almost as long. Ender's two older siblings are every bit as unusual as he is, but in very different ways. Between the three of them lie the abilities to remake a world. If, that is, the world survives.&lt;/div&gt;<br/>
										]]>
									</description>
									<authors>
										<author>
											<id>589</id>
											<name>
												<![CDATA[ Orson Scott Card ]]>
											</name>
											<image_url>
												<![CDATA[
												http://photo.goodreads.com/authors/1294099952p5/589.jpg
												]]>
											</image_url>
											<small_image_url>
												<![CDATA[
												http://photo.goodreads.com/authors/1294099952p2/589.jpg
												]]>
											</small_image_url>
											<link>
												<![CDATA[
												http://www.goodreads.com/author/show/589.Orson_Scott_Card
												]]>
											</link>
											<average_rating>3.93</average_rating>
											<ratings_count>533747</ratings_count>
											<text_reviews_count>30262</text_reviews_count>
										</author>
									</authors>
									<published>1985</published>
								</book>
								<rating>4</rating>
								<votes>0</votes>
								<spoiler_flag>false</spoiler_flag>
								<spoilers_state>none</spoilers_state>
								<shelves>
									<shelf name="read"/>
									<shelf name="test"/>
									<shelf name="sci-fi-fantasy"/>
								</shelves>
								<recommended_for>
									<![CDATA[ ]]>
								</recommended_for>
								<recommended_by>
									<![CDATA[ ]]>
								</recommended_by>
								<started_at/>
								<read_at>Wed May 01 00:00:00 -0700 1991</read_at>
								<date_added>Tue Mar 15 01:51:42 -0700 2011</date_added>
								<date_updated>Sun Jan 01 05:43:30 -0800 2012</date_updated>
								<read_count/>
								<body>
									<![CDATA[ ]]>
								</body>
								<comments_count>0</comments_count>
								<url>
									<![CDATA[ http://www.goodreads.com/review/show/154477749 ]]>
								</url>
								<link>
									<![CDATA[ http://www.goodreads.com/review/show/154477749 ]]>
								</link>
								<owned>1</owned>
							</my_review>
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
    /** Total results available, as opposed to number returned on first page. */
    private Long mTotalResults;
    private final XmlHandler mHandleTotalResults = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mTotalResults = Long.parseLong(context.body);
        }
    };
    /** Transient global data for current work in search results. */
    @Nullable
    private GoodreadsWork mCurrentWork;
    /**
     * At the START of a "work" tag, we create a new work.
     */
    private final XmlHandler mHandleWorkStart = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mCurrentWork = new GoodreadsWork();
        }
    };
    /**
     * At the END of a "work" tag, we add it to list and reset the pointer.
     */
    private final XmlHandler mHandleWorkEnd = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            //mCurrentWork.requestImage();
            mWorks.add(mCurrentWork);
            mCurrentWork = null;
        }
    };
    private final XmlHandler mHandleWorkId = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mCurrentWork.workId = Long.parseLong(context.body);
        }
    };
    private final XmlHandler mHandlePubDay = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            try {
                mCurrentWork.pubDay = Long.parseLong(context.body);
            } catch (Exception ignored) {
            }
        }
    };
    private final XmlHandler mHandlePubMonth = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            try {
                mCurrentWork.pubMonth = Long.parseLong(context.body);
            } catch (Exception ignored) {
            }
        }
    };
    private final XmlHandler mHandlePubYear = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            try {
                mCurrentWork.pubYear = Long.parseLong(context.body);
            } catch (Exception ignored) {
            }
        }
    };
    private final XmlHandler mHandleBookId = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mCurrentWork.bookId = Long.parseLong(context.body);
        }
    };
    private final XmlHandler mHandleBookTitle = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mCurrentWork.title = context.body;
        }
    };
    private final XmlHandler mHandleAuthorId = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mCurrentWork.authorId = Long.parseLong(context.body);
        }
    };
    private final XmlHandler mHandleAuthorName = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mCurrentWork.authorName = context.body;
        }
    };
    private final XmlHandler mHandleImageUrl = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mCurrentWork.imageUrl = context.body;
        }
    };
    private final XmlHandler mHandleSmallImageUrl = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mCurrentWork.smallImageUrl = context.body;
        }
    };

    /**
     * Constructor
     *
     */
    public SearchBooksApiHandler(@NonNull GoodreadsManager manager) {
        super(manager);
        // Build the XML filters needed to get the data we're interested in.
        buildFilters();
    }

    /**
     * Perform a search and handle the results.
     *
     * @return the array of GoodreadsWork objects.
     */
    @NonNull
    public List<GoodreadsWork> search(final @NonNull String query) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, IOException, NetworkException {

        // Setup API call
        HttpPost post = new HttpPost(GoodreadsManager.GOODREADS_API_ROOT + "/search/index.xml");
        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("q", query.trim()));
        parameters.add(new BasicNameValuePair("key", mManager.getDeveloperKey()));

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
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, "results-start")
                .setEndAction(mHandleResultsStart);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, "results-end")
                .setEndAction(mHandleResultsEnd);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, "total-results")
                .setEndAction(mHandleTotalResults);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK)
                .setStartAction(mHandleWorkStart)
                .setEndAction(mHandleWorkEnd);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, "id")
                .setEndAction(mHandleWorkId);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, "original_publication_day")
                .setEndAction(mHandlePubDay);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, "original_publication_month")
                .setEndAction(mHandlePubMonth);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, "original_publication_year")
                .setEndAction(mHandlePubYear);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, GR_BEST_BOOK, "id")
                .setEndAction(mHandleBookId);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, GR_BEST_BOOK, "title")
                .setEndAction(mHandleBookTitle);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, GR_BEST_BOOK, "author", "id")
                .setEndAction(mHandleAuthorId);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, GR_BEST_BOOK, "author", "name")
                .setEndAction(mHandleAuthorName);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, GR_BEST_BOOK, "image_url")
                .setEndAction(mHandleImageUrl);
        XmlFilter.buildFilter(mRootFilter, GR_RESP, GR_SEARCH, GR_RESULT, GR_WORK, GR_BEST_BOOK, "small_image_url")
                .setEndAction(mHandleSmallImageUrl);
    }

}
