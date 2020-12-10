/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayerBundle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BitmaskFilterTest {

    private StylePersistenceLayer mLayerMock;

    @Before
    public void setupMock() {
        mLayerMock = new StylePersistenceLayerBundle();
    }

    @Test
    public void cc() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final BitmaskFilter p1 =
                new BitmaskFilter(false, mLayerMock, R.string.lbl_edition,
                                  Filters.PK_FILTER_EDITIONS,
                                  DBDefinitions.TBL_BOOKS,
                                  DBDefinitions.KEY_EDITION_BITMASK,
                                  Book.Edition.BITMASK_ALL);
        p1.set(Book.Edition.SIGNED | Book.Edition.LIMITED);

        final BitmaskFilter p2 = new BitmaskFilter(false, mLayerMock, p1);
        assertEquals(p1, p2);
        assertEquals(Book.Edition.SIGNED | Book.Edition.LIMITED, (int) p2.getValue());
        assertTrue(p2.isActive(context));
    }
}
