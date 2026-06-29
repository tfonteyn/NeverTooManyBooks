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

package com.hardbacknutter.nevertoomanybooks.citations;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RISCitationTest
        extends BaseDBTest {

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    void cite() {
        final Book book = new Book();
        book.setTitle("The Deeper Meaning of Liff");
        book.setRawProductCode("1234567890123");
        book.setPublicationDate(1990);

        book.setAuthors(List.of(Author.from("Douglas Adams"),
                                Author.from(" John Lloyd")));
        book.setPublishers(List.of(Publisher.from("Pan Books")));
        book.setSeries(List.of(Series.from("The Meaning of Liff", "2")));
        book.setIdentifierValue(Identifier.SID_GOOGLE, "zXEgAQAAQBAJ");

        final Citation citation = new RISCitation();
        final String s = citation.cite(context, book);

        final String s1 = "TY  - BOOK"
                          + "\r\nT1  - The Deeper Meaning of Liff"
                          + "\r\nA1  - Adams, Douglas"
                          + "\r\nA1  - Lloyd, John"
                          + "\r\nSN  - 1234567890123"
                          + "\r\nT3  - The Meaning of Liff"
                          + "\r\nSV  - 2"
                          + "\r\nPB  - Pan Books"
                          + "\r\nY1  - 1990"
                          + "\r\nUR  - https://books.google.co.uk/books?id=zXEgAQAAQBAJ"
                          + "\r\nER  -"
                          + "\r\n";

        assertEquals(s1, s);
    }
}
