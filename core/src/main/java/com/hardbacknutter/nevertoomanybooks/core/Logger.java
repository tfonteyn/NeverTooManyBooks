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
package com.hardbacknutter.nevertoomanybooks.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

public interface Logger {

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
     *
     * @param tag    log tag
     * @param e      cause
     * @param params objects/text to log
     */
    void e(@NonNull final String tag,
           @NonNull final Throwable e,
           @Nullable final Object... params);

    /**
     * WARN message. Send to the logfile (always) and the console (when in DEBUG mode).
     *
     * @param tag    log tag
     * @param params objects/text to log
     */
    void w(@NonNull final String tag,
           @Nullable final Object... params);

    /**
     * DEBUG message.
     *
     * @param tag    log tag
     * @param method the calling method (added to force the developer to log the method name)
     * @param params objects/text to log
     */
    void d(@NonNull final String tag,
           @NonNull final String method,
           @Nullable final Object... params);
}
