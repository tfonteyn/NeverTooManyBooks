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

package com.hardbacknutter.nevertoomanybooks.citations;

import android.os.Bundle;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.booklist.style.WritableStyle;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BibTeXCitationTest
        extends BaseDBTest {

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void cite() {
        final Bundle bundle = new Bundle();
        bundle.putString(DBKey.TITLE, "The Deeper Meaning of Liff");
        bundle.putString(DBKey.ISBN, "1234567890123");
        bundle.putString(DBKey.PUBLICATION_DATE, "1990");

        final Book book = new Book(bundle);
        book.setAuthors(List.of(Author.from("Douglas Adams"),
                                Author.from(" John Lloyd")));
        book.setPublishers(List.of(Publisher.from("Pan Books")));
        book.setSeries(List.of(Series.from("The Meaning of Liff", "2")));

        final WritableStyle style = getUserStyle().orElseThrow();
        style.setCitationType(CitationType.BibTeX);
        style.setShowAuthorByGivenName(false);

        final Citation citation = new BibTeXCitation(style);
        final String s = citation.cite(context, book);

        final String s1 = "@book{NeverTooManyBooks,"
                          + "\n  title     = {The Deeper Meaning of Liff},"
                          + "\n  author    = {{Adams}, Douglas and {Lloyd}, John},"
                          + "\n  isbn      = {1234567890123},"
                          + "\n  publisher = {Pan Books},"
                          + "\n  year      = {1990},"
                          + "\n  series    = {The Meaning of Liff},"
                          + "\n  number    = {2}"
                          + "\n}"
                          + "\n";

        assertEquals(s1, s);
    }
}
