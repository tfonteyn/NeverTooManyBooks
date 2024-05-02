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
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.VersionedFileService;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class FileLogger
        implements Logger {

    /**
     * Base name of the logfile.
     */
    public static final String ERROR_LOG_FILE = "error.log";

    /** Sub directory of {@link Context#getFilesDir()}. */
    public static final String DIR_LOG = "log";

    /** Keep the last 3 log files. */
    private static final int LOGFILE_COPIES = 3;

    /** Prefix for logfile entries. Not used on the console. */
    private static final String ERROR = "ERROR";
    private static final String WARN = "WARN";
    private static final String DEBUG = "DEBUG";
    @NonNull
    private final File logDir;

    /**
     * Constructor.
     *
     * @param logDir the directory where logs will be written
     */
    public FileLogger(@NonNull final File logDir) {
        this.logDir = logDir;
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

    /**
     * Use instead of {@link Log#getStackTraceString(Throwable)} so we can use it in unit tests.
     * <p>
     * Handy function to get a loggable stack trace from a Throwable
     *
     * @param e An exception to log
     *
     * @return stacktrace as a printable string
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
    private void debugArguments(@NonNull final Activity activity,
                                @NonNull final String tag,
                                @NonNull final String method) {
        debugArguments(tag, method, activity.getIntent().getExtras());
    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private void debugArguments(@NonNull final Fragment fragment,
                                @NonNull final String tag,
                                @NonNull final String method) {
        debugArguments(tag, method, fragment.getArguments());
    }

    private void debugArguments(@NonNull final String tag,
                                @NonNull final String method,
                                @Nullable final Bundle args) {
        if (args != null) {
            d(tag, method, "args=" + args);
            if (args.containsKey(Book.BKEY_BOOK_DATA)) {
                d(tag, method, "args[Book]=" + args.getParcelable(Book.BKEY_BOOK_DATA));
            }
        }
    }

    @Override
    @NonNull
    public String getErrorLog()
            throws IOException {
        return String.join("\n", Files.readAllLines(
                Paths.get(logDir.getAbsolutePath(), ERROR_LOG_FILE), StandardCharsets.UTF_8));
    }

    @Override
    @NonNull
    public File getLogDir() {
        return logDir;
    }

    @Override
    public void cycleLogs() {
        File logFile = null;
        //noinspection CheckStyle,OverlyBroadCatchBlock
        try {
            logFile = new File(logDir, ERROR_LOG_FILE);
            if (logFile.exists() && logFile.length() > 0) {
                final File backup = new File(logFile.getPath() + ".bak");
                // Move/rename the previous/original file
                new VersionedFileService(LOGFILE_COPIES).save(backup);
                // and write the new copy.
                FileUtils.copy(logFile, backup);
            }
        } catch (@NonNull final Exception ignore) {
            // do nothing - we can't log an error in the logger
        }

        FileUtils.delete(logFile);
    }

    @Override
    public void e(@NonNull final String tag,
                  @Nullable final Throwable e,
                  @Nullable final Object... params) {

        final String msg = concat(params);
        writeToLog(tag, ERROR, msg, e);

        if (BuildConfig.DEBUG /* always */) {
            Log.e(tag, msg, e);
        }
    }

    @Override
    public void w(@NonNull final String tag,
                  @Nullable final Object... params) {

        final String msg = concat(params);
        writeToLog(tag, WARN, msg, null);

        if (BuildConfig.DEBUG /* always */) {
            Log.w(tag, msg);
        }
    }

    @Override
    public void d(@NonNull final String tag,
                  @Nullable final Object... params) {

        final String msg = concat(params);
        writeToLog(tag, DEBUG, msg, null);

        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag, msg);
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

        //noinspection OverlyBroadCatchBlock,CheckStyle
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
