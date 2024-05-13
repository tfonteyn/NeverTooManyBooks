/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class TestUtils {
    private static final int BUFFER_SIZE = 32768;

    private TestUtils() {
    }

    /**
     * Create a file from the raw test resource.
     *
     * @param resId source
     * @param file  to create
     *
     * @return the created file
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    public static File createFile(@RawRes final int resId,
                                  @NonNull final File file)
            throws IOException {

        //noinspection ResultOfMethodCallIgnored
        file.delete();

        // getContext(): we want the "androidTest" context which is where our test resources live
        final Context ic = InstrumentationRegistry.getInstrumentation().getContext();
        try (final InputStream is = ic.getResources().openRawResource(resId);
             final OutputStream os = new FileOutputStream(file)) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int nRead;
            while ((nRead = is.read(buffer)) > 0) {
                os.write(buffer, 0, nRead);
            }
            os.flush();
        }

        return file;
    }
}
