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
package com.hardbacknutter.nevertoomanybooks.backup.zip;

import android.content.Context;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.backup.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveContainer;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ZipArchiveWriterTest {

    private static final String TAG = "ZipArchiveWriterTest";

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
            throws IOException, InvalidArchiveException, ImportException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File file = AppDir.Log.getFile(context, TAG + ".zip");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(ExportHelper.OPTIONS_BOOKS
                                                           | ImportHelper.OPTIONS_PREFS
                                                           | ImportHelper.OPTIONS_STYLES);
        exportHelper.setArchiveContainer(ArchiveContainer.Zip);
        exportHelper.setUri(Uri.fromFile(file));

        try (ArchiveWriter writer = exportHelper.getArchiveWriter(context)) {
            exportResults = writer.write(context, new TestProgressListener(TAG));
        }
        // assume success; a failure would have thrown an exception
        exportHelper.onSuccess(context);

        assertEquals(mBookInDb, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
        assertTrue(exportResults.preferences > 0);
        assertTrue(exportResults.styles > 0);
        assertFalse(exportResults.database);

        final long exportCount = exportResults.getBookCount();

        final ImportHelper importHelper = new ImportHelper(Uri.fromFile(file));
        final ImportResults importResults;

        // only NEW books, hence 0/0 created/updated as outcome of the test
        importHelper.setOption(ImportHelper.OPTIONS_BOOKS, true);
        try (ArchiveReader reader = importHelper.getArchiveReader(context)) {

            final ArchiveInfo archiveInfo = reader.readArchiveInfo(context);
            assertNotNull(archiveInfo);

            assertEquals(mBookInDb, archiveInfo.getBookCount());
            assertEquals(0, archiveInfo.getCoverCount());

            importResults = reader.read(context, new TestProgressListener(TAG));
        }
        assertEquals(exportCount, importResults.booksProcessed);


        assertEquals(0, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        assertEquals(exportCount, importResults.booksSkipped);

    }
}
