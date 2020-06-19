/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import com.hardbacknutter.nevertoomanybooks.CommonSetup;
import com.hardbacknutter.nevertoomanybooks.database.DAO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PruneListTest
        extends CommonSetup {

    private static final long ISAAC_ASIMOV_ID = 100;
    private static final String ISAAC_ASIMOV = "Isaac Asimov";
    private static final long PHILIP_JOSE_FARMER_ID = 200;
    private static final String PHILIP_JOSE_FARMER = "Philip Jose Farmer";
    private static final long PHILIP_DICK_ID = 300;
    private static final String PHILIP_DICK = "Philip K. Dick";

    @Mock
    DAO mDb;

    @BeforeEach
    public void setUp() {
        super.setUp();

        mDb = mock(DAO.class);

        when(mDb.getAuthorId(eq(mContext), any(Author.class), anyBoolean(), any(Locale.class)))
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
        when(mDb.getSeriesId(eq(mContext), any(Series.class), anyBoolean(), any(Locale.class)))
                .thenAnswer((Answer<Long>) invocation -> {
                    final Series series = invocation.getArgument(1, Series.class);
                    return series.getId();
                });

        // the return value is not used for now but fits the Series title data used.
        when(mDb.getSeriesLanguage(100L)).thenReturn("eng");
        when(mDb.getSeriesLanguage(200L)).thenReturn("nld");
    }

    @Test
    void pruneAuthorList() {

        final List<Author> authorList = new ArrayList<>();
        Author author;

        // Keep
        author = Author.from(ISAAC_ASIMOV);
        author.setId(ISAAC_ASIMOV_ID);
        author.setComplete(false);
        authorList.add(author);
        // discard
        author = Author.from(ISAAC_ASIMOV);
        author.setId(0);
        author.setComplete(true);
        authorList.add(author);
        // discard
        author = Author.from(ISAAC_ASIMOV);
        author.setId(ISAAC_ASIMOV_ID);
        authorList.add(author);


        // keep
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(PHILIP_JOSE_FARMER_ID);
        authorList.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(PHILIP_JOSE_FARMER_ID);
        authorList.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(PHILIP_JOSE_FARMER_ID);
        authorList.add(author);


        // keep
        author = Author.from(PHILIP_DICK);
        author.setId(PHILIP_DICK_ID);
        author.setType(Author.TYPE_WRITER);
        authorList.add(author);

        // discard
        author = Author.from(PHILIP_DICK);
        author.setId(PHILIP_DICK_ID);
        author.setType(Author.TYPE_UNKNOWN);
        authorList.add(author);

        // discard, but add type to existing author
        author = Author.from(PHILIP_DICK);
        author.setId(PHILIP_DICK_ID);
        author.setType(Author.TYPE_CONTRIBUTOR);
        authorList.add(author);

        boolean modified = Author.pruneList(authorList, mContext, mDb, false, Locale.getDefault());

        assertTrue(modified);
        assertEquals(3, authorList.size());

        author = authorList.get(0);
        assertEquals(ISAAC_ASIMOV_ID, author.getId());
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertFalse(author.isComplete());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = authorList.get(1);
        assertEquals(PHILIP_JOSE_FARMER_ID, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        assertEquals("Philip Jose", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = authorList.get(2);
        assertEquals(PHILIP_DICK_ID, author.getId());
        assertEquals("Dick", author.getFamilyName());
        assertEquals("Philip K.", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_CONTRIBUTOR, author.getType());

    }

    @Test
    void pruneSeriesList() {
        setLocale(Locale.UK);

        final List<Series> list = new ArrayList<>();
        Series series;

        // keep, position=0
        series = Series.from("The series (5)");
        series.setId(100);
        series.setComplete(true);
        list.add(series);
        // discard
        series = Series.from("The series");
        series.setId(100);
        list.add(series);

        // discard
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);
        // discard
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);
        // keep, position=1
        series = Series.from("De reeks (1)");
        series.setId(200);
        list.add(series);

        // discard
        series = Series.from("The series (5)");
        series.setId(100);
        series.setComplete(false);
        list.add(series);
        // keep, position=2
        series = Series.from("The series (6)");
        series.setId(100);
        list.add(series);

        boolean modified = Series.pruneList(list, mContext, mDb, false, Locale.getDefault());

        assertTrue(modified);
        assertEquals(3, list.size());

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
}
