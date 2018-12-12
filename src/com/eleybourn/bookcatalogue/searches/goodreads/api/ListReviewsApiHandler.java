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

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.utils.xml.SimpleXmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.SimpleXmlFilter.BuilderContext;
import com.eleybourn.bookcatalogue.utils.xml.SimpleXmlFilter.XmlListener;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

/**
 * Class to implement the reviews.list api call. It queries based on the passed parameters and returns
 * a single Bundle containing all results. The Bundle itself will contain other bundles: typically an
 * array of 'Review' bundles, each of which will contains arrays of 'author' bundles.
 *
 * Processing this data is up to the caller, but it is guaranteed to be type-safe if present, with the
 * exception of dates, which are collected as text strings.
 *
 * @author Philip Warner
 */
public class ListReviewsApiHandler extends ApiHandler {

    /** Date format used for parsing 'last_update_date' */
    @SuppressLint("SimpleDateFormat")
    static final SimpleDateFormat mUpdateDateFmt = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy");

    /**
     * Listener to handle the contents of the date_updated field. We only
     * keep it if it is a valid date, and we store it in SQL format using
     * UTC TZ so comparisons work.
     */
    private final XmlListener mUpdatedListener = new XmlListener() {
        @Override
        public void onStart(@NonNull BuilderContext bc, @NonNull ElementContext c) {
        }

        @Override
        public void onFinish(@NonNull BuilderContext bc, @NonNull ElementContext c) {
            date2Sql(bc.getData(), ListReviewsFieldNames.UPDATED);
        }
    };
    /**
     * Listener to handle the contents of the date_added field. We only
     * keep it if it is a valid date, and we store it in SQL format using
     * UTC TZ so comparisons work.
     */
    private final XmlListener mAddedListener = new XmlListener() {
        @Override
        public void onStart(@NonNull BuilderContext bc, @NonNull ElementContext c) {
        }

        @Override
        public void onFinish(@NonNull BuilderContext bc, @NonNull ElementContext c) {
            date2Sql(bc.getData(), ListReviewsFieldNames.ADDED);
        }
    };
    private SimpleXmlFilter mFilters;

    public ListReviewsApiHandler(final @NonNull GoodreadsManager manager) throws NotAuthorizedException {
        super(manager);
        if (!manager.hasValidCredentials()) {
            throw new NotAuthorizedException();
        }
        // Build the XML filters needed to get the data we're interested in.
        buildFilters();
    }

