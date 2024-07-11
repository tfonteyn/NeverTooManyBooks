/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.TestUtils;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.BasicMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class GoodreadsCsvImportTest
        extends BaseDBTest {

    private static final String TAG = "GoodreadsCsvImportTest";

    private BookDao bookDao;
    private int booksPresent;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        ServiceLocator.getInstance().getDb().delete(DBDefinitions.TBL_BOOKS.getName(),
                                                    DBKey.SID_GOODREADS_BOOK + " <>''",
                                                    null);

        bookDao = ServiceLocator.getInstance().getBookDao();
        booksPresent = bookDao.count();
    }

    @SuppressWarnings("LocalCanBeFinal")
    @Test
    public void goodreads()
            throws DataReaderException, IOException,
                   StorageException, CredentialsException, CertificateException {


        final RealNumberParser realNumberParser = new RealNumberParser(
                LocaleListUtils.asList(context));

        File file;
        Locale locale;
        ImportHelper importHelper;
        Optional<ArchiveMetaData> oMetaData;
        ArchiveMetaData metaData;
        ImportResults importResults;

        file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.goodreads_library_export_csv,
                new File(context.getCacheDir(), "goodreads_library_export.csv"));

        locale = context.getResources().getConfiguration().getLocales().get(0);

        importHelper = new ImportHelper(context, locale, Uri.fromFile(file));

        oMetaData = importHelper.readMetaData(context);
        assertTrue(oMetaData.isPresent());
        metaData = oMetaData.get();
        assertNotNull(metaData);
        assertEquals(CsvRecordReader.Origin.Goodreads,
                     metaData.getData().getParcelable(CsvRecordReader.Origin.BKEY));
        assertTrue(metaData.getData().containsKey(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED));
        // "goodreads_library_export.csv" does NOT contain such a field
        assertFalse(metaData.getData().getBoolean(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED));

        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);
        importResults = importHelper.read(context, new TestProgressListener(TAG));

        assertEquals(22, importResults.booksProcessed);
        assertEquals(22, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 22, bookDao.count());

        // 8998451,Jack van de Schaduwen,
        // Roger Zelazny,"Zelazny, Roger",Ruud Löbler,"=""9027406928""","=""9789027406927""",
        // 5,3.99,Het Spectrum,Paperback,172,1973,1972,,2020/06/05,books,books (#8),read,
        // ,,,1,0
        try (Cursor cursor = bookDao.fetchByKey(DBKey.SID_GOODREADS_BOOK, "8998451")) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToNext());
            final Book book = Book.from(cursor);

            assertEquals("Jack van de Schaduwen", book.getTitle());
            assertEquals("9789027406927", book.getString(DBKey.BOOK_ISBN));
            // "my_rating" was set to 5; "average_rating" of 3.99 is ignored
            assertEquals(5, book.getFloat(DBKey.RATING, realNumberParser), 0.1);
            assertEquals("Paperback", book.getString(DBKey.FORMAT));
            assertEquals("172", book.getString(DBKey.PAGE_COUNT));
            assertEquals("1973", book.getString(DBKey.BOOK_PUBLICATION__DATE));
            assertEquals("1972", book.getString(DBKey.FIRST_PUBLICATION__DATE));
            assertEquals("2020-06-05 00:00:00", book.getString(DBKey.DATE_ADDED__UTC));

            final List<Publisher> allPublishers = book.getPublishers();
            assertEquals(1, allPublishers.size());
            assertEquals("Het Spectrum", allPublishers.get(0).getName());

            final List<Author> allAuthors = book.getAuthors();
            assertEquals(2, allAuthors.size());
            Author author;
            author = allAuthors.get(0);
            assertEquals("Zelazny", author.getFamilyName());
            assertEquals("Roger", author.getGivenNames());
            author = allAuthors.get(1);
            assertEquals("Löbler", author.getFamilyName());
            assertEquals("Ruud", author.getGivenNames());

            // Reminder: the list is sorted by name
            final List<Bookshelf> allBookshelves = book.getBookshelves();
            assertEquals(2, allBookshelves.size());
            Bookshelf bookshelf;
            bookshelf = allBookshelves.get(0);
            assertEquals("books", bookshelf.getName());
            bookshelf = allBookshelves.get(1);
            assertEquals("read", bookshelf.getName());
        }

        // 20518872,"The Three-Body Problem (Remembrance of Earth’s Past, #1)",
        // Liu Cixin,"Cixin, Liu","Ken Liu, Cixin Liu","=""""","=""""",0,4.09,
        // Tor Books,Hardcover,472,2014,2006,,2024/04/24,
        // "currently-reading, books","currently-reading (#3), books (#15)",currently-reading,
        // On my todo list,,my own notes on this book,1,0
        try (Cursor cursor = bookDao.fetchByKey(DBKey.SID_GOODREADS_BOOK, "20518872")) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToNext());
            final Book book = Book.from(cursor);

            assertEquals("The Three-Body Problem", book.getTitle());
            assertEquals("", book.getString(DBKey.BOOK_ISBN));
            // "my_rating" 0f 0 is ignored; "average_rating" of 4.09
            assertEquals(4, book.getFloat(DBKey.RATING, realNumberParser), 0.1);
            assertEquals("Hardcover", book.getString(DBKey.FORMAT));
            assertEquals("472", book.getString(DBKey.PAGE_COUNT));
            assertEquals("2014", book.getString(DBKey.BOOK_PUBLICATION__DATE));
            assertEquals("2006", book.getString(DBKey.FIRST_PUBLICATION__DATE));
            assertEquals("2024-04-24 00:00:00", book.getString(DBKey.DATE_ADDED__UTC));

            assertEquals("", book.getString(DBKey.DESCRIPTION));
            assertEquals("my own notes on this book\n\nOn my todo list",
                         book.getString(DBKey.PERSONAL_NOTES));

            final List<Publisher> allPublishers = book.getPublishers();
            assertEquals(1, allPublishers.size());
            assertEquals("Tor Books", allPublishers.get(0).getName());


            final List<Author> allAuthors = book.getAuthors();
            assertEquals(3, allAuthors.size());
            Author author;

            author = allAuthors.get(0);
            assertEquals("Cixin", author.getFamilyName());
            assertEquals("Liu", author.getGivenNames());
            author = allAuthors.get(1);
            // wrong order, see note in BookCoder
            assertEquals("Liu", author.getFamilyName());
            assertEquals("Ken", author.getGivenNames());
            // duplicate/wrong-order, see note in BookCoder
            author = allAuthors.get(2);
            assertEquals("Liu", author.getFamilyName());
            assertEquals("Cixin", author.getGivenNames());

            // Reminder: the list is sorted by name
            final List<Bookshelf> allBookshelves = book.getBookshelves();
            assertEquals(2, allBookshelves.size());
            Bookshelf bookshelf;
            bookshelf = allBookshelves.get(0);
            assertEquals("books", bookshelf.getName());
            bookshelf = allBookshelves.get(1);
            assertEquals("currently-reading", bookshelf.getName());
        }
    }

}
