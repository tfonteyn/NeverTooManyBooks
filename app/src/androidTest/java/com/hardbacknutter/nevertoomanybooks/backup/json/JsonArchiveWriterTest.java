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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;
import android.net.Uri;

import androidx.test.filters.MediumTest;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ExportException;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@MediumTest
public class JsonArchiveWriterTest
        extends BaseDBTest {

    private static final String TAG = "JsonArchiveWriterTest";

    private long mBookInDb;
    private int mNrOfStyles;

    @Before
    public void setup()
            throws DaoWriteException, CoverStorageException {
        super.setup();
        final Context context = ServiceLocator.getLocalizedAppContext();
        mBookInDb = ServiceLocator.getInstance().getBookDao().count();
        if (mBookInDb < 10) {
            throw new IllegalStateException("need at least 10 books for testing");
        }
        mNrOfStyles = ServiceLocator.getInstance().getStyles().getStyles(context, true).size();
    }

    // Disabled. The JsonArchiveWriter is currently hardcoded NOT to write styles.
    // @Test
    public void styles()
            throws ImportException, ExportException,
                   InvalidArchiveException,
                   IOException, CertificateException, StorageException {

        final Context context = ServiceLocator.getLocalizedAppContext();
        final File file = new File(context.getFilesDir(), TAG + "-styles.json");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(RecordType.MetaData, RecordType.Styles);

        exportHelper.setEncoding(ArchiveEncoding.Json);
        exportHelper.setUri(Uri.fromFile(file));

        try (ArchiveWriter writer = exportHelper.createArchiveWriter(context)) {
            exportResults = writer.write(context, new TestProgressListener(TAG + ":export"));
        }
        // assume success; a failure would have thrown an exception
        exportHelper.onSuccess(context);

        assertEquals(0, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
        assertEquals(0, exportResults.preferences);
        assertEquals(mNrOfStyles, exportResults.styles);
        assertFalse(exportResults.database);

        final ImportHelper importHelper = ImportHelper.withFile(context, Uri.fromFile(file));
        final ImportResults importResults;

        importHelper.setImportEntry(RecordType.Styles, true);
        try (ArchiveReader reader = importHelper.createArchiveReader(context)) {

            final ArchiveMetaData archiveMetaData = reader.readMetaData(context);
            assertNull(archiveMetaData);

            importResults = reader.read(context, new TestProgressListener(TAG + ":import"));
        }
        assertEquals(exportResults.styles, importResults.styles);
    }

    @Test
    public void books()
            throws ImportException, DaoWriteException,
                   InvalidArchiveException,
                   IOException, CertificateException, ExportException,
                   StorageException {

        final Context context = ServiceLocator.getLocalizedAppContext();
        final File file = new File(context.getFilesDir(), TAG + "-books.json");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(
                RecordType.MetaData,
                RecordType.Books
//                ,
//                // write out styles/prefs just to have them in the output file.
//                // No further tests with them in this method.
//                RecordType.Preferences,
//                RecordType.Styles
        );

        exportHelper.setEncoding(ArchiveEncoding.Json);
        exportHelper.setUri(Uri.fromFile(file));

        try (ArchiveWriter writer = exportHelper.createArchiveWriter(context)) {
            exportResults = writer.write(context, new TestProgressListener(TAG + ":export"));
        }
        // assume success; a failure would have thrown an exception
        exportHelper.onSuccess(context);

        assertEquals(mBookInDb, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
//        assertEquals(1, exportResults.preferences);
//        assertEquals(mNrOfStyles, exportResults.styles);
        assertFalse(exportResults.database);

        // Now modify/delete some books. We have at least 10 books to play with
        final List<Long> ids = exportResults.getBooksExported();

        final long deletedBookId = ids.get(3);
        final long modifiedBookId = ids.get(5);

        final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
        bookDao.delete(deletedBookId);

        final Book book = Book.from(modifiedBookId, bookDao);
        book.putString(DBKey.KEY_PRIVATE_NOTES,
                       "MODIFIED" + book.getString(DBKey.KEY_PRIVATE_NOTES));
        bookDao.update(context, book, 0);

        final ImportHelper importHelper = ImportHelper.withFile(context, Uri.fromFile(file));
        importHelper.setImportEntry(RecordType.Books, true);
        importHelper.setNewBooksOnly();

        ImportResults importResults;
        try (ArchiveReader reader = importHelper.createArchiveReader(context)) {

            final ArchiveMetaData archiveMetaData = reader.readMetaData(context);
            assertNull(archiveMetaData);

            importResults = reader.read(context, new TestProgressListener(TAG + ":import"));
        }
        assertEquals(exportResults.getBookCount(), importResults.booksProcessed);

        // we re-created the deleted book
        assertEquals(1, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        // we skipped the updated book
        assertEquals(exportResults.getBookCount() - 1, importResults.booksSkipped);


        importHelper.setImportEntry(RecordType.Books, true);
        importHelper.setAllBooks();

        try (ArchiveReader reader = importHelper.createArchiveReader(context)) {

            final ArchiveMetaData archiveMetaData = reader.readMetaData(context);
            assertNull(archiveMetaData);

            importResults = reader.read(context, new TestProgressListener(TAG + ":header"));
        }
        assertEquals(exportResults.getBookCount(), importResults.booksProcessed);


        assertEquals(0, importResults.booksCreated);
        // we did an overwrite of ALL books
        assertEquals(mBookInDb, importResults.booksUpdated);
        // so we skipped none
        assertEquals(0, importResults.booksSkipped);
    }
}
