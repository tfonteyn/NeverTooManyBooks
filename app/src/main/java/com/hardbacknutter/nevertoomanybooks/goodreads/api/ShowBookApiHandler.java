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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.function.Consumer;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.AuthorTypeMapper;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.ElementContext;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * <ul>
 *      <li>book.show  —   Get the reviews for a book given a Goodreads book id.</li>
 *      <li>book.show_by_isbn   —   Get the reviews for a book given an ISBN.</li>
 * </ul>
 * <p>
 * This is an abstract class designed to be used by other classes that implement specific
 * search methods. It does the heavy lifting of parsing the results etc.
 * <p>
 * It's very similar to a standard SearchEngine, but the 'bookData' it delivers
 * is Goodreads specific.
 */
public abstract class ShowBookApiHandler
        extends ApiHandler {

    /**
     * Popular shelf names == genre names to skip. The names must be all lowercase.
     */
    private static final Collection<String> sGenreExclusions = new HashSet<>();

    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    private final XmlFilter mRootFilter = new XmlFilter("");
    /** Minimal filter to only extract the cover urls. */
    @NonNull
    private final XmlFilter mCoverFilter = new XmlFilter("");


    @NonNull
    private final Locale mBookLocale;

    @NonNull
    private final String mEBookString;


    /** Local storage for Authors. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** Local storage for Series. */
    @NonNull
    private final ArrayList<Series> mSeries = new ArrayList<>();
    /** Local storage for Publishers. */
    @NonNull
    private final ArrayList<Publisher> mPublishers = new ArrayList<>();

    /** Local storage for Goodreads shelf names. */
    @NonNull
    private final ArrayList<String> mGoodreadsShelves = new ArrayList<>();
    /**
     * Current Shelves being processed.
     */
    private final Consumer<ElementContext> mHandleShelvesStart = ec -> mGoodreadsShelves.clear();
    private final Consumer<ElementContext> mHandleShelf = ec -> {
        // Leave "name" as string... it's an attribute for THIS tag.
        final String name = ec.getAttributes().getValue("name");
        if (name != null) {
            mGoodreadsShelves.add(name);
        }
    };
    private final Consumer<ElementContext> mHandleAuthorListStart = ec -> mAuthors.clear();
    private final Consumer<ElementContext> mHandleSeriesListStart = ec -> mSeries.clear();
    /**
     * Current Publisher being processed.
     */
    private final Consumer<ElementContext> mHandlePublisher = ec -> {
        final String name = ec.getBody();
        if (!name.isEmpty()) {
            final Publisher publisher = Publisher.from(name);
            // bit silly... there is only one publisher on the site,
            // but it's easier to keep the class logic universal.
            mPublishers.clear();
            mPublishers.add(publisher);
        }
    };
    /** Injected by the search method calls. Collects the results. */
    private Bundle mBookData;
    /**
     * Generic handlers.
     */
    private final Consumer<ElementContext> mHandleText = ec -> {
        final String name = (String) ec.getUserArg();
        mBookData.putString(name, ec.getBody());
    };
    private final Consumer<ElementContext> mHandleLong = ec -> {
        final String name = (String) ec.getUserArg();
        try {
            long l = Long.parseLong(ec.getBody());
            mBookData.putLong(name, l);
        } catch (@NonNull final NumberFormatException ignore) {
            // Ignore but don't add
        }
    };
    private final Consumer<ElementContext> mHandleDouble = ec -> {
        final String name = (String) ec.getUserArg();
        try {
            double d = ParseUtils.parseDouble(ec.getBody(), GoodreadsManager.SITE_LOCALE);
            mBookData.putDouble(name, d);
        } catch (@NonNull final NumberFormatException ignore) {
            // Ignore but don't add
        }
    };
    private final Consumer<ElementContext> mHandleBoolean = ec -> {
        final String name = (String) ec.getUserArg();
        try {
            final boolean b = ParseUtils.parseBoolean(ec.getBody(), true);
            mBookData.putBoolean(name, b);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
    };
    /**
     * The highest rated popular shelf name can optionally be used as the genre.
     * We weed out commonly used non-genre names.
     */
    private final Consumer<ElementContext> mHandlePopularShelf = ec -> {
        // only accept one
        if (!mBookData.containsKey(DBDefinitions.KEY_GENRE)) {
            // Leave "name" as string... it's an attribute for THIS tag.
            final String name = ec.getAttributes().getValue("name");
            if (name != null && sGenreExclusions.contains(name.toLowerCase(Locale.ENGLISH))) {
                mBookData.putString(DBDefinitions.KEY_GENRE, name);
            }
        }
    };
    /**
     * Current Author being processed.
     */
    @Nullable
    private String mCurrentAuthorName;
    private final Consumer<ElementContext> mHandleAuthorName = ec -> mCurrentAuthorName = ec
            .getBody();
    @Nullable
    private String mCurrentAuthorRole;
    private final Consumer<ElementContext> mHandleAuthorRole = ec -> mCurrentAuthorRole = ec
            .getBody();
    private final Consumer<ElementContext> mHandleAuthorEnd = ec -> {
        if (mCurrentAuthorName != null && !mCurrentAuthorName.isEmpty()) {
            final Author author = Author.from(mCurrentAuthorName);
            if (mCurrentAuthorRole != null && !mCurrentAuthorRole.isEmpty()) {
                author.setType(AuthorTypeMapper.map(getBookLocale(), mCurrentAuthorRole));
            }
            mAuthors.add(author);
            // reset for next
            mCurrentAuthorName = null;
            mCurrentAuthorRole = null;
        }
    };
    /**
     * Current Series being processed.
     */
    @Nullable
    private String mCurrentSeriesName;
    private final Consumer<ElementContext> mHandleSeriesName = ec -> mCurrentSeriesName = ec
            .getBody();
    @Nullable
    private Integer mCurrentSeriesPosition;
    private final Consumer<ElementContext> mHandleSeriesPosition = ec -> {
        try {
            mCurrentSeriesPosition = Integer.parseInt(ec.getBody());
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
    };
    private final Consumer<ElementContext> mHandleSeriesEnd = ec -> {
        if (mCurrentSeriesName != null && !mCurrentSeriesName.isEmpty()) {
            final Series series = Series.from(mCurrentSeriesName);
            if (mCurrentSeriesPosition != null) {
                series.setNumber(String.valueOf(mCurrentSeriesPosition));
            }
            mSeries.add(series);
            // reset for next
            mCurrentSeriesName = null;
            mCurrentSeriesPosition = null;
        }
    };


    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     *
     * @throws CredentialsException with GoodReads
     */
    ShowBookApiHandler(@NonNull final Context appContext,
                       @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(appContext, grAuth);

        if (sGenreExclusions.isEmpty()) {
            sGenreExclusions.addAll(Arrays.asList(
                    appContext.getResources().getStringArray(R.array.goodreads_genre_exclusions)));
        }

        mGrAuth.hasValidCredentialsOrThrow(appContext);

        mEBookString = appContext.getString(R.string.book_format_ebook);

        // Ideally we should use the Book locale
        mBookLocale = AppLocale.getInstance().getUserLocale(appContext);

        buildFilters();
    }

    @NonNull
    private Locale getBookLocale() {
        return mBookLocale;
    }

    /**
     * Perform a search and extract/fetch only the cover.
     *
     * @param url      url to get
     * @param bookData Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return fileSpec, or {@code null} if no image found.
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @Nullable
    String searchCoverImage(@NonNull final String url,
                            @NonNull final Bundle bookData)
            throws CredentialsException, Http404Exception, IOException {

        mBookData = bookData;

        // Uses an optimized filter
        final DefaultHandler handler = new XmlResponseParser(mCoverFilter);
        executeGet(url, null, true, handler);

        return ApiUtils.handleThumbnail(mAppContext,
                                        mBookData,
                                        SiteField.LARGE_IMAGE_URL,
                                        SiteField.SMALL_IMAGE_URL);
    }

    /**
     * Perform a search and handle the results.
     *
     * @param url            url to get
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return the Bundle of book data.
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @NonNull
    Bundle searchBook(@NonNull final String url,
                      @NonNull final boolean[] fetchThumbnail,
                      @NonNull final Bundle bookData)
            throws CredentialsException, Http404Exception, IOException {

        mBookData = bookData;

        final DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executeGet(url, null, true, handler);

        // When we get here, the data has been collected into mBookData
        // but needs processing into standard form.

        // Use ISBN13 by preference
        if (mBookData.containsKey(SiteField.ISBN13)) {
            final String isbnStr = mBookData.getString(SiteField.ISBN13);
            if (isbnStr != null && isbnStr.length() == 13) {
                mBookData.putString(DBDefinitions.KEY_ISBN, isbnStr);
                mBookData.remove(SiteField.ISBN13);
            }
        }

        // TODO: Evaluate if ShowBook should store SiteField.BOOK_ID.
        // Pros: easier sync
        // Cons: Overwrite Goodreads id when it should not
//        if (mBookData.containsKey(SiteField.BOOK_ID)) {
//            mBookData.putLong(DBDefinitions.KEY_EID_GOODREADS_BOOK,
//                              mBookData.getLong(SiteField.BOOK_ID));
//        }

        // TODO: Evaluate if ShowBook should store SiteField.WORK_ID.
//        if (mBookData.containsKey(SiteField.WORK_ID)) {
//            mBookData.putLong(DBDefinitions.KEY_GOODREADS_WORK_ID,
//                              mBookData.getLong(SiteField.WORK_ID));
//        }

        // Build the FIRST publication date based on the components
        ApiUtils.buildDate(mBookData,
                           SiteField.ORIG_PUBLICATION_YEAR,
                           SiteField.ORIG_PUBLICATION_MONTH,
                           SiteField.ORIG_PUBLICATION_DAY,
                           DBDefinitions.KEY_DATE_FIRST_PUBLICATION);

        // Build the publication date based on the components
        ApiUtils.buildDate(mBookData,
                           SiteField.PUBLICATION_YEAR,
                           SiteField.PUBLICATION_MONTH,
                           SiteField.PUBLICATION_DAY,
                           DBDefinitions.KEY_DATE_PUBLISHED);

        // is it an eBook ? Overwrite the format key
        if (mBookData.containsKey(SiteField.IS_EBOOK)
            && mBookData.getBoolean(SiteField.IS_EBOOK)) {
            mBookData.remove(SiteField.IS_EBOOK);
            mBookData.putString(DBDefinitions.KEY_FORMAT, mEBookString);
        }

        if (mBookData.containsKey(DBDefinitions.KEY_LANGUAGE)) {
            String source = mBookData.getString(DBDefinitions.KEY_LANGUAGE);
            if (source != null && !source.isEmpty()) {
                final Locale userLocale = AppLocale.getInstance().getUserLocale(mAppContext);
                // Goodreads sometimes uses the 2-char code with region code (e.g. "en_GB")
                source = Languages.getInstance().getISO3FromCode(source);
                // and sometimes the alternative 3-char code for specific languages.
                source = Languages.getInstance().toBibliographic(userLocale, source);
                // store the iso3
                mBookData.putString(DBDefinitions.KEY_LANGUAGE, source);
            }
        }

        if (mBookData.containsKey(DBDefinitions.KEY_RATING)) {
            double rating = mBookData.getDouble(DBDefinitions.KEY_RATING);
            if (rating == 0) {
                // we did not have a personal rating, see if we have the average
                if (mBookData.containsKey(SiteField.AVERAGE_RATING)) {
                    rating = mBookData.getDouble(SiteField.AVERAGE_RATING);
                    mBookData.remove(SiteField.AVERAGE_RATING);
                    if (rating > 0) {
                        // use the average rating
                        mBookData.putDouble(DBDefinitions.KEY_RATING, rating);
                    } else {
                        // unlikely: no personal and no average, clear it.
                        mBookData.remove(DBDefinitions.KEY_RATING);
                    }
                }
            }
        }

        // These are Goodreads shelves, not ours.
        // They are used during a sync only where they will be mapped to our own.
        // They are NOT used during simple searches.
        if (!mGoodreadsShelves.isEmpty()) {
            mBookData.putStringArrayList(SiteField.SHELVES, mGoodreadsShelves);
        }


        if (!mAuthors.isEmpty()) {
            mBookData.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            mBookData.putParcelableArrayList(Book.BKEY_SERIES_LIST, mSeries);
        }
        if (!mPublishers.isEmpty()) {
            mBookData.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, mPublishers);
        }

        // It's tempting to always replace KEY_TITLE with SiteField.ORIG_TITLE,
        // but that does bad things to translations (it uses the original language)
        if (mBookData.containsKey(DBDefinitions.KEY_TITLE)) {
            // Cleanup the title by removing Series name, if present
            // Example: "<title>The Anome (Durdane, #1)</title>"
            SearchEngineBase.checkForSeriesNameInTitle(mBookData);
        }

        if (fetchThumbnail[0]) {
            final String fileSpec = ApiUtils.handleThumbnail(mAppContext,
                                                             mBookData,
                                                             SiteField.LARGE_IMAGE_URL,
                                                             SiteField.SMALL_IMAGE_URL);
            if (fileSpec != null) {
                final ArrayList<String> list = new ArrayList<>();
                list.add(fileSpec);
                mBookData.putStringArrayList(SearchCoordinator.BKEY_TMP_FILE_SPEC_ARRAY[0], list);
            }
        }

        return mBookData;
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
     * [snip]
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

        //
        // Simple filter to extract the cover urls only.
        //
        XmlFilter.buildFilter(mCoverFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_IMAGE_URL)
                 .setEndAction(mHandleText, SiteField.LARGE_IMAGE_URL);
        XmlFilter.buildFilter(mCoverFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SMALL_IMAGE_URL)
                 .setEndAction(mHandleText, SiteField.SMALL_IMAGE_URL);


        //
        // The complete filter
        //

        // Goodreads specific ID fields
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ID)
                 .setEndAction(mHandleLong, DBDefinitions.KEY_EID_GOODREADS_BOOK);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ID)
                 .setEndAction(mHandleLong, SiteField.WORK_ID);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_MY_REVIEW,
                              XmlTags.XML_ID)
                 .setEndAction(mHandleLong, SiteField.REVIEW_ID);

        // Amazon ID: <asin></asin>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ASIN)
                 .setEndAction(mHandleText, DBDefinitions.KEY_EID_ASIN);

        // <isbn>0689840926</isbn>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ISBN)
                 .setEndAction(mHandleText, DBDefinitions.KEY_ISBN);
        // <isbn13>9780689840920</isbn13>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_ISBN_13)
                 .setEndAction(mHandleText, SiteField.ISBN13);


        // <title>Hatchet (Hatchet, #1)</title>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_TITLE)
                 .setEndAction(mHandleText, DBDefinitions.KEY_TITLE);
        // <original_title>Hatchet</original_title>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK, XmlTags.XML_ORIGINAL_TITLE)
                 .setEndAction(mHandleText, SiteField.ORIG_TITLE);


        /*      <authors>
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
         */
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AUTHORS)
                 .setStartAction(mHandleAuthorListStart);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AUTHORS, XmlTags.XML_AUTHOR)
                 .setEndAction(mHandleAuthorEnd);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AUTHORS, XmlTags.XML_AUTHOR,
                              XmlTags.XML_NAME)
                 .setEndAction(mHandleAuthorName);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AUTHORS, XmlTags.XML_AUTHOR,
                              XmlTags.XML_ROLE)
                 .setEndAction(mHandleAuthorRole);



        /*      <series_works>
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
         */
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SERIES_WORKS)
                 .setStartAction(mHandleSeriesListStart);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SERIES_WORKS, XmlTags.XML_SERIES_WORK)
                 .setEndAction(mHandleSeriesEnd);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SERIES_WORKS, XmlTags.XML_SERIES_WORK,
                              XmlTags.XML_USER_POSITION)
                 .setEndAction(mHandleSeriesPosition);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SERIES_WORKS, XmlTags.XML_SERIES_WORK,
                              XmlTags.XML_SERIES, XmlTags.XML_TITLE)
                 .setEndAction(mHandleSeriesName);


        // <publisher/>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_PUBLISHER)
                 .setEndAction(mHandlePublisher);


        // <image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_IMAGE_URL)
                 .setEndAction(mHandleText, SiteField.LARGE_IMAGE_URL);
        // <small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_SMALL_IMAGE_URL)
                 .setEndAction(mHandleText, SiteField.SMALL_IMAGE_URL);


        /*      <publication_year>2000</publication_year>
         *      <publication_month>4</publication_month>
         *      <publication_day>1</publication_day>
         */
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_PUBLICATION_YEAR)
                 .setEndAction(mHandleLong, SiteField.PUBLICATION_YEAR);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_PUBLICATION_MONTH)
                 .setEndAction(mHandleLong, SiteField.PUBLICATION_MONTH);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_PUBLICATION_DAY)
                 .setEndAction(mHandleLong, SiteField.PUBLICATION_DAY);


        /*        <original_publication_day type="integer">1</original_publication_day>
         *        <original_publication_month type="integer">1</original_publication_month>
         *        <original_publication_year type="integer">1987</original_publication_year>
         */
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_DAY)
                 .setEndAction(mHandleLong, SiteField.ORIG_PUBLICATION_DAY);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_MONTH)
                 .setEndAction(mHandleLong, SiteField.ORIG_PUBLICATION_MONTH);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_ORIGINAL_PUBLICATION_YEAR)
                 .setEndAction(mHandleLong, SiteField.ORIG_PUBLICATION_YEAR);



        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_COUNTRY_CODE)
                 .setEndAction(mHandleText, SiteField.COUNTRY_CODE);



        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_LANGUAGE)
                 .setEndAction(mHandleText, DBDefinitions.KEY_LANGUAGE);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_DESCRIPTION)
                 .setEndAction(mHandleText, DBDefinitions.KEY_DESCRIPTION);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_NUM_PAGES)
                 .setEndAction(mHandleLong, DBDefinitions.KEY_PAGES);


        // <average_rating>3.57</average_rating>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_AVERAGE_RATING)
                 .setEndAction(mHandleDouble, SiteField.AVERAGE_RATING);
        // <rating>0</rating>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_MY_REVIEW,
                              XmlTags.XML_RATING)
                 .setEndAction(mHandleDouble, DBDefinitions.KEY_RATING);


        // <format>Hardcover</format>
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_FORMAT)
                 .setEndAction(mHandleText, DBDefinitions.KEY_FORMAT);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_IS_EBOOK)
                 .setEndAction(mHandleBoolean, SiteField.IS_EBOOK);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_WORK,
                              XmlTags.XML_MEDIA_TYPE)
                 .setEndAction(mHandleText, SiteField.MEDIA_TYPE);



        /*      <my_review>
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
         * [snip]
         *      </my_review>
         */
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_MY_REVIEW, XmlTags.XML_SHELVES)
                 .setStartAction(mHandleShelvesStart);
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                              XmlTags.XML_MY_REVIEW, XmlTags.XML_SHELVES, XmlTags.XML_SHELF)
                 .setStartAction(mHandleShelf);


        /*
         *     <popular_shelves>
         *        <shelf name="to-read" count="3496"/>
         *        <shelf name="young-adult" count="810"/>
         *        <shelf name="fiction" count="537"/>
         *        <shelf name="currently-reading" count="284"/>
         * [snip]
         *      </popular_shelves>
         *
         * optionally used as genre.
         */
        if (GoodreadsManager.isCollectGenre(mAppContext)) {
            XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XmlTags.XML_BOOK,
                                  XmlTags.XML_POPULAR_SHELVES,
                                  XmlTags.XML_SHELF)
                     .setStartAction(mHandlePopularShelf);
        }
    }

    /**
     * Goodreads specific field names we add to the bundle based on parsed XML data.
     */
    public static final class SiteField {

        public static final String REVIEW_ID = "__review_id";
        public static final String SHELVES = "__shelves";

        static final String WORK_ID = "__work_id";
        //static final String BOOK_URL = "__url";

        static final String ISBN13 = "__isbn13";
        static final String COUNTRY_CODE = "__country_code";
        static final String IS_EBOOK = "__is_ebook";
        static final String ORIG_TITLE = "__orig_title";
        static final String AVERAGE_RATING = "__rating";
        static final String MEDIA_TYPE = "__media";

        static final String LARGE_IMAGE_URL = "__image";
        static final String SMALL_IMAGE_URL = "__smallImage";

        static final String ORIG_PUBLICATION_YEAR = "__orig_pub_year";
        static final String ORIG_PUBLICATION_MONTH = "__orig_pub_month";
        static final String ORIG_PUBLICATION_DAY = "__orig_pub_day";

        static final String PUBLICATION_YEAR = "__pub_year";
        static final String PUBLICATION_MONTH = "__pub_month";
        static final String PUBLICATION_DAY = "__pub_day";

        private SiteField() {
        }
    }
}
