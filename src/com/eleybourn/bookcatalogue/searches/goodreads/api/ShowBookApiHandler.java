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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Class to query and response to search.books api call. This is an abstract class
 * designed to be used by other classes that implement specific search methods. It does
 * the heavy lifting of parsing the results etc.
 *
 * @author Philip Warner
 */
public abstract class ShowBookApiHandler
        extends ApiHandler {

    /**
     * Options to indicate if request should be signed. Signed requests via ISB cause server errors
     * and unsigned requests do not return review (not a big problem for searches)
     */
    private final boolean mSignRequest;

    /*
     * Typical result:
        <pre>
            <GoodreadsResponse>
                <Request>
                    <authentication>true</authentication>
                    <key>GJ59HZyvOM5KGm6Wn8GDzg</key>
                    <method>book_show</method>
                </Request>
                <book>
                    <id>50</id>
                    <title>Hatchet (Hatchet, #1)</title>
                    <isbn>0689840926</isbn>
                    <isbn13>9780689840920</isbn13>
                    <asin></asin>
                    <image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
                    <small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>
                    <publication_year>2000</publication_year>
                    <publication_month>4</publication_month>
                    <publication_day>1</publication_day>
                    <publisher/>
                    <language_code/>
                    <is_ebook>false</is_ebook>
                    <description>
                        <p>Since it was first published in 1987, blah blah....</p></p>
                     </description>
                    <work>
                        <best_book_id type="integer">50</best_book_id>
                        <books_count type="integer">47</books_count>
                        <id type="integer">1158125</id>
                        <media_type>book</media_type>
                        <original_language_id type="integer" nil="true"/>
                        <original_publication_day type="integer">1</original_publication_day>
                        <original_publication_month type="integer">1</original_publication_month>
                        <original_publication_year type="integer">1987</original_publication_year>
                        <original_title>Hatchet</original_title>
                        <rating_dist>5:12626|4:17440|3:15621|2:6008|1:2882|total:54577</rating_dist>
                        <ratings_count type="integer">54545</ratings_count>
                        <ratings_sum type="integer">194541</ratings_sum>
                        <reviews_count type="integer">64752</reviews_count>
                        <text_reviews_count type="integer">3705</text_reviews_count>
                    </work>
                    <average_rating>3.57</average_rating>
                    <num_pages>208</num_pages>
                    <format>Hardcover</format>
                    <edition_information></edition_information>
                    <ratings_count>51605</ratings_count>
                    <text_reviews_count>3299</text_reviews_count>
                    <url>http://www.goodreads.com/book/show/50.Hatchet</url>
                    <link>http://www.goodreads.com/book/show/50.Hatchet</link>
                    <authors>
                        <author>
                            <id>18</id>
                            <name>Gary Paulsen</name>
                            <image_url>http://photo.goodreads.com/authors/1309159225p5/18.jpg</image_url>
                            <small_image_url>http://photo.goodreads.com/authors/1309159225p2/18.jpg</small_image_url>
                            <link>http://www.goodreads.com/author/show/18.Gary_Paulsen</link>
                            <average_rating>3.64</average_rating>
                            <ratings_count>92755</ratings_count>
                            <text_reviews_count>9049</text_reviews_count>
                        </author>
                    </authors>
                    <my_review>
                        <id>255221284</id>
                        <rating>0</rating>
                        <votes>0</votes>
                        <spoiler_flag>false</spoiler_flag>
                        <spoilers_state>none</spoilers_state>
                        <shelves>
                            <shelf name="sci-fi-fantasy"/>
                            <shelf name="to-read"/>
                            <shelf name="default"/>
                            <shelf name="environment"/>
                            <shelf name="games"/>
                            <shelf name="history"/>
                        </shelves>
                        <recommended_for></recommended_for>
                        <recommended_by></recommended_by>
                        <started_at/>
                        <read_at/>
                        <date_added>Mon Jan 02 19:07:11 -0800 2012</date_added>
                        <date_updated>Sat Mar 03 08:10:09 -0800 2012</date_updated>
                        <read_count/>
                        <body>Test again</body>
                        <comments_count>0</comments_count>
                        <url>http://www.goodreads.com/review/show/255221284</url>
                        <link>http://www.goodreads.com/review/show/255221284</link>
                        <owned>0</owned>
                    </my_review>
                       <friend_reviews>
                    </friend_reviews>
                    <reviews_widget>....</reviews_widget>
                    <popular_shelves>
                        <shelf name="to-read" count="3496"/>
                        <shelf name="young-adult" count="810"/>
                        <shelf name="fiction" count="537"/>
                        <shelf name="currently-reading" count="284"/>
                        <shelf name="adventure" count="247"/>
                        <shelf name="childrens" count="233"/>
                        <shelf name="ya" count="179"/>
                        <shelf name="survival" count="170"/>
                        <shelf name="favorites" count="164"/>
                        <shelf name="classics" count="155"/>
                    </popular_shelves>
                    <book_links>
                        <book_link>
                            <id>3</id>
                            <name>Barnes & Noble</name>
                            <link>http://www.goodreads.com/book_link/follow/3?book_id=50</link>
                        </book_link>
                        <book_link>
                            <id>8</id>
                            <name>WorldCat</name>
                            <link>http://www.goodreads.com/book_link/follow/8?book_id=50</link>
                        </book_link>
                        <book_link>
                            <id>1027</id>
                            <name>Kobo</name>
                            <link>http://www.goodreads.com/book_link/follow/1027?book_id=50</link>
                        </book_link>
                        <book_link>
                            <id>9</id>
                            <name>Indigo</name>
                            <link>http://www.goodreads.com/book_link/follow/9?book_id=50</link>
                        </book_link>
                        <book_link><id>4</id><name>Abebooks</name><link>http://www.goodreads.com/book_link/follow/4?book_id=50</link></book_link>
                        <book_link><id>2</id><name>Half.com</name><link>http://www.goodreads.com/book_link/follow/2?book_id=50</link></book_link>
                        <book_link><id>10</id><name>Audible</name><link>http://www.goodreads.com/book_link/follow/10?book_id=50</link></book_link>
                        <book_link><id>5</id><name>Alibris</name><link>http://www.goodreads.com/book_link/follow/5?book_id=50</link></book_link>
                        <book_link><id>2102</id><name>iBookstore</name><link>http://www.goodreads.com/book_link/follow/2102?book_id=50</link></book_link>
                        <book_link><id>1602</id><name>Google eBooks</name><link>http://www.goodreads.com/book_link/follow/1602?book_id=50</link></book_link>
                        <book_link><id>107</id><name>Better World Books</name><link>http://www.goodreads.com/book_link/follow/107?book_id=50</link></book_link>
                        <book_link><id>7</id><name>IndieBound</name><link>http://www.goodreads.com/book_link/follow/7?book_id=50</link></book_link>
                        <book_link><id>1</id><name>Amazon</name><link>http://www.goodreads.com/book_link/follow/1?book_id=50</link></book_link>
                    </book_links>
                    <series_works>
                        <series_work>
                            <id>268218</id>
                            <user_position>1</user_position>
                            <series>
                                <id>62223</id>
                                <title>Brian's Saga</title>
                                <description></description>
                                <note></note>
                                <series_works_count>7</series_works_count>
                                <primary_work_count>5</primary_work_count>
                                <numbered>true</numbered>
                            </series>
                        </series_work>
                    </series_works>
                </book>
            </GoodreadsResponse>
    </pre>
     */
    private final XmlHandler mHandleSeriesStart = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            //mCurrSeries = new Series();
        }
    };
    private final XmlHandler mHandleSeriesId = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
			/*
			try {
				mCurrSeriesId = Integer.parseInt(context.body.trim());
			} catch (NumberFormatException ignore) {
			}
			*/
        }
    };
    private final XmlHandler mHandleAuthorStart = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            //mCurrAuthor = new Author();
        }
    };
    private final XmlHandler mHandleAuthorId = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
			/*
			try {
				mCurrAuthorId = Long.parseLong(context.body.trim());
			} catch (Exception ignore) {
			}
			*/
        }
    };

    /** Transient global data for current work in search results. */
    private Bundle mBookData;
    private final XmlHandler mHandleText = new XmlHandler() {

        @Override
        public void process(@NonNull final ElementContext context) {

            final String name = (String) context.userArg;
            mBookData.putString(name, context.body.trim());
        }
    };
    private final XmlHandler mHandleLong = new XmlHandler() {

        @Override
        public void process(@NonNull final ElementContext context) {

            final String name = (String) context.userArg;
            try {
                long l = Long.parseLong(context.body.trim());
                mBookData.putLong(name, l);
            } catch (NumberFormatException ignore) {
                // Ignore but don't add
            }
        }
    };
    // Current series being processed
    //private int mCurrSeriesId = 0;
    private final XmlHandler mHandleFloat = new XmlHandler() {

        @Override
        public void process(@NonNull final ElementContext context) {

            final String name = (String) context.userArg;
            try {
                double d = Double.parseDouble(context.body.trim());
                mBookData.putDouble(name, d);
            } catch (NumberFormatException ignore) {
                // Ignore but don't add
            }
        }
    };

    // Current author being processed
    //private long mCurrAuthorId = 0;
    private final XmlHandler mHandleBoolean = new XmlHandler() {

        @Override
        public void process(@NonNull final ElementContext context) {

            final String name = (String) context.userArg;
            try {
                String s = context.body.trim();
                boolean b;
                if (s.isEmpty()) {
                    b = false;
                } else if ("false".equalsIgnoreCase(s)) {
                    b = false;
                } else if ("true".equalsIgnoreCase(s)) {
                    b = true;
                } else if ("f".equalsIgnoreCase(s)) {
                    b = false;
                } else if ("t".equalsIgnoreCase(s)) {
                    b = true;
                } else {
                    long l = Long.parseLong(s);
                    b = (l != 0);
                }
                mBookData.putBoolean(name, b);
            } catch (NumberFormatException ignore) {
            }
        }
    };
    /** Local storage for series book appears in */
    @Nullable
    private ArrayList<Series> mSeries = null;
    /** Local storage for author names */
    @Nullable
    private ArrayList<Author> mAuthors = null;
    /** Local storage for shelf names */
    @Nullable
    private ArrayList<String> mShelves = null;
    /**
     * Create a new shelves collection when the "shelves" tag is encountered.
     */
    private final XmlHandler mHandleShelvesStart = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {

            mShelves = new ArrayList<>();
        }
    };
    /**
     * Add a shelf to the array
     */
    private final XmlHandler mHandleShelf = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {

            if (context.attributes != null) {
                String name = context.attributes.getValue(XML_NAME);
                mShelves.add(name);
            }
        }
    };
    /** Current author being processed */
    @Nullable
    private String mCurrAuthorName = null;
    private final XmlHandler mHandleAuthorEnd = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {

            if (mCurrAuthorName != null && !mCurrAuthorName.isEmpty()) {
                if (mAuthors == null) {
                    mAuthors = new ArrayList<>();
                }
                mAuthors.add(new Author(mCurrAuthorName));
                mCurrAuthorName = null;
            }
        }
    };
    private final XmlHandler mHandleAuthorName = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {

            mCurrAuthorName = context.body.trim();
        }
    };
    /** Current series being processed */
    @Nullable
    private String mCurrSeriesName = null;
    private final XmlHandler mHandleSeriesName = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {

            mCurrSeriesName = context.body.trim();
        }
    };
    /** Current series being processed */
    @Nullable
    private Integer mCurrSeriesPosition = null;
    private final XmlHandler mHandleSeriesEnd = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {

            if (mCurrSeriesName != null && !mCurrSeriesName.isEmpty()) {
                if (mSeries == null) {
                    mSeries = new ArrayList<>();
                }
                if (mCurrSeriesPosition == null) {
                    mSeries.add(new Series(mCurrSeriesName, ""));
                } else {
                    mSeries.add(new Series(mCurrSeriesName, mCurrSeriesPosition + ""));
                }
                mCurrSeriesName = null;
                mCurrSeriesPosition = null;
            }
        }
    };
    private final XmlHandler mHandleSeriesPosition = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {

            try {
                mCurrSeriesPosition = Integer.parseInt(context.body.trim());
            } catch (NumberFormatException ignore) {
            }
        }
    };

    ShowBookApiHandler(@NonNull final GoodreadsManager manager,
                       @SuppressWarnings("SameParameterValue") final boolean signRequest) {

        super(manager);
        mSignRequest = signRequest;
        // Build the XML filters needed to get the data we're interested in.
        buildFilters();
    }

    /**
     * Perform a search and handle the results.
     *
     * @param request        HttpGet request to use
     * @param fetchThumbnail Indicates if thumbnail file should be retrieved
     *
     * @return the Bundle of data.
     */
    @NonNull
    Bundle sendRequest(@NonNull final HttpGet request,
                       final boolean fetchThumbnail)
            throws NotAuthorizedException,
                   BookNotFoundException,
                   IOException {

        mBookData = new Bundle();
        mShelves = null;

        // Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        // We sign the GET request so we get shelves
        mManager.execute(request, handler, mSignRequest);

        // When we get here, the data has been collected but needs to be processed into standard form.

        // Use ISBN13 by preference
        if (mBookData.containsKey(ShowBookFieldNames.ISBN13)) {
            String s = mBookData.getString(ShowBookFieldNames.ISBN13);
            if (s != null && s.length() == 13) {
                mBookData.putString(UniqueId.KEY_BOOK_ISBN, s);
            }
        }

        // TODO: Evaluate if ShowBook should store GR book ID.
        // Pros: easier sync
        // Cons: Overwrite GR id when it should not
        //if (mBookData.containsKey(BOOK_ID)) {
        //	mBookData.putLong(DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID.name, mBookData.getLong(BOOK_ID));
        //}

        // ENHANCE: Store WORK_ID = "__work_id" into GR_WORK_ID;

        if (fetchThumbnail) {
            String bestImage = null;
            if (mBookData.containsKey(ShowBookFieldNames.IMAGE)) {
                bestImage = mBookData.getString(ShowBookFieldNames.IMAGE);
                if (bestImage != null && bestImage.contains(
                        UniqueId.BKEY_NO_COVER) && mBookData.containsKey(
                        ShowBookFieldNames.SMALL_IMAGE)) {
                    bestImage = mBookData.getString(ShowBookFieldNames.SMALL_IMAGE);
                    if (bestImage != null && bestImage.contains(UniqueId.BKEY_NO_COVER)) {
                        bestImage = null;
                    }
                }
            }
            if (bestImage != null) {
                String fileSpec = ImageUtils.saveThumbnailFromUrl(bestImage,
                                                                  GoodreadsUtils.FILENAME_SUFFIX);
                if (fileSpec != null) {
                    ArrayList<String> imageList = mBookData.getStringArrayList(
                            UniqueId.BKEY_THUMBNAIL_FILE_SPEC_ARRAY);
                    if (imageList == null) {
                        imageList = new ArrayList<>();
                    }
                    imageList.add(fileSpec);
                    mBookData.putStringArrayList(UniqueId.BKEY_THUMBNAIL_FILE_SPEC_ARRAY,
                                                 imageList);
                }
            }
        }

        /* TEST: Build the original publication date based on the components */
        String origPublicationDate = GoodreadsManager.buildDate(mBookData,
                                                                ShowBookFieldNames.ORIG_PUBLICATION_YEAR,
                                                                ShowBookFieldNames.ORIG_PUBLICATION_MONTH,
                                                                ShowBookFieldNames.ORIG_PUBLICATION_DAY,
                                                                UniqueId.KEY_FIRST_PUBLICATION);

        /* Build the publication date based on the components */
        GoodreadsManager.buildDate(mBookData,
                                   ShowBookFieldNames.PUBLICATION_YEAR,
                                   ShowBookFieldNames.PUBLICATION_MONTH,
                                   ShowBookFieldNames.PUBLICATION_DAY,
                                   UniqueId.KEY_BOOK_DATE_PUBLISHED);

        /* If no published date, try original date */
        if (!mBookData.containsKey(UniqueId.KEY_BOOK_DATE_PUBLISHED)) {
            if (origPublicationDate != null && !origPublicationDate.isEmpty()) {
                mBookData.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED, origPublicationDate);
            }
        }

        /* is it an eBook ? Overwrite the format key */
        if (mBookData.containsKey(ShowBookFieldNames.IS_EBOOK) && mBookData.getBoolean(
                ShowBookFieldNames.IS_EBOOK)) {
            mBookData.putString(UniqueId.KEY_BOOK_FORMAT,
                                BookCatalogueApp.getResourceString(R.string.book_format_ebook));
        }

        /*
         * Cleanup the title by removing series name, if present
         */
        if (mBookData.containsKey(UniqueId.KEY_TITLE)) {
            String thisTitle = mBookData.getString(UniqueId.KEY_TITLE);
            Series.SeriesDetails details = Series.findSeriesFromBookTitle(thisTitle);
            if (details != null && !details.name.isEmpty()) {
                if (mSeries == null) {
                    mSeries = new ArrayList<>();
                }
                mSeries.add(new Series(details.name, details.position));
                // Tempting to replace title with ORIG_TITLE, but that does bad things to translations (it used the original language)
                mBookData.putString(UniqueId.KEY_TITLE,
                                    thisTitle.substring(0, details.startChar - 1));
                //if (mBookData.containsKey(ORIG_TITLE)) {
                //	mBookData.putString(UniqueId.KEY_TITLE, mBookData.getString(ORIG_TITLE));
                //} else {
                //	mBookData.putString(UniqueId.KEY_TITLE, thisTitle.substring(0, details.startChar-1));
                //}
            }
        } else if (mBookData.containsKey(ShowBookFieldNames.ORIG_TITLE)) {
            // if we did not get a title, but there is an original title, use that.
            mBookData.putString(UniqueId.KEY_TITLE,
                                mBookData.getString(ShowBookFieldNames.ORIG_TITLE));
        }

        if (mAuthors != null && mAuthors.size() > 0) {
            //mBookData.putString(UniqueId.BKEY_AUTHOR_STRING_LIST, StringList.getAuthorUtils().encode(mAuthors));
            mBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        }

        if (mSeries != null && mSeries.size() > 0) {
            //mBookData.putString(UniqueId.BKEY_SERIES_STRING_LIST, StringList.getSeriesUtils().encode(mSeries));
            mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);
        }

        // these are Goodreads shelves, not ours.
        if (mShelves != null && mShelves.size() > 0) {
            mBookData.putStringArrayList(ShowBookFieldNames.SHELVES, mShelves);
        }


        //ENHANCE: rating ? url ? country_code (publisher) ?

        // Return parsed results.
        return mBookData;
    }

    /**
     * Setup filters to process the XML parts we care about.
     */
    private void buildFilters() {
		/*
		   Stuff we care about

			<GoodreadsResponse>
				...
				<book>
					<id>50</id>
					<title>Hatchet (Hatchet, #1)</title>
					<isbn>0689840926</isbn>
					<isbn13>9780689840920</isbn13>
					...
					<image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
					<small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>

					<publication_year>2000</publication_year>
					<publication_month>4</publication_month>
					<publication_day>1</publication_day>

					<publisher/>
					<language_code/>
					<is_ebook>false</is_ebook>
                    <description>
                        <p>Since it was first published in 1987, blah blah....</p></p>
                     </description>
					<work>
						...
						<id type="integer">1158125</id>
						...
						<original_publication_day type="integer">1</original_publication_day>
						<original_publication_month type="integer">1</original_publication_month>
						<original_publication_year type="integer">1987</original_publication_year>
						<original_title>Hatchet</original_title>
						...
					</work>
					<average_rating>3.57</average_rating>
					<num_pages>208</num_pages>
					<format>Hardcover</format>
					...
					<url>http://www.goodreads.com/book/show/50.Hatchet</url>
					<link>http://www.goodreads.com/book/show/50.Hatchet</link>

					<authors>
						<author>
							<id>18</id>
							<name>Gary Paulsen</name>
							...
						</author>
					</authors>
					<my_review>
						<id>255221284</id>
						<rating>0</rating>
						...
						<shelves>
							<shelf name="sci-fi-fantasy"/>
							<shelf name="to-read"/>
							<shelf name="default"/>
							<shelf name="environment"/>
							<shelf name="games"/>
							<shelf name="history"/>
						</shelves>
						...
						<date_added>Mon Jan 02 19:07:11 -0800 2012</date_added>
						<date_updated>Sat Mar 03 08:10:09 -0800 2012</date_updated>
						<body>Test again</body>
					</my_review>
					...
					<series_works>
						<series_work>
							<id>268218</id>
							<user_position>1</user_position>
							<series>
								<id>62223</id>
								<title>Brian's Saga</title>
								...
							</series>
						</series_work>
					</series_works>
				</book>
			</GoodreadsResponse>
		 */

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_ID)
                 .setEndAction(mHandleLong, ShowBookFieldNames.BOOK_ID);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_TITLE)
                 .setEndAction(mHandleText, UniqueId.KEY_TITLE);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_AUTHORS,
                              XML_AUTHOR)
                 .setStartAction(mHandleAuthorStart)
                 .setEndAction(mHandleAuthorEnd);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_SERIES_WORKS,
                              XML_SERIES_WORK)
                 .setStartAction(mHandleSeriesStart)
                 .setEndAction(mHandleSeriesEnd);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_ISBN)
                 .setEndAction(mHandleText, UniqueId.KEY_BOOK_ISBN);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_ISBN_13)
                 .setEndAction(mHandleText, ShowBookFieldNames.ISBN13);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_IMAGE_URL)
                 .setEndAction(mHandleText, ShowBookFieldNames.IMAGE);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_SMALL_IMAGE_URL)
                 .setEndAction(mHandleText, ShowBookFieldNames.SMALL_IMAGE);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_PUBLICATION_YEAR)
                 .setEndAction(mHandleLong, ShowBookFieldNames.PUBLICATION_YEAR);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_PUBLICATION_MONTH)
                 .setEndAction(mHandleLong, ShowBookFieldNames.PUBLICATION_MONTH);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_PUBLICATION_DAY)
                 .setEndAction(mHandleLong, ShowBookFieldNames.PUBLICATION_DAY);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_PUBLISHER)
                 .setEndAction(mHandleText, UniqueId.KEY_BOOK_PUBLISHER);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_COUNTRY_CODE)
                 .setEndAction(mHandleText, ShowBookFieldNames.COUNTRY_CODE);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_LANGUAGE)
                 .setEndAction(mHandleText, UniqueId.KEY_BOOK_LANGUAGE);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_IS_EBOOK)
                 .setEndAction(mHandleBoolean, ShowBookFieldNames.IS_EBOOK);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_DESCRIPTION)
                 .setEndAction(mHandleText, UniqueId.KEY_BOOK_DESCRIPTION);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_WORK, XML_ID)
                 .setEndAction(mHandleLong, ShowBookFieldNames.WORK_ID);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_WORK,
                              XML_ORIGINAL_PUBLICATION_DAY)
                 .setEndAction(mHandleLong, ShowBookFieldNames.ORIG_PUBLICATION_DAY);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_WORK,
                              XML_ORIGINAL_PUBLICATION_MONTH)
                 .setEndAction(mHandleLong, ShowBookFieldNames.ORIG_PUBLICATION_MONTH);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_WORK,
                              XML_ORIGINAL_PUBLICATION_YEAR)
                 .setEndAction(mHandleLong, ShowBookFieldNames.ORIG_PUBLICATION_YEAR);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_WORK,
                              XML_ORIGINAL_TITLE)
                 .setEndAction(mHandleText, ShowBookFieldNames.ORIG_TITLE);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_AVERAGE_RATING)
                 .setEndAction(mHandleFloat, ShowBookFieldNames.RATING);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_NUM_PAGES)
                 .setEndAction(mHandleLong, UniqueId.KEY_BOOK_PAGES);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_FORMAT)
                 .setEndAction(mHandleText, UniqueId.KEY_BOOK_FORMAT);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_URL)
                 .setEndAction(mHandleText, ShowBookFieldNames.BOOK_URL);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_AUTHORS,
                              XML_AUTHOR, XML_ID)
                 .setEndAction(mHandleAuthorId);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_AUTHORS,
                              XML_AUTHOR, XML_NAME)
                 .setEndAction(mHandleAuthorName);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_MY_REVIEW, XML_ID)
                 .setEndAction(mHandleLong, ShowBookFieldNames.REVIEW_ID);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_MY_REVIEW,
                              XML_SHELVES)
                 .setStartAction(mHandleShelvesStart);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_MY_REVIEW,
                              XML_SHELVES, XML_SHELF)
                 .setStartAction(mHandleShelf);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_SERIES_WORKS,
                              XML_SERIES_WORK, XML_USER_POSITION)
                 .setEndAction(mHandleSeriesPosition);

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_SERIES_WORKS,
                              XML_SERIES_WORK, XML_SERIES, XML_ID)
                 .setEndAction(mHandleSeriesId);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_BOOK, XML_SERIES_WORKS,
                              XML_SERIES_WORK, XML_SERIES, XML_TITLE)
                 .setEndAction(mHandleSeriesName);
    }

    /**
     * Field names we add to the bundle based on parsed XML data
     *
     * @author Philip Warner
     */
    public static final class ShowBookFieldNames {

        public static final String SHELVES = "__shelves";
        public static final String BOOK_ID = "__book_id";
        public static final String REVIEW_ID = "__review_id";

        static final String ISBN13 = "__isbn13";

        static final String IMAGE = "__image";
        static final String SMALL_IMAGE = "__smallImage";

        static final String ORIG_PUBLICATION_YEAR = "__orig_pub_year";
        static final String ORIG_PUBLICATION_MONTH = "__orig_pub_month";
        static final String ORIG_PUBLICATION_DAY = "__orig_pub_day";

        static final String PUBLICATION_YEAR = "__pub_year";
        static final String PUBLICATION_MONTH = "__pub_month";
        static final String PUBLICATION_DAY = "__pub_day";

        static final String IS_EBOOK = "__is_ebook";
        static final String WORK_ID = "__work_id";
        static final String ORIG_TITLE = "__orig_title";
        static final String RATING = "__rating";
        static final String BOOK_URL = "__url";
        static final String COUNTRY_CODE = "__country_code";

        private ShowBookFieldNames() {

        }
    }
}
