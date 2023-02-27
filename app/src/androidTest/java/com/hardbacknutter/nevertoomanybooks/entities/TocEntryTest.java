/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.test.filters.MediumTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

//FIXME: test 1 does not use toc id's (and passes) + test 2/3 uses id's (and fails)
@MediumTest
public class TocEntryTest
        extends BaseDBTest {

    private static final String ISAAC_ASIMOV = "Isaac Asimov";

    @Test
    public void pruneTocEntries01()
            throws DaoWriteException {
        final Locale bookLocale = Locale.getDefault();
        final AuthorDao authorDao = serviceLocator.getAuthorDao();
        final TocEntryDao tocEntryDao = serviceLocator.getTocEntryDao();

        final Author author0 = Author.from(ISAAC_ASIMOV);
        authorDao.fixId(context, author0, false, bookLocale);
        long authorId0 = author0.getId();
        if (authorId0 == 0) {
            authorId0 = authorDao.insert(context, author0, bookLocale);
        }
        assertTrue(authorId0 > 0);

        final List<TocEntry> list = new ArrayList<>();
        TocEntry tocEntry;

        // All id's are set == 0


        // keep, position 0, merged with next entry with the same title
        tocEntry = new TocEntry(author0, "title 1");
        list.add(tocEntry);

        // keep, position 1
        tocEntry = new TocEntry(author0, "title 2", "2019");
        list.add(tocEntry);

        // discard after merging with position 0
        tocEntry = new TocEntry(author0, "title 1", "1978");
        list.add(tocEntry);

        // discard in favour of position 1
        tocEntry = new TocEntry(author0, "title 2");
        list.add(tocEntry);

        final boolean modified = tocEntryDao.pruneList(context, list, false,
                                                       bookLocale);

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 2, list.size());

        // first element is the first "title 1" but with the data from the second entry
        tocEntry = list.get(0);
        assertEquals("title 1", tocEntry.getTitle());
        assertEquals(1978, tocEntry.getFirstPublicationDate().getYearValue());

        // second element is the first "title 2"
        tocEntry = list.get(1);
        assertEquals("title 2", tocEntry.getTitle());
        assertEquals(2019, tocEntry.getFirstPublicationDate().getYearValue());
    }

    @Test
    public void pruneTocEntries02()
            throws DaoWriteException {
        final Locale bookLocale = Locale.getDefault();
        final AuthorDao authorDao = serviceLocator.getAuthorDao();
        final TocEntryDao tocEntryDao = serviceLocator.getTocEntryDao();

        final Author author0 = Author.from(ISAAC_ASIMOV);
        authorDao.fixId(context, author0, false, bookLocale);
        long authorId0 = author0.getId();
        if (authorId0 == 0) {
            authorId0 = authorDao.insert(context, author0, bookLocale);
        }
        assertTrue(authorId0 > 0);

        final List<TocEntry> list = new ArrayList<>();
        TocEntry tocEntry;

        // id's are set, results same as if id's were all 0

        tocEntry = new TocEntry(1, author0, "title 1", null, 0);
        list.add(tocEntry);

        tocEntry = new TocEntry(2, author0, "title 2", "2019", 0);
        list.add(tocEntry);

        tocEntry = new TocEntry(1, author0, "title 1", "1978", 0);
        list.add(tocEntry);
        tocEntry = new TocEntry(2, author0, "title 2", null, 0);
        list.add(tocEntry);

        // pruning will reset the id's to 0 as the entries don't exist in the db
        final boolean modified = tocEntryDao.pruneList(context, list, false,
                                                       bookLocale);

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 2, list.size());

        // first element is the first "title 1" but with the data from the second entry
        tocEntry = list.get(0);
        assertEquals(0, tocEntry.getId());
        assertEquals("title 1", tocEntry.getTitle());
        assertEquals(1978, tocEntry.getFirstPublicationDate().getYearValue());

        // second element is the first "title 2"
        tocEntry = list.get(1);
        assertEquals(0, tocEntry.getId());
        assertEquals("title 2", tocEntry.getTitle());
        assertEquals(2019, tocEntry.getFirstPublicationDate().getYearValue());
    }


    @Test
    public void pruneTocEntries03()
            throws DaoWriteException {
        final Locale bookLocale = Locale.getDefault();
        final AuthorDao authorDao = serviceLocator.getAuthorDao();
        final TocEntryDao tocEntryDao = serviceLocator.getTocEntryDao();

        final Author author0 = Author.from(ISAAC_ASIMOV);
        authorDao.fixId(context, author0, false, bookLocale);
        long authorId0 = author0.getId();
        if (authorId0 == 0) {
            authorId0 = authorDao.insert(context, author0, bookLocale);
        }
        assertTrue(authorId0 > 0);

        final List<TocEntry> list = new ArrayList<>();
        TocEntry tocEntry;

        // keep, position 0, but merged with entry 3 -> "1978"
        tocEntry = new TocEntry(1, author0, "title 1", null, 0);
        list.add(tocEntry);

        // keep, position 1, merged with entry 4 -> id=2
        tocEntry = new TocEntry(0, author0, "title 2", "2019", 0);
        list.add(tocEntry);

        // dropped, merged with position 0
        tocEntry = new TocEntry(0, author0, "title 1", "1978", 0);
        list.add(tocEntry);

        // dropped, merged with position 1
        tocEntry = new TocEntry(2, author0, "title 2", null, 0);
        list.add(tocEntry);

        // keep, position 2, with id reset to 0 because of conflict with position 1
        tocEntry = new TocEntry(2, author0, "title 2", "1880", 0);
        list.add(tocEntry);

        // keep, position 3
        tocEntry = new TocEntry(0, author0, "title 3", "1955", 0);
        list.add(tocEntry);

        // keep, position 4, with id reset to 0 because base data matches position 3
        // but still kept because extra data (the year) is different from position 3
        tocEntry = new TocEntry(4, author0, "title 3", "1965", 0);
        list.add(tocEntry);

        // keep, position 5
        tocEntry = new TocEntry(0, author0, "title 3", "1975", 0);
        list.add(tocEntry);

        final boolean modified = tocEntryDao.pruneList(context, list, false,
                                                       bookLocale);

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 6, list.size());

        // first element is the first "title 1" but with the data from the second entry
        tocEntry = list.get(0);
        assertEquals(0, tocEntry.getId());
        assertEquals("title 1", tocEntry.getTitle());
        assertEquals(1978, tocEntry.getFirstPublicationDate().getYearValue());

        // second element is the first "title 2"
        tocEntry = list.get(1);
        assertEquals(0, tocEntry.getId());
        assertEquals("title 2", tocEntry.getTitle());
        assertEquals(2019, tocEntry.getFirstPublicationDate().getYearValue());

        // this is the third "title 2" with the id reset to 0
        tocEntry = list.get(2);
        assertEquals(0, tocEntry.getId());
        assertEquals("title 2", tocEntry.getTitle());
        assertEquals(1880, tocEntry.getFirstPublicationDate().getYearValue());

        tocEntry = list.get(3);
        assertEquals(0, tocEntry.getId());
        assertEquals("title 3", tocEntry.getTitle());
        assertEquals(1955, tocEntry.getFirstPublicationDate().getYearValue());

        tocEntry = list.get(4);
        assertEquals(0, tocEntry.getId());
        assertEquals("title 3", tocEntry.getTitle());
        assertEquals(1965, tocEntry.getFirstPublicationDate().getYearValue());

        tocEntry = list.get(5);
        assertEquals(0, tocEntry.getId());
        assertEquals("title 3", tocEntry.getTitle());
        assertEquals(1975, tocEntry.getFirstPublicationDate().getYearValue());
    }
}
