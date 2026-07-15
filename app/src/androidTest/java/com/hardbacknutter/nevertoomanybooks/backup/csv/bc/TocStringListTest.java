/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.backup.csv.bc;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.backup.csv.util.StringList;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TocStringListTest {

    private static final String ENCODED =
            "Giants In The Sky (1952) * Blish, James"
            + '|' + "Other title (1986-02) * Person, Some"
            + '|' + "The other one (2013-12-28) * Else, Someone"
            + '|' + "Simple entry * Me, Myoh";

    private StringList<TocEntry> coder;

    @BeforeEach
    void setUp() {
        coder = new StringList<>(new TocEntryCoder());
    }

    @Test
    void decode() {
        final List<TocEntry> decoded = coder.decodeList(ENCODED);
        assertEquals(4, decoded.size());
        TocEntry entry;
        Author author;

        entry = decoded.get(0);
        assertNotNull(entry);
        author = entry.getPrimaryAuthor();
        assertNotNull(author);
        assertEquals("Blish", author.getFamilyName());
        assertEquals("James", author.getGivenNames());
        assertEquals("1952", entry.getFirstPublicationDate().getIsoString());
        assertEquals("Giants In The Sky", entry.getTitle());

        entry = decoded.get(1);
        assertNotNull(entry);
        author = entry.getPrimaryAuthor();
        assertNotNull(author);
        assertEquals("Person", author.getFamilyName());
        assertEquals("Some", author.getGivenNames());
        assertEquals("1986-02", entry.getFirstPublicationDate().getIsoString());
        assertEquals("Other title", entry.getTitle());

        entry = decoded.get(2);
        assertNotNull(entry);
        author = entry.getPrimaryAuthor();
        assertNotNull(author);
        assertEquals("Else", author.getFamilyName());
        assertEquals("Someone", author.getGivenNames());
        assertEquals("2013-12-28", entry.getFirstPublicationDate().getIsoString());
        assertEquals("The other one", entry.getTitle());

        entry = decoded.get(3);
        assertNotNull(entry);
        author = entry.getPrimaryAuthor();
        assertNotNull(author);
        assertEquals("Me", author.getFamilyName());
        assertEquals("Myoh", author.getGivenNames());
        assertEquals(PartialDate.NOT_SET, entry.getFirstPublicationDate());
        assertEquals("Simple entry", entry.getTitle());
    }
}
