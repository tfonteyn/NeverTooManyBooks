/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@MediumTest
public class CsvArchiveWriterTest
        extends BaseDBTest {

    private static final String TAG = "CsvArchiveWriterTest";

    private long mBookInDb;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();
        mBookInDb = serviceLocator.getBookDao().count();
        if (mBookInDb < 10) {
            throw new IllegalStateException("need at least 10 books for testing");
        }
    }

    @Test
    public void write()
            throws DataReaderException, DaoWriteException, DataWriterException,
                   IOException, StorageException, CredentialsException, CertificateException {

        final Context context = serviceLocator.getLocalizedAppContext();
        final File file = new File(context.getFilesDir(), TAG + ".csv");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(ArchiveEncoding.Csv,
                                                           EnumSet.of(RecordType.Books));
        exportHelper.setUri(Uri.fromFile(file));

        exportResults = exportHelper.write(context, new TestProgressListener(TAG + ":export"));

        assertEquals(mBookInDb, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
        assertEquals(0, exportResults.preferences);
        assertEquals(0, exportResults.styles);
        assertFalse(exportResults.database);


        // count the lines in the export file
        final long exportCount;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            // -1 for the header line.
            exportCount = reader.lines().count() - 1;
        } catch (@NonNull final UncheckedIOException e) {
            // caused by lines()
            //noinspection ConstantConditions
            throw e.getCause();
        }
        assertEquals(mBookInDb, exportCount);


        // Now modify/delete some books. We have at least 10 books to play with
        final List<Long> ids = exportResults.getBooksExported();

        final long deletedBookId = ids.get(3);
        final long modifiedBookId = ids.get(5);

        final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
        bookDao.delete(deletedBookId);

        final Book book = Book.from(modifiedBookId);
        book.putString(DBKey.PERSONAL_NOTES,
                       "MODIFIED" + book.getString(DBKey.PERSONAL_NOTES));
        bookDao.update(context, book, 0);

        final ImportHelper importHelper = new ImportHelper(context, Uri.fromFile(file));
        // The default, fail if the default was changed without changing this test!
        assertEquals(DataReader.Updates.OnlyNewer, importHelper.getUpdateOption());

        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Skip);

        assertFalse(importHelper.readMetaData(context).isPresent());

        ImportResults importResults = importHelper
                .read(context, new TestProgressListener(TAG + ":import"));
        assertNotNull(importResults);
        assertEquals(exportCount, importResults.booksProcessed);

        // we re-created the deleted book
        assertEquals(1, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        // we skipped the updated book
        assertEquals(exportCount - 1, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);


        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);

        assertFalse(importHelper.readMetaData(context).isPresent());

        importResults = importHelper.read(context, new TestProgressListener(TAG + ":header"));
        assertNotNull(importResults);
        assertEquals(exportCount, importResults.booksProcessed);


        assertEquals(0, importResults.booksCreated);
        // we did an overwrite of ALL books
        assertEquals(mBookInDb, importResults.booksUpdated);
        // so we skipped none
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
    }
}
