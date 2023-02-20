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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.content.Context;

import androidx.test.filters.SmallTest;

import java.io.File;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SmallTest
public class LoggerTest {

    private int msgNr;

    @Test
    public void cycleLogs()
            throws StorageException {

        final Context context = ServiceLocator.getAppContext();
        final Logger logger = LoggerFactory.getLogger();

        CoverDir.initVolume(context, 0);

        final File logDir = logger.getLogDir();

        FileUtils.deleteDirectory(logDir, null);

        List<File> files;

        files = FileUtils.collectFiles(logDir, null);
        assertTrue(files.isEmpty());

        for (int i = 0; i < 6; i++) {
            final Object[] params1 = {"message " + (msgNr++)};
            logger.w("loop=" + i, params1);
            final Object[] params = {"message " + (msgNr++)};
            logger.w("loop=" + i, params);
            logger.cycleLogs();
        }

        files = FileUtils.collectFiles(logDir, null);
        // 4 files: .bak, .bak.1, .bak.2, .bak.3
        assertEquals(4, files.size());
        logger.w("final", files.toString());
    }
}
