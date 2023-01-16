/*
 * @Copyright 2018-2022 HardBackNutter
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


import android.os.Parcel;

import androidx.test.filters.MediumTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@MediumTest
public class AuthorTest
        extends BaseDBTest {

    private static final String ISAAC_ASIMOV = "Isaac Asimov";
    private static final String PAUL_FRENCH = "Paul French";

    private static final String PHILIP_JOSE_FARMER = "Philip Jose Farmer";
    private static final String PHILIP_JOSE_FARMER_VARIANT = "Philip José Farmer";

    private static final String PHILIP_DICK = "Philip K. Dick";

    private static final long FAKE_ID_0 = 2_000_100;
    private static final long FAKE_ID_1 = 2_000_200;
    private static final long FAKE_ID_2 = 2_000_300;
    private static final long FAKE_ID_3 = 2_000_400;

    /**
     * Reminder: The base test {@code assertEquals(pAuthor, author)}
     * is testing {@link Author#equals(Object)} only.
     */
    @Test
    public void parcelling() {
        final Author author = Author.from(PAUL_FRENCH);
        author.setRealAuthor(Author.from(ISAAC_ASIMOV));

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
        assertEquals(pAuthor.getRealAuthor(), author.getRealAuthor());
    }

    @Test
    public void pruneAuthorList01()
            throws DaoWriteException {
        final Locale bookLocale = Locale.getDefault();
        final AuthorDao authorDao = serviceLocator.getAuthorDao();

        final List<Author> list = new ArrayList<>();
        Author author;

        // Keep, position 0
        author = Author.from(ISAAC_ASIMOV);
        authorDao.fixId(context, author, false, bookLocale);
        long id0 = author.getId();
        if (id0 == 0) {
            id0 = authorDao.insert(context, author, bookLocale);
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
        final Author author2 = Author.from(PAUL_FRENCH);
        author2.setId(FAKE_ID_3);
        author2.setRealAuthor(author);
        authorDao.fixId(context, author2, false, bookLocale);
        long id1 = author2.getId();
        if (id1 == 0) {
            id1 = authorDao.insert(context, author2, bookLocale);
        }
        list.add(author2);

        // keep, position 2
        author = Author.from(PHILIP_JOSE_FARMER);
        authorDao.fixId(context, author, false, bookLocale);
        long id2 = author.getId();
        if (id2 == 0) {
            id2 = authorDao.insert(context, author, bookLocale);
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

        // keep, position 3
        author = Author.from(PHILIP_DICK);
        authorDao.fixId(context, author, false, bookLocale);
        long id3 = author.getId();
        if (id3 == 0) {
            id3 = authorDao.insert(context, author, bookLocale);
        }
        author.setId(FAKE_ID_2);
        author.setType(Author.TYPE_WRITER);
        list.add(author);

        // discard
        author = Author.from(PHILIP_DICK);
        author.setId(FAKE_ID_2);
        author.setType(Author.TYPE_UNKNOWN);
        list.add(author);

        // discard, but add type to existing author in position 3
        author = Author.from(PHILIP_DICK);
        author.setId(FAKE_ID_2);
        author.setType(Author.TYPE_CONTRIBUTOR);
        list.add(author);

        final boolean modified = authorDao.pruneList(context, list, false, bookLocale);

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 4, list.size());

        assertTrue(id0 > 0);
        assertTrue(id1 > 0);
        assertTrue(id2 > 0);
        assertTrue(id3 > 0);

        author = list.get(0);
        assertEquals(id0, author.getId());
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertFalse(author.isComplete());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = list.get(1);
        assertEquals(id1, author.getId());
        assertEquals("French", author.getFamilyName());
        assertEquals("Paul", author.getGivenNames());
        assertNotNull(author.getRealAuthor());
        assertEquals("Asimov", author.getRealAuthor().getFamilyName());
        assertEquals("Isaac", author.getRealAuthor().getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = list.get(2);
        assertEquals(id2, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        assertEquals("Philip Jose", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = list.get(3);
        assertEquals(id3, author.getId());
        assertEquals("Dick", author.getFamilyName());
        assertEquals("Philip K.", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_CONTRIBUTOR, author.getType());
    }

    @Test
    public void pruneAuthorList02() {
        final Locale bookLocale = Locale.getDefault();
        final AuthorDao authorDao = serviceLocator.getAuthorDao();

        final List<Author> authorList = new ArrayList<>();
        Author author;

        // keep, position 0
        author = Author.from(PHILIP_JOSE_FARMER_VARIANT);
        authorDao.fixId(context, author, false, bookLocale);
        final long id0 = author.getId();
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

        final boolean modified = authorDao.pruneList(context, authorList, false, bookLocale);

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
