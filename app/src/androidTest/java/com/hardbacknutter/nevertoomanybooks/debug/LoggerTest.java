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

import androidx.test.filters.SmallTest;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SmallTest
public class LoggerTest {

    private static final int MAX_FILES = 50;
    private int msgNr;

    @Test
    public void cycleLogs()
            throws StorageException {

        final Context context = ServiceLocator.getAppContext();

        CoverDir.initVolume(context, 0);

        final File logDir = ServiceLocator.getLogDir();

        FileUtils.deleteDirectory(logDir, null, null);

        List<File> files;

        files = FileUtils.collectFiles(logDir, null, MAX_FILES);
        assertTrue(files.isEmpty());

        for (int i = 0; i < 6; i++) {
            final Object[] params1 = {"message " + (msgNr++)};
            Logger.warn("loop=" + i, params1);
            final Object[] params = {"message " + (msgNr++)};
            Logger.warn("loop=" + i, params);
            Logger.cycleLogs();
        }

        files = FileUtils.collectFiles(logDir, null, MAX_FILES);
        // 4 files: .bak, .bak.1, .bak.2, .bak.3
        assertEquals(4, files.size());
        Logger.warn("final", files.toString());
    }
}
