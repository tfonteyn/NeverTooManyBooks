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
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

import org.apache.http.client.methods.HttpPost;

/**
 * API handler for the authUser call. Just gets the current user details.
 *
 * @author Philip Warner
 */
public class AuthUserApiHandler extends ApiHandler {

    private long mUserId = 0;

    private final XmlHandler mHandleUserStart = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mUserId = Long.parseLong(context.attributes.getValue("", "id"));
        }
    };

    @Nullable
    private String mUsername = null;

    private final XmlHandler mHandleUsernameEnd = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            mUsername = context.body;
        }
    };

    /**
     * Constructor. Setup the filters.
     */
    public AuthUserApiHandler(final @NonNull GoodreadsManager manager) {
        super(manager);
        buildFilters();
    }

    /**
     * Call the API.
     *
     * @return Resulting User ID, 0 if error/none.
     */
    public long getAuthUser() {
        // Setup API call
        HttpPost post = new HttpPost(GoodreadsManager.BASE_URL + "/api/auth_user");

        mUserId = 0;
        try {
            // Get a handler and run query.
            XmlResponseParser handler = new XmlResponseParser(mRootFilter);
            mManager.execute(post, handler, true);
            // Return user found.
            return mUserId;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Typical response:
     *
     * <GoodreadsResponse>
     * <Request>
     * <authentication>true</authentication>
     * <key><![CDATA[KEY]]></key>
     * <method><![CDATA[api_auth_user]]></method>
     * </Request>
     * <user id="5129458">
     * <name><![CDATA[Grunthos]]></name>
     * <link><![CDATA[http://www.goodreads.com/user/show/5129458-grunthos?utm_medium=api]]></link>
     * </user>
     * </GoodreadsResponse>
     */
    private void buildFilters() {
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_USER).setStartAction(mHandleUserStart);
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_USER, XML_NAME).setEndAction(mHandleUsernameEnd);
    }

    @Nullable
    public String getUsername() {
        return mUsername;
    }

    public long getUserId() {
        return mUserId;
    }

}
