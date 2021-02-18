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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.UUID;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.database.DAO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class UserStyleTest {

    private static final String TAG = "UserStyleTest";

    @Test
    public void clone01() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final BuiltinStyle s1;
        try (DAO db = new DAO(context, TAG)) {
            s1 = (BuiltinStyle) StyleUtils.getStyle(context, db, StyleUtils.BuiltinStyles
                    // This style has:
                    // BooklistGroup.AUTHOR
                    // BooklistGroup.SERIES
                    // Filters.PK_FILTER_READ: false
                    .UNREAD_AUTHOR_THEN_SERIES_UUID);
        }
        assertNotNull(s1);

        final UserStyle cloned = s1.clone(context, 0,
                                          UUID.randomUUID().toString(),
                                          // FALSE!!
                                          false);

        // new style, so no id, and a new uuid
        assertEquals(0, cloned.getId());
        assertNotEquals(s1.getUuid(), cloned.getUuid());
        assertFalse(cloned.getUuid().isEmpty());

        assertEquals(s1.getLabel(context), cloned.getLabel(context));

        assertEquals(s1.isPreferred(), cloned.isPreferred());
        assertEquals(s1.getMenuPosition(), cloned.getMenuPosition());
        assertEquals(s1.isGlobal(), cloned.isGlobal());

        assertEquals(s1.isShowAuthorByGivenName(), cloned.isShowAuthorByGivenName());
        assertEquals(s1.isSortAuthorByGivenName(), cloned.isSortAuthorByGivenName());

        assertEquals(s1.isShowHeader(ListStyle.HEADER_SHOW_BOOK_COUNT),
                     cloned.isShowHeader(ListStyle.HEADER_SHOW_BOOK_COUNT));
        assertEquals(s1.isShowHeader(ListStyle.HEADER_SHOW_STYLE_NAME),
                     cloned.isShowHeader(ListStyle.HEADER_SHOW_STYLE_NAME));
        assertEquals(s1.isShowHeader(ListStyle.HEADER_SHOW_FILTER),
                     cloned.isShowHeader(ListStyle.HEADER_SHOW_FILTER));

        assertEquals(s1.getGroupRowHeight(context), cloned.getGroupRowHeight(context));
        assertEquals(s1.getTopLevel(), cloned.getTopLevel());

        assertEquals(s1.isShowBooksUnderEachAuthor(context),
                     cloned.isShowBooksUnderEachAuthor(context));
        assertEquals(s1.isShowBooksUnderEachBookshelf(context),
                     cloned.isShowBooksUnderEachBookshelf(context));
        assertEquals(s1.isShowBooksUnderEachPublisher(context),
                     cloned.isShowBooksUnderEachPublisher(context));
        assertEquals(s1.isShowBooksUnderEachSeries(context),
                     cloned.isShowBooksUnderEachSeries(context));

        assertEquals(s1.getPrimaryAuthorType(context), cloned.getPrimaryAuthorType(context));

        assertEquals(s1.getTextScale(), cloned.getTextScale());

        assertEquals(s1.getListScreenBookFields(), cloned.getListScreenBookFields());
        assertEquals(s1.getDetailScreenBookFields(), cloned.getDetailScreenBookFields());

        assertEquals(s1.getFilters(), cloned.getFilters());
        assertEquals(s1.getGroups(), cloned.getGroups());
    }
}
