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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.content.Context;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.cert.CertificateException;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class XmlArchiveWriterTest {

    private static final String TAG = "XmlArchiveWriterTest";

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
            throws IOException, GeneralParsingException,
                   CertificateException, KeyManagementException {

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File file = AppDir.Log.getFile(context, TAG + ".xml");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        // doesn't actually matter what we specify here.
        // The XmlArchiveWriter is hardcoded to always/only write Books.
        final ExportHelper exportHelper = new ExportHelper(RecordType.MetaData, RecordType.Books);
        exportHelper.setEncoding(ArchiveEncoding.Xml);
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
    }
}
