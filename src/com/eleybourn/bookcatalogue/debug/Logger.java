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

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * All message start with either:
 * DEBUG:
 * INFO:
 * ERROR:
 * <p>
 * Info messages are console only.
 * <p>
 * ALWAYS call the 'info' methods like this:
 * * if (BuildConfig.DEBUG) {
 * *     Logger.info(...);
 * * }
 * <p>
 * Error and debug go to the log and the console.
 * Error and debug will always print a stacktrace (even if you do not pass in an exception).
 * Debug should not be used except when a known issue needs tracing.
 */
public final class Logger {

    /** prefix for console logging. */
    private static final String LOG_TAG = "BC_Logger";

    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", LocaleUtils.getSystemLocale());

    private Logger() {
    }

    /**
     * For static callers
     */
    public static void info(@NonNull final Class clazz,
                            @NonNull final String methodName,
                            @NonNull final Object... params) {
        Log.w(LOG_TAG, buildInfoMessage(clazz, Tracker.State.Running, methodName, concat(params)));
    }

    /**
     * For instance callers
     */
    public static void info(@NonNull final Object object,
                            @NonNull final String methodName,
                            @NonNull final Object... params) {
        Log.w(LOG_TAG, buildInfoMessage(object.getClass(), Tracker.State.Running, methodName,
                                        concat(params)));
    }

    /**
     * For tracking enter/exit of methods.
     */
    public static void info(@NonNull final Object object,
                            @NonNull final Tracker.State state,
                            @NonNull final String methodName,
                            @NonNull final Object... params) {
        Log.w(LOG_TAG, buildInfoMessage(object.getClass(), state, methodName, concat(params)));
    }


    private static String concat(@NonNull final Object[] params) {
        StringBuilder message = new StringBuilder();
        for (Object parameter : params) {
            message.append(parameter).append('|');
        }
        message.append('.');
        return message.toString();
    }

    private static String buildInfoMessage(@NonNull final Class clazz,
                                           @NonNull final Tracker.State state,
                                           @NonNull final String methodName,
                                           @Nullable final String message) {
        return "INFO"
                + '|' + (clazz.isAnonymousClass() ? "AnonymousClass" : clazz.getCanonicalName())
                + '|' + state
                + '|' + methodName
                + '|' + message;
    }

    /* ****************************************************************************************** */

    public static void debug(@NonNull final String message) {
        if (BuildConfig.DEBUG /* always log */) {
            Log.e(LOG_TAG, buildErrorMessage(new DebugStackTrace(), message)
                    .replaceFirst("ERROR", "DEBUG"));
        }
    }

    public static void debug(@NonNull final Exception e) {
        if (BuildConfig.DEBUG /* always log */) {
            Log.e(LOG_TAG, buildErrorMessage(e, "")
                    .replaceFirst("ERROR", "DEBUG"));
        }
    }
    /* ****************************************************************************************** */

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
     * <p>
     * When in debug mode, also to the console.
     *
     * @param e       The exception to log
     * @param message extra message
     */
    public static void error(@Nullable final Exception e,
                             @NonNull final String message) {
        String result = buildErrorMessage(e, message);
        // only place where we should unconditionally write to the logfile.
        writeToLog(result);
        // for convenience also send to console during development
        if (/* always log */ BuildConfig.DEBUG) {
            Log.e(LOG_TAG, result);
        }
    }

    private static String buildErrorMessage(@Nullable final Exception e,
                                            @NonNull final String message) {

        StringBuilder msg = new StringBuilder("ERROR|");
        String exMsg;
        StringWriter stacktrace = new StringWriter();
        try (PrintWriter pw = new PrintWriter(stacktrace)) {
            if (e != null) {
                e.printStackTrace(pw);
                exMsg = e.getLocalizedMessage() + '\n';
            } else {
                exMsg = "";
            }
        }

        if (!(e instanceof DebugStackTrace)) {
            String now = DATE_FORMAT.format(new Date());
            msg.append("An Exception/Error Occurred @ ").append(now).append('\n')
               .append(exMsg)
               .append("In Phone ").append(Build.MODEL)
               .append(" (").append(Build.VERSION.SDK_INT).append(")\n");
        }

        msg.append(message).append('\n').append(stacktrace);
        return msg.toString();
    }

    /* ****************************************************************************************** */


    /**
     * This is an expensive call... file open+close... booooo
     *
     * @param message to write
     */
    private static void writeToLog(@NonNull final String message) {
        try (FileWriter fw = new FileWriter(StorageUtils.getErrorLog(), true);
             BufferedWriter out = new BufferedWriter(fw)) {
            out.write(message);
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
