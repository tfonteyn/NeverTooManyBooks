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

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.TestUtils;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void books()
            throws DataReaderException, DataWriterException, DaoWriteException, IOException,
                   StorageException, CredentialsException, CertificateException {

        File file;
        Locale locale;
        ImportHelper importHelper;
        ImportResults importResults;
        TypedCursor bookCursor;
        Book book;

        file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.testdata_csv,
                new File(context.getCacheDir(), "testdata.csv"));

        locale = context.getResources().getConfiguration().getLocales().get(0);

        importHelper = new ImportHelper(context, locale, Uri.fromFile(file));
        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);
        importResults = importHelper.read(context, new TestProgressListener(TAG));

        assertEquals(4, importResults.booksProcessed);
        assertEquals(4, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);

        assertTrue(bookDao.bookExistsById(666000001));
        assertTrue(bookDao.bookExistsById(666000002));
        assertTrue(bookDao.bookExistsById(666000003));
        assertTrue(bookDao.bookExistsById(666000004));
        assertEquals(booksPresent + 4, bookDao.count());

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
        assertEquals(1, importResults.booksCreated);
        assertEquals(3, importResults.booksUpdated);
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

        bookCursor = bookDao.fetchById(666000002);
        assertTrue(bookCursor.moveToFirst());
        book = Book.from(bookCursor);
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
        assertEquals(3, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        assertEquals(1, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 4, bookDao.count());

        bookCursor = bookDao.fetchById(666000002);
        assertTrue(bookCursor.moveToFirst());
        book = Book.from(bookCursor);
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
        assertEquals(0, importResults.booksCreated);
        assertEquals(4, importResults.booksUpdated);
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 4, bookDao.count());

        bookCursor = bookDao.fetchById(666000002);
        assertTrue(bookCursor.moveToFirst());
        book = Book.from(bookCursor);
        assertFalse(book.isRead());
    }
}
