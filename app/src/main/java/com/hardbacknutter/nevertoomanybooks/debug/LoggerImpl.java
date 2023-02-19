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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class LoggerImpl
        implements Logger {

    /** Keep the last 3 log files. */
    private static final int LOGFILE_COPIES = 3;

    /** Prefix for logfile entries. Not used on the console. */
    private static final String ERROR = "ERROR";
    private static final String WARN = "WARN";
    @NonNull
    private final File logDir;

    public LoggerImpl(@NonNull final Context context) {
        logDir = new File(context.getApplicationContext().getFilesDir(), DIR_LOG);
        if (!logDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logDir.mkdirs();
        }
    }

    /**
     * Concatenate all parameters. If a parameter is an exception,
     * add its stacktrace at the end of the message. (Only one exception is logged!)
     *
     * @param params to concat
     *
     * @return String
     */
    @VisibleForTesting
    @NonNull
    public static String concat(@Nullable final Object... params) {
        if (params == null) {
            return "";
        }
        final StringBuilder message = new StringBuilder();
        Exception e = null;
        for (final Object parameter : params) {
            if (parameter instanceof Exception) {
                e = (Exception) parameter;
            } else {
                message.append(parameter).append('|');
            }
        }
        message.append('.');
        if (e != null) {
            message.append('\n').append(getStackTraceString(e));
        }
        return message.toString();
    }

    /**
     * Use instead of {@link Log#getStackTraceString(Throwable)} so we can use it in unit tests.
     * <p>
     * Handy function to get a loggable stack trace from a Throwable
     *
     * @param e An exception to log
     */
    @VisibleForTesting
    @NonNull
    public static String getStackTraceString(@Nullable final Throwable e) {
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
     * DEBUG only.
     * Format the passed bundle in a way that is convenient for display.
     *
     * @param bundle Bundle to format, strings will be trimmed before adding
     *
     * @return Formatted string
     */
    @NonNull
    public static String toString(@NonNull final Bundle bundle) {
        final StringBuilder sb = new StringBuilder();
        for (final String k : bundle.keySet()) {
            sb.append(k).append("->");
            final Object o = bundle.get(k);
            if (o != null) {
                sb.append(o.toString().trim());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * DEBUG only.
     * Dump an InputStream to the console.
     */
    @SuppressLint("LogConditional")
    @SuppressWarnings("unused")
    public void dump(@NonNull final String tag,
                     @NonNull final String method,
                     @NonNull final InputStream inputStream) {
        try {
            final BufferedInputStream bis = new BufferedInputStream(inputStream);
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int result = bis.read();
            while (result != -1) {
                buf.write((byte) result);
                result = bis.read();
            }
            // Charset needs API 33
            //noinspection CharsetObjectCanBeUsed
            final String msg = buf.toString("UTF-8");
            d(tag, method, msg);
        } catch (@NonNull final IOException e) {
            Log.d(tag, "dumping failed: ", e);
        }
    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private void debugArguments(@NonNull final String tag,
                                @NonNull final String method,
                                @NonNull final Object fragmentOrActivity) {
        if (fragmentOrActivity instanceof Activity) {
            final Bundle extras = ((Activity) fragmentOrActivity).getIntent().getExtras();
            if (extras != null) {
                d(tag, method, "extras=" + extras);
                if (extras.containsKey(Book.BKEY_BOOK_DATA)) {
                    d(tag, method, "extras=" + extras.getParcelable(Book.BKEY_BOOK_DATA));
                }
            }
        } else if (fragmentOrActivity instanceof Fragment) {
            final Bundle args = ((Fragment) fragmentOrActivity).getArguments();
            if (args != null) {
                d(tag, method, "args=" + args);
                if (args.containsKey(Book.BKEY_BOOK_DATA)) {
                    d(tag, method, "args=" + args.getParcelable(Book.BKEY_BOOK_DATA));
                }
            }
        }
    }

    /** Wrapper for {@link Log#e(String, String, Throwable)}. */
    public void e(@NonNull final String tag,
                  @Nullable final Throwable e,
                  @NonNull final String msg) {
        if (BuildConfig.DEBUG /* always */) {
            Log.e(tag, msg, e);
        }
    }

    /**
     * Wrapper for {@link Log#d(String, String)} with an extra 'method' parameter.
     *
     * @param tag    Used to identify the source of a log message.  It usually identifies
     *               the class or activity where the log call occurs.
     * @param method the calling method (added to force the developer to log the method name)
     * @param msg    The message you would like logged.
     */
    public void d(@NonNull final String tag,
                  @NonNull final String method,
                  @NonNull final String msg) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag, method + "|" + msg);
        }
    }

    @Override
    public void d(@NonNull final String tag,
                  @NonNull final Throwable e,
                  @NonNull final String msg) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag, msg, e);
        }
    }

    /** Wrapper for {@link Log#w(String, String)}. */
    public void w(@NonNull final String tag,
                  @NonNull final Object... params) {
        if (BuildConfig.DEBUG /* always */) {
            Log.w(tag, concat(params));
        }
    }

    @NonNull
    public String getErrorLog()
            throws IOException {
        return String.join("\n", Files.readAllLines(
                Paths.get(logDir.getAbsolutePath(), ERROR_LOG_FILE), StandardCharsets.UTF_8));
    }

    @NonNull
    public File getLogDir() {
        return logDir;
    }

    /**
     * Cycle the log each time the app is started; preserve previous if non-empty.
     */
    public void cycleLogs() {
        File logFile = null;
        try {
            logFile = new File(logDir, ERROR_LOG_FILE);
            if (logFile.exists() && logFile.length() > 0) {
                final File backup = new File(logFile.getPath() + ".bak");
                FileUtils.copyWithBackup(logFile, backup, LOGFILE_COPIES);
            }
        } catch (@NonNull final Exception ignore) {
            // do nothing - we can't log an error in the logger
        }

        FileUtils.delete(logFile);
    }

    /**
     * ERROR message. Send to the logfile (always) and the console (when in DEBUG mode).
     * <p>
     * Use sparingly, writing to the log is expensive.
     *
     * @param tag    log tag
     * @param e      cause
     * @param params to concat
     */
    public void error(@NonNull final String tag,
                      @Nullable final Throwable e,
                      @Nullable final Object... params) {
        final String msg;
        if (params != null) {
            msg = '|' + concat(params);
        } else {
            msg = "";
        }
        writeToLog(tag, ERROR, msg, e);

        if (BuildConfig.DEBUG /* always */) {
            e(tag, e, msg);
        }
    }

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
    public void warn(@NonNull final String tag,
                     @NonNull final Object... params) {

        final String msg = concat(params);
        writeToLog(tag, WARN, msg, null);

        if (BuildConfig.DEBUG /* always */) {
            w(tag, msg);
        }
    }

    /**
     * This is an expensive call... file open+close... BOOOO!
     *
     * @param tag     log tag
     * @param type    warn,error,...
     * @param message to write
     * @param e       optional Throwable
     */
    private void writeToLog(@NonNull final String tag,
                            @NonNull final String type,
                            @NonNull final String message,
                            @Nullable final Throwable e) {

        final String exMsg;
        if (e != null) {
            exMsg = '|' + getStackTraceString(e);
        } else {
            exMsg = "";
        }

        // UTC based
        final String fullMsg = LocalDateTime.now(ZoneOffset.UTC)
                                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                               + '|' + tag + '|' + type + '|' + message + exMsg;

        try {
            final File logFile = new File(logDir, ERROR_LOG_FILE);
            try (FileOutputStream fos = new FileOutputStream(logFile, true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                 PrintWriter out = new PrintWriter(new BufferedWriter(osw))) {
                out.println(fullMsg);
            }
        } catch (@NonNull final Exception ignore) {
            // do nothing - we can't log an error in the logger
        }
    }
}
