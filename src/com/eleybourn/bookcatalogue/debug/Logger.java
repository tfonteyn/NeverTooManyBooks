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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Non-error messages easier to find in the logfile, they always start with either:
 * DEBUG:
 * INFO:
 *
 * error methods will always print a stacktrace (even if you do not pass in an exception)
 *
 *  FIXME Remove Log.error! Replace with ACRA?
 *  ACRA.getErrorReporter().handleException(e);
 */
public class Logger {

    private static final String TAG = "BC Logger";

    @SuppressLint("SimpleDateFormat")
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Logger() {
    }

    /**
     * Should really only be used from within a code block.
     * And even then only in problematic places.
     *
     * Generates stacktrace!
     *
     * if (BuildConfig.DEBUG) {
     * Logger.debug("blah");
     * }
     */
    public static void debug(final String message) {
        error("DEBUG: " + message);
    }

    /**
     * Pure info, no stacktrace.
     */
    public static void info(final String message) {
        Log.e(TAG, "INFO: " + message);
        writeToErrorLog("INFO: " + message);
    }

    /**
     * Generates stacktrace
     */
    public static void error(@NonNull final String message) {
        error(new RuntimeException(), message);
    }

    public static void error(@NonNull final Exception e) {
        error(e, "");
    }

    /**
     * Transforms a Java 'Error' into an Exception, so it's not fatal.
     *
     * Generates stacktrace
     */
    public static void error(@NonNull final Error e) {
        error(new RuntimeException(e), "");
    }

    /**
     * Write the exception stacktrace to the error log file
     * Will use e.getLocalizedMessage()
     *
     * @param e       The exception to log
     * @param message extra message
     */
    public static void error(@Nullable final Exception e, @NonNull final String message) {
        String now = dateFormat.format(new Date());
        String exMsg = null;
        StringWriter stacktrace = new StringWriter();
        PrintWriter pw = new PrintWriter(stacktrace);
        if (e != null) {
            e.printStackTrace(pw);
            exMsg = e.getLocalizedMessage();
        }

        String error = "An Exception/Error Occurred @ " + now + "\n" +
                (exMsg != null ? exMsg + "\n" : "") +
                "In Phone " + Build.MODEL + " (" + Build.VERSION.SDK_INT + ") \n" +
                message + "\n" +
                stacktrace;

        // Log the exception to the console in full when in debug, but only the message when deployed.
        // Either way, the exception will be in the physical logfile.
        if (BuildConfig.DEBUG) {
            Log.e(TAG, error);
        } else {
            Log.e(TAG, message); // message without the exception stuff
        }

        writeToErrorLog(error);
        pw.close();
    }

    private static void writeToErrorLog(@NonNull final String message) {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(StorageUtils.getErrorLog()), "utf8"), 8192);
            out.write(message);
            out.close();
        } catch (Exception ignored) {
            // do nothing - we can't log an error in the error logger. (and we don't want to CF the app)
        }
    }

    /**
     * Clear the error log each time the app is started; preserve previous if non-empty
     */
    public static void clearLog() {
        try {
            try {
                File orig = new File(StorageUtils.getErrorLog());
                if (orig.exists() && orig.length() > 0) {
                    File backup = new File(StorageUtils.getErrorLog() + ".bak");
                    StorageUtils.renameFile(orig, backup);
                }
            } catch (Exception ignore) {
                // Ignore backup failure...
            }
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(StorageUtils.getErrorLog()), "utf8"), 8192);
            out.write("");
            out.close();
        } catch (Exception ignore) {
            // do nothing - we can't log an error in the error logger. (and we don't want to CF the app)
        }
    }
}
