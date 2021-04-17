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

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks._mocks.StylePersistenceLayerBundle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntListFilterTest
        extends Base {

    private StylePersistenceLayer mLayerMock;

    @BeforeEach
    public void setUp() {
        super.setUp();
        mLayerMock = new StylePersistenceLayerBundle();
    }

    @Test
    void cc() {
        final IntListFilter p1 = new IntListFilter(
                false, mLayerMock, R.string.lbl_bookshelves,
                Filters.PK_FILTER_BOOKSHELVES,
                new DomainExpression(DBDefinitions.DOM_BOOKSHELF_NAME,
                                     TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF)));

        final ArrayList<Integer> value = new ArrayList<>();
        value.add(1);
        value.add(13);
        p1.set(value);
        assertTrue(p1.isActive(mContext));

        final IntListFilter p2 = p1.clone(false, mLayerMock);
        assertEquals(p1, p2);
        assertEquals(p1.getValue(), p2.getValue());
        assertTrue(p2.isActive(mContext));
    }
}
