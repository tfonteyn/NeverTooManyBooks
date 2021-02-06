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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class CsvArchiveWriterTest {

    private static final String TAG = "CsvArchiveWriterTest";

    private long mBookInDb;

    @Before
    public void count() {
        try (DAO db = new DAO(TAG)) {
            mBookInDb = db.countBooks();
        }
        if (mBookInDb < 10) {
            throw new IllegalStateException("need at least 10 books for testing");
        }
    }

    @Test
    public void write()
            throws ImportException, DAO.DaoWriteException,
                   InvalidArchiveException, GeneralParsingException,
                   IOException, CertificateException {

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File file = AppDir.Log.getFile(context, TAG + ".csv");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(RecordType.Books);
        exportHelper.setEncoding(ArchiveEncoding.Csv);
        exportHelper.setUri(Uri.fromFile(file));

        try (ArchiveWriter writer = exportHelper.createArchiveWriter(context)) {
            exportResults = writer.write(context, new TestProgressListener(TAG + ":export"));
        }
        // assume success; a failure would have thrown an exception
        exportHelper.onSuccess(context);

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
            //noinspection ConstantConditions
            throw e.getCause();
        }
        assertEquals(mBookInDb, exportCount);


        // Now modify/delete some books. We have at least 10 books to play with
        final List<Long> ids = exportResults.getBooksExported();

        final long deletedBookId = ids.get(3);
        final long modifiedBookId = ids.get(5);

        try (DAO db = new DAO(TAG)) {
            db.deleteBook(context, deletedBookId);

            final Book book = Book.from(modifiedBookId, db);
            book.putString(DBDefinitions.KEY_PRIVATE_NOTES,
                           "MODIFIED" + book.getString(DBDefinitions.KEY_PRIVATE_NOTES));
            db.update(context, book, 0);
        }

        final ImportHelper importHelper = ImportHelper.withFile(context, Uri.fromFile(file));
        ImportResults importResults;


        importHelper.setImportEntry(RecordType.Books, true);
        try (ArchiveReader reader = importHelper.createArchiveReader(context)) {

            final ArchiveMetaData archiveMetaData = reader.readMetaData(context);
            assertNull(archiveMetaData);

            importResults = reader.read(context, new TestProgressListener(TAG + ":import"));
        }
        assertEquals(exportCount, importResults.booksProcessed);

        // we re-created the deleted book
        assertEquals(1, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        // we skipped the updated book
        assertEquals(exportCount - 1, importResults.booksSkipped);


        importHelper.setImportEntry(RecordType.Books, true);
        importHelper.setAllBooks();
        try (ArchiveReader reader = importHelper.createArchiveReader(context)) {

            final ArchiveMetaData archiveMetaData = reader.readMetaData(context);
            assertNull(archiveMetaData);

            importResults = reader.read(context, new TestProgressListener(TAG + ":header"));
        }
        assertEquals(exportCount, importResults.booksProcessed);


        assertEquals(0, importResults.booksCreated);
        // we did an overwrite of ALL books
        assertEquals(mBookInDb, importResults.booksUpdated);
        // so we skipped none
        assertEquals(0, importResults.booksSkipped);
    }
}
