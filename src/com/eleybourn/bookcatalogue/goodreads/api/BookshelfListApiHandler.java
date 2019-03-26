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

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.xml.SimpleXmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

/**
 * Class to implement the reviews.list api call. It queries based on the passed parameters
 * and returns a single Bundle containing all results. The Bundle itself will contain
 * other bundles: typically an array of 'Review' bundles, each of which will contains
 * arrays of 'author' bundles.
 * <p>
 * Processing this data is up to the caller, but it is guaranteed to be type-safe if present,
 * With the exception of dates, which are collected as text strings.
 * <p>
 * Typical result:
 * <pre>
 *  {@code
 *  <GoodreadsResponse>
 *    <Request>
 *      <authentication>true</authentication>
 *      <key><![CDATA[...]]></key>
 *      <method><![CDATA[shelf_list]]></method>
 *    </Request>
 *
 *    <shelves start='1' end='29' total='29'>
 *      <user_shelf>
 *        <book_count type='integer'>546</book_count>
 *        <description nil='true'></description>
 *        <display_fields></display_fields>
 * 	      <exclusive_flag type='boolean'>true</exclusive_flag>
 *        <featured type='boolean'>false</featured>
 *        <id type='integer'>16480894</id>
 *        <name>read</name>
 *        <order nil='true'></order>
 *        <per_page type='integer' nil='true'></per_page>
 *        <recommend_for type='boolean'>false</recommend_for>
 *        <sort></sort>
 *        <sticky type='boolean' nil='true'></sticky>
 *      </user_shelf>
 *
 *      <user_shelf>
 *        <book_count type='integer'>0</book_count>
 *        <description nil='true'></description>
 *        <display_fields></display_fields>
 *        <exclusive_flag type='boolean'>true</exclusive_flag>
 *        <featured type='boolean'>false</featured>
 *        <id type='integer'>16480900</id>
 *        <name>currently-reading</name>
 *        <order nil='true'></order>
 *        <per_page type='integer' nil='true'></per_page>
 *        <recommend_for type='boolean'>false</recommend_for>
 *        <sort nil='true'></sort>
 *        <sticky type='boolean' nil='true'></sticky>
 *      </user_shelf>
 *      ...
 *    </shelves>
 *  </GoodreadsResponse>
 *  }
 * </pre>
 *
 * @author Philip Warner
 */
public class BookshelfListApiHandler
        extends ApiHandler {

    private SimpleXmlFilter mFilters;

    public BookshelfListApiHandler(@NonNull final GoodreadsManager manager)
            throws AuthorizationException {
        super(manager);
        if (!manager.hasValidCredentials()) {
            throw new AuthorizationException(R.string.goodreads);
        }
        // Build the XML filters needed to get the data we're interested in.
        buildFilters();
    }

    @NonNull
    public Bundle run(final int page)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {
        @SuppressWarnings("UnusedAssignment")
        long t0 = System.currentTimeMillis();

        // Sort by update_dte (descending) so sync is faster.
        // Specify 'shelf=all' because it seems goodreads returns
        // the shelf that is selected in 'My Books' on the web interface by default.
        final String urlBase = GoodreadsManager.BASE_URL
                + "/shelf/list.xml?key=%1$s&page=%2$s&user_id=%3$s";

        final String url = String.format(urlBase, mManager.getDevKey(), page, mManager.getUserId());
        HttpGet get = new HttpGet(url);

        // Initial debug code:
        //TrivialParser handler = new TrivialParser();
        //mTaskManager.execute(get, handler, true);
        //String s = handler.getHtml();
        //Logger.info(s);

        // Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);

        // Even thought it's only a GET, it needs a signature.
        mManager.execute(get, handler, true);

        // When we get here, the data has been collected but needs to be processed
        // into standard form.
        Bundle results = mFilters.getData();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Logger.info(this, "run", "Found "
                    + results.getLong(GrBookshelfFields.TOTAL)
                    + " shelves in " + (System.currentTimeMillis() - t0) + "ms");
        }

        return results;
    }

    /**
     * Setup filters to process the XML parts we care about.
     */
    private void buildFilters() {
        mFilters = new SimpleXmlFilter(mRootFilter);

        mFilters
                //<GoodreadsResponse>
                .s(XML_GOODREADS_RESPONSE)
                // ...
                // <shelves start='1' end='29' total='29'>
                .s(XML_SHELVES).isArray(GrBookshelfFields.SHELVES)
                .longAttr(XML_START, GrBookshelfFields.START)
                .longAttr(XML_END, GrBookshelfFields.END)
                .longAttr(XML_TOTAL, GrBookshelfFields.TOTAL)
                //  <user_shelf>
                .s(XML_USER_SHELF).isArrayItem()
                //      <exclusive_flag type='boolean'>false</exclusive_flag>
                .booleanBody(XML_EXCLUSIVE_FLAG, GrBookshelfFields.EXCLUSIVE)
                //      <id type='integer'>26567684</id>
                .longBody(XML_ID, GrBookshelfFields.ID)
                .stringBody(XML_NAME, GrBookshelfFields.NAME)
                .pop()
                .done();
    }


    /**
     * Field names we add to the bundle based on parsed XML data.
     * <p>
     * We duplicate the DBA names (and give them a DB_ prefix) so
     * that (a) it is clear which fields are provided by this call, and (b) it is clear
     * which fields directly relate to DB fields.
     *
     * @author Philip Warner
     */
    public static final class GrBookshelfFields {

        public static final String SHELVES = "shelves";
        public static final String START = "start";
        public static final String END = "end";
        public static final String TOTAL = "total";
        public static final String EXCLUSIVE = "exclusive";
        public static final String ID = "id";
        public static final String NAME = "name";

        private GrBookshelfFields() {
        }
    }

//	/**
//	 * Listener to handle the contents of the date_updated field. We only
//	 * keep it if it is a valid date, and we store it in SQL format using 
//	 * UTC TZ so comparisons work.
//	 */
//	XmlListener mUpdatedListener = new XmlListener() {
//		@Override
//		public void onStart(BuilderContext bc, ElementContext c) {
//		}
//
//		@Override
//		public void onFinish(BuilderContext bc, ElementContext c) {
//			date2Sql(bc.getData(), UPDATED);
//		}
//	};
//
//	/**
//	 * Listener to handle the contents of the date_added field. We only
//	 * keep it if it is a valid date, and we store it in SQL format using 
//	 * UTC TZ so comparisons work.
//	 */
//	XmlListener mAddedListener = new XmlListener() {
//		@Override
//		public void onStart(BuilderContext bc, ElementContext c) {
//		}
//
//		@Override
//		public void onFinish(BuilderContext bc, ElementContext c) {
//			date2Sql(bc.getData(), ADDED);
//		}
//	};
}
