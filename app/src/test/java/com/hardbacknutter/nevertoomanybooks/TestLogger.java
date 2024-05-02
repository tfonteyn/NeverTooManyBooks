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

package com.hardbacknutter.nevertoomanybooks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.FileLogger;

public class TestLogger
        implements Logger {

    @NonNull
    private final File logDir;

    TestLogger(@NonNull final File tmpDir) {
        logDir = new File(tmpDir, FileLogger.DIR_LOG);
        if (!logDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logDir.mkdirs();
        }
    }

    @NonNull
    @Override
    public String getErrorLog() {
        return "TestLogger";
    }

    @NonNull
    @Override
    public File getLogDir() {
        return logDir;
    }

    @Override
    public void cycleLogs() {

    }

    @Override
    public void e(@NonNull final String tag,
                  @Nullable final Throwable e,
                  @Nullable final Object... params) {
        System.out.println("JUnit|ERROR|" + tag + "|" + FileLogger.concat(params)
                           + "|" + (e == null ? null : e.getMessage())
                           + "\n" + FileLogger.getStackTraceString(e));
    }

    @Override
    public void w(@NonNull final String tag,
                  @Nullable final Object... params) {
        System.out.println("JUnit|WARN|" + tag + "|" + FileLogger.concat(params));
    }

    @Override
    public void d(@NonNull final String tag,
                  @Nullable final Object... params) {
        System.out.println("JUnit|DEBUG|" + tag + "|" + FileLogger.concat(params));
    }
}
