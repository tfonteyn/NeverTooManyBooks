/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.searches.AuthorTypeMapper;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter.XmlHandler;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * <ul>
 *      <li>book.show  —   Get the reviews for a book given a Goodreads book id.</li>
 *      <li>book.show_by_isbn   —   Get the reviews for a book given an ISBN.</li>
 * </ul>
 * <p>
 * This is an abstract class designed to be used by other classes that implement specific
 * search methods. It does the heavy lifting of parsing the results etc.
 */
public abstract class ShowBookApiHandler
        extends ApiHandler {

    /**
     * Popular shelf names == genre names to skip. This list must be all lowercase.
     * <p>
     * Obviously far from complete. For a start non-english names should be added.
     */
    private static List<String> sGenreExclusions;

    // Current Series being processed
    // private int mCurrSeriesId = 0;
    private final XmlHandler mHandleSeriesStart = elementContext -> {
//        mCurrSeries = new Series();
    };
    private final XmlHandler mHandleSeriesId = elementContext -> {
//        try {
//            mCurrSeriesId = Integer.parseInt(context.getBody());
//        } catch (@NonNull final NumberFormatException ignore) {
        // ignore
//        }
    };
    // Current author being processed
    // private long mCurrAuthorId = 0;
    private final XmlHandler mHandleAuthorStart = elementContext -> {
//        mCurrAuthor = new Author();
    };
    private final XmlHandler mHandleAuthorId = elementContext -> {
//        try {
//            mCurrAuthorId = Long.parseLong(context.getBody());
//        } catch (@NonNull final Exception ignore) {
        // ignore
//        }
    };
    @NonNull
    private final String mEBookString;
    private final Locale mLocale;
    /** Global data for the <b>current work</b> in search results. */
    private Bundle mBookData;
    private final XmlHandler mHandleText = elementContext -> {
        final String name = (String) elementContext.getUserArg();
        mBookData.putString(name, elementContext.getBody());
    };
    private final XmlHandler mHandleLong = elementContext -> {
        final String name = (String) elementContext.getUserArg();
        try {
            long l = Long.parseLong(elementContext.getBody());
            mBookData.putLong(name, l);
        } catch (@NonNull final NumberFormatException ignore) {
            // Ignore but don't add
        }
    };
    private final XmlHandler mHandleDouble = elementContext -> {
        final String name = (String) elementContext.getUserArg();
        try {
            double d = ParseUtils.parseDouble(elementContext.getBody(),
                                              GoodreadsHandler.SITE_LOCALE);
            mBookData.putDouble(name, d);
        } catch (@NonNull final NumberFormatException ignore) {
            // Ignore but don't add
        }
    };
    private final XmlHandler mHandleBoolean = elementContext -> {
        final String name = (String) elementContext.getUserArg();
        try {
            String s = elementContext.getBody();
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
            // ignore
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
    private final XmlHandler mHandleShelvesStart = elementContext -> mShelves = new ArrayList<>();
    /**
     * Add a shelf to the array.
     */
    private final XmlHandler mHandleShelf = elementContext -> {
        String name = elementContext.getAttributes().getValue(XmlTags.XML_NAME);
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
    private final XmlHandler mHandlePopularShelf = elementContext -> {
        if (mGenre == null) {
            String name = elementContext.getAttributes().getValue(XmlTags.XML_NAME);
            if (name != null && sGenreExclusions.contains(name.toLowerCase(Locale.ENGLISH))) {
                mGenre = name;
            }
        }
    };
    /** Current author being processed. */
    @Nullable
    private String mCurrAuthorName;
    private final XmlHandler mHandleAuthorName = elementContext ->
            mCurrAuthorName = elementContext.getBody();
    @Nullable
    private String mCurrAuthorRole;
    private final XmlHandler mHandleAuthorRole = elementContext ->
            mCurrAuthorRole = elementContext.getBody();
    private final XmlHandler mHandleAuthorEnd = elementContext -> {
        if (mCurrAuthorName != null && !mCurrAuthorName.isEmpty()) {
            if (mAuthors == null) {
                mAuthors = new ArrayList<>();
            }
            Author author = Author.from(mCurrAuthorName);
            if (mCurrAuthorRole != null && !mCurrAuthorRole.isEmpty()) {
                author.setType(AuthorTypeMapper.map(getLocale(), mCurrAuthorRole));
            }
            mAuthors.add(author);
            mCurrAuthorName = null;
            mCurrAuthorRole = null;
        }
    };
    /** Current Series being processed. */
    @Nullable
    private String mCurrSeriesName;
    private final XmlHandler mHandleSeriesName = elementContext ->
            mCurrSeriesName = elementContext.getBody();
    /** Current Series being processed. */
    @Nullable
    private Integer mCurrSeriesPosition;
    private final XmlHandler mHandleSeriesEnd = elementContext -> {
        if (mCurrSeriesName != null && !mCurrSeriesName.isEmpty()) {
            if (mSeries == null) {
                mSeries = new ArrayList<>();
            }
            if (mCurrSeriesPosition != null) {
                mSeries.add(Series.from(mCurrSeriesName,
                                        String.valueOf(mCurrSeriesPosition)));
            } else {
                mSeries.add(Series.from(mCurrSeriesName));
            }
            mCurrSeriesName = null;
            mCurrSeriesPosition = null;
        }
    };
    private final XmlHandler mHandleSeriesPosition = elementContext -> {
        try {
            mCurrSeriesPosition = Integer.parseInt(elementContext.getBody());
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
    };

    /**
     * Constructor.
     *
     * @param context Current context
     * @param grAuth  Authentication handler
     *
     * @throws CredentialsException with GoodReads
     */
    ShowBookApiHandler(@NonNull final Context context,
                       @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(grAuth);
        if (sGenreExclusions == null) {
            sGenreExclusions = Arrays.asList(
                    context.getResources().getStringArray(R.array.goodreads_genre_exclusions));
        }

        mGoodreadsAuth.hasValidCredentialsOrThrow(context);

        mEBookString = context.getString(R.string.book_format_ebook);

        // Ideally we should use the Book locale
        mLocale = LocaleUtils.getUserLocale(context);

        buildFilters(context);
    }

    private Locale getLocale() {
        // Ideally we should use the Book locale
        return mLocale;
    }

    /**
     * Perform a search and handle the results.
     *
     * @param context        Current context
     * @param url            url to get
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to save results in (passed in to allow mocking)
     *
     * @return the Bundle of book data.
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @NonNull
    Bundle getBookData(@NonNull final Context context,
                       @NonNull final String url,
                       @NonNull final boolean[] fetchThumbnail,
                       @NonNull final Bundle bookData)
            throws CredentialsException, Http404Exception, IOException {

        mBookData = bookData;
        mShelves = null;

        final DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executeGet(url, null, true, handler);

        // When we get here, the data has been collected but needs processing into standard form.

        // Use ISBN13 by preference
        if (mBookData.containsKey(ShowBookFieldName.ISBN13)) {
            final String s = mBookData.getString(ShowBookFieldName.ISBN13);
            if (s != null && s.length() == 13) {
                mBookData.putString(DBDefinitions.KEY_ISBN, s);
            }
        }

        // TODO: Evaluate if ShowBook should store ShowBookFieldName.BOOK_ID.
        // Pros: easier sync
        // Cons: Overwrite Goodreads id when it should not
//        if (mBookData.containsKey(ShowBookFieldName.BOOK_ID)) {
//            mBookData.putLong(DBDefinitions.KEY_EID_GOODREADS_BOOK,
//                              mBookData.getLong(ShowBookFieldName.BOOK_ID));
//        }

        // TODO: Evaluate if ShowBook should store ShowBookFieldName.WORK_ID.
//        if (mBookData.containsKey(ShowBookFieldName.WORK_ID)) {
//            mBookData.putLong(DBDefinitions.KEY_GOODREADS_WORK_ID,
//                              mBookData.getLong(ShowBookFieldName.WORK_ID));
//        }

        // Build the FIRST publication date based on the components
        GoodreadsHandler.buildDate(mBookData,
                                   ShowBookFieldName.ORIG_PUBLICATION_YEAR,
                                   ShowBookFieldName.ORIG_PUBLICATION_MONTH,
                                   ShowBookFieldName.ORIG_PUBLICATION_DAY,
                                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION);

        // Build the publication date based on the components
        GoodreadsHandler.buildDate(mBookData,
                                   ShowBookFieldName.PUBLICATION_YEAR,
                                   ShowBookFieldName.PUBLICATION_MONTH,
                                   ShowBookFieldName.PUBLICATION_DAY,
                                   DBDefinitions.KEY_DATE_PUBLISHED);

        // is it an eBook ? Overwrite the format key
        if (mBookData.containsKey(ShowBookFieldName.IS_EBOOK)
            && mBookData.getBoolean(ShowBookFieldName.IS_EBOOK)) {
            mBookData.putString(DBDefinitions.KEY_FORMAT, mEBookString);
        }

        if (mBookData.containsKey(DBDefinitions.KEY_LANGUAGE)) {
            String source = mBookData.getString(DBDefinitions.KEY_LANGUAGE);
            if (source != null && !source.isEmpty()) {
                Locale locale = LocaleUtils.getUserLocale(context);
                // Goodreads sometimes uses the 2-char code with region code (e.g. "en_GB")
                source = LanguageUtils.getISO3FromCode(source);
                // and sometimes the alternative 3-char code for specific languages.
                source = LanguageUtils.toBibliographic(locale, source);
                // store the iso3
                mBookData.putString(DBDefinitions.KEY_LANGUAGE, source);
            }
        }

        // Cleanup the title by removing Series name, if present
        // Example: "<title>The Anome (Durdane, #1)</title>"
        if (mBookData.containsKey(DBDefinitions.KEY_TITLE)) {
            final String fullTitle = mBookData.getString(DBDefinitions.KEY_TITLE);
            if (fullTitle != null && !fullTitle.isEmpty()) {
                final Matcher matcher = Series.TEXT1_BR_TEXT2_BR_PATTERN.matcher(fullTitle);
                if (matcher.find()) {
                    final String bookTitle = matcher.group(1);
                    final String seriesTitleWithNumber = matcher.group(2);
                    if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                        if (mSeries == null) {
                            mSeries = new ArrayList<>();
                        }
                        final Series newSeries = Series.from(seriesTitleWithNumber);
                        mSeries.add(newSeries);
                        // It's tempting to replace KEY_TITLE with ORIG_TITLE, but that does
                        // bad things to translations (it uses the original language)
                        mBookData.putString(DBDefinitions.KEY_TITLE, bookTitle);
//                        if (mBookData.containsKey(ShowBookFieldName.ORIG_TITLE)) {
//                            mBookData.putString(DBDefinitions.KEY_TITLE,
//                                              mBookData.getString(ShowBookFieldName.ORIG_TITLE));
//                        } else {
//                            mBookData.putString(DBDefinitions.KEY_TITLE, bookTitle);
//                        }
                    }
                }
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
            mBookData.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
        }

        if (mSeries != null && !mSeries.isEmpty()) {
            mBookData.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, mSeries);
        }

        final String publisher = mBookData.getString(ShowBookFieldName.PUBLISHER);
        if (publisher != null && !publisher.isEmpty()) {
            final ArrayList<Publisher> publishers = new ArrayList<>();
            publishers.add(Publisher.from(publisher));
            mBookData.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, publishers);
        }

        // these are Goodreads shelves, not ours.
        if (mShelves != null && !mShelves.isEmpty()) {
            mBookData.putStringArrayList(ShowBookFieldName.SHELVES, mShelves);
        }

        if (fetchThumbnail[0]) {
            handleThumbnail(context);
        }

        return mBookData;
    }

    private void handleThumbnail(@NonNull final Context appContext) {

        // first check what the "best" image is that we have.
        String coverUrl = null;
        if (mBookData.containsKey(ShowBookFieldName.IMAGE_URL)) {
            coverUrl = mBookData.getString(ShowBookFieldName.IMAGE_URL);
            if (!GoodreadsHandler.hasCover(coverUrl)
                && mBookData.containsKey(ShowBookFieldName.SMALL_IMAGE_URL)) {
                coverUrl = mBookData.getString(ShowBookFieldName.SMALL_IMAGE_URL);
                if (!GoodreadsHandler.hasCover(coverUrl)) {
                    coverUrl = null;
                }
            }
        }

        // and if we do have an image, save it using the Goodreads book id as base name.
        if (coverUrl != null) {
            final String tmpName = mBookData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK)
                                   + GoodreadsSearchEngine.FILENAME_SUFFIX;
            final String fileSpec = ImageUtils.saveImage(appContext, coverUrl, tmpName,
                                                         GoodreadsSearchEngine.CONNECT_TIMEOUT_MS,
                                                         GoodreadsSearchEngine.READ_TIMEOUT_MS,
                                                         GoodreadsSearchEngine.THROTTLER);
            if (fileSpec != null) {
                ArrayList<String> list =
                        mBookData.getStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[0]);
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(fileSpec);
                mBookData.putStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[0], list);
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
     *
     * @param context Current context
     */
    private void buildFilters(@NonNull final Context context) {
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ID)
                 .setEndAction(mHandleLong, DBDefinitions.KEY_EID_GOODREADS_BOOK);

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
                 .setEndAction(mHandleText, DBDefinitions.KEY_EID_ASIN);

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
                 .setEndAction(mHandleText, ShowBookFieldName.PUBLISHER);
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
                 .setEndAction(mHandleDouble, ShowBookFieldName.RATING);
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
                              XmlTags.XML_AUTHORS,
                              XmlTags.XML_AUTHOR, XmlTags.XML_ROLE)
                 .setEndAction(mHandleAuthorRole);

        if (GoodreadsHandler.isCollectGenre(context)) {
            XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                                  XmlTags.XML_POPULAR_SHELVES,
                                  XmlTags.XML_SHELF)
                     .setStartAction(mHandlePopularShelf);
        }

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

        static final String PUBLISHER = "__publisher";

        static final String IS_EBOOK = "__is_ebook";
        static final String WORK_ID = "__work_id";
        static final String ORIG_TITLE = "__orig_title";
        static final String RATING = "__rating";

        private ShowBookFieldName() {
        }
    }
}
