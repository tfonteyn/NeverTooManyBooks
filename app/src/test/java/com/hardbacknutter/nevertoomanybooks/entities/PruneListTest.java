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

import com.hardbacknutter.nevertoomanybooks.CommonSetup;
import com.hardbacknutter.nevertoomanybooks.database.DAO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIXME: pretend fixing the id - how to return the actual id of the current author/series?
 * <p>
 * isUniqueById().
 * - 'true' for Author, Bookshelf, TocEntry
 * - 'false' for Series
 */
class PruneListTest
        extends CommonSetup {

    private static final String PHILIP_JOSE_FARMER = "Philip Jose Farmer";
    private static final String ISAAC_ASIMOV = "Isaac Asimov";

    @Mock
    DAO mDb;

    @BeforeEach
    public void setUp() {
        super.setUp();

        mDb = mock(DAO.class);

//        when(mDb.getAuthorId(any(Author.class), any(Locale.class)))
//                .thenReturn(100L);
//        when(mDb.getSeriesId(eq(mContext), any(Series.class), any(Locale.class)))
//                .thenReturn(100L);

        // the return value is not used for now but fits the Series title data used.
        when(mDb.getSeriesLanguage(100L)).thenReturn("eng");
        when(mDb.getSeriesLanguage(200L)).thenReturn("nld");
    }

    /**
     * Test {@link ItemWithFixableId#pruneList}.
     *
     * Currently this test will fail as we don't mock the right id for the objects.
     */
    @Test
    void pruneAuthorList() {

        List<Author> authorList = new ArrayList<>();

        // Keep
        Author author = Author.from(ISAAC_ASIMOV);
        author.setId(100);
        authorList.add(author);

        // discard even with isComplete==true
        author = Author.from(ISAAC_ASIMOV);
        author.setId(100);
        author.setComplete(true);
        authorList.add(author);

        // discard even with different name
        author = Author.from("bogus name");
        author.setId(100);
        authorList.add(author);

        // keep
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(200);
        author.setComplete(true);
        authorList.add(author);

        // discard even with setComplete==false
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(200);
        authorList.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(200);
        authorList.add(author);

        // discard, even with setComplete==true, and type != 0
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(200);
        author.setComplete(true);
        author.setType(Author.TYPE_CONTRIBUTOR);
        authorList.add(author);


        boolean modified = ItemWithFixableId.pruneList(authorList, mContext,
                                                       mDb, Locale.getDefault(), false);
        System.out.println(authorList);

        assertTrue(author.isUniqueById());
        assertTrue(modified);
        assertEquals(2, authorList.size());

        author = authorList.get(0);
        assertEquals(100, author.getId());
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        // isComplete matching the first author in the list with this id.
        assertFalse(author.isComplete());
        // type is ignored
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = authorList.get(1);
        assertEquals(200, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        assertEquals("Philip Jose", author.getGivenNames());
        // isComplete matching the first author in the list with this id.
        assertTrue(author.isComplete());
        // type is ignored
        assertEquals(Author.TYPE_UNKNOWN, author.getType());
    }


    /**
     * While {@link Series#pruneList} does call {@link ItemWithFixableId#pruneList},
     * this Series data is only setup for testing {@link Series#pruneList} itself.
     * <p>
     * The {@link #pruneAuthorList()} is used to test {@link ItemWithFixableId#pruneList}.
     *
     */
    @Test
    void pruneSeriesList() {
        setLocale(Locale.UK);

        List<Series> list = new ArrayList<>();

        Series series = Series.from("The series (5)");
        series.setId(100);
        list.add(series);
        series = Series.from("The series");
        series.setId(100);
        list.add(series);
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);
        series = Series.from("De reeks (1)");
        series.setId(200);
        list.add(series);
        series = Series.from("The series (5)");
        series.setId(100);
        list.add(series);
        series = Series.from("The series (6)");
        series.setId(100);
        list.add(series);

        //System.out.println(list);
        boolean modified = Series.pruneList(list, mContext, mDb, Locale.getDefault(), false);

        assertFalse(series.isUniqueById());
        assertTrue(modified);
        assertEquals(3, list.size());

        series = list.get(0);
        assertEquals("The series", series.getTitle());
        assertEquals("5", series.getNumber());

        series = list.get(1);
        assertEquals("De reeks", series.getTitle());
        assertEquals("1", series.getNumber());

        series = list.get(2);
        assertEquals("The series", series.getTitle());
        assertEquals("6", series.getNumber());
    }
}
