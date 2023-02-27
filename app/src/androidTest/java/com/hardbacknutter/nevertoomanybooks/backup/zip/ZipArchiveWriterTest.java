/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@MediumTest
public class ZipArchiveWriterTest
        extends BaseDBTest {

    private static final String TAG = "ZipArchiveWriterTest";

    private long bookInDb;
    private int nrOfStyles;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();
        bookInDb = serviceLocator.getBookDao().count();
        if (bookInDb < 10) {
            throw new IllegalStateException("need at least 10 books for testing");
        }
        nrOfStyles = serviceLocator.getStyles().getStyles(context, true).size();
    }

    @Test
    public void write()
            throws DataReaderException, DataWriterException,
                   IOException, StorageException, CredentialsException, CertificateException {
        final File file = new File(context.getFilesDir(), TAG + ".zip");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final Uri uri = Uri.fromFile(file);

        final ExportResults exportResults;

        // Full backup except covers.
        final ExportHelper exportHelper = new ExportHelper(ArchiveEncoding.Zip,
                                                           EnumSet.of(RecordType.Books,
                                                                      RecordType.Preferences,
                                                                      RecordType.Certificates,
                                                                      RecordType.Styles),
                                                           systemLocale);
        exportHelper.setEncoding(ArchiveEncoding.Zip);
        exportHelper.setUri(uri);

        exportResults = exportHelper.write(context, new TestProgressListener(TAG + ":export"));

        assertEquals(bookInDb, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
        assertEquals(1, exportResults.preferences);
        assertEquals(nrOfStyles, exportResults.styles);
        assertFalse(exportResults.database);

        final long exportCount = exportResults.getBookCount();

        read(uri, exportCount);
    }

    private void read(@NonNull final Uri uri,
                      final long expectedNrOfBooks)
            throws DataReaderException, IOException,
                   StorageException, CredentialsException, CertificateException {

        final ImportHelper importHelper = new ImportHelper(context, systemLocale, uri);
        // The default, fail if the default was changed without changing this test!
        assertEquals(DataReader.Updates.OnlyNewer, importHelper.getUpdateOption());

        importHelper.addRecordType(RecordType.Books);

        final ArchiveMetaData archiveMetaData = importHelper.readMetaData(context).orElse(null);
        assertNotNull(archiveMetaData);
        assertEquals(bookInDb, (long) archiveMetaData.getBookCount().orElse(-1));
        assertEquals(-1, (long) archiveMetaData.getCoverCount().orElse(-1));

        final ImportResults importResults = importHelper.read(context, new TestProgressListener(
                TAG + ":header"));
        assertNotNull(importResults);

        // booksProcessed is updated for each imported book record
        assertEquals(expectedNrOfBooks, importResults.booksProcessed);

        // ImportHelper.Updates.OnlyNewer ... so we don't actually import anything
        assertEquals(0, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
    }
}
