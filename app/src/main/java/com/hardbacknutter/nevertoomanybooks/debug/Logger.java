/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;


public interface Logger {

    /** serious errors are written to this file. */
    String ERROR_LOG_FILE = "error.log";
    /**
     * Sub directory of {@link Context#getFilesDir()}.
     * log files.
     */
    String DIR_LOG = "log";

    /** JUnit aware wrapper for {@link Log#e(String, String, Throwable)}. */
    void e(@NonNull final String tag,
           @Nullable final Throwable e,
           @NonNull final String msg);

    void d(@NonNull final String tag,
           @NonNull final Throwable e,
           @NonNull final String msg);

    /**
     * JUnit aware wrapper for {@link Log#d(String, String)} with an extra 'method' parameter.
     *
     * @param tag    Used to identify the source of a log message.  It usually identifies
     *               the class or activity where the log call occurs.
     * @param method the calling method (added to force the developer to log the method name)
     * @param msg    The message you would like logged.
     */
    void d(@NonNull final String tag,
           @NonNull final String method,
           @NonNull final String msg);

    /** JUnit aware wrapper for {@link Log#w(String, String)}. */
    void w(@NonNull final String tag,
           @NonNull final Object... params);

    @NonNull
    String getErrorLog()
            throws IOException;

    @NonNull
    File getLogDir();

    /**
     * Cycle the log each time the app is started; preserve previous if non-empty.
     */
    void cycleLogs();

    /**
     * ERROR message. Send to the logfile (always) and the console (when in DEBUG mode).
     * <p>
     * Use sparingly, writing to the log is expensive.
     *
     * @param tag    log tag
     * @param e      cause
     * @param params to concat
     */
    void error(@NonNull final String tag,
               @Nullable final Throwable e,
               @Nullable final Object... params);

    /**
     * WARN message. Send to the logfile (always) and the console (when in DEBUG mode).
     * <p>
     * Use sparingly, writing to the log is expensive.
     * <p>
     * Use when an error or unusual result should be noted, but will not affect the flow of the app.
     * No stacktrace!
     *
     * @param tag    log tag
     * @param params to concat
     */
    void warn(@NonNull final String tag,
              @NonNull final Object... params);


}
