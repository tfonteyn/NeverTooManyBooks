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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.SimpleXmlFilter.XmlListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * "reviews.list"   —   Get the books on a members shelf.
 *
 * <a href="https://www.goodreads.com/api/index#reviews.list">reviews.list</a>
 *
 * <strong>IMPORTANT:</strong> Goodreads private notes are not included in this response.
 * So we cannot import them here.
 */
public class ReviewsListApiHandler
        extends ApiHandler {

    /**
     * Parameters.
     * <p>
     * 1: key
     * 2: page
     * 3: reviews per page
     * 4: user id
     */
    private static final String URL =
            GoodreadsManager.BASE_URL + "/review/list/%4$s.xml?"
            + "key=%1$s"
            + "&v=2"
            + "&page=%2$s"
            + "&per_page=%3$s"
            // Sort by date_updated (descending) so sync is faster.
            + "&sort=date_updated"
            + "&order=d"
            // Specify 'shelf=all' because it seems Goodreads returns the shelf that is selected
            // in 'My Books' on the web interface by default.
            + "&shelf=all";

    /** Handle date_added. */
    private final XmlListener mAddedListener = (bc, c) ->
            Review.validateDate(bc.getData(), Review.ADDED);

    /** Handle date_updated. */
    private final XmlListener mUpdatedListener = (bc, c) ->
            Review.validateDate(bc.getData(), Review.UPDATED);
    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    private final XmlFilter mRootFilter = new XmlFilter("");
    private SimpleXmlFilter mFilters;

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     *
     * @throws CredentialsException if there are no valid credentials available
     */
    public ReviewsListApiHandler(@NonNull final Context appContext,
                                 @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(appContext, grAuth);
        mGrAuth.hasValidCredentialsOrThrow(appContext);

        buildFilters();
    }

    /**
     * Get the reviews.
     * <p>
     * {@code
     * Bundle results = get(page,perPage);
     * mTotalBooks = (int) results.getLong(ReviewsListApiHandler.Review.TOTAL);
     * <p>
     * ArrayList<Bundle> reviews = results.getParcelableArrayList(
     * ReviewsListApiHandler.Review.REVIEWS);
     * }
     *
     * @return A bundle containing an ArrayList of Bundles, one for each review.
     *
     * @throws IOException on failures
     */
    @NonNull
    public Bundle get(final int page,
                      final int perPage)
            throws GeneralParsingException, IOException {

        final String url = String.format(URL, mGrAuth.getDevKey(), page, perPage,
                                         mGrAuth.getUserId());
        final DefaultHandler handler = new XmlResponseParser(mRootFilter);
//        DefaultHandler handler = new XmlDumpParser();
        executeGet(url, null, true, handler);
        return mFilters.getData();
    }

    /**
     * Setup filters to process the XML parts we care about.
     * <p>
     * It queries based on the passed parameters and returns a single Bundle containing all results.
     * The Bundle itself will contain other bundles: typically an array of 'Review' bundles,
     * each of which will contains arrays of 'author' bundles.
     * <p>
     * Processing this data is up to the caller, but it is guaranteed to be type-safe if present,
     * with the exception of dates, which are collected as text strings.
     * <p>
     * Typical result:
     * <pre>
     * {@code
     *   <GoodreadsResponse>
     *     <Request>
     *     ...
     *     </Request>
     *     <reviews start="3" end="4" total="933">
     *       <review>
     *         <id>276860380</id>
     *         <book>
     *           <id type="integer">951750</id>
     *           <isbn>0583120911</isbn>
     *           <isbn13>9780583120913</isbn13>
     *           <text_reviews_count type="integer">2</text_reviews_count>
     *           <title>
     *             <![CDATA[The Dying Earth]]>
     *           </title>
     *           <image_url>
     *               http://photo.goodreads.com/books/1294108593m/951750.jpg
     *           </image_url>
     *           <small_image_url>
     *               http://photo.goodreads.com/books/1294108593s/951750.jpg
     *           </small_image_url>
     *           <link>
     *               http://www.goodreads.com/book/show/951750.The_Dying_Earth
     *           </link>
     *           <num_pages>159</num_pages>
     *           <format></format>
     *           <edition_information></edition_information>
     *           <publisher></publisher>
     *           <publication_day>20</publication_day>
     *           <publication_year>1972</publication_year>
     *           <publication_month>4</publication_month>
     *           <average_rating>3.99</average_rating>
     *           <ratings_count>713</ratings_count>
     *           <description>
     *             <![CDATA[]]>
     *           </description>
     *
     *           <authors>
     *             <author>
     *               <id>5376</id>
     *               <name><![CDATA[Jack Vance]]></name>
     *               <image_url>
     *                   <![CDATA[http://photo.goodreads.com/authors/1207604643p5/5376.jpg]]>
     *               </image_url>
     *               <small_image_url>
     *                   <![CDATA[http://photo.goodreads.com/authors/1207604643p2/5376.jpg]]>
     *               </small_image_url>
     *               <link>
     *                   <![CDATA[http://www.goodreads.com/author/show/5376.Jack_Vance]]>
     *               </link>
     *               <average_rating>3.94</average_rating>
     *               <ratings_count>12598</ratings_count>
     *               <text_reviews_count>844</text_reviews_count>
     *             </author>
     *           </authors>
     *           <published>1972</published>
     *         </book>
     *
     *        * <rating>0</rating>
     *        * <votes>0</votes>
     *        * <spoiler_flag>false</spoiler_flag>
     *        * <spoilers_state>none</spoilers_state>
     *        *
     *       <shelves>
     *         <shelf name="sci-fi-fantasy" />
     *         <shelf name="to-read" />
     *       </shelves>
     *
     *       <recommended_for><![CDATA[]]></recommended_for>
     *       <recommended_by><![CDATA[]]></recommended_by>
     *       <started_at></started_at>
     *       <read_at></read_at>
     *       <date_added>Mon Feb 13 05:32:30 -0800 2012</date_added>
     *       <date_updated>Mon Feb 13 05:32:31 -0800 2012</date_updated>
     *       <read_count></read_count>
     *       <body>
     *         <![CDATA[]]>
     *       </body>
     *       <comments_count>0</comments_count>
     *
     *       <url>
     *           <![CDATA[http://www.goodreads.com/review/show/276860380]]>
     *       </url>
     *       <link>
     *           <![CDATA[http://www.goodreads.com/review/show/276860380]]>
     *       </link>
     *       <owned>0</owned>
     *     </review>
     *
     *     <review>
     *       <id>273090417</id>
     *       <book>
     *         <id type="integer">2042540</id>
     *         <isbn>0722129203</isbn>
     *         <isbn13>9780722129203</isbn13>
     *         <text_reviews_count type="integer">0</text_reviews_count>
     *         <title>
     *           <![CDATA[The Fallible Fiend]]>
     *         </title>
     *         <image_url>
     *             http://www.goodreads.com/images/nocover-111x148.jpg
     *         </image_url>
     *         <small_image_url>
     *             http://www.goodreads.com/images/nocover-60x80.jpg
     *         </small_image_url>
     *         <link>
     *             http://www.goodreads.com/book/show/2042540.The_Fallible_Fiend
     *         </link>
     *         <num_pages></num_pages>
     *         <format></format>
     *         <edition_information></edition_information>
     *         <publisher></publisher>
     *         <publication_day></publication_day>
     *         <publication_year></publication_year>
     *         <publication_month></publication_month>
     *
     *         <average_rating>3.55</average_rating>
     *         <ratings_count>71</ratings_count>
     *         <description>
     *           <![CDATA[]]>
     *         </description>
     *
     *         <authors>
     *           <author>
     *             <id>3305</id>
     *             <name>
     *                 <![CDATA[L. Sprague de Camp]]>
     *             </name>
     *             <image_url>
     *                 <![CDATA[http://photo.goodreads.com/authors/1218217726p5/3305.jpg]]>
     *             </image_url>
     *             <small_image_url>
     *                 <![CDATA[http://photo.goodreads.com/authors/1218217726p2/3305.jpg]]>
     *             </small_image_url>
     *             <link>
     *                 <![CDATA[http://www.goodreads.com/author/show/3305.L_Sprague_de_Camp]]>
     *             </link>
     *             <average_rating>3.78</average_rating>
     *             <ratings_count>9424</ratings_count>
     *             <text_reviews_count>441</text_reviews_count>
     *           </author>
     *         </authors>
     *         <published></published>
     *       </book>
     *
     *       <rating>0</rating>
     *       <votes>0</votes>
     *       <spoiler_flag>false</spoiler_flag>
     *       <spoilers_state>none</spoilers_state>
     *       <shelves>
     *         <shelf name="read" />
     *         <shelf name="sci-fi-fantasy" />
     *       </shelves>
     *       <recommended_for><![CDATA[]]></recommended_for>
     *       <recommended_by><![CDATA[]]></recommended_by>
     *       <started_at></started_at>
     *
     *       <read_at></read_at>
     *       <date_added>Mon Feb 06 03:40:52 -0800 2012</date_added>
     *       <date_updated>Mon Feb 06 03:40:52 -0800 2012</date_updated>
     *       <read_count></read_count>
     *       <body>
     *         <![CDATA[]]>
     *       </body>
     *       <comments_count>0</comments_count>
     *
     *       <url><![CDATA[http://www.goodreads.com/review/show/273090417]]></url>
     *       <link><![CDATA[http://www.goodreads.com/review/show/273090417]]></link>
     *       <owned>0</owned>
     *     </review>
     *
     *   </reviews>
     * </GoodreadsResponse>
     * }
     * </pre>
     */
    private void buildFilters() {

        mFilters = new SimpleXmlFilter(mRootFilter, GoodreadsManager.SITE_LOCALE);
        mFilters
                //<GoodreadsResponse>
                .s(XmlTags.XML_GOODREADS_RESPONSE)
                //  <Request>
                //      ...
                //  </Request>
                //  <reviews start="3" end="4" total="933">
                .s(XmlTags.XML_REVIEWS).asArray(Review.REVIEWS)
                .longAttr(XmlTags.XML_START, Review.START)
                .longAttr(XmlTags.XML_END, Review.END)
                .longAttr(XmlTags.XML_TOTAL, Review.TOTAL)
                //      <review>
                .s(XmlTags.XML_REVIEW).asArrayItem()
                //          <id>276860380</id>
                .longBody(XmlTags.XML_ID, Review.REVIEW_ID)
                //          <book>
                .s(XmlTags.XML_BOOK)
                //              <id type="integer">951750</id>
                .longBody(XmlTags.XML_ID, DBDefinitions.KEY_ESID_GOODREADS_BOOK)
                //              <isbn>0583120911</isbn>
                .stringBody(XmlTags.XML_ISBN, DBDefinitions.KEY_ISBN)
                //              <isbn13>9780583120913</isbn13>
                .stringBody(XmlTags.XML_ISBN_13, Review.ISBN13)
                //              ...
                //              <title><![CDATA[The Dying Earth]]></title>
                .stringBody(XmlTags.XML_TITLE, DBDefinitions.KEY_TITLE)
                //              <image_url>
                //      http://photo.goodreads.com/books/1294108593m/951750.jpg</image_url>
                .stringBody(XmlTags.XML_IMAGE_URL, Review.LARGE_IMAGE_URL)
                //              <small_image_url>
                //      http://photo.goodreads.com/books/1294108593s/951750.jpg</small_image_url>
                .stringBody(XmlTags.XML_SMALL_IMAGE_URL, Review.SMALL_IMAGE_URL)
                //              ...
                //              <num_pages>159</num_pages>
                // Note we get this as a LONG to be consistent with the Goodreads type,
                // while our app uses a String. So use a Review and convert later.
                .longBody(XmlTags.XML_NUM_PAGES, Review.PAGES)
                //              <format></format>
                .stringBody(XmlTags.XML_FORMAT, DBDefinitions.KEY_FORMAT)
                //              <publisher></publisher>
                .stringBody(XmlTags.XML_PUBLISHER, Review.PUBLISHER)
                //              <publication_day>20</publication_day>
                .longBody(XmlTags.XML_PUBLICATION_DAY, Review.PUBLICATION_DAY)
                //              <publication_year>1972</publication_year>
                .longBody(XmlTags.XML_PUBLICATION_YEAR, Review.PUBLICATION_YEAR)
                //              <publication_month>4</publication_month>
                .longBody(XmlTags.XML_PUBLICATION_MONTH, Review.PUBLICATION_MONTH)
                //              <description><![CDATA[]]></description>
                .stringBody(XmlTags.XML_DESCRIPTION, DBDefinitions.KEY_DESCRIPTION)
                //              ...
                //              <authors>
                .s(XmlTags.XML_AUTHORS).asArray(Review.AUTHORS)
                //                  <author>
                .s(XmlTags.XML_AUTHOR).asArrayItem()
                //                      <id>5376</id>
                .longBody(XmlTags.XML_ID, DBDefinitions.KEY_FK_AUTHOR)
                //                      <name><![CDATA[Jack Vance]]></name>
                .stringBody(XmlTags.XML_NAME, Review.AUTHOR_NAME_GF)
                //                      <role>Illustrator</role>
                .stringBody(XmlTags.XML_ROLE, Review.AUTHOR_ROLE)
                //                      ...
                //                  </author>
                //              </authors>
                //              ...
                //          </book>
                .popTo(XmlTags.XML_REVIEW)
                //          <rating>0</rating>
                .doubleBody(XmlTags.XML_RATING, DBDefinitions.KEY_RATING)
                //          ...
                //          <shelves>
                .s(XmlTags.XML_SHELVES).asArray(Review.SHELVES)
                //              <shelf name="sci-fi-fantasy" />
                .s(XmlTags.XML_SHELF).asArrayItem()
                // Leave "name" as string... it's an attribute for THIS tag.
                .stringAttr("name", Review.SHELF)
                .popTo(XmlTags.XML_REVIEW)
                //              <shelf name="to-read" />
                //          </shelves>
                //          ...
                //          <started_at></started_at>
                .stringBody(XmlTags.XML_STARTED_AT, DBDefinitions.KEY_READ_START)
                //          <read_at></read_at>
                .stringBody(XmlTags.XML_READ_AT, DBDefinitions.KEY_READ_END)
                //          <date_added>Mon Feb 13 05:32:30 -0800 2012</date_added>
                .s(XmlTags.XML_DATE_ADDED)
                .stringBody(Review.ADDED).setListener(mAddedListener).pop()
                //          <date_updated>Mon Feb 13 05:32:31 -0800 2012</date_updated>
                .s(XmlTags.XML_DATE_UPDATED)
                .stringBody(Review.UPDATED).setListener(mUpdatedListener).pop()
                //          ...
                //          <body><![CDATA[]]></body>
                .stringBody(XmlTags.XML_BODY, Review.BODY).pop()
                //          ...
                //          <owned>0</owned>
                //      </review>
                //  </reviews>
                //
                //</GoodreadsResponse>
                .done();
    }

    /**
     * Goodreads specific field names we add to the bundle based on parsed XML data.
     */
    public static final class Review {

        public static final String TOTAL = "__r_total";
        public static final String ISBN13 = "__r_isbn13";

        public static final String LARGE_IMAGE_URL = "__r_largeImage";
        public static final String SMALL_IMAGE_URL = "__r_smallImage";

        public static final String PUBLICATION_YEAR = "__r_pub_year";
        public static final String PUBLICATION_MONTH = "__r_pub_month";
        public static final String PUBLICATION_DAY = "__r_pub_day";

        /** <date_added>Mon Feb 13 05:32:30 -0800 2012</date_added>. */
        public static final String ADDED = "__r_added";
        /** <date_updated>Mon Feb 13 05:32:31 -0800 2012</date_updated>. */
        public static final String UPDATED = "__r_updated";

        public static final String REVIEWS = "__r_reviews";
        public static final String AUTHORS = "__r_authors";
        public static final String AUTHOR_NAME_GF = "__r_author_name";
        public static final String AUTHOR_ROLE = "__r_author_role";
        public static final String SHELF = "__r_shelf";
        public static final String SHELVES = "__r_shelves";
        public static final String PUBLISHER = "__r_publisher";

        /** Type: long. */
        public static final String PAGES = "__r_pages";

        static final String BODY = "__r_body";
        static final String REVIEW_ID = "__r_review_id";
        static final String START = "__r_start";
        static final String END = "__r_end";

        /** Date format used for parsing the date fields. */
        private static final DateTimeFormatter DATE_PARSER = DateTimeFormatter
                .ofPattern("EEE MMM dd HH:mm:ss ZZZZ yyyy",
                           AppLocale.getInstance().getSystemLocale());

        private Review() {
        }

        @Nullable
        public static LocalDateTime parseDate(@Nullable final String dateStr) {
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(dateStr, Review.DATE_PARSER);
                //FIXME: DateTimeParseException https://issuetracker.google.com/issues/158417777
//            } catch (@NonNull final DateTimeParseException ignore) {
            } catch (@NonNull final RuntimeException ignore) {
                return null;
            }
        }

        /**
         * Check a field for being a valid date.
         * If it's valid, store the reformatted value back into the bundle.
         * If it's invalid, remove the field from the bundle.
         */
        static void validateDate(@NonNull final Bundle bundle,
                                 @NonNull final String key) {
            if (bundle.containsKey(key)) {
                final LocalDateTime date = parseDate(bundle.getString(key));
                if (date != null) {
                    bundle.putString(key, date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } else {
                    bundle.remove(key);
                }
            }
        }

        /**
         * Copy a valid datetime string from the source to the destination bundle.
         * The source bundle is not changed.
         *
         * @return ISO datetime string, or {@code null} if parsing failed
         */
        @Nullable
        public static String copyDateIfValid(@NonNull final Bundle sourceBundle,
                                             @NonNull final String sourceKey,
                                             @NonNull final Book destBundle,
                                             @NonNull final String destKey) {

            final LocalDateTime date = parseDate(sourceBundle.getString(sourceKey));
            if (date != null) {
                final String value = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                destBundle.putString(destKey, value);
                return value;
            }

            return null;
        }
    }
}
