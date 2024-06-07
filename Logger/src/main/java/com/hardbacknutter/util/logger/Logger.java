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

package com.hardbacknutter.util.logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringJoiner;

public interface Logger {

    /**
     * Concatenate all parameters. If a parameter is an exception,
     * add its stacktrace at the end of the message. (Only one exception is logged!)
     *
     * @param params to concat
     *
     * @return String
     */
    @NonNull
    static String concat(@Nullable final Object... params) {
        if (params == null) {
            return "";
        }

        final StringJoiner sj = new StringJoiner("|");
        Exception e = null;
        for (final Object parameter : params) {
            if (parameter instanceof Exception) {
                e = (Exception) parameter;
            } else {
                sj.add(String.valueOf(parameter));
            }
        }
        if (e == null) {
            return sj.toString();
        }

        final StringWriter stackTrace = new StringWriter();
        final PrintWriter pw = new PrintWriter(stackTrace);
        e.printStackTrace(pw);
        pw.flush();

        return sj.add(e.getMessage()).toString() + '\n' + stackTrace;
    }

    /**
     * ERROR message.
     *
     * @param tag    log tag
     * @param e      cause
     * @param params objects/text to log
     */
    void e(@NonNull String tag,
           @Nullable Throwable e,
           @Nullable Object... params);

    /**
     * WARN message.
     *
     * @param tag    log tag
     * @param params objects/text to log
     */
    void w(@NonNull String tag,
           @Nullable Object... params);

    /**
     * DEBUG message.
     *
     * @param tag    log tag
     * @param params objects/text to log
     */
    void d(@NonNull String tag,
           @Nullable Object... params);
}
