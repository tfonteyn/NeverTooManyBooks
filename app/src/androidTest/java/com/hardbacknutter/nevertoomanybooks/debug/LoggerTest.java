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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoggerTest {

    private int msgNr;

    @Test
    public void cycleLogs()
            throws ExternalStorageException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        AppDir.Log.purge(context, true, null);

        List<File> files;

        files = AppDir.Log.collectFiles(context, null);
        assertTrue(files.isEmpty());

        for (int i = 0; i < 6; i++) {
            Logger.warn(context, "loop=" + i, "message " + (msgNr++));
            Logger.warn(context, "loop=" + i, "message " + (msgNr++));
            Logger.cycleLogs(context);
        }

        files = AppDir.Log.collectFiles(context, null);
        // 4 files: .bak, .bak.1, .bak.2, .bak.3
        assertEquals(4, files.size());
        Logger.warn(context, "final", files.toString());
    }
}
