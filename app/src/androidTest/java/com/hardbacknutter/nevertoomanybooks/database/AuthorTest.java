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

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.entities.Author;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Very basic test of insert/update/delete an Author.
 */
@SmallTest
public class AuthorTest {

    private final Author[] author = new Author[5];
    private final long[] authorId = new long[5];

    @Before
    public void setup()
            throws DAO.DaoWriteException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "clean")) {

            db.getSyncDb().delete(TBL_AUTHORS.getName(),
                                  KEY_AUTHOR_FAMILY_NAME + " LIKE 'Author%'", null);

            author[0] = Author.from("Test0 Author0");
            authorId[0] = db.insert(context, author[0]);
        }
    }

    @Test
    public void author() {
        boolean updated;

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "author")) {

            author[0] = db.getAuthor(authorId[0]);
            assertNotNull(author[0]);
            assertEquals("Author0", author[0].getFamilyName());
            assertEquals("Test0", author[0].getGivenNames());
            assertFalse(author[0].isComplete());

            author[0].setComplete(true);
            updated = db.update(context, author[0]);
            assertTrue(updated);

            author[0] = db.getAuthor(authorId[0]);
            assertNotNull(author[0]);
            assertEquals("Author0", author[0].getFamilyName());
            assertEquals("Test0", author[0].getGivenNames());
            assertTrue(author[0].isComplete());

            updated = db.delete(context, author[0]);
            assertTrue(updated);
        }
    }
}
