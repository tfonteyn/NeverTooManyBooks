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
package com.hardbacknutter.nevertoomanybooks.entities;


import android.content.Context;
import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.database.DaoLocator;
import com.hardbacknutter.nevertoomanybooks.database.DbLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


@SmallTest
public class AuthorTest {

    private static final String ISAAC_ASIMOV = "Isaac Asimov";

    private static final String PHILIP_JOSE_FARMER = "Philip Jose Farmer";
    private static final String PHILIP_JOSE_FARMER_VARIANT = "Philip José Farmer";

    private static final String PHILIP_DICK = "Philip K. Dick";

    private static final long FAKE_ID_0 = 2_000_100;
    private static final long FAKE_ID_1 = 2_000_200;
    private static final long FAKE_ID_2 = 2_000_300;

    /**
     * Reminder: The base test {@code assertEquals(pAuthor, author)}
     * is testing {@link Author#equals(Object)} only.
     */
    @Test
    public void parcelling() {
        final Author author = Author.from(ISAAC_ASIMOV);

        final Parcel parcel = Parcel.obtain();
        author.writeToParcel(parcel, author.describeContents());
        parcel.setDataPosition(0);
        final Author pAuthor = Author.CREATOR.createFromParcel(parcel);

        assertEquals(pAuthor, author);

        assertEquals(pAuthor.getId(), author.getId());
        assertEquals(pAuthor.getFamilyName(), author.getFamilyName());
        assertEquals(pAuthor.getGivenNames(), author.getGivenNames());
        assertEquals(pAuthor.isComplete(), author.isComplete());
        assertEquals(pAuthor.getType(), author.getType());
    }

    @Test
    public void pruneAuthorList01() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DbLocator.init(context);

        final AuthorDao authorDao = DaoLocator.getInstance().getAuthorDao();

        final List<Author> list = new ArrayList<>();
        Author author;

        // Keep, position 0
        author = Author.from(ISAAC_ASIMOV);
        final long id0 = authorDao.fixId(context, author, false, Locale.getDefault());
        if (id0 == 0) {
            authorDao.insert(context, author);
        }
        author.setId(FAKE_ID_0);
        author.setComplete(false);
        list.add(author);

        // discard
        author = Author.from(ISAAC_ASIMOV);
        author.setId(0);
        author.setComplete(true);
        list.add(author);

        // discard
        author = Author.from(ISAAC_ASIMOV);
        author.setId(FAKE_ID_0);
        list.add(author);

        // keep, position 1
        author = Author.from(PHILIP_JOSE_FARMER);
        final long id1 = authorDao.fixId(context, author, false, Locale.getDefault());
        if (id1 == 0) {
            authorDao.insert(context, author);
        }
        author.setId(FAKE_ID_1);
        list.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(FAKE_ID_1);
        list.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(FAKE_ID_1);
        list.add(author);

        // keep, position 2
        author = Author.from(PHILIP_DICK);
        final long id2 = authorDao.fixId(context, author, false, Locale.getDefault());
        if (id2 == 0) {
            authorDao.insert(context, author);
        }
        author.setId(FAKE_ID_2);
        author.setType(Author.TYPE_WRITER);
        list.add(author);

        // discard
        author = Author.from(PHILIP_DICK);
        author.setId(FAKE_ID_2);
        author.setType(Author.TYPE_UNKNOWN);
        list.add(author);

        // discard, but add type to existing author in position 2
        author = Author.from(PHILIP_DICK);
        author.setId(FAKE_ID_2);
        author.setType(Author.TYPE_CONTRIBUTOR);
        list.add(author);

        final boolean modified = Author.pruneList(list, context, false, Locale.getDefault());

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 3, list.size());

        assertTrue(id0 > 0);
        assertTrue(id1 > 0);
        assertTrue(id2 > 0);

        author = list.get(0);
        assertEquals(id0, author.getId());
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertFalse(author.isComplete());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = list.get(1);
        assertEquals(id1, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        assertEquals("Philip Jose", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = list.get(2);
        assertEquals(id2, author.getId());
        assertEquals("Dick", author.getFamilyName());
        assertEquals("Philip K.", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_CONTRIBUTOR, author.getType());
    }

    @Test
    public void pruneAuthorList02() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DbLocator.init(context);

        final AuthorDao authorDao = DaoLocator.getInstance().getAuthorDao();

        final List<Author> authorList = new ArrayList<>();
        Author author;

        // keep, position 0
        author = Author.from(PHILIP_JOSE_FARMER_VARIANT);
        final long id0 = authorDao.fixId(context, author, false, Locale.getDefault());
        author.setId(FAKE_ID_1);
        author.setType(Author.TYPE_UNKNOWN);
        authorList.add(author);

        // merge type with position 1
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(FAKE_ID_1);
        author.setType(Author.TYPE_WRITER);
        authorList.add(author);

        // merge type with position 1
        author = Author.from(PHILIP_JOSE_FARMER_VARIANT);
        author.setId(FAKE_ID_1);
        author.setType(Author.TYPE_AFTERWORD);
        authorList.add(author);

        final boolean modified = Author.pruneList(authorList, context, false, Locale.getDefault());

        assertTrue(modified);
        assertEquals(1, authorList.size());

        assertTrue(id0 > 0);

        author = authorList.get(0);
        assertEquals(id0, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        // Note the "José" because we added PHILIP_JOSE_FARMER_VARIANT as the first in the list
        assertEquals("Philip José", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_AFTERWORD, author.getType());
    }
}
