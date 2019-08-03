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

package com.hardbacknutter.nevertomanybooks.goodreads.api;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.Format;
import com.hardbacknutter.nevertomanybooks.entities.Series;
import com.hardbacknutter.nevertomanybooks.goodreads.GoodreadsShelf;
import com.hardbacknutter.nevertomanybooks.goodreads.tasks.GoodreadsTasks;
import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertomanybooks.utils.xml.XmlFilter.XmlHandler;
import com.hardbacknutter.nevertomanybooks.utils.xml.XmlResponseParser;

/**
 * <ul>
 * <li>book.show  —   Get the reviews for a book given a Goodreads book id.</li>
 * <li>book.show_by_isbn   —   Get the reviews for a book given an ISBN.</li>
 * </ul>
 * The latter also accepts (with an identical URL) an ASIN.
 * <p>
 * This is an abstract class designed to be used by other classes that implement specific
 * search methods. It does the heavy lifting of parsing the results etc.
 *
 * @author Philip Warner
 */
public abstract class ShowBookApiHandler
        extends ApiHandler {

    // Current series being processed
    // private int mCurrSeriesId = 0;
    private final XmlHandler mHandleSeriesStart = context -> {
//        mCurrSeries = new Series();
    };
    private final XmlHandler mHandleSeriesId = context -> {
//        try {
//            mCurrSeriesId = Integer.parseInt(context.getBody());
//        } catch (@NonNull final NumberFormatException ignore) {
//        }
    };
    // Current author being processed
    // private long mCurrAuthorId = 0;
    private final XmlHandler mHandleAuthorStart = context -> {
//        mCurrAuthor = new Author();
    };
    private final XmlHandler mHandleAuthorId = context -> {
//        try {
//            mCurrAuthorId = Long.parseLong(context.getBody());
//        } catch (@NonNull final Exception ignore) {
//        }
    };

    /** Transient global data for current work in search results. */
    private Bundle mBookData;

    private final XmlHandler mHandleText = context -> {
        final String name = (String) context.getUserArg();
        mBookData.putString(name, context.getBody());
    };

    private final XmlHandler mHandleLong = context -> {
        final String name = (String) context.getUserArg();
        try {
            long l = Long.parseLong(context.getBody());
            mBookData.putLong(name, l);
        } catch (@NonNull final NumberFormatException ignore) {
            // Ignore but don't add
        }
    };

    private final XmlHandler mHandleFloat = context -> {
        final String name = (String) context.getUserArg();
        try {
            double d = Double.parseDouble(context.getBody());
            mBookData.putDouble(name, d);
        } catch (@NonNull final NumberFormatException ignore) {
            // Ignore but don't add
        }
    };

    private final XmlHandler mHandleBoolean = context -> {
        final String name = (String) context.getUserArg();
        try {
            String s = context.getBody();
            boolean b;
            if (s.isEmpty() || "false".equalsIgnoreCase(s) || "f".equalsIgnoreCase(s)) {
                b = false;
            } else if ("true".equalsIgnoreCase(s) || "t".equalsIgnoreCase(s)) {
                b = true;
            } else {
                b = Long.parseLong(s) != 0;
            }
            mBookData.putBoolean(name, b);
        } catch (@NonNull final NumberFormatException ignore) {
        }
    };

    /** Local storage for Series the book appears in. */
    @Nullable
    private ArrayList<Series> mSeries;
    /** Local storage for Authors. */
    @Nullable
    private ArrayList<Author> mAuthors;
    /** Local storage for shelf names. */
    @Nullable
    private ArrayList<String> mShelves;
    /**
     * Create a new shelves collection when the "shelves" tag is encountered.
     */
    private final XmlHandler mHandleShelvesStart = context -> mShelves = new ArrayList<>();
    /**
     * Add a shelf to the array.
     */
    private final XmlHandler mHandleShelf = context -> {
        String name = context.getAttributes().getValue(XmlTags.XML_NAME);
        if (name != null) {
            mShelves.add(name);
        }
    };
    /** Local storage for genre aka highest rated popular shelf name. */
    @Nullable
    private String mGenre;
    /**
     * Popular shelves are Goodreads "genres". They come in descending order.
     *
     * <pre>
     *   {@code
     *     <popular_shelves>
     *       <shelf name="to-read" count="43843"/>
     *       <shelf name="currently-reading" count="4209"/>
     *       <shelf name="young-adult" count="2469"/>
     *       <shelf name="fiction" count="1996"/>
     *       ...
     *   }
     * </pre>
     * <p>
     * We skip the virtual shelves.
     */
    private final XmlHandler mHandlePopularShelf = context -> {
        if (mGenre == null) {
            String name = context.getAttributes().getValue(XmlTags.XML_NAME);
            if (name != null
                    && !GoodreadsShelf.VIRTUAL_TO_READ.equals(name)
                    && !GoodreadsShelf.VIRTUAL_CURRENTLY_READING.equals(name)
                    && !GoodreadsShelf.VIRTUAL_READ.equals(name)
            ) {
                mGenre = name;
            }
        }
    };

    /** Current author being processed. */
    @Nullable
    private String mCurrAuthorName;
    private final XmlHandler mHandleAuthorEnd = context -> {
        if (mCurrAuthorName != null && !mCurrAuthorName.isEmpty()) {
            if (mAuthors == null) {
                mAuthors = new ArrayList<>();
            }
            mAuthors.add(Author.fromString(mCurrAuthorName));
            mCurrAuthorName = null;
        }
    };
    private final XmlHandler mHandleAuthorName = context -> mCurrAuthorName = context.getBody();
    /** Current series being processed. */
    @Nullable
    private String mCurrSeriesName;
    private final XmlHandler mHandleSeriesName = context -> mCurrSeriesName = context.getBody();
    /** Current series being processed. */
    @Nullable
    private Integer mCurrSeriesPosition;
    private final XmlHandler mHandleSeriesEnd = context -> {
        if (mCurrSeriesName != null && !mCurrSeriesName.isEmpty()) {
            if (mSeries == null) {
                mSeries = new ArrayList<>();
            }
            if (mCurrSeriesPosition == null) {
                mSeries.add(new Series(mCurrSeriesName));
            } else {
                Series newSeries = new Series(mCurrSeriesName);
                newSeries.setNumber(
                        Series.cleanupSeriesPosition(String.valueOf(mCurrSeriesPosition)));
                mSeries.add(newSeries);
            }
            mCurrSeriesName = null;
            mCurrSeriesPosition = null;
        }
    };
    private final XmlHandler mHandleSeriesPosition = context -> {
        try {
            mCurrSeriesPosition = Integer.parseInt(context.getBody());
        } catch (@NonNull final NumberFormatException ignore) {
        }
    };

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     *
     * @throws CredentialsException with GoodReads
     */
    ShowBookApiHandler(@NonNull final GoodreadsManager grManager)
            throws CredentialsException {
        super(grManager);
        if (!grManager.hasValidCredentials()) {
            throw new CredentialsException(R.string.goodreads);
        }

        buildFilters();
    }

    /**
     * Perform a search and handle the results.
     *
     * @param url            url to get
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     *
     * @return the Bundle of book data.
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    Bundle getBookData(@NonNull final String url,
                       final boolean fetchThumbnail)
            throws CredentialsException, BookNotFoundException, IOException {

        mBookData = new Bundle();
        mShelves = null;

        //TODO: should be using a user context.
        Context userContext = App.getAppContext();
        Resources resources = LocaleUtils.getLocalizedResources(userContext,
                LocaleUtils.getPreferredLocale(userContext));
        String ebook = resources.getString(R.string.book_format_ebook);

        DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executeGet(url, null, true, handler);

        // When we get here, the data has been collected but needs processing into standard form.

        // Use ISBN13 by preference
        if (mBookData.containsKey(ShowBookFieldName.ISBN13)) {
            String s = mBookData.getString(ShowBookFieldName.ISBN13);
            if (s != null && s.length() == 13) {
                mBookData.putString(DBDefinitions.KEY_ISBN, s);
            }
        }

        // TODO: Evaluate if ShowBook should store ShowBookFieldName.BOOK_ID.
        // Pros: easier sync
        // Cons: Overwrite GR id when it should not
//        if (mBookData.containsKey(ShowBookFieldName.BOOK_ID)) {
//            mBookData.putLong(DBDefinitions.KEY_GOODREADS_BOOK_ID,
//                              mBookData.getLong(ShowBookFieldName.BOOK_ID));
//        }

        // TODO: Evaluate if ShowBook should store ShowBookFieldName.WORK_ID.
//        if (mBookData.containsKey(ShowBookFieldName.WORK_ID)) {
//            mBookData.putLong(DBDefinitions.KEY_GOODREADS_WORK_ID,
//                              mBookData.getLong(ShowBookFieldName.WORK_ID));
//        }

        // Build the FIRST publication date based on the components
        GoodreadsManager.buildDate(mBookData,
                                   ShowBookFieldName.ORIG_PUBLICATION_YEAR,
                                   ShowBookFieldName.ORIG_PUBLICATION_MONTH,
                                   ShowBookFieldName.ORIG_PUBLICATION_DAY,
                                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION);

        // Build the publication date based on the components
        GoodreadsManager.buildDate(mBookData,
                                   ShowBookFieldName.PUBLICATION_YEAR,
                                   ShowBookFieldName.PUBLICATION_MONTH,
                                   ShowBookFieldName.PUBLICATION_DAY,
                                   DBDefinitions.KEY_DATE_PUBLISHED);

        // is it an eBook ? Overwrite the format key
        if (mBookData.containsKey(ShowBookFieldName.IS_EBOOK)
                && mBookData.getBoolean(ShowBookFieldName.IS_EBOOK)) {
            mBookData.putString(DBDefinitions.KEY_FORMAT, ebook);

        } else if (mBookData.containsKey(DBDefinitions.KEY_FORMAT)) {
            // normalise the format
            String source = mBookData.getString(DBDefinitions.KEY_FORMAT);
            if (source != null && !source.isEmpty()) {
                mBookData.putString(DBDefinitions.KEY_FORMAT, Format.map(userContext, source));
            }
        }

        if (mBookData.containsKey(DBDefinitions.KEY_LANGUAGE)) {
            String source = mBookData.getString(DBDefinitions.KEY_LANGUAGE);
            if (source != null && !source.isEmpty()) {
                mBookData.putString(DBDefinitions.KEY_LANGUAGE,
                                    LocaleUtils.getISO3LanguageFromISO2(source));
            }
        }

        // Cleanup the title by removing series name, if present
        // Example: "<title>The Anome (Durdane, #1)</title>"
        if (mBookData.containsKey(DBDefinitions.KEY_TITLE)) {
            String thisTitle = mBookData.getString(DBDefinitions.KEY_TITLE);
            Series.SeriesDetails details = Series.findSeriesFromBookTitle(thisTitle);
            if (details != null && !details.getTitle().isEmpty()) {
                if (mSeries == null) {
                    mSeries = new ArrayList<>();
                }
                Series newSeries = new Series(details.getTitle());
                newSeries.setNumber(details.getPosition());
                mSeries.add(newSeries);
                // It's tempting to replace KEY_TITLE with ORIG_TITLE, but that does
                // bad things to translations (it uses the original language)
                mBookData.putString(DBDefinitions.KEY_TITLE,
                                    thisTitle.substring(0, details.startChar - 1));
//                if (mBookData.containsKey(ShowBookFieldName.ORIG_TITLE)) {
//                    mBookData.putString(DBDefinitions.KEY_TITLE,
//                                        mBookData.getString(ShowBookFieldName.ORIG_TITLE));
//                } else {
//                    mBookData.putString(DBDefinitions.KEY_TITLE,
//                                        thisTitle.substring(0, details.startChar - 1));
//                }
            }
        } else if (mBookData.containsKey(ShowBookFieldName.ORIG_TITLE)) {
            // if we did not get a title at all, but there is an original title, use that.
            mBookData.putString(DBDefinitions.KEY_TITLE,
                                mBookData.getString(ShowBookFieldName.ORIG_TITLE));
        }

        if (mGenre != null) {
            mBookData.putString(DBDefinitions.KEY_GENRE, mGenre);
        }

        if (mAuthors != null && !mAuthors.isEmpty()) {
            mBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        }

        if (mSeries != null && !mSeries.isEmpty()) {
            mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);
        }

        // these are Goodreads shelves, not ours.
        if (mShelves != null && !mShelves.isEmpty()) {
            mBookData.putStringArrayList(ShowBookFieldName.SHELVES, mShelves);
        }

        if (fetchThumbnail) {
            handleThumbnail();
        }

        return mBookData;
    }

    private void handleThumbnail() {

        // first check what the "best" image is that we have.
        String bestImage = null;
        if (mBookData.containsKey(ShowBookFieldName.IMAGE_URL)) {
            bestImage = mBookData.getString(ShowBookFieldName.IMAGE_URL);
            if (!GoodreadsTasks.hasCover(bestImage)
                    && mBookData.containsKey(ShowBookFieldName.SMALL_IMAGE_URL)) {
                bestImage = mBookData.getString(ShowBookFieldName.SMALL_IMAGE_URL);
                if (!GoodreadsTasks.hasCover(bestImage)) {
                    bestImage = null;
                }
            }
        }

        // and if we do have an image, save it using the Goodreads book ID as base name.
        if (bestImage != null) {
            long grBookId = mBookData.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID);
            String fileSpec = ImageUtils.saveImage(bestImage, String.valueOf(grBookId),
                                                   GoodreadsManager.FILENAME_SUFFIX);
            if (fileSpec != null) {
                ArrayList<String> list =
                        mBookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(fileSpec);
                mBookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, list);
            }
        }
    }

    /**
     * Setup filters to process the XML parts we care about.
     * <p>
     * Typical result:
     * <pre>
     * {@code
     *  <GoodreadsResponse>
     *    <Request>
     *      <authentication>true</authentication>
     *      <key>...</key>
     *      <method>book_show</method>
     *    </Request>
     *
     *    <book>
     *      <id>50</id>
     *      <title>Hatchet (Hatchet, #1)</title>
     *      <isbn>0689840926</isbn>
     *      <isbn13>9780689840920</isbn13>
     *      <asin></asin>
     *      <image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
     *      <small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>
     *      <publication_year>2000</publication_year>
     *      <publication_month>4</publication_month>
     *      <publication_day>1</publication_day>
     *      <publisher/>
     *      <language_code/>
     *      <is_ebook>false</is_ebook>
     *      <description>
     *        <p>Since it was first published in 1987, blah blah....</p></p>
     *      </description>
     *      <work>
     *        <best_book_id type="integer">50</best_book_id>
     *        <books_count type="integer">47</books_count>
     *        <id type="integer">1158125</id>
     *        <media_type>book</media_type>
     *        <original_language_id type="integer" nil="true"/>
     *        <original_publication_day type="integer">1</original_publication_day>
     *        <original_publication_month type="integer">1</original_publication_month>
     *        <original_publication_year type="integer">1987</original_publication_year>
     *        <original_title>Hatchet</original_title>
     *        <rating_dist>5:12626|4:17440|3:15621|2:6008|1:2882|total:54577</rating_dist>
     *        <ratings_count type="integer">54545</ratings_count>
     *        <ratings_sum type="integer">194541</ratings_sum>
     *        <reviews_count type="integer">64752</reviews_count>
     *        <text_reviews_count type="integer">3705</text_reviews_count>
     *      </work>
     *      <average_rating>3.57</average_rating>
     *      <num_pages>208</num_pages>
     *      <format>Hardcover</format>
     *      <edition_information></edition_information>
     *      <ratings_count>51605</ratings_count>
     *      <text_reviews_count>3299</text_reviews_count>
     *      <url>http://www.goodreads.com/book/show/50.Hatchet</url>
     *      <link>http://www.goodreads.com/book/show/50.Hatchet</link>
     *      <authors>
     *        <author>
     *          <id>18</id>
     *          <name>Gary Paulsen</name>
     *          <image_url>http://photo.goodreads.com/authors/1309159225p5/18.jpg</image_url>
     *          <small_image_url>
     *              http://photo.goodreads.com/authors/1309159225p2/18.jpg
     *          </small_image_url>
     *          <link>http://www.goodreads.com/author/show/18.Gary_Paulsen</link>
     *          <average_rating>3.64</average_rating>
     *          <ratings_count>92755</ratings_count>
     *          <text_reviews_count>9049</text_reviews_count>
     *        </author>
     *      </authors>
     *      <my_review>
     *        <id>255221284</id>
     *        <rating>0</rating>
     *        <votes>0</votes>
     *        <spoiler_flag>false</spoiler_flag>
     *        <spoilers_state>none</spoilers_state>
     *        <shelves>
     *          <shelf name="sci-fi-fantasy"/>
     *          <shelf name="to-read"/>
     *          <shelf name="default"/>
     *          <shelf name="environment"/>
     *          <shelf name="games"/>
     *          <shelf name="history"/>
     *        </shelves>
     *        <recommended_for></recommended_for>
     *        <recommended_by></recommended_by>
     *        <started_at/>
     *        <read_at/>
     *        <date_added>Mon Jan 02 19:07:11 -0800 2012</date_added>
     *        <date_updated>Sat Mar 03 08:10:09 -0800 2012</date_updated>
     *        <read_count/>
     *        <body>Test again</body>
     *        <comments_count>0</comments_count>
     *        <url>http://www.goodreads.com/review/show/255221284</url>
     *        <link>http://www.goodreads.com/review/show/255221284</link>
     *        <owned>0</owned>
     *      </my_review>
     *      <friend_reviews>
     *      </friend_reviews>
     *      <reviews_widget>....</reviews_widget>
     *      <popular_shelves>
     *        <shelf name="to-read" count="3496"/>
     *        <shelf name="young-adult" count="810"/>
     *        <shelf name="fiction" count="537"/>
     *        <shelf name="currently-reading" count="284"/>
     *        <shelf name="adventure" count="247"/>
     *        <shelf name="childrens" count="233"/>
     *        <shelf name="ya" count="179"/>
     *        <shelf name="survival" count="170"/>
     *        <shelf name="favorites" count="164"/>
     *        <shelf name="classics" count="155"/>
     *      </popular_shelves>
     *      <book_links>
     *        <book_link>
     *          <id>3</id>
     *          <name>Barnes & Noble</name>
     *          <link>http://www.goodreads.com/book_link/follow/3?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *          <id>8</id>
     *          <name>WorldCat</name>
     *          <link>http://www.goodreads.com/book_link/follow/8?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *          <id>1027</id>
     *          <name>Kobo</name>
     *          <link>http://www.goodreads.com/book_link/follow/1027?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *          <id>9</id>
     *          <name>Indigo</name>
     *          <link>http://www.goodreads.com/book_link/follow/9?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>4</id>
     *            <name>Abebooks</name>
     *            <link>http://www.goodreads.com/book_link/follow/4?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>2</id>
     *            <name>Half.com</name>
     *            <link>http://www.goodreads.com/book_link/follow/2?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>10</id>
     *            <name>Audible</name>
     *            <link>http://www.goodreads.com/book_link/follow/10?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>5</id>
     *            <name>Alibris</name>
     *            <link>http://www.goodreads.com/book_link/follow/5?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>2102</id>
     *            <name>iBookstore</name
     *            ><link>http://www.goodreads.com/book_link/follow/2102?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>1602</id>
     *            <name>Google eBooks</name>
     *            <link>http://www.goodreads.com/book_link/follow/1602?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>107</id>
     *            <name>Better World Books</name>
     *            <link>http://www.goodreads.com/book_link/follow/107?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>7</id>
     *            <name>IndieBound</name>
     *            <link>http://www.goodreads.com/book_link/follow/7?book_id=50</link>
     *        </book_link>
     *        <book_link>
     *            <id>1</id>
     *            <name>Amazon</name>
     *            <link>http://www.goodreads.com/book_link/follow/1?book_id=50</link>
     *        </book_link>
     *      </book_links>
     *      <series_works>
     *        <series_work>
     *          <id>268218</id>
     *          <user_position>1</user_position>
     *          <series>
     *            <id>62223</id>
     *            <title>Brian's Saga</title>
     *            <description></description>
     *            <note></note>
     *            <series_works_count>7</series_works_count>
     *            <primary_work_count>5</primary_work_count>
     *            <numbered>true</numbered>
     *          </series>
     *        </series_work>
     *      </series_works>
     *    </book>
     *  </GoodreadsResponse>
     * }
     * </pre>
     */
    private void buildFilters() {
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ID)
                 .setEndAction(mHandleLong, DBDefinitions.KEY_GOODREADS_BOOK_ID);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_TITLE)
                 .setEndAction(mHandleText, DBDefinitions.KEY_TITLE);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AUTHORS,
                              XmlTags.XML_AUTHOR)
                 .setStartAction(mHandleAuthorStart)
                 .setEndAction(mHandleAuthorEnd);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SERIES_WORKS,
                              XmlTags.XML_SERIES_WORK)
                 .setStartAction(mHandleSeriesStart)
                 .setEndAction(mHandleSeriesEnd);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ISBN)
                 .setEndAction(mHandleText, DBDefinitions.KEY_ISBN);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ISBN_13)
                 .setEndAction(mHandleText, ShowBookFieldName.ISBN13);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ASIN)
                 .setEndAction(mHandleText, DBDefinitions.KEY_ASIN);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_IMAGE_URL)
                 .setEndAction(mHandleText, ShowBookFieldName.IMAGE_URL);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SMALL_IMAGE_URL)
                 .setEndAction(mHandleText, ShowBookFieldName.SMALL_IMAGE_URL);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_PUBLICATION_YEAR)
                 .setEndAction(mHandleLong, ShowBookFieldName.PUBLICATION_YEAR);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_PUBLICATION_MONTH)
                 .setEndAction(mHandleLong, ShowBookFieldName.PUBLICATION_MONTH);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_PUBLICATION_DAY)
                 .setEndAction(mHandleLong, ShowBookFieldName.PUBLICATION_DAY);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_PUBLISHER)
                 .setEndAction(mHandleText, DBDefinitions.KEY_PUBLISHER);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_COUNTRY_CODE)
                 .setEndAction(mHandleText, ShowBookFieldName.COUNTRY_CODE);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_LANGUAGE)
                 .setEndAction(mHandleText, DBDefinitions.KEY_LANGUAGE);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_IS_EBOOK)
                 .setEndAction(mHandleBoolean, ShowBookFieldName.IS_EBOOK);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_DESCRIPTION)
                 .setEndAction(mHandleText, DBDefinitions.KEY_DESCRIPTION);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ID)
                 .setEndAction(mHandleLong, ShowBookFieldName.WORK_ID);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_DAY)
                 .setEndAction(mHandleLong, ShowBookFieldName.ORIG_PUBLICATION_DAY);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_MONTH)
                 .setEndAction(mHandleLong, ShowBookFieldName.ORIG_PUBLICATION_MONTH);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_YEAR)
                 .setEndAction(mHandleLong, ShowBookFieldName.ORIG_PUBLICATION_YEAR);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_TITLE)
                 .setEndAction(mHandleText, ShowBookFieldName.ORIG_TITLE);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AVERAGE_RATING)
                 .setEndAction(mHandleFloat, ShowBookFieldName.RATING);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_NUM_PAGES)
                 .setEndAction(mHandleLong, DBDefinitions.KEY_PAGES);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_FORMAT)
                 .setEndAction(mHandleText, DBDefinitions.KEY_FORMAT);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_URL)
                 .setEndAction(mHandleText, ShowBookFieldName.BOOK_URL);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AUTHORS,
                              XmlTags.XML_AUTHOR, XmlTags.XML_ID)
                 .setEndAction(mHandleAuthorId);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AUTHORS,
                              XmlTags.XML_AUTHOR, XmlTags.XML_NAME)
                 .setEndAction(mHandleAuthorName);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_POPULAR_SHELVES,
                              XmlTags.XML_SHELF)
                 .setStartAction(mHandlePopularShelf);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_MY_REVIEW,
                              XmlTags.XML_ID)
                 .setEndAction(mHandleLong, ShowBookFieldName.REVIEW_ID);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_MY_REVIEW,
                              XmlTags.XML_SHELVES)
                 .setStartAction(mHandleShelvesStart);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_MY_REVIEW,
                              XmlTags.XML_SHELVES, XmlTags.XML_SHELF)
                 .setStartAction(mHandleShelf);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SERIES_WORKS,
                              XmlTags.XML_SERIES_WORK, XmlTags.XML_USER_POSITION)
                 .setEndAction(mHandleSeriesPosition);

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SERIES_WORKS,
                              XmlTags.XML_SERIES_WORK, XmlTags.XML_SERIES, XmlTags.XML_ID)
                 .setEndAction(mHandleSeriesId);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SERIES_WORKS,
                              XmlTags.XML_SERIES_WORK, XmlTags.XML_SERIES, XmlTags.XML_TITLE)
                 .setEndAction(mHandleSeriesName);
    }

    /**
     * Goodreads specific field names we add to the bundle based on parsed XML data.
     */
    public static final class ShowBookFieldName {

        public static final String SHELVES = "__shelves";
        public static final String REVIEW_ID = "__review_id";

        static final String ISBN13 = "__isbn13";

        static final String IMAGE_URL = "__image";
        static final String SMALL_IMAGE_URL = "__smallImage";
        static final String BOOK_URL = "__url";

        static final String ORIG_PUBLICATION_YEAR = "__orig_pub_year";
        static final String ORIG_PUBLICATION_MONTH = "__orig_pub_month";
        static final String ORIG_PUBLICATION_DAY = "__orig_pub_day";

        static final String PUBLICATION_YEAR = "__pub_year";
        static final String PUBLICATION_MONTH = "__pub_month";
        static final String PUBLICATION_DAY = "__pub_day";
        static final String COUNTRY_CODE = "__country_code";

        static final String IS_EBOOK = "__is_ebook";
        static final String WORK_ID = "__work_id";
        static final String ORIG_TITLE = "__orig_title";
        static final String RATING = "__rating";

        private ShowBookFieldName() {
        }
    }
}
