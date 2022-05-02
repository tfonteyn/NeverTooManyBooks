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
package com.hardbacknutter.nevertoomanybooks.booklist.style.filters;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks._mocks.StylePersistenceLayerBundle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class BitmaskFilterTest
        extends Base {

    private StylePersistenceLayer mLayerMock;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        mLayerMock = new StylePersistenceLayerBundle();
    }

    @Test
    void cc() {
        final BitmaskFilter p1 = new BitmaskFilter(
                false, mLayerMock, R.string.lbl_edition,
                Filters.PK_FILTER_EDITION_BITMASK,
                DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_EDITION_BITMASK,
                Book.Edition.BITMASK_ALL_BITS);

        p1.set(Book.Edition.SIGNED | Book.Edition.LIMITED);
        assertTrue(p1.isActive(mContext));

        final BitmaskFilter p2 = p1.clone(false, mLayerMock);
        assertEquals(p1, p2);
        assertEquals(Book.Edition.SIGNED | Book.Edition.LIMITED, (int) p2.getValue());
        assertTrue(p2.isActive(mContext));
    }
}
