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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test the test database as setup by {@link BaseSetup#setup()}.
 */
@SmallTest
public class BaseSetupTest
        extends BaseSetup {

    @Test
    public void basic() {

        ArrayList<Long> bookIdList;
        ArrayList<AuthorWork> works;

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "basic")) {

            // The objects should have been updated with their id
            assertTrue(author[0].getId() > 0);
            assertTrue(author[1].getId() > 0);
            assertTrue(author[2].getId() > 0);

            assertEquals(book[0].getId(), bookId[0]);
            assertEquals(book[1].getId(), bookId[1]);
            assertEquals(book[2].getId(), bookId[2]);
            assertEquals(book[3].getId(), bookId[3]);
            assertEquals(book[4].getId(), bookId[4]);

            assertTrue(tocEntry[0].getId() > 0);
            assertTrue(tocEntry[1].getId() > 0);
            assertTrue(tocEntry[2].getId() > 0);
            assertTrue(tocEntry[3].getId() > 0);

            // a0 is present in b0, b3
            bookIdList = db.getBookIdsByAuthor(author[0].getId());
            assertEquals(2, bookIdList.size());
            assertEquals(bookId[0], (long) bookIdList.get(0));
            assertEquals(bookId[3], (long) bookIdList.get(1));

            // a1 is present in b0, b1, b4
            bookIdList = db.getBookIdsByAuthor(author[1].getId());
            assertEquals(3, bookIdList.size());
            assertEquals(bookId[0], (long) bookIdList.get(0));
            assertEquals(bookId[1], (long) bookIdList.get(1));
            assertEquals(bookId[4], (long) bookIdList.get(2));

            // a2 is present in b2, b3, b4
            bookIdList = db.getBookIdsByAuthor(author[2].getId());
            assertEquals(3, bookIdList.size());
            assertEquals(bookId[2], (long) bookIdList.get(0));
            assertEquals(bookId[3], (long) bookIdList.get(1));
            assertEquals(bookId[4], (long) bookIdList.get(2));

            works = db.getAuthorWorks(author[1], mBookshelf.getId(), true, false);
            assertEquals(2, works.size());
            works = db.getAuthorWorks(author[2], mBookshelf.getId(), true, false);
            assertEquals(2, works.size());

            works = db.getAuthorWorks(author[1], mBookshelf.getId(), true, true);
            assertEquals(5, works.size());
            works = db.getAuthorWorks(author[2], mBookshelf.getId(), true, true);
            assertEquals(5, works.size());

            works = db.getAuthorWorks(author[1], mBookshelf.getId(), false, true);
            assertEquals(3, works.size());
            works = db.getAuthorWorks(author[2], mBookshelf.getId(), false, true);
            assertEquals(3, works.size());
        }
    }
}
