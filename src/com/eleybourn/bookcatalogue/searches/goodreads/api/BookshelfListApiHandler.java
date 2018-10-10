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
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

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
public class BookshelfListApiHandler extends ApiHandler {

    private SimpleXmlFilter mFilters;

    public BookshelfListApiHandler(@NonNull final GoodreadsManager manager) {
        super(manager);
        if (!manager.hasValidCredentials())
            throw new RuntimeException("Goodreads credentials not valid");
        // Build the XML filters needed to get the data we're interested in.
        buildFilters();
    }

    @NonNull
    public Bundle run(final int page)
            throws OAuthMessageSignerException, OAuthExpectationFailedException,
            OAuthCommunicationException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
        long t0 = System.currentTimeMillis();

        // Sort by update_dte (descending) so sync is faster. Specify 'shelf=all' because it seems goodreads returns
        // the shelf that is selected in 'My Books' on the web interface by default.
        final String urlBase = GoodreadsManager.GOODREADS_API_ROOT + "/shelf/list.xml?key=%1$s&page=%2$s&user_id=%3$s";
        final String url = String.format(urlBase, mManager.getDeveloperKey(), page, mManager.getUserId());
        HttpGet get = new HttpGet(url);

        // Initial debug code:
        //TrivialParser handler = new TrivialParser();
        //mManager.execute(get, handler, true);
        //String s = handler.getHtml();
        //Logger.debug(s);

        // Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);

        // Even thought it's only a GET, it needs a signature.
        mManager.execute(get, handler, true);

        // When we get here, the data has been collected but needs to be processed into standard form.
        Bundle results = mFilters.getData();

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.debug("Found " + results.getLong(BookshelfListFieldNames.TOTAL) + " shelves in " + (System.currentTimeMillis() - t0) + "ms");
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
                .s("GoodreadsResponse")
                //	<Request>
                //		...
                //	</Request>
                //	<reviews start="3" end="4" total="933">
                //  <shelves start='1' end='29' total='29'>
                .s("shelves").isArray(BookshelfListFieldNames.SHELVES)
                .longAttr("start", BookshelfListFieldNames.START)
                .longAttr("end", BookshelfListFieldNames.END)
                .longAttr("total", BookshelfListFieldNames.TOTAL)

                //  <user_shelf>
                .s("user_shelf").isArrayItem()
                //	  <exclusive_flag type='boolean'>false</exclusive_flag>
                .booleanBody("exclusive_flag", BookshelfListFieldNames.EXCLUSIVE)

                //	  <id type='integer'>26567684</id>
                .longBody("id", BookshelfListFieldNames.ID)
                .stringBody("name", BookshelfListFieldNames.NAME)
                .pop()
                .done();
    }

	/*
	 * Typical result:

		<GoodreadsResponse>
		  <Request>
		    <authentication>true</authentication>
		      <key>....</key>
		    <method>shelf_list</method>
		  </Request>
		  <shelves start='1' end='29' total='29'>
		      <user_shelf>
				  <book_count type='integer'>546</book_count>
				  <description nil='true'></description>
				  <display_fields></display_fields>
				  <exclusive_flag type='boolean'>true</exclusive_flag>
				  <featured type='boolean'>false</featured>
				  <id type='integer'>16480894</id>
				  <name>read</name>
				  <order nil='true'></order>
				  <per_page type='integer' nil='true'></per_page>
				  <recommend_for type='boolean'>false</recommend_for>
				  <sort></sort>
				  <sticky type='boolean' nil='true'></sticky>
		      </user_shelf>
		
		      <user_shelf>
				  <book_count type='integer'>0</book_count>
				  <description nil='true'></description>
				  <display_fields></display_fields>
				  <exclusive_flag type='boolean'>true</exclusive_flag>
				  <featured type='boolean'>false</featured>
				  <id type='integer'>16480900</id>
				  <name>currently-reading</name>
				  <order nil='true'></order>
				  <per_page type='integer' nil='true'></per_page>
				  <recommend_for type='boolean'>false</recommend_for>
				  <sort nil='true'></sort>
				  <sticky type='boolean' nil='true'></sticky>
		      </user_shelf>
		
		      <user_shelf>
				  <book_count type='integer'>381</book_count>
				  <description nil='true'></description>
				  <display_fields></display_fields>
				  <exclusive_flag type='boolean'>true</exclusive_flag>
				  <featured type='boolean'>false</featured>
				  <id type='integer'>16480892</id>
				  <name>to-read</name>
				  <order>a</order>
				  <per_page type='integer' nil='true'></per_page>
				  <recommend_for type='boolean'>false</recommend_for>
				  <sort>position</sort>
				  <sticky type='boolean' nil='true'></sticky>
		      </user_shelf>
		
		      <user_shelf>
				  <book_count type='integer'>5</book_count>
				  <description nil='true'></description>
				  <display_fields></display_fields>
				  <exclusive_flag type='boolean'>false</exclusive_flag>
				  <featured type='boolean'>false</featured>
				  <id type='integer'>26567684</id>
				  <name>aabug</name>
				  <order nil='true'></order>
				  <per_page type='integer' nil='true'></per_page>
				  <recommend_for type='boolean'>true</recommend_for>
				  <sort nil='true'></sort>
				  <sticky type='boolean' nil='true'></sticky>
		      </user_shelf>
		
		      <user_shelf>
				  <book_count type='integer'>6</book_count>
				  <description nil='true'></description>
				  <display_fields></display_fields>
				  <exclusive_flag type='boolean'>false</exclusive_flag>
				  <featured type='boolean'>false</featured>
				  <id type='integer'>24457791</id>
				  <name>anthologies</name>
				  <order nil='true'></order>
				  <per_page type='integer' nil='true'></per_page>
				  <recommend_for type='boolean'>true</recommend_for>
				  <sort nil='true'></sort>
				  <sticky type='boolean' nil='true'></sticky>
		      </user_shelf>
		  </shelves>
		</GoodreadsResponse>

	 */

    /**
     * Field names we add to the bundle based on parsed XML data.
     *
     * We duplicate the CatalogueDBAdapter names (and give them a DB_ prefix) so
     * that (a) it is clear which fields are provided by this call, and (b) it is clear
     * which fields directly relate to DB fields.
     *
     * @author Philip Warner
     */
    public static final class BookshelfListFieldNames {
        public static final String SHELVES = "shelves";
        public static final String START = "start";
        public static final String END = "end";
        public static final String TOTAL = "total";
        public static final String EXCLUSIVE = "exclusive";
        public static final String ID = "id";
        public static final String NAME = "name";
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
