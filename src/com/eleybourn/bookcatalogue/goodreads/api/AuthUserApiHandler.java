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
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.ElementContext;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

import org.apache.http.client.methods.HttpPost;

import java.io.IOException;

/**
 * API handler for the authUser call. Just gets the current user details.
 * <p>
 * Typical response.
 * <pre>
 *  {@code
 *  <GoodreadsResponse>
 *    <Request>
 *      <authentication>true</authentication>
 *      <key><![CDATA[...]]></key>
 *      <method><![CDATA[api_auth_user]]></method>
 *    </Request>
 *
 *    <user id="5129458">
 *      <name><![CDATA[Grunthos]]></name>
 *      <link>
 *        <![CDATA[http://www.goodreads.com/user/show/5129458-grunthos?utm_medium=api]]>
 *      </link>
 *    </user>
 *   </GoodreadsResponse>
 *   }
 * </pre>
 *
 * @author Philip Warner
 */
public class AuthUserApiHandler
        extends ApiHandler {

    private long mUserId;
    @Nullable
    private String mUsername;

    /**
     * Constructor.
     */
    public AuthUserApiHandler(@NonNull final GoodreadsManager manager) {
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
        } catch (GoodreadsExceptions.BookNotFoundException
                | GoodreadsExceptions.NotAuthorizedException
                | IOException
                | RuntimeException e) {
            return 0;
        }
    }

    /**
     * Setup filters to process the XML parts we care about.
     */
    private void buildFilters() {
        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_USER)
                 .setStartAction(new XmlHandler() {
                     @Override
                     public void process(@NonNull final ElementContext context) {
                         mUserId = Long.parseLong(context.getAttributes().getValue("", XML_ID));
                     }
                 });

        XmlFilter.buildFilter(mRootFilter, XML_GOODREADS_RESPONSE, XML_USER, XML_NAME)
                 .setEndAction(new XmlHandler() {
                     @Override
                     public void process(@NonNull final ElementContext context) {
                         mUsername = context.getBody();
                     }
                 });
    }

    @Nullable
    public String getUsername() {
        return mUsername;
    }

    public long getUserId() {
        return mUserId;
    }

}
