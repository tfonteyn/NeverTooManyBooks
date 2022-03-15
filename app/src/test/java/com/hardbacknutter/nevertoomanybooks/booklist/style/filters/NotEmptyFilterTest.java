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
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotEmptyFilterTest
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

        final NotEmptyFilter p1 = new NotEmptyFilter(
                false, mLayerMock, R.string.lbl_isbn,
                Filters.PK_FILTER_ISBN,
                new DomainExpression(DBDefinitions.DOM_BOOK_ISBN,
                                     DBDefinitions.TBL_BOOKS.dot(DBKey.KEY_ISBN)));
        p1.set(1);

        final NotEmptyFilter p2 = p1.clone(false, mLayerMock);
        assertEquals(p1, p2);
        assertEquals(1, (int) p2.getValue());
        assertTrue(p2.isActive(mContext));
    }
}
