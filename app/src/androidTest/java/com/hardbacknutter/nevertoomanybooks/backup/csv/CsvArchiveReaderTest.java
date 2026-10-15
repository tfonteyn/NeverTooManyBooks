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

import androidx.annotation.NonNull;

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

    private static final int BOOK_ID_01 = 666000001;
    private static final int BOOK_ID_02 = 666000002;
    private static final int BOOK_ID_03 = 666000003;
    private static final int BOOK_ID_04 = 666000004;

    private BookDao bookDao;
    private int booksPresent;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        bookDao = ServiceLocator.getInstance().getBookDao();

        bookDao.delete(BOOK_ID_01);
        bookDao.delete(BOOK_ID_02);
        bookDao.delete(BOOK_ID_03);
        bookDao.delete(BOOK_ID_04);

        booksPresent = bookDao.count();
    }

    @NonNull
    private ImportHelper createImportHelper()
            throws DataReaderException, IOException {
        final File file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                new File(context.getCacheDir(), "testdata.csv"));
        return new ImportHelper(context, Uri.fromFile(file));
    }

    @NonNull
    private ImportResults runImport(@NonNull final DataReader.Updates overwrite)
            throws DataReaderException, IOException, CertificateException,
                   CredentialsException, StorageException {

        final ImportHelper importHelper = createImportHelper();
        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(overwrite);
        return importHelper.read(context, new TestProgressListener(TAG));
    }

    @Test
    void books()
            throws DataReaderException, IOException,
                   StorageException, CredentialsException, CertificateException {

        books_01_initialInsert();
        books_02_delete1BookThenOverwrite();
        books_03_delete3booksAndModify1ThenOnlyNewer();
        books_04_modify1bookThenOverwrite();
    }

    private void books_01_initialInsert()
            throws DataReaderException, IOException, CredentialsException,
                   CertificateException, StorageException {

        final ImportHelper importHelper = createImportHelper();
        final Optional<ArchiveMetaData> oMetaData = importHelper.readMetaData(context);
        assertTrue(oMetaData.isPresent());
        final ArchiveMetaData metaData = oMetaData.get();
        assertNotNull(metaData);
        assertTrue(metaData.getData().containsKey(CsvFormat.BKEY));
        //noinspection deprecation
        assertEquals(CsvFormat.BC, metaData.getData().getParcelable(CsvFormat.BKEY));
        // "testdata.csv" DOES contain such a field
        assertTrue(metaData.getData().getBoolean(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED));


        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);
        final ImportResults importResults = importHelper
                .read(context, new TestProgressListener(TAG));

        assertEquals(4, importResults.booksProcessed);
        assertEquals(4, importResults.getBooksCreated());
        assertEquals(0, importResults.getBooksUpdated());
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);

        assertTrue(bookDao.bookExists(BOOK_ID_01));
        assertTrue(bookDao.bookExists(BOOK_ID_02));
        assertTrue(bookDao.bookExists(BOOK_ID_03));
        assertTrue(bookDao.bookExists(BOOK_ID_04));
        assertEquals(booksPresent + 4, bookDao.count());

        checkBook1();
        checkBook2();
        checkBook3Rating();
    }

    private void books_02_delete1BookThenOverwrite()
            throws DataReaderException, IOException, CertificateException,
                   CredentialsException, StorageException {

        // delete 1 book
        bookDao.delete(BOOK_ID_02);
        assertFalse(bookDao.bookExists(BOOK_ID_02));

        final ImportResults importResults = runImport(DataReader.Updates.Overwrite);

        assertEquals(4, importResults.booksProcessed);
        assertEquals(1, importResults.getBooksCreated());
        assertEquals(0, importResults.getBooksUpdated());
        assertEquals(3, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 4, bookDao.count());
    }

    private void books_03_delete3booksAndModify1ThenOnlyNewer()
            throws DataReaderException, IOException, CertificateException,
                   CredentialsException, StorageException {

        // Delete 3 books
        bookDao.delete(BOOK_ID_01);
        bookDao.delete(BOOK_ID_03);
        bookDao.delete(BOOK_ID_04);
        assertFalse(bookDao.bookExists(BOOK_ID_01));
        assertFalse(bookDao.bookExists(BOOK_ID_03));
        assertFalse(bookDao.bookExists(BOOK_ID_04));
        // and modify 1: the imported one will be ignored as the local modification date is newer
        try (Cursor cursor = bookDao.fetchById(BOOK_ID_02)) {
            assertTrue(cursor.moveToFirst());
            final Book book = Book.from(cursor);
            bookDao.setRead(book, true);
        }

        final ImportResults importResults = runImport(DataReader.Updates.OnlyNewer);

        assertEquals(4, importResults.booksProcessed);
        assertEquals(3, importResults.getBooksCreated());
        assertEquals(0, importResults.getBooksUpdated());
        assertEquals(1, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 4, bookDao.count());
    }

    private void books_04_modify1bookThenOverwrite()
            throws DataReaderException, IOException, CertificateException,
                   CredentialsException, StorageException {

        // modify 1 book: the imported one will always overwrite this
        try (Cursor cursor = bookDao.fetchById(BOOK_ID_02)) {
            assertTrue(cursor.moveToFirst());
            final Book book = Book.from(cursor);
            assertTrue(book.isRead());
        }

        final ImportResults importResults = runImport(DataReader.Updates.Overwrite);

        assertEquals(4, importResults.booksProcessed);
        assertEquals(0, importResults.getBooksCreated());
        assertEquals(1, importResults.getBooksUpdated());
        assertEquals(3, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 4, bookDao.count());

        try (Cursor cursor = bookDao.fetchById(BOOK_ID_02)) {
            assertTrue(cursor.moveToFirst());
            final Book book = Book.from(cursor);
            // Check we've been overwritten
            assertFalse(book.isRead());
        }
    }

    private void checkBook1() {
        final RealNumberParser ratingNumberParser = new RealNumberParser(List.of(Locale.FRANCE));

        final Book book = Book.from(BOOK_ID_01);
        assertEquals(BOOK_ID_01, book.getId());
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

        final Book book = Book.from(BOOK_ID_02);
        assertEquals(BOOK_ID_02, book.getId());
        assertEquals("Dracula", book.getString(DBKey.TITLE, null));
        assertEquals("9780141439846", book.getString(DBKey.ISBN, null));
        assertEquals("2003-04-29", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(0.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertFalse(book.isRead());
        assertEquals("454", book.getString(DBKey.PAGES, null));
        assertEquals("", book.getString(DBKey.PERSONAL_NOTES, null));

        assertEquals("11", book.getString(DBKey.PRICE_LISTED, null));

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

        final Book book = Book.from(BOOK_ID_03);
        // "3,55"
        assertEquals(3.5f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
    }
}
