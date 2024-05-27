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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringJoiner;

/**
 * This is really a test-logger only, but due to some gradle dependency issues
 * it's just so much easier to dump it into the regular source set. Oh well...
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class SystemOutLogger
        implements Logger {

    /**
     * Used instead of {@link Log#getStackTraceString(Throwable)} so we can use it in unit tests.
     * <p>
     * Handy function to get a loggable stack trace from a Throwable
     *
     * @param e An exception to log
     *
     * @return stacktrace as a printable string
     */
    @NonNull
    private static String getStackTraceString(@Nullable final Throwable e) {
        if (e == null) {
            return "";
        }

        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /**
     * Concatenate all parameters. If a parameter is an exception,
     * add its stacktrace at the end of the message. (Only one exception is logged!)
     *
     * @param params to concat
     *
     * @return String
     */
    @NonNull
    private static String concat(@Nullable final Object... params) {
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
        final String message = sj.toString();
        if (e == null) {
            return message;
        }

        return message + '\n' + getStackTraceString(e);
    }

    @Override
    public void e(@NonNull final String tag,
                  @Nullable final Throwable e,
                  @Nullable final Object... params) {
        System.out.println("JUnit|ERROR|" + tag + "|" + concat(params)
                           + "|" + (e == null ? null : e.getMessage())
                           + "\n" + getStackTraceString(e));
    }

    @Override
    public void w(@NonNull final String tag,
                  @Nullable final Object... params) {
        System.out.println("JUnit|WARN|" + tag + "|" + concat(params));
    }

    @Override
    public void d(@NonNull final String tag,
                  @Nullable final Object... params) {
        System.out.println("JUnit|DEBUG|" + tag + "|" + concat(params));
    }
}
