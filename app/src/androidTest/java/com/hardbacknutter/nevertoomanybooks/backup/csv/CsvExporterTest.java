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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.backup.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

import static org.junit.Assert.assertEquals;

@MediumTest
public class CsvExporterTest {

    private static final String TAG = "CsvExporterTest";

    @Test
    public void write()
            throws IOException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        final File file = AppDir.Log.getFile(context, TAG + ".csv");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults results;

        final int entities = ExportHelper.Options.BOOKS;
        //final int entities = ExportHelper.Options.BOOKS | ExportHelper.Options.COVERS;
        try (Exporter exporter = new CsvExporter(context, entities, null)) {
            results = exporter.write(context, file, new TestProgressListener(TAG));
        }

        final long exportCount;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            // -1 for the header line.
            exportCount = reader.lines().count() - 1;
        }

        final long bookCount;
        try (DAO db = new DAO(TAG)) {
            bookCount = db.countBooks();
        }
        assertEquals(bookCount, exportCount);
        assertEquals(results.getBookCount(), exportCount);
    }

}
