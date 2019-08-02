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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertomanybooks.utils.xml.XmlResponseParser;

/**
 * API handler for the "auth.user" call. Just gets the current user details.
 * <p>
 * <a href="https://www.goodreads.com/api/index#auth.user">
 *     https://www.goodreads.com/api/index#auth.user</a>
 *
 * @author Philip Warner
 */
public class AuthUserApiHandler
        extends ApiHandler {

    private static final String URL = GoodreadsManager.BASE_URL + "/api/auth_user";

    private static final String XML_USER = "user";

    private long mUserId;
    @Nullable
    private String mUsername;

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     */
    public AuthUserApiHandler(@NonNull final GoodreadsManager grManager) {
        super(grManager);
        // don't ...if (!grManager.hasValidCredentials()) {

        buildFilters();
    }

    /**
     * Call the API.
     *
     * @return Resulting User ID, 0 if error/none.
     */
    public long getAuthUser() {
        mUserId = 0;
        try {
            DefaultHandler handler = new XmlResponseParser(mRootFilter);
            executePost(URL, null, true, handler);
            // Return user found.
            return mUserId;

        } catch (@NonNull final CredentialsException | BookNotFoundException | IOException
                | RuntimeException e) {
            return 0;
        }
    }

    /**
     * Setup filters to process the XML parts we care about.
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
     */
    private void buildFilters() {
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XML_USER)
                 .setStartAction(context -> mUserId
                         = Long.parseLong(context.getAttributes().getValue("", XmlTags.XML_ID)));

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XML_USER,
                              XmlTags.XML_NAME)
                 .setEndAction(context -> mUsername
                         = context.getBody());
    }

    @Nullable
    public String getUsername() {
        return mUsername;
    }

    public long getUserId() {
        return mUserId;
    }

}
