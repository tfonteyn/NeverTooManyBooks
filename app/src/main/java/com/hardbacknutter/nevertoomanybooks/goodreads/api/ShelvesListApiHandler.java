/*
 * @Copyright 2018-2021 HardBackNutter
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsShelf;
import com.hardbacknutter.nevertoomanybooks.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * shelves.list   —   Get a user's shelves.
 *
 * <a href="https://www.goodreads.com/api/index#shelves.list">shelves.list</a>
 */
public class ShelvesListApiHandler
        extends ApiHandler {

    private static final String URL = GoodreadsManager.BASE_URL + "/shelf/list.xml?"
                                      + "key=%1$s&page=%2$s&user_id=%3$s";
    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    private final XmlFilter mRootFilter = new XmlFilter("");
    private SimpleXmlFilter mFilters;

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     *
     * @throws CredentialsException if there are no valid credentials available
     */
    public ShelvesListApiHandler(@NonNull final Context appContext,
                                 @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(appContext, grAuth);
        mGrAuth.hasValidCredentialsOrThrow(appContext);

        buildFilters();
    }

    @NonNull
    public Map<String, GoodreadsShelf> getAll()
            throws GeneralParsingException, IOException {

        final Map<String, GoodreadsShelf> map = new HashMap<>();
        int page = 1;
        while (true) {
            final Bundle result = get(page);
            final List<Bundle> shelves = result.getParcelableArrayList(ShelvesField.SHELVES);
            if (shelves == null || shelves.isEmpty()) {
                break;
            }

            for (final Bundle shelf : shelves) {
                final GoodreadsShelf grShelf = new GoodreadsShelf(shelf);
                map.put(grShelf.getName(), grShelf);
            }

            if (result.getLong(ShelvesField.END) >= result.getLong(ShelvesField.TOTAL)) {
                break;
            }

            page++;
        }
        return map;
    }

    /**
     * Goodreads delivers the list split into pages.
     *
     * @param page we want, start with 1.
     *
     * @return the shelves listed on this page.
     *
     * @throws IOException on failures
     */
    @NonNull
    private Bundle get(final int page)
            throws GeneralParsingException, IOException {

        final String url = String.format(URL, mGrAuth.getDevKey(), page, mGrAuth.getUserId());

        final DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executeGet(url, null, true, handler);

        return mFilters.getData();
    }

    /**
     * Setup filters to process the XML parts we care about.
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
     *        <exclusive_flag type='boolean'>true</exclusive_flag>
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
     */
    private void buildFilters() {
        mFilters = new SimpleXmlFilter(mRootFilter, GoodreadsManager.SITE_LOCALE);

        mFilters
                //<GoodreadsResponse>
                .s(XmlTags.XML_GOODREADS_RESPONSE)
                // ...
                // <shelves start='1' end='29' total='29'>
                .s(XmlTags.XML_SHELVES).asArray(ShelvesField.SHELVES)
                .longAttr(XmlTags.XML_START, ShelvesField.START)
                .longAttr(XmlTags.XML_END, ShelvesField.END)
                .longAttr(XmlTags.XML_TOTAL, ShelvesField.TOTAL)
                //  <user_shelf>
                .s(XmlTags.XML_USER_SHELF).asArrayItem()
                //      <exclusive_flag type='boolean'>false</exclusive_flag>
                .booleanBody(XmlTags.XML_EXCLUSIVE_FLAG, ShelvesField.EXCLUSIVE)
                //      <id type='integer'>26567684</id>
                .longBody(XmlTags.XML_ID, ShelvesField.ID)
                //      <name>read</name>
                .stringBody(XmlTags.XML_NAME, ShelvesField.NAME)
                .pop()
                .done();
    }


    /**
     * Goodreads specific field names we add to the bundle based on parsed XML data.
     */
    public static final class ShelvesField {

        public static final String END = "end";
        public static final String EXCLUSIVE = "exclusive";
        public static final String ID = "id";
        public static final String NAME = "name";
        static final String SHELVES = "shelves";
        static final String START = "start";
        static final String TOTAL = "total";

        private ShelvesField() {
        }
    }
}
