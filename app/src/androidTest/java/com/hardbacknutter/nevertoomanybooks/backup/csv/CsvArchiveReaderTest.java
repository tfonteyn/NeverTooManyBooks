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

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.TestUtils;
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
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.BasicMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvArchiveReaderTest
        extends BaseDBTest {

    private static final String TAG = "CsvArchiveReaderTest";

    private BookDao bookDao;
    private int booksPresent;

    @BeforeEach
    void setup()
            throws StorageException {
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
    void books()
            throws DataReaderException, IOException,
                   StorageException, CredentialsException, CertificateException {

        File file;
        ImportHelper importHelper;
        Optional<ArchiveMetaData> oMetaData;
        ArchiveMetaData metaData;
        ImportResults importResults;

        Book book;

        file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                new File(context.getCacheDir(), "testdata.csv"));

        importHelper = new ImportHelper(context, Uri.fromFile(file));

        oMetaData = importHelper.readMetaData(context);
        assertTrue(oMetaData.isPresent());
        metaData = oMetaData.get();
        assertNotNull(metaData);
        assertTrue(metaData.getData().containsKey(CsvFormat.BKEY));

        assertEquals(CsvFormat.BC,
                     metaData.getData().getParcelable(CsvFormat.BKEY));

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

        assertTrue(bookDao.bookExists(666000001));
        assertTrue(bookDao.bookExists(666000002));
        assertTrue(bookDao.bookExists(666000003));
        assertTrue(bookDao.bookExists(666000004));
        assertEquals(booksPresent + 4, bookDao.count());

        checkBook1();
        checkBook2();
        checkBook3Rating();

        // Delete 1 book; then re-import using "Overwrite"
        bookDao.delete(666000002);
        assertFalse(bookDao.bookExists(666000002));

        file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                new File(context.getCacheDir(), "testdata.csv"));

        importHelper = new ImportHelper(context, Uri.fromFile(file));
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
        assertFalse(bookDao.bookExists(666000001));
        assertFalse(bookDao.bookExists(666000003));
        assertFalse(bookDao.bookExists(666000004));

        try (Cursor cursor = bookDao.fetchById(666000002)) {
            assertTrue(cursor.moveToFirst());
            book = Book.from(cursor);
            bookDao.setRead(book, true);

            file = TestUtils.createFile(
                    com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                    new File(context.getCacheDir(), "testdata.csv"));

            importHelper = new ImportHelper(context, Uri.fromFile(file));
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

            importHelper = new ImportHelper(context, Uri.fromFile(file));
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
        final RealNumberParser ratingNumberParser = new RealNumberParser(List.of(Locale.FRANCE));

        final Book book = Book.from(666000001);
        assertEquals(666000001, book.getId());
        assertEquals("Myths and Folk Tales of Ireland", book.getString(DBKey.TITLE, null));
        assertEquals("0486224309", book.getString(DBKey.ISBN, null));
        // "1975-06-01" => day will be dropped
        assertEquals("1975-06", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(0.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertFalse(book.isRead());
        assertEquals("272", book.getString(DBKey.PAGES, null));
        assertEquals("", book.getString(DBKey.PERSONAL_NOTES, null));

        assertEquals("12.95", book.getString(DBKey.PRICE_LISTED, null));

        assertEquals(Book.ContentType.Book, book.getContentType());
        assertEquals("", book.getString(DBKey.LOCATION, null));
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));
        assertEquals("", book.getString(DBKey.READ_END__DATE, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertFalse(book.getBoolean(DBKey.SIGNED__BOOL));
        assertEquals("", book.getString(DBKey.LOANEE_NAME, null));
        assertEquals("Fearsome giants, magic spells, ...", book.getString(DBKey.DESCRIPTION, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2017-12-21 16:38:57", book.getString(DBKey.DATE_ADDED__UTC, null));
        assertEquals("1294006", book.requireIdentifierValue(Identifier.SID_GOODREADS));
        assertEquals("2017-12-21 16:38:57", book.getString(DBKey.DATE_LAST_UPDATED__UTC, null));
        assertEquals("e9787a594f11549db20f163db56a3ec9", book.getString(DBKey.BOOK_UUID, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("History"));
        assertTrue(tags.contains("Europe"));
        assertTrue(tags.contains("Ireland"));

        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        // A new shelf was created. Don't test the id== as we might have others
        assertTrue(bookshelves.get(0).getId() > 0);
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
        final RealNumberParser ratingNumberParser = new RealNumberParser(List.of(Locale.FRANCE));

        final Book book = Book.from(666000002);
        assertEquals(666000002, book.getId());
        assertEquals("Dracula", book.getString(DBKey.TITLE, null));
        assertEquals("9780141439846", book.getString(DBKey.ISBN, null));
        assertEquals("2003-04-29", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(0.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertFalse(book.isRead());
        assertEquals("454", book.getString(DBKey.PAGES, null));
        assertEquals("", book.getString(DBKey.PERSONAL_NOTES, null));

        assertEquals("11.0", book.getString(DBKey.PRICE_LISTED, null));

        assertEquals(Book.ContentType.Book, book.getContentType());
        assertEquals("", book.getString(DBKey.LOCATION, null));
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));
        assertEquals("", book.getString(DBKey.READ_END__DATE, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertFalse(book.getBoolean(DBKey.SIGNED__BOOL));
        assertEquals("", book.getString(DBKey.LOANEE_NAME, null));
        assertEquals("Jonathan Harker is travelling to Castle Dracula ...",
                     book.getString(DBKey.DESCRIPTION, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2017-12-21 16:39:24", book.getString(DBKey.DATE_ADDED__UTC, null));
        assertTrue(book.getIdentifierValue(Identifier.SID_GOODREADS).isEmpty());
        assertEquals("2017-12-21 16:39:24", book.getString(DBKey.DATE_LAST_UPDATED__UTC, null));
        assertEquals("b483250f6016cbe775ce16bfbc6d64da", book.getString(DBKey.BOOK_UUID, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Fiction"));
        assertTrue(tags.contains("Literary"));

        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        // A new shelf was created
        assertTrue(bookshelves.get(0).getId() > 0);
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

    private void checkBook3Rating() {
        final RealNumberParser ratingNumberParser = new RealNumberParser(List.of(Locale.FRANCE));

        final Book book = Book.from(666000003);
        // "3,55"
        assertEquals(3.5f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
    }
}
