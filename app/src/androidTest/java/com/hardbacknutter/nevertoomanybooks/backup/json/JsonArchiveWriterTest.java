/*
 * @Copyright 2020 HardBackNutter
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

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class JsonArchiveWriterTest {

    private static final String TAG = "JsonArchiveWriterTest";

    private long mBookInDb;
    private int mNrOfStyles;

    @Before
    public void count() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(TAG)) {
            mBookInDb = db.countBooks();

            mNrOfStyles = StyleDAO.getStyles(context, db, true).size();
        }
        if (mBookInDb < 10) {
            throw new IllegalStateException("need at least 10 books for testing");
        }
    }

    @Test
    public void styles()
            throws IOException, InvalidArchiveException, ImportException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File file = AppDir.Log.getFile(context, TAG + "-styles.json");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(RecordType.MetaData, RecordType.Styles);

        exportHelper.setArchiveEncoding(ArchiveEncoding.Json);
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

        final ImportHelper importHelper = new ImportHelper(context, Uri.fromFile(file));
        final ImportResults importResults;

        importHelper.setImportEntry(RecordType.Styles, true);
        try (ArchiveReader reader = importHelper.createArchiveReader(context)) {

            final ArchiveMetaData archiveMetaData = reader.readMetaData(context);
            assertNull(archiveMetaData);

            ((JsonArchiveReader) reader).setTypeToRead(RecordType.Styles);

            importResults = reader.read(context, new TestProgressListener(TAG + ":import"));
        }
        assertEquals(exportResults.styles, importResults.styles);
    }

    @Test
    public void books()
            throws IOException, InvalidArchiveException, ImportException, DAO.DaoWriteException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File file = AppDir.Log.getFile(context, TAG + "-books.json");
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

        exportHelper.setArchiveEncoding(ArchiveEncoding.Json);
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

        try (DAO db = new DAO(TAG)) {
            db.deleteBook(context, deletedBookId);

            final Book book = Book.from(modifiedBookId, db);
            book.putString(DBDefinitions.KEY_PRIVATE_NOTES,
                           "MODIFIED" + book.getString(DBDefinitions.KEY_PRIVATE_NOTES));
            db.update(context, book, 0);
        }

        final ImportHelper importHelper = new ImportHelper(context, Uri.fromFile(file));
        ImportResults importResults;

        importHelper.setImportEntry(RecordType.Books, true);
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
        importHelper.setUpdatesMayOverwrite();
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
