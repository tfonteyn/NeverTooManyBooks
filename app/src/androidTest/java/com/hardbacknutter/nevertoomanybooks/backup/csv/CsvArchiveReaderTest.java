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
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
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
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class CsvArchiveReaderTest
        extends BaseDBTest {

    private static final String TAG = "CsvArchiveReaderTest";

    private BookDao bookDao;
    private int booksPresent;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        bookDao = ServiceLocator.getInstance().getBookDao();

        bookDao.delete(666000001);
        bookDao.delete(666000002);
        bookDao.delete(666000003);
        bookDao.delete(666000004);

        booksPresent = bookDao.count();
    }

    @SuppressWarnings("LocalCanBeFinal")
    @Test
    public void books()
            throws DataReaderException, DataWriterException, DaoWriteException, IOException,
                   StorageException, CredentialsException, CertificateException {

        File file;
        Locale locale;
        ImportHelper importHelper;
        Optional<ArchiveMetaData> oMetaData;
        ArchiveMetaData metaData;
        ImportResults importResults;

        Book book;

        file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                new File(context.getCacheDir(), "testdata.csv"));

        locale = context.getResources().getConfiguration().getLocales().get(0);

        importHelper = new ImportHelper(context, locale, Uri.fromFile(file));

        oMetaData = importHelper.readMetaData(context);
        assertTrue(oMetaData.isPresent());
        metaData = oMetaData.get();
        assertNotNull(metaData);
        assertTrue(metaData.getData().containsKey(CsvRecordReader.Origin.BKEY));

        assertEquals(CsvRecordReader.Origin.BC,
                     metaData.getData().getParcelable(CsvRecordReader.Origin.BKEY));

        // "testdata.csv" does contain such a field
        assertTrue(metaData.getData().getBoolean(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED));

        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);
        importResults = importHelper.read(context, new TestProgressListener(TAG));

        assertEquals(4, importResults.booksProcessed);
        assertEquals(4, importResults.getBooksCreated());
        assertEquals(0, importResults.getBooksUpdated());
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);

        assertTrue(bookDao.bookExistsById(666000001));
        assertTrue(bookDao.bookExistsById(666000002));
        assertTrue(bookDao.bookExistsById(666000003));
        assertTrue(bookDao.bookExistsById(666000004));
        assertEquals(booksPresent + 4, bookDao.count());

        checkBook1();
        checkBook2();


        // Delete 1 book; then re-import using "Overwrite"
        bookDao.delete(666000002);
        assertFalse(bookDao.bookExistsById(666000002));

        file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                new File(context.getCacheDir(), "testdata.csv"));

        locale = context.getResources().getConfiguration().getLocales().get(0);

        importHelper = new ImportHelper(context, locale, Uri.fromFile(file));
        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);
        importResults = importHelper.read(context, new TestProgressListener(TAG));

        assertEquals(4, importResults.booksProcessed);
        assertEquals(1, importResults.getBooksCreated());
        assertEquals(3, importResults.getBooksUpdated());
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 4, bookDao.count());

        // Delete 3 books, and modify 1; then re-import using "OnlyNewer"
        bookDao.delete(666000001);
        bookDao.delete(666000003);
        bookDao.delete(666000004);
        assertFalse(bookDao.bookExistsById(666000001));
        assertFalse(bookDao.bookExistsById(666000003));
        assertFalse(bookDao.bookExistsById(666000004));

        try (Cursor cursor = bookDao.fetchById(666000002)) {
            assertTrue(cursor.moveToFirst());
            book = Book.from(cursor);
            bookDao.setRead(book, true);

            file = TestUtils.createFile(
                    com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                    new File(context.getCacheDir(), "testdata.csv"));

            locale = context.getResources().getConfiguration().getLocales().get(0);

            importHelper = new ImportHelper(context, locale, Uri.fromFile(file));
            importHelper.addRecordType(RecordType.Books);
            importHelper.setUpdateOption(DataReader.Updates.OnlyNewer);
            importResults = importHelper.read(context, new TestProgressListener(TAG));

            assertEquals(4, importResults.booksProcessed);
            assertEquals(3, importResults.getBooksCreated());
            assertEquals(0, importResults.getBooksUpdated());
            assertEquals(1, importResults.booksSkipped);
            assertEquals(0, importResults.booksFailed);
            assertEquals(booksPresent + 4, bookDao.count());
        }

        try (Cursor cursor = bookDao.fetchById(666000002)) {
            assertTrue(cursor.moveToFirst());
            book = Book.from(cursor);
            assertTrue(book.isRead());

            // same import, but using DataReader.Updates.Overwrite
            file = TestUtils.createFile(
                    com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                    new File(context.getCacheDir(), "testdata.csv"));

            locale = context.getResources().getConfiguration().getLocales().get(0);

            importHelper = new ImportHelper(context, locale, Uri.fromFile(file));
            importHelper.addRecordType(RecordType.Books);
            importHelper.setUpdateOption(DataReader.Updates.Overwrite);
            importResults = importHelper.read(context, new TestProgressListener(TAG));

            assertEquals(4, importResults.booksProcessed);
            assertEquals(0, importResults.getBooksCreated());
            assertEquals(4, importResults.getBooksUpdated());
            assertEquals(0, importResults.booksSkipped);
            assertEquals(0, importResults.booksFailed);
            assertEquals(booksPresent + 4, bookDao.count());
        }

        try (Cursor cursor = bookDao.fetchById(666000002)) {
            assertTrue(cursor.moveToFirst());
            book = Book.from(cursor);
            assertFalse(book.isRead());
        }
    }

    private void checkBook1() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.FRANCE));

        final Book book = Book.from(666000001);
        assertEquals(666000001, book.getId());
        assertEquals("Myths and Folk Tales of Ireland", book.getTitle());
        assertEquals("0486224309", book.getString(DBKey.BOOK_ISBN, null));
        // "1975-06-01" => day will be dropped
        assertEquals("1975-06", book.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals(0, book.getFloat(DBKey.RATING, realNumberParser), 0);
        assertFalse(book.isRead());
        assertEquals("272", book.getString(DBKey.PAGE_COUNT));
        assertEquals("", book.getString(DBKey.PERSONAL_NOTES, null));

        // URGENT: price was "12,95" ... see BookDaoHelper#filterValues
        assertEquals("0.0", book.getString(DBKey.PRICE_LISTED));

        assertEquals(Book.ContentType.Book, book.getContentType());
        assertEquals("", book.getString(DBKey.LOCATION, null));
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));
        assertEquals("", book.getString(DBKey.READ_END__DATE, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT));
        assertFalse(book.getBoolean(DBKey.SIGNED__BOOL));
        assertEquals("", book.getString(DBKey.LOANEE_NAME, null));
        assertEquals("Fearsome giants, magic spells, ...", book.getString(DBKey.DESCRIPTION));
        assertEquals("History / Europe / Ireland", book.getString(DBKey.GENRE));
        assertEquals("English", book.getString(DBKey.LANGUAGE));
        assertEquals("2017-12-21 16:38:57", book.getString(DBKey.DATE_ADDED__UTC));
        assertEquals(1294006, book.getLong(Identifier.SID_GOODREADS_BOOK));
        assertEquals("2017-12-21 16:38:57", book.getString(DBKey.DATE_LAST_UPDATED__UTC));
        assertEquals("e9787a594f11549db20f163db56a3ec9", book.getString(DBKey.BOOK_UUID));

        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        // A new shelf was created
        assertEquals(2, bookshelves.get(0).getId());
        assertEquals("Default", bookshelves.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());
        assertEquals("Curtin", authors.get(0).getFamilyName());
        assertEquals("Jeremiah", authors.get(0).getGivenNames());

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals("Dover Publications", publishers.get(0).getName());

        assertEquals(0, book.getSeries().size());
        assertEquals(0, book.getToc().size());
    }

    private void checkBook2() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.FRANCE));

        final Book book = Book.from(666000002);
        assertEquals(666000002, book.getId());
        assertEquals("Dracula", book.getTitle());
        assertEquals("9780141439846", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2003-04-29", book.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals(0, book.getFloat(DBKey.RATING, realNumberParser), 0);
        assertFalse(book.isRead());
        assertEquals("454", book.getString(DBKey.PAGE_COUNT));
        assertEquals("", book.getString(DBKey.PERSONAL_NOTES, null));

        // URGENT: price was "11,00" ... see BookDaoHelper#filterValues
        assertEquals("0.0", book.getString(DBKey.PRICE_LISTED));

        assertEquals(Book.ContentType.Book, book.getContentType());
        assertEquals("", book.getString(DBKey.LOCATION, null));
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));
        assertEquals("", book.getString(DBKey.READ_END__DATE, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT));
        assertFalse(book.getBoolean(DBKey.SIGNED__BOOL));
        assertEquals("", book.getString(DBKey.LOANEE_NAME, null));
        assertEquals("Jonathan Harker is travelling to Castle Dracula ...",
                     book.getString(DBKey.DESCRIPTION));
        assertEquals("Fiction / Literary", book.getString(DBKey.GENRE));
        assertEquals("English", book.getString(DBKey.LANGUAGE));
        assertEquals("2017-12-21 16:39:24", book.getString(DBKey.DATE_ADDED__UTC));
        assertNull(book.getString(Identifier.SID_GOODREADS_BOOK, null));
        assertEquals("2017-12-21 16:39:24", book.getString(DBKey.DATE_LAST_UPDATED__UTC));
        assertEquals("b483250f6016cbe775ce16bfbc6d64da", book.getString(DBKey.BOOK_UUID));

        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        // A new shelf was created
        assertEquals(2, bookshelves.get(0).getId());
        assertEquals("Default", bookshelves.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertEquals(3, authors.size());
        assertEquals("Stoker", authors.get(0).getFamilyName());
        assertEquals("Bram", authors.get(0).getGivenNames());
        assertEquals("Frayling", authors.get(1).getFamilyName());
        assertEquals("Christopher", authors.get(1).getGivenNames());
        assertEquals("Hindle", authors.get(2).getFamilyName());
        assertEquals("Maurice", authors.get(2).getGivenNames());

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals("Penguin Books", publishers.get(0).getName());

        final List<Series> series = book.getSeries();
        assertEquals(1, series.size());
        assertEquals("Penguin Classics", series.get(0).getTitle());

        assertEquals(0, book.getToc().size());
    }
}
