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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class PruneListTest
        extends Base {

    private static final long ISAAC_ASIMOV_ID = 100;
    private static final String ISAAC_ASIMOV = "Isaac Asimov";

    private static final long PHILIP_JOSE_FARMER_ID = 200;
    private static final String PHILIP_JOSE_FARMER = "Philip Jose Farmer";
    private static final String PHILIP_JOSE_FARMER_VARIANT = "Philip José Farmer";

    private static final long PHILIP_DICK_ID = 300;
    private static final String PHILIP_DICK = "Philip K. Dick";

    @Mock
    BookDao mDb;

    @BeforeEach
    public void setUp() {
        super.setUp();

        final SeriesDao seriesDao = SeriesDao.getInstance();
        final AuthorDao authorDao = AuthorDao.getInstance();
        final PublisherDao publisherDao = PublisherDao.getInstance();
        final TocEntryDao tocEntryDao = TocEntryDao.getInstance();

        when(authorDao.find(eq(mContext), any(Author.class),
                            anyBoolean(), any(Locale.class)))
                .thenAnswer((Answer<Long>) invocation -> {
                    final Author author = invocation.getArgument(1, Author.class);
                    final long id = author.getId();
                    if (id == 0) {
                        if ("Asimov".equals(author.getFamilyName())) {
                            return ISAAC_ASIMOV_ID;
                        }
                        if ("Farmer".equals(author.getFamilyName())) {
                            return PHILIP_JOSE_FARMER_ID;
                        }
                        if ("Dick".equals(author.getFamilyName())) {
                            return PHILIP_DICK_ID;
                        }
                    }
                    return id;
                });
        when(seriesDao.find(eq(mContext), any(Series.class),
                            anyBoolean(), any(Locale.class)))
                .thenAnswer((Answer<Long>) invocation -> {
                    final Series series = invocation.getArgument(1, Series.class);
                    return series.getId();
                });
        when(publisherDao.find(eq(mContext), any(Publisher.class),
                               anyBoolean(), any(Locale.class)))
                .thenAnswer((Answer<Long>) invocation -> {
                    final Publisher publisher = invocation.getArgument(1, Publisher.class);
                    return publisher.getId();
                });
        when(tocEntryDao.find(eq(mContext), any(TocEntry.class),
                              anyBoolean(), any(Locale.class)))
                .thenAnswer((Answer<Long>) invocation -> {
                    final TocEntry tocEntry = invocation.getArgument(1, TocEntry.class);
                    return tocEntry.getId();
                });

        // the return value is not used for now but fits the Series title data used.
        when(seriesDao.getLanguage(100L)).thenReturn("eng");
        when(seriesDao.getLanguage(200L)).thenReturn("nld");
    }

    @Test
    void pruneAuthorList01() {

        final List<Author> list = new ArrayList<>();
        Author author;

        // Keep, position 0
        author = Author.from(ISAAC_ASIMOV);
        author.setId(ISAAC_ASIMOV_ID);
        author.setComplete(false);
        list.add(author);

        // discard
        author = Author.from(ISAAC_ASIMOV);
        author.setId(0);
        author.setComplete(true);
        list.add(author);

        // discard
        author = Author.from(ISAAC_ASIMOV);
        author.setId(ISAAC_ASIMOV_ID);
        list.add(author);

        // keep, position 1
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(PHILIP_JOSE_FARMER_ID);
        list.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(PHILIP_JOSE_FARMER_ID);
        list.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(PHILIP_JOSE_FARMER_ID);
        list.add(author);

        // keep, position 2
        author = Author.from(PHILIP_DICK);
        author.setId(PHILIP_DICK_ID);
        author.setType(Author.TYPE_WRITER);
        list.add(author);

        // discard
        author = Author.from(PHILIP_DICK);
        author.setId(PHILIP_DICK_ID);
        author.setType(Author.TYPE_UNKNOWN);
        list.add(author);

        // discard, but add type to existing author in position 2
        author = Author.from(PHILIP_DICK);
        author.setId(PHILIP_DICK_ID);
        author.setType(Author.TYPE_CONTRIBUTOR);
        list.add(author);

        final boolean modified = Author
                .pruneList(list, mContext, mDb, false, Locale.getDefault());

        assertTrue(modified, list.toString());
        assertEquals(3, list.size(), list.toString());

        author = list.get(0);
        assertEquals(ISAAC_ASIMOV_ID, author.getId());
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertFalse(author.isComplete());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = list.get(1);
        assertEquals(PHILIP_JOSE_FARMER_ID, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        assertEquals("Philip Jose", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = list.get(2);
        assertEquals(PHILIP_DICK_ID, author.getId());
        assertEquals("Dick", author.getFamilyName());
        assertEquals("Philip K.", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_CONTRIBUTOR, author.getType());

    }

    @Test
    void pruneAuthorList02() {
        final List<Author> authorList = new ArrayList<>();
        Author author;

        // keep, position 0
        author = Author.from(PHILIP_JOSE_FARMER_VARIANT);
        author.setId(PHILIP_JOSE_FARMER_ID);
        author.setType(Author.TYPE_UNKNOWN);
        authorList.add(author);

        // merge type with position 1
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(PHILIP_JOSE_FARMER_ID);
        author.setType(Author.TYPE_WRITER);
        authorList.add(author);

        // merge type with position 1
        author = Author.from(PHILIP_JOSE_FARMER_VARIANT);
        author.setId(PHILIP_JOSE_FARMER_ID);
        author.setType(Author.TYPE_AFTERWORD);
        authorList.add(author);

        final boolean modified = Author
                .pruneList(authorList, mContext, mDb, false, Locale.getDefault());

        assertTrue(modified);
        assertEquals(1, authorList.size());

        author = authorList.get(0);
        assertEquals(PHILIP_JOSE_FARMER_ID, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        // Note the "José" because we added PHILIP_JOSE_FARMER_VARIANT as the first in the list
        assertEquals("Philip José", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_AFTERWORD, author.getType());
    }

    @Test
    void pruneSeries01List() {
        setLocale(Locale.UK);

        final List<Series> list = new ArrayList<>();
        Series series;

        // keep, position 0
        series = Series.from("The series (5)");
        series.setId(100);
        series.setComplete(true);
        list.add(series);

        // discard in favour of position 0 which has a number set
        series = Series.from("The series");
        series.setId(100);
        list.add(series);

        // discard in favour of position 1 (added two below here) which has a number set
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);

        // discard in favour of position 1 (added one below here) which has a number set
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);

        // keep, position 1
        series = Series.from("De reeks (1)");
        series.setId(200);
        list.add(series);

        // discard in favour of position 0 where we already had the number "5".
        // Note the difference in 'isComplete' is disregarded (first occurrence 'wins')
        series = Series.from("The series (5)");
        series.setId(100);
        series.setComplete(false);
        list.add(series);

        // keep, position 2. Note duplicate id, but different nr as compared to position 0
        series = Series.from("The series (6)");
        series.setId(100);
        list.add(series);

        final boolean modified = Series.pruneList(list, mContext, false, Locale.getDefault());

        assertTrue(modified, list.toString());
        assertEquals(3, list.size(), list.toString());

        series = list.get(0);
        assertEquals(100, series.getId());
        assertEquals("The series", series.getTitle());
        assertEquals("5", series.getNumber());
        assertTrue(series.isComplete());

        series = list.get(1);
        assertEquals(200, series.getId());
        assertEquals("De reeks", series.getTitle());
        assertEquals("1", series.getNumber());

        series = list.get(2);
        assertEquals(100, series.getId());
        assertEquals("The series", series.getTitle());
        assertEquals("6", series.getNumber());
    }

    @Test
    void prunePublisherNames01() {
        setLocale(Locale.UK);

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // keep, position 0
        publisher = new Publisher("Some publisher");
        publisher.setId(1001);
        list.add(publisher);

        // keep, position 1
        publisher = new Publisher("The publisher");
        publisher.setId(1002);
        list.add(publisher);

        // DISCARD ! The base data is different, but the id already exists.
        publisher = new Publisher("publisher, The");
        publisher.setId(1002);
        list.add(publisher);

        final boolean modified =
                Publisher.pruneList(list, mContext, false, Locale.getDefault());

        assertTrue(modified, list.toString());
        assertEquals(2, list.size(), list.toString());

        publisher = list.get(0);
        assertEquals(1001, publisher.getId());
        assertEquals("Some publisher", publisher.getName());

        publisher = list.get(1);
        assertEquals(1002, publisher.getId());
        assertEquals("The publisher", publisher.getName());
    }

    @Test
    void prunePublisherNames02() {
        setLocale(Locale.UK);

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // Keep; list will not be modified
        publisher = new Publisher("Some publisher");
        publisher.setId(1001);
        list.add(publisher);

        // Keep; list will not be modified
        publisher = new Publisher("The publisher");
        publisher.setId(0);
        list.add(publisher);

        // Keep; list will not be modified - this entry is fully different from the above
        publisher = new Publisher("publisher, The");
        publisher.setId(1002);
        list.add(publisher);

        final boolean modified =
                Publisher.pruneList(list, mContext, false, Locale.getDefault());

        assertFalse(modified, list.toString());
    }

    @Test
    void prunePublisherNames03() {
        setLocale(Locale.UK);

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // keep, position 0
        publisher = new Publisher("Some publisher");
        publisher.setId(1001);
        list.add(publisher);

        // keep, position 1
        publisher = new Publisher("The publisher");
        publisher.setId(1002);
        list.add(publisher);

        // keep, position 2
        publisher = new Publisher("publisher, The");
        publisher.setId(0);
        list.add(publisher);

        // Discard in favour of position 0
        publisher = new Publisher("Some publisher");
        publisher.setId(0);
        list.add(publisher);

        // Keep, but merge with the next entry and copy the id=1003
        publisher = new Publisher("José publisher");
        publisher.setId(0);
        list.add(publisher);

        // Discard; the id is copied to the above entry
        publisher = new Publisher("Jose publisher");
        publisher.setId(1003);
        list.add(publisher);

        final boolean modified =
                Publisher.pruneList(list, mContext, false, Locale.getDefault());

        assertTrue(modified, list.toString());
        assertEquals(4, list.size(), list.toString());

        publisher = list.get(0);
        assertEquals(1001, publisher.getId());
        assertEquals("Some publisher", publisher.getName());

        publisher = list.get(1);
        assertEquals(1002, publisher.getId());
        assertEquals("The publisher", publisher.getName());

        publisher = list.get(2);
        assertEquals(0, publisher.getId());
        assertEquals("publisher, The", publisher.getName());

        publisher = list.get(3);
        assertEquals(1003, publisher.getId());
        assertEquals("José publisher", publisher.getName());
    }

    @Test
    void pruneTocEntries01() {
        setLocale(Locale.UK);

        final Author author1 = Author.from(ISAAC_ASIMOV);
        author1.setId(ISAAC_ASIMOV_ID);

        final List<TocEntry> list = new ArrayList<>();
        TocEntry tocEntry;

        // All id's are set == 0


        // keep, position 0, merged with next entry with the same title
        tocEntry = new TocEntry(author1, "title 1", null);
        list.add(tocEntry);

        // keep, position 1
        tocEntry = new TocEntry(author1, "title 2", "2019");
        list.add(tocEntry);

        // discard after merging with position 0
        tocEntry = new TocEntry(author1, "title 1", "1978");
        list.add(tocEntry);

        // discard in favour of position 1
        tocEntry = new TocEntry(author1, "title 2", null);
        list.add(tocEntry);

        final boolean modified =
                TocEntry.pruneList(list, mContext, false, Locale.getDefault());

        assertTrue(modified, list.toString());
        assertEquals(2, list.size(), list.toString());

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
    void pruneTocEntries02() {
        setLocale(Locale.UK);

        final Author author1 = Author.from(ISAAC_ASIMOV);
        author1.setId(ISAAC_ASIMOV_ID);

        final List<TocEntry> list = new ArrayList<>();
        TocEntry tocEntry;

        // id's are set, results same as if id's were all 0

        tocEntry = new TocEntry(1, author1, "title 1", null, 0);
        list.add(tocEntry);

        tocEntry = new TocEntry(2, author1, "title 2", "2019", 0);
        list.add(tocEntry);

        tocEntry = new TocEntry(1, author1, "title 1", "1978", 0);
        list.add(tocEntry);
        tocEntry = new TocEntry(2, author1, "title 2", null, 0);
        list.add(tocEntry);

        final boolean modified =
                TocEntry.pruneList(list, mContext, false, Locale.getDefault());

        assertTrue(modified, list.toString());
        assertEquals(2, list.size(), list.toString());

        // first element is the first "title 1" but with the data from the second entry
        tocEntry = list.get(0);
        assertEquals(1, tocEntry.getId());
        assertEquals("title 1", tocEntry.getTitle());
        assertEquals(1978, tocEntry.getFirstPublicationDate().getYearValue());

        // second element is the first "title 2"
        tocEntry = list.get(1);
        assertEquals(2, tocEntry.getId());
        assertEquals("title 2", tocEntry.getTitle());
        assertEquals(2019, tocEntry.getFirstPublicationDate().getYearValue());
    }

    @Test
    void pruneTocEntries03() {
        setLocale(Locale.UK);

        final Author author1 = Author.from(ISAAC_ASIMOV);
        author1.setId(ISAAC_ASIMOV_ID);

        final List<TocEntry> list = new ArrayList<>();
        TocEntry tocEntry;

        // keep, position 0, but merged with entry 3 -> "1978"
        tocEntry = new TocEntry(1, author1, "title 1", null, 0);
        list.add(tocEntry);

        // keep, position 1, merged with entry 4 -> id=2
        tocEntry = new TocEntry(0, author1, "title 2", "2019", 0);
        list.add(tocEntry);

        // dropped, merged with position 0
        tocEntry = new TocEntry(0, author1, "title 1", "1978", 0);
        list.add(tocEntry);

        // dropped, merged with position 1
        tocEntry = new TocEntry(2, author1, "title 2", null, 0);
        list.add(tocEntry);

        // keep, position 2, with id reset to 0 because of conflict with position 1
        tocEntry = new TocEntry(2, author1, "title 2", "1880", 0);
        list.add(tocEntry);

        // keep, position 3
        tocEntry = new TocEntry(0, author1, "title 3", "1955", 0);
        list.add(tocEntry);

        // keep, position 4, with id reset to 0 because base data matches position 3
        // but still kept because extra data (the year) is different from position 3
        tocEntry = new TocEntry(4, author1, "title 3", "1965", 0);
        list.add(tocEntry);

        // keep, position 5
        tocEntry = new TocEntry(0, author1, "title 3", "1975", 0);
        list.add(tocEntry);

        final boolean modified =
                TocEntry.pruneList(list, mContext, false, Locale.getDefault());

        assertTrue(modified, list.toString());
        assertEquals(6, list.size(), list.toString());

        // first element is the first "title 1" but with the data from the second entry
        tocEntry = list.get(0);
        assertEquals(1, tocEntry.getId());
        assertEquals("title 1", tocEntry.getTitle());
        assertEquals(1978, tocEntry.getFirstPublicationDate().getYearValue());

        // second element is the first "title 2"
        tocEntry = list.get(1);
        assertEquals(2, tocEntry.getId());
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
