/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.CallSuper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

import org.junit.Before;
import org.junit.Test;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GermanEszettTest
        extends BaseDBTest {

    private static final String GERMAN_GROSS_1F = "Groß";
    private static final String GERMAN_GROSS_2F = "Gross";
    private static final String GERMAN_GROSS_1 = "Jan Groß";
    private static final String GERMAN_GROSS_2 = "Jan Gross";
    private static final String BIRTH_DATE_1 = "1970-01-02";
    private long id1;
    private AuthorDao authorDao;

    @Before
    @CallSuper
    public void setup()
            throws IOException, StorageException, DaoWriteException {
        super.setup("de_DE");

        final SynchronizedDb db = serviceLocator.getDb();
        db.delete(TBL_AUTHORS.getName(), DBKey.AUTHOR.FAMILY_NAME
                                         + "='Groß'", null);
        db.delete(TBL_AUTHORS.getName(), DBKey.AUTHOR.FAMILY_NAME
                                         + "='Gross'", null);

        this.authorDao = serviceLocator.getAuthorDao();
        final Author author1 = Author.from(GERMAN_GROSS_1);
        author1.setBirthDate(BIRTH_DATE_1);
        id1 = authorDao.insert(context, author1, Locale.GERMANY);
    }

    @Test
    public void germanEszett() {
        final Locale bookLocale = Locale.GERMANY;

        final List<Author> authorList = new ArrayList<>();

        // Author existing in the database, i.e. with a valid id
        final Author author1 = Author.from(GERMAN_GROSS_1);
        author1.setBirthDate(BIRTH_DATE_1);
        authorDao.fixId(context, author1, bookLocale);
        authorList.add(author1);

        // Author does NOT exist in the db, forcefully set same id
        // => must be removed due to duplicate id
        final Author author2 = Author.from(GERMAN_GROSS_2);
        author1.setBirthDate(BIRTH_DATE_1);
        author2.setId(author1.getId());
        authorList.add(author2);

        // Same as Author2 but WITHOUT a birthday, and with id==0
        // => must be removed to duplicate name and missing birthday
        final Author author3 = Author.from(GERMAN_GROSS_2);
        authorList.add(author3);

        final boolean modified = authorDao.pruneList(context, authorList, item -> bookLocale);
        assertTrue(modified);

        assertEquals(1, authorList.size());
        final Author result = authorList.get(0);
        assertEquals(id1, result.getId());
        assertEquals(GERMAN_GROSS_1F, result.getFamilyName());
        assertEquals(BIRTH_DATE_1, result.getBirthDate().orElseThrow());
    }
}
