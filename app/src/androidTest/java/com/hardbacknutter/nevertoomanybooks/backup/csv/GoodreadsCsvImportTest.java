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

package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.database.Cursor;
import android.net.Uri;
import android.os.LocaleList;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.TestUtils;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierValueDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.BasicMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("OptionalGetWithoutIsPresent")
class GoodreadsCsvImportTest
        extends BaseDBTest {

    private static final String TAG = "GoodreadsCsvImportTest";

    private BookDao bookDao;
    private int booksPresent;
    private IdentifierValueDao bookIdentifierDao;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        bookIdentifierDao = serviceLocator.getBookIdentifierDao();
        bookDao = serviceLocator.getBookDao();
        booksPresent = bookDao.count();

        final long grId = serviceLocator
                .getIdentifierDao()
                .find(Identifier.SID_GOODREADS, Identifier.EntityType.Book)
                .get()
                .getId();

        try (SynchronizedStatement stmt = serviceLocator.getDb().compileStatement(
                "DELETE FROM " + DBDefinitions.TBL_BOOK_IDENTIFIER.getName()
                + " WHERE " + DBKey.FK_IDENTIFIER + "=" + grId)) {
            stmt.executeUpdateDelete(null);
        }
    }

    @SuppressWarnings("LocalCanBeFinal")
    @Test
    void goodreads()
            throws DataReaderException, IOException,
                   StorageException, CredentialsException, CertificateException {

        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        final RealNumberParser ratingNumberParser = new RealNumberParser(allLocales);

        File file;
        ImportHelper importHelper;
        Optional<ArchiveMetaData> oMetaData;
        ArchiveMetaData metaData;
        ImportResults importResults;

        file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.goodreads_library_export_csv,
                new File(context.getCacheDir(), "goodreads_library_export.csv"));

        importHelper = new ImportHelper(context, Uri.fromFile(file));

        oMetaData = importHelper.readMetaData(context);
        assertTrue(oMetaData.isPresent());
        metaData = oMetaData.get();
        assertNotNull(metaData);
        //noinspection deprecation
        Assertions.assertEquals(CsvFormat.Goodreads,
                                metaData.getData().getParcelable(CsvFormat.BKEY));
        assertTrue(metaData.getData().containsKey(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED));
        // "goodreads_library_export.csv" does NOT contain such a field
        assertFalse(metaData.getData().getBoolean(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED));

        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);
        importResults = importHelper.read(context, new TestProgressListener(TAG));

        assertEquals(22, importResults.booksProcessed);
        assertEquals(22, importResults.getBooksCreated());
        assertEquals(0, importResults.getBooksUpdated());
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 22, bookDao.count());

        // 8998451,Jack van de Schaduwen,
        // Roger Zelazny,"Zelazny, Roger",Ruud Löbler,"=""9027406928""","=""9789027406927""",
        // 5,3.99,Het Spectrum,Paperback,172,1973,1972,,2020/06/05,books,books (#8),read,
        // ,,,1,0

        Optional<Long> oBookId = bookIdentifierDao
                .findIdentifierOwnerId(Identifier.SID_GOODREADS, "8998451");
        assertTrue(oBookId.isPresent());
        long bookId = oBookId.get();

        try (Cursor cursor = bookDao.fetchById(bookId)) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToNext());
            final Book book = Book.from(cursor);

            assertEquals("Jack van de Schaduwen", book.getString(DBKey.TITLE, null));
            assertEquals("9789027406927", book.getString(DBKey.ISBN, null));
            // "my_rating" was set to 5; "average_rating" of 3.99 is ignored
            assertEquals(5.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
            assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
            assertEquals("172", book.getString(DBKey.PAGES, null));
            assertEquals("1973", book.getString(DBKey.PUBLICATION_DATE, null));
            assertEquals("1972", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));
            assertEquals("2020-06-05 00:00:00", book.getString(DBKey.DATE_ADDED__UTC, null));

            assertEquals("8998451", book.requireIdentifierValue(Identifier.SID_GOODREADS));

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
        oBookId = bookIdentifierDao.findIdentifierOwnerId(Identifier.SID_GOODREADS, "20518872");
        assertTrue(oBookId.isPresent());
        bookId = oBookId.get();

        try (Cursor cursor = bookDao.fetchById(bookId)) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToNext());
            final Book book = Book.from(cursor);

            assertEquals("The Three-Body Problem", book.getString(DBKey.TITLE, null));
            assertEquals("", book.getString(DBKey.ISBN, null));
            // "my_rating" 0f 0 is ignored; "average_rating" of 4.09
            assertEquals(4.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
            assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
            assertEquals("472", book.getString(DBKey.PAGES, null));
            assertEquals("2014", book.getString(DBKey.PUBLICATION_DATE, null));
            assertEquals("2006", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));
            assertEquals("2024-04-24 00:00:00", book.getString(DBKey.DATE_ADDED__UTC, null));

            assertEquals("", book.getString(DBKey.DESCRIPTION, null));
            assertEquals("my own notes on this book\n\nOn my todo list",
                         book.getString(DBKey.PERSONAL_NOTES, null));

            assertEquals("20518872", book.requireIdentifierValue(Identifier.SID_GOODREADS));

            final List<Publisher> allPublishers = book.getPublishers();
            assertEquals(1, allPublishers.size());
            assertEquals("Tor Books", allPublishers.get(0).getName());

            final List<Series> allSeries = book.getSeries();
            assertEquals(1, allSeries.size());
            final Series series = allSeries.get(0);
            assertEquals("Remembrance of Earth’s Past", series.getTitle());
            assertEquals("1", series.getNumber());

            final List<Author> allAuthors = book.getAuthors();
            assertEquals(3, allAuthors.size());
            Author author;

            author = allAuthors.get(0);
            assertEquals("Cixin", author.getFamilyName());
            assertEquals("Liu", author.getGivenNames());
            author = allAuthors.get(1);
            // wrong order, see note in DefaultBookCoder
            assertEquals("Liu", author.getFamilyName());
            assertEquals("Ken", author.getGivenNames());
            // duplicate/wrong-order, see note in DefaultBookCoder
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
