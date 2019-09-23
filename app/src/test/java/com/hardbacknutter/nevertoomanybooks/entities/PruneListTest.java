/*
 * @Copyright 2019 HardBackNutter
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * isUniqueById().
 * - 'true' for Author, Bookshelf, TocEntry
 * - 'false' for Series
 */
class PruneListTest {

    private static final String PHILIP_JOSE_FARMER = "Philip Jose Farmer";
    private static final String ISAAC_ASIMOV = "Isaac Asimov";

    /**
     * TEST: add authors with an id==0
     */
    @Test
    void pruneAuthorList() {

        List<Author> authorList = new ArrayList<>();
        Author author;

        // Keep
        author = Author.fromString(ISAAC_ASIMOV);
        author.setId(100);
        authorList.add(author);

        // discard even with isComplete==true
        author = Author.fromString(ISAAC_ASIMOV);
        author.setId(100);
        author.setComplete(true);
        authorList.add(author);

        // discard even with different name
        author = Author.fromString("bogus name");
        author.setId(100);
        authorList.add(author);

        // keep
        author = Author.fromString(PHILIP_JOSE_FARMER);
        author.setId(200);
        author.setComplete(true);
        authorList.add(author);

        // discard even with setComplete==false
        author = Author.fromString(PHILIP_JOSE_FARMER);
        author.setId(200);
        authorList.add(author);

        // discard
        author = Author.fromString(PHILIP_JOSE_FARMER);
        author.setId(200);
        authorList.add(author);

        // discard, even with setComplete==true, and type != 0
        author = Author.fromString(PHILIP_JOSE_FARMER);
        author.setId(200);
        author.setComplete(true);
        author.setType(Author.TYPE_CONTRIBUTOR);
        authorList.add(author);


        boolean modified = ItemWithFixableId.pruneList(authorList, null, null, Locale.getDefault());
        //System.out.println(list);

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
     * TEST: add series with an id!=0
     */
    @Test
    void pruneSeriesList() {
        List<Series> list = new ArrayList<>();
        Series series;

        series = Series.fromString("fred(5)");
        list.add(series);
        series = Series.fromString("fred");
        list.add(series);
        series = Series.fromString("bill");
        list.add(series);
        series = Series.fromString("bill");
        list.add(series);
        series = Series.fromString("bill(1)");
        list.add(series);
        series = Series.fromString("fred(5)");
        list.add(series);
        series = Series.fromString("fred(6)");
        list.add(series);

//        series = Series.fromString("bill(1)");
//        series.setId(1);
//        list.add(series);
//        series = Series.fromString("fred(5)");
//        series.setId(2);
//        list.add(series);
//        series = Series.fromString("fred(6)");
//        series.setId(3);
//        list.add(series);
//        series = Series.fromString("fred(6)");
//        series.setId(2);
//        list.add(series);

        boolean modified = Series.pruneList(list, null, null, Locale.getDefault());
        System.out.println(list);

        assertFalse(series.isUniqueById());
        assertTrue(modified);
        assertEquals(3, list.size());

        series = list.get(0);
        assertEquals("fred", series.getTitle());
        assertEquals("5", series.getNumber());

        series = list.get(1);
        assertEquals("bill", series.getTitle());
        assertEquals("1", series.getNumber());

        series = list.get(2);
        assertEquals("fred", series.getTitle());
        assertEquals("6", series.getNumber());
    }
}