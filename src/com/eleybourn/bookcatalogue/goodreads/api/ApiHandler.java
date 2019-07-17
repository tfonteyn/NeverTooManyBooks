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

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;

/**
 * Base class for all Goodreads API handler classes.
 * <p>
 * The job of an API handler is to implement a method to run the API (e.g. 'search' in
 * {@link SearchBooksApiHandler} and to process the output.
 *
 * @author Philip Warner
 */
abstract class ApiHandler {

    /** XML tags/attrs we look for. */
    static final String XML_GOODREADS_RESPONSE = "GoodreadsResponse";
    static final String XML_NAME = "name";

    static final String XML_ID = "id";
    static final String XML_WORK = "work";
    static final String XML_BODY = "body";
    static final String XML_SEARCH = "search";

    static final String XML_RESULT = "results";
    static final String XML_TOTAL_RESULTS = "total-results";
    static final String XML_RESULTS_END = "results-end";
    static final String XML_RESULTS_START = "results-start";

    static final String XML_REVIEWS = "reviews";
    static final String XML_REVIEW = "review";
    static final String XML_MY_REVIEW = "my_review";

    static final String XML_AUTHORS = "authors";
    static final String XML_AUTHOR = "author";

    static final String XML_TITLE = "title";
    static final String XML_TITLE_WITHOUT_SERIES = "title_without_series";
    static final String XML_ORIGINAL_TITLE = "original_title";

    /** <language_code>en-GB</language_code> */
    static final String XML_LANGUAGE = "language_code";

    static final String XML_BOOK = "book";
    static final String XML_BEST_BOOK = "best_book";
    /** <isbn13><![CDATA[9780340198278]]></isbn13> */
    static final String XML_ISBN_13 = "isbn13";
    /** <isbn><![CDATA[0340198273]]></isbn> */
    static final String XML_ISBN = "isbn";
    /**
     * <num_pages><![CDATA[206]]></num_pages>
     * <num_pages>448</num_pages>
     */
    static final String XML_NUM_PAGES = "num_pages";
    /** <format><![CDATA[Mass Market Paperback]]></format> */
    static final String XML_FORMAT = "format";
    /** <is_ebook>false</is_ebook> */
    static final String XML_IS_EBOOK = "is_ebook";
    static final String XML_DESCRIPTION = "description";

    static final String XML_SERIES = "series";
    static final String XML_SERIES_WORK = "series_work";
    static final String XML_SERIES_WORKS = "series_works";

    static final String XML_SHELVES = "shelves";
    static final String XML_SHELF = "shelf";
    static final String XML_USER_SHELF = "user_shelf";

    /** <publisher>Coronet</publisher> */
    static final String XML_PUBLISHER = "publisher";
    /** <country_code><![CDATA[GB]]></country_code> */
    static final String XML_COUNTRY_CODE = "country_code";

    /** <publication_year>1977</publication_year> */
    static final String XML_PUBLICATION_YEAR = "publication_year";
    /** <publication_month>10</publication_month> */
    static final String XML_PUBLICATION_MONTH = "publication_month";
    /** <publication_day></publication_day> */
    static final String XML_PUBLICATION_DAY = "publication_day";

    static final String XML_ORIGINAL_PUBLICATION_DAY = "original_publication_day";
    static final String XML_ORIGINAL_PUBLICATION_MONTH = "original_publication_month";
    static final String XML_ORIGINAL_PUBLICATION_YEAR = "original_publication_year";

    static final String XML_DATE_ADDED = "date_added";
    static final String XML_DATE_UPDATED = "date_updated";

    static final String XML_RATING = "rating";
    static final String XML_AVERAGE_RATING = "average_rating";

    static final String XML_URL = "url";
    static final String XML_USER_POSITION = "user_position";
    static final String XML_SMALL_IMAGE_URL = "small_image_url";
    static final String XML_IMAGE_URL = "image_url";
    static final String XML_EXCLUSIVE_FLAG = "exclusive_flag";
    static final String XML_START = "start";
    static final String XML_END = "end";
    static final String XML_TOTAL = "total";
    static final String XML_STARTED_AT = "started_at";
    static final String XML_READ_AT = "read_at";
    static final String XML_REVIEW_ID = "review-id";


    @NonNull
    final GoodreadsManager mManager;

    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    final XmlFilter mRootFilter = new XmlFilter("");

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     */
    ApiHandler(@NonNull final GoodreadsManager grManager) {
        mManager = grManager;
    }
}
