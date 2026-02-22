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
package com.hardbacknutter.nevertoomanybooks.backup.zip;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.DbPrep;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ArchiveWriterEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ZipArchiveWriterTest
        extends BaseDBTest {

    private static final String TAG = "ZipArchiveWriterTest";

    private int bookInDb;
    private int nrOfStyles;

    private ISODateParser dateParser;

    @BeforeEach
    void setup()
            throws StorageException, IOException, DataReaderException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        dateParser = new ISODateParser(serviceLocator.getSystemLocaleList().get(0));

        bookInDb = new DbPrep().maybeInstallTestData(context);
        // +1 for the global style which will be added during export
        nrOfStyles = serviceLocator.getStyles().getStyles(true).size() + 1;
    }

    @Test
    void write()
            throws DataReaderException, DataWriterException,
                   IOException, StorageException, CredentialsException, CertificateException {
        final File file = new File(context.getFilesDir(), TAG + ".zip");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final Uri uri = Uri.fromFile(file);

        final ExportResults exportResults;

        // Full backup except covers.
        final ExportHelper exportHelper = new ExportHelper(ArchiveWriterEncoding.Zip,
                                                           EnumSet.of(RecordType.Books,
                                                                      RecordType.Preferences,
                                                                      RecordType.Certificates,
                                                                      RecordType.Styles),
                                                           dateParser);
        exportHelper.setEncoding(ArchiveWriterEncoding.Zip);
        exportHelper.setUri(uri);

        exportResults = exportHelper.write(context, new TestProgressListener(TAG + ":export"));

        assertEquals(bookInDb, exportResults.getBookCount());
        assertEquals(0, exportResults.getImageCount());
        assertEquals(1, exportResults.preferences);
        assertEquals(nrOfStyles, exportResults.styles);
        assertFalse(exportResults.database);

        final int exportCount = exportResults.getBookCount();

        read(uri, exportCount);
    }

    private void read(@NonNull final Uri uri,
                      final int expectedNrOfBooks)
            throws DataReaderException, IOException,
                   StorageException, CredentialsException, CertificateException {

        final ImportHelper importHelper = new ImportHelper(context, uri);
        // The default, fail if the default was changed without changing this test!
        assertEquals(DataReader.Updates.OnlyNewer, importHelper.getUpdateOption());

        importHelper.addRecordType(RecordType.Books);

        final ArchiveMetaData archiveMetaData = importHelper.readMetaData(context).orElse(null);
        assertNotNull(archiveMetaData);
        assertEquals(bookInDb, (long) archiveMetaData.getBookCount().orElse(-1));
        assertEquals(-1, (long) archiveMetaData.getImageCount().orElse(-1));

        final ImportResults importResults = importHelper.read(context, new TestProgressListener(
                TAG + ":header"));
        assertNotNull(importResults);

        // booksProcessed is updated for each imported book record
        assertEquals(expectedNrOfBooks, importResults.booksProcessed);

        // ImportHelper.Updates.OnlyNewer ... so we don't actually import anything
        assertEquals(0, importResults.getBooksCreated());
        assertEquals(0, importResults.getBooksUpdated());
        // We skipped all of them
        assertEquals(expectedNrOfBooks, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
    }
}
