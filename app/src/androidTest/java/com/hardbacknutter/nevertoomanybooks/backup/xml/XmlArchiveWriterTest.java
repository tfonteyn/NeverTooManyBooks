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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.content.Context;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class XmlArchiveWriterTest {

    private static final String TAG = "XmlArchiveWriterTest";

    private final Map<String, ListStyle> mClonedStyles = new HashMap<>();

    @Before
    public void cloneStyles() {
        try (DAO db = new DAO(TAG)) {
            final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            for (final ListStyle style : StyleDAO.getStyles(context, db, true)) {
                // cast to access internal VisibleForTesting clone method
                final UserStyle clonedStyle = ((BooklistStyle) style)
                        .clone(context, style.getId(), style.getUuid());
                mClonedStyles.put(style.getUuid(), clonedStyle);
            }
        }
    }

    @Test
    public void write()
            throws IOException, InvalidArchiveException, ImportException {

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File file = AppDir.Log.getFile(context, TAG + ".xml");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(
                ArchiveWriterRecord.Type.Styles,
                ArchiveWriterRecord.Type.Preferences);
        exportHelper.setArchiveType(ArchiveType.Xml);
        exportHelper.setUri(Uri.fromFile(file));

        try (ArchiveWriter writer = exportHelper.createArchiveWriter(context)) {
            exportResults = writer.write(context, new TestProgressListener(TAG + ":export"));
        }
        // assume success; a failure would have thrown an exception
        exportHelper.onSuccess(context);

        assertEquals(0, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
        assertEquals(1, exportResults.preferences);
        assertEquals(mClonedStyles.size(), exportResults.styles);
        assertFalse(exportResults.database);

        // remove all user, reset all builtin
        try (DAO db = new DAO(TAG)) {
            for (final ListStyle style : StyleDAO.getStyles(context, db, true)) {
                if (style instanceof UserStyle) {
                    db.delete(style);
                } else if (style instanceof BuiltinStyle) {
                    style.setPreferred(false);
                    style.setMenuPosition((int) -style.getId());
                    StyleDAO.update(db, style);
                } else {
                    throw new IllegalStateException("Unhandled style: " + style);
                }
            }

            final ArrayList<ListStyle> styles = StyleDAO.getStyles(context, db, true);
            assertEquals(StyleDAO.BuiltinStyles.MAX_ID, -styles.size());
        }


        final ImportHelper importHelper = new ImportHelper(context, Uri.fromFile(file));
        final ImportResults importResults;
        importHelper.setImportEntry(ArchiveReaderRecord.Type.Styles, true);
        try (ArchiveReader reader = importHelper.createArchiveReader(context)) {

            final ArchiveInfo archiveInfo = reader.readHeader(context);
            assertNotNull(archiveInfo);

            importResults = reader.read(context, new TestProgressListener(TAG + ":header"));
        }

        assertEquals(exportResults.styles, importResults.styles);

        try (DAO db = new DAO(TAG)) {
            // the database reflects what we imported; compare that with what we cloned before
            final ArrayList<ListStyle> styles = StyleDAO.getStyles(context, db, true);
            assertEquals(mClonedStyles.size(), styles.size());

            for (final ListStyle style : styles) {
                assertEquals(mClonedStyles.get(style.getUuid()), style);
            }
        }
    }
}
