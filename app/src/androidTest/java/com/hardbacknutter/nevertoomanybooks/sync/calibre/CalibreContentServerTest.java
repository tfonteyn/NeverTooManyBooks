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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;

import java.io.FileNotFoundException;
import java.security.cert.CertificateException;
import java.util.ArrayList;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import static org.junit.Assert.assertEquals;

public class CalibreContentServerTest {

    @Test
    public void filenames()
            throws CertificateException, FileNotFoundException {

        final ArrayList<Author> authors = new ArrayList<>();
        authors.add(Author.from("Arthur C. Clarke"));
        final ArrayList<Series> series = new ArrayList<>();
        series.add(Series.from("Rama"));

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final CalibreContentServer server = new CalibreContentServer(context);

        final Book book = new Book();
        book.setAuthors(authors);

        book.putString(DBKey.TITLE, "Rama");

        final String dir = server.createAuthorDirectoryName(context, book);
        assertEquals("Clarke, Arthur C", dir);

        String fileName = server.createFilename(context, book);
        assertEquals("Rama", fileName);

        book.putString(DBKey.TITLE, "Rama: the omnibus");

        // without a series
        fileName = server.createFilename(context, book);
        assertEquals("Rama_ the omnibus", fileName);

        // now with a series
        book.setSeries(series);
        fileName = server.createFilename(context, book);
        assertEquals("Rama - Rama_ the omnibus", fileName);
    }
}
