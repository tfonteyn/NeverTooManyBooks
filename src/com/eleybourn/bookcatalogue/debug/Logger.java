/*
 * @copyright 2011 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.debug;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Non-error messages easier to find in the logfile.
 * They always start with either:
 * DEBUG:
 * INFO:
 * <p>
 * error methods will always print a stacktrace (even if you do not pass in an exception)
 * <p>
 * Log.d: compiled in, but stripped out at runtime
 */
public final class Logger {

    private static final int OUTPUT_BUFFER = 8192;

    private static final String TAG = "BC_Logger";
    @SuppressLint("SimpleDateFormat")
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Logger() {
    }

    /**
     * Should really only be used from within a debug code block.
     * And even then only in problematic places, as the stack trace can get large
     * <p>
     * Generates stacktrace!
     * <p>
     * if (BuildConfig.DEBUG) {
     * Logger.debug("blah");
     * }
     */
    public static void debug(@Nullable final String message) {
        error("DEBUG|" + message);
    }

    /**
     * Pure info, no stacktrace.
     * <p>
     * For static callers
     */
    public static void info(@NonNull final Class clazz,
                            @Nullable final String message) {
        String msg = "INFO|" + clazz.getCanonicalName() + '|' + message;
        Log.d(TAG, msg);
        writeToErrorLog(msg);
    }


    /**
     * Pure info, no stacktrace.
     * <p>
     * For instance callers
     */
    public static void info(@NonNull final Object object,
                            @Nullable final String message) {
        info(object, "", message);
    }

    /**
     * Pure info, no stacktrace.
     * <p>
     * For instance callers
     */
    public static void info(@NonNull final Object object,
                            @NonNull final String methodName,
                            @Nullable final String message) {
        Class clazz = object.getClass();
        StringBuilder msg = new StringBuilder("INFO|");
        msg.append(clazz.isAnonymousClass() ? "AnonymousClass" : clazz.getCanonicalName());
        msg.append('|');
        if (!methodName.isEmpty()) {
            msg.append(methodName).append('|');
        }
        msg.append(message);
        String m = msg.toString();
        Log.d(TAG, m);
        writeToErrorLog(m);
    }

    /**
     * Generates stacktrace.
     */
    public static void error(@NonNull final String message) {
        error(new DebugStackTrace(), message);
    }

    public static void error(@NonNull final Exception e) {
        error(e, "");
    }

    /**
     * For really bad things... 2019-02: right now only an out of memory error.
     * <p>
     * Generates stacktrace
     */
    public static void error(@NonNull final Error e) {
        error(new DebugStackTrace(e), "");
    }

    /**
     * Write the exception stacktrace to the error log file.
     * Will use e.getLocalizedMessage()
     *
     * @param e       The exception to log
     * @param message extra message
     */
    public static void error(@Nullable final Exception e,
                             @NonNull final String message) {
        String now = DATE_FORMAT.format(new Date());
        String exMsg = null;
        StringWriter stacktrace = new StringWriter();
        PrintWriter pw = new PrintWriter(stacktrace);
        if (e != null) {
            e.printStackTrace(pw);
            exMsg = e.getLocalizedMessage();
        }

        String error;
        if (e instanceof DebugStackTrace) {
            error = message;
        } else {
            error = "ERROR|An Exception/Error Occurred @ " + now + '\n'
                    + (exMsg != null ? exMsg + '\n' : "")
                    + "In Phone " + Build.MODEL + " (" + Build.VERSION.SDK_INT + ") \n"
                    + message + '\n';
        }

        if (/* always log */ BuildConfig.DEBUG) {
            Log.d(TAG, error + '\n' + stacktrace);
        }

        writeToErrorLog(error + stacktrace);
        pw.close();
    }

    private static void writeToErrorLog(@NonNull final String message) {
        try {
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(StorageUtils.getErrorLog()),
                            StandardCharsets.UTF_8), OUTPUT_BUFFER);
            out.write(message);
            out.close();
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignored) {
            // do nothing - we can't log an error in the error logger.
            // (and we don't want to CF the app)
        }
    }

    /**
     * Clear the error log each time the app is started; preserve previous if non-empty.
     */
    public static void clearLog() {
        try {
            File logFile = new File(StorageUtils.getErrorLog());
            if (logFile.exists() && logFile.length() > 0) {
                File backup = new File(StorageUtils.getErrorLog() + ".bak");
                StorageUtils.renameFile(logFile, backup);
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignore) {
            // Ignore backup failure...
        } finally {
            writeToErrorLog("");
        }
    }

    private static class DebugStackTrace
            extends RuntimeException {

        private static final long serialVersionUID = 5549905921391722588L;

        DebugStackTrace() {
        }

        DebugStackTrace(@NonNull final Throwable cause) {
            super(cause);
        }
    }
}