	/*
	 * Typical result:

			<GoodreadsResponse>
				<Request>
					...
				</Request>
				<reviews start="3" end="4" total="933">
					<review>
						<id>276860380</id>
						<book>
							<id type="integer">951750</id>
							<isbn>0583120911</isbn>
							<isbn13>9780583120913</isbn13>
							<text_reviews_count type="integer">2</text_reviews_count>
			
							<title>
								<![CDATA[The Dying Earth]]>
							</title>
							<image_url>http://photo.goodreads.com/books/1294108593m/951750.jpg</image_url>
							<small_image_url>http://photo.goodreads.com/books/1294108593s/951750.jpg</small_image_url>
							<link>http://www.goodreads.com/book/show/951750.The_Dying_Earth</link>
							<num_pages>159</num_pages>
			
							<format></format>
							<edition_information></edition_information>
							<publisher></publisher>
							<publication_day>20</publication_day>
							<publication_year>1972</publication_year>
							<publication_month>4</publication_month>
							<average_rating>3.99</average_rating>
			
							<ratings_count>713</ratings_count>
							<description>
								<![CDATA[]]>
							</description>
			
							<authors>
								<author>
									<id>5376</id>
									<name><![CDATA[Jack Vance]]></name>
									<image_url><![CDATA[http://photo.goodreads.com/authors/1207604643p5/5376.jpg]]></image_url>
									<small_image_url><![CDATA[http://photo.goodreads.com/authors/1207604643p2/5376.jpg]]></small_image_url>
									<link><![CDATA[http://www.goodreads.com/author/show/5376.Jack_Vance]]></link>
									<average_rating>3.94</average_rating>
									<ratings_count>12598</ratings_count>
									<text_reviews_count>844</text_reviews_count>
								</author>
			
							</authors>
							<published>1972</published>
						</book>
			
						<rating>0</rating>
						<votes>0</votes>
						<spoiler_flag>false</spoiler_flag>
						<spoilers_state>none</spoilers_state>
			
						<shelves>
							<shelf name="sci-fi-fantasy" />
							<shelf name="to-read" />
						</shelves>
						<recommended_for><![CDATA[]]></recommended_for>
						<recommended_by><![CDATA[]]></recommended_by>
						<started_at></started_at>
						<read_at></read_at>
						<date_added>Mon Feb 13 05:32:30 -0800 2012</date_added>
						<date_updated>Mon Feb 13 05:32:31 -0800 2012</date_updated>
						<read_count></read_count>
						<body>
							<![CDATA[]]>
						</body>
						<comments_count>0</comments_count>
			
						<url><![CDATA[http://www.goodreads.com/review/show/276860380]]></url>
						<link><![CDATA[http://www.goodreads.com/review/show/276860380]]></link>
			
						<owned>0</owned>
					</review>
			
					<review>
						<id>273090417</id>
						<book>
							<id type="integer">2042540</id>
							<isbn>0722129203</isbn>
			
							<isbn13>9780722129203</isbn13>
							<text_reviews_count type="integer">0</text_reviews_count>
							<title>
								<![CDATA[The Fallible Fiend]]>
							</title>
							<image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
							<small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>
			
							<link>http://www.goodreads.com/book/show/2042540.The_Fallible_Fiend</link>
							<num_pages></num_pages>
							<format></format>
							<edition_information></edition_information>
							<publisher></publisher>
							<publication_day></publication_day>
							<publication_year></publication_year>
							<publication_month></publication_month>
			
							<average_rating>3.55</average_rating>
							<ratings_count>71</ratings_count>
							<description>
								<![CDATA[]]>
							</description>
			
							<authors>
								<author>
									<id>3305</id>
									<name><![CDATA[L. Sprague de Camp]]></name>
									<image_url><![CDATA[http://photo.goodreads.com/authors/1218217726p5/3305.jpg]]></image_url>
									<small_image_url><![CDATA[http://photo.goodreads.com/authors/1218217726p2/3305.jpg]]></small_image_url>
									<link><![CDATA[http://www.goodreads.com/author/show/3305.L_Sprague_de_Camp]]></link>
									<average_rating>3.78</average_rating>
									<ratings_count>9424</ratings_count>
									<text_reviews_count>441</text_reviews_count>
								</author>
							</authors> 
							<published></published>
						</book>
			
						<rating>0</rating>
						<votes>0</votes>
						<spoiler_flag>false</spoiler_flag>
			
						<spoilers_state>none</spoilers_state>
						<shelves>
							<shelf name="read" />
							<shelf name="sci-fi-fantasy" />
						</shelves>
						<recommended_for><![CDATA[]]></recommended_for>
						<recommended_by><![CDATA[]]></recommended_by>
						<started_at></started_at>
			
						<read_at></read_at>
						<date_added>Mon Feb 06 03:40:52 -0800 2012</date_added>
						<date_updated>Mon Feb 06 03:40:52 -0800 2012</date_updated>
						<read_count></read_count>
						<body>
							<![CDATA[]]>
						</body>
						<comments_count>0</comments_count>
			
			
						<url><![CDATA[http://www.goodreads.com/review/show/273090417]]></url>
						<link><![CDATA[http://www.goodreads.com/review/show/273090417]]></link>
						<owned>0</owned>
					</review>
			
				</reviews>
			
			</GoodreadsResponse>

	 */

    @NonNull
    public Bundle run(final int page, final int perPage)
            throws OAuthMessageSignerException, OAuthExpectationFailedException,
            OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
        @SuppressWarnings("UnusedAssignment")
        long t0 = System.currentTimeMillis();

        // Sort by update_dte (descending) so sync is faster. Specify 'shelf=all' because it seems goodreads returns
        // the shelf that is selected in 'My Books' on the web interface by default.
        final String urlBase = GoodreadsManager.BASE_URL + "/review/list/%4$s.xml?key=%1$s&v=2&page=%2$s&per_page=%3$s&sort=date_updated&order=d&shelf=all";
        final String url = String.format(urlBase, mManager.getDevKey(), page, perPage, mManager.getUserId());
        HttpGet get = new HttpGet(url);

        // Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        // Even thought it's only a GET, it needs a signature.
        mManager.execute(get, handler, true);

        // When we get here, the data has been collected but needs to be processed into standard form.
        Bundle results = mFilters.getData();

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.info(this,"Found " + results.getLong(ListReviewsFieldNames.TOTAL) + " books in " + (System.currentTimeMillis() - t0) + "ms");
        }

        return results;
    }

    /**
     * Setup filters to process the XML parts we care about.
     */
    private void buildFilters() {
        /*
         * Process the stuff we care about
         */
        mFilters = new SimpleXmlFilter(mRootFilter);

        mFilters
                //<GoodreadsResponse>
                .s(XML_GOODREADS_RESPONSE)
                //	<Request>
                //		...
                //	</Request>
                //	<reviews start="3" end="4" total="933">
                .s(XML_REVIEWS).isArray(ListReviewsFieldNames.REVIEWS)
                .longAttr(XML_START, ListReviewsFieldNames.START)
                .longAttr(XML_END, ListReviewsFieldNames.END)
                .longAttr(XML_TOTAL, ListReviewsFieldNames.TOTAL)
                //		<review>
                .s(XML_REVIEW).isArrayItem()
                //			<id>276860380</id>
                .longBody(XML_ID, ListReviewsFieldNames.GR_REVIEW_ID)
                //			<book>
                .s(XML_BOOK)
                //				<id type="integer">951750</id>
                .longBody(XML_ID, ListReviewsFieldNames.GR_BOOK_ID)
                //				<isbn>0583120911</isbn>
                .stringBody(XML_ISBN, ListReviewsFieldNames.DB_ISBN)
                //				<isbn13>9780583120913</isbn13>
                .stringBody(XML_ISBN_13, ListReviewsFieldNames.ISBN13)
                //				...
                //				<title><![CDATA[The Dying Earth]]></title>
                .stringBody(XML_TITLE, ListReviewsFieldNames.DB_TITLE)
                //				<image_url>http://photo.goodreads.com/books/1294108593m/951750.jpg</image_url>
                .stringBody(XML_IMAGE_URL, ListReviewsFieldNames.LARGE_IMAGE)
                //				<small_image_url>http://photo.goodreads.com/books/1294108593s/951750.jpg</small_image_url>
                .stringBody(XML_SMALL_IMAGE_URL, ListReviewsFieldNames.SMALL_IMAGE)
                //				...
                //				<num_pages>159</num_pages>
                .longBody(XML_NUM_PAGES, ListReviewsFieldNames.DB_PAGES)
                //				<format></format>
                .stringBody(XML_FORMAT, ListReviewsFieldNames.DB_FORMAT)
                //				<publisher></publisher>
                .stringBody(XML_PUBLISHER, ListReviewsFieldNames.DB_PUBLISHER)
                //				<publication_day>20</publication_day>
                .longBody(XML_PUBLICATION_DAY, ListReviewsFieldNames.PUB_DAY)
                //				<publication_year>1972</publication_year>
                .longBody(XML_PUBLICATION_YEAR, ListReviewsFieldNames.PUB_YEAR)
                //				<publication_month>4</publication_month>
                .longBody(XML_PUBLICATION_MONTH, ListReviewsFieldNames.PUB_MONTH)
                //				<description><![CDATA[]]></description>
                .stringBody(XML_DESCRIPTION, ListReviewsFieldNames.DB_DESCRIPTION)
                //				...
                //
                //				<authors>
                .s(XML_AUTHORS)
                .isArray(ListReviewsFieldNames.AUTHORS)
                //					<author>
                .s(XML_AUTHOR)
                .isArrayItem()
                //						<id>5376</id>
                .longBody(XML_ID, ListReviewsFieldNames.DB_AUTHOR_ID)
                //						<name><![CDATA[Jack Vance]]></name>
                .stringBody(XML_NAME, ListReviewsFieldNames.AUTHOR_NAME_GF)
                //						...
                //					</author>
                //				</authors>
                //				...
                //			</book>
                .popTo(XML_REVIEW)
                //
                //			<rating>0</rating>
                .doubleBody(XML_RATING, ListReviewsFieldNames.DB_RATING)
                //			...
                //			<shelves>
                .s(XML_SHELVES)
                .isArray(ListReviewsFieldNames.SHELVES)
                //				<shelf name="sci-fi-fantasy" />
                .s(XML_SHELF)
                .isArrayItem()
                .stringAttr(XML_NAME, ListReviewsFieldNames.SHELF)
                .popTo(XML_REVIEW)
                //				<shelf name="to-read" />
                //			</shelves>
                //			...
                //			<started_at></started_at>
                .stringBody(XML_STARTED_AT, ListReviewsFieldNames.DB_READ_START)
                //			<read_at></read_at>
                .stringBody(XML_READ_AT, ListReviewsFieldNames.DB_READ_END)
                //			<date_added>Mon Feb 13 05:32:30 -0800 2012</date_added>
                //.stringBody("date_added", ADDED)
                .s(XML_DATE_ADDED).stringBody(ListReviewsFieldNames.ADDED).setListener(mAddedListener).pop()
                //			<date_updated>Mon Feb 13 05:32:31 -0800 2012</date_updated>
                .s(XML_DATE_UPDATED).stringBody(ListReviewsFieldNames.UPDATED).setListener(mUpdatedListener).pop()
                //			...
                //			<body><![CDATA[]]></body>
                .stringBody(XML_BODY, ListReviewsFieldNames.DB_NOTES).pop()
                //			...
                //			<owned>0</owned>
                //		</review>
                //	</reviews>
                //
                //</GoodreadsResponse>
                .done();
    }

    private void date2Sql(final @NonNull Bundle b, final @NonNull String key) {
        if (b.containsKey(key)) {
            String date = b.getString(key);
            try {
                Date d = mUpdateDateFmt.parse(date);
                b.putString(key, DateUtils.utcSqlDateTime(d));
            } catch (Exception e) {
                b.remove(key);
            }
        }
    }

    /**
     * Field names we add to the bundle based on parsed XML data.
     *
     * We duplicate the CatalogueDBAdapter names (and give them a DB_ prefix) so
     * that (a) it is clear which fields are provided by this call, and (b) it is clear
     * which fields directly relate to DB fields.
     *
     * @author Philip Warner
     */
    public static final class ListReviewsFieldNames {
        public static final String START = "__start";
        public static final String END = "__end";
        public static final String TOTAL = "__total";
        public static final String GR_BOOK_ID = "__gr_book_id";
        public static final String GR_REVIEW_ID = "__gr_review_id";
        public static final String ISBN13 = "__isbn13";
        public static final String SMALL_IMAGE = "__smallImage";
        public static final String LARGE_IMAGE = "__largeImage";
        public static final String PUB_DAY = "__pubDay";
        public static final String PUB_YEAR = "__pubYear";
        public static final String PUB_MONTH = "__pubMonth";
        public static final String ADDED = "__added";
        public static final String UPDATED = "__updated";
        public static final String REVIEWS = "__reviews";
        public static final String AUTHORS = "__authors";
        public static final String SHELF = "__shelf";
        public static final String SHELVES = "__shelves";
        public static final String AUTHOR_NAME_GF = "__author_name";

        public static final String DB_PAGES = DatabaseDefinitions.DOM_BOOK_PAGES.name;
        public static final String DB_ISBN = DatabaseDefinitions.DOM_BOOK_ISBN.name;
        public static final String DB_TITLE = DatabaseDefinitions.DOM_TITLE.name;
        public static final String DB_NOTES = DatabaseDefinitions.DOM_BOOK_NOTES.name;
        public static final String DB_FORMAT = DatabaseDefinitions.DOM_BOOK_FORMAT.name;
        public static final String DB_PUBLISHER = DatabaseDefinitions.DOM_BOOK_PUBLISHER.name;
        public static final String DB_DESCRIPTION = DatabaseDefinitions.DOM_BOOK_DESCRIPTION.name;
        public static final String DB_AUTHOR_ID = DatabaseDefinitions.DOM_FK_AUTHOR_ID.name;
        public static final String DB_RATING = DatabaseDefinitions.DOM_BOOK_RATING.name;
        public static final String DB_READ_START = DatabaseDefinitions.DOM_BOOK_READ_START.name;
        public static final String DB_READ_END = DatabaseDefinitions.DOM_BOOK_READ_END.name;
    }
}
