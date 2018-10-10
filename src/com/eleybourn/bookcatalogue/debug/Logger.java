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
import android.util.Log;

import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * error methods will always print a stacktrace (even if you do not pass in an exception)
 */
public class Logger {

    private static final String TAG = "BC Logger";

    private Logger() {
    }

    public static void info(final String message) {
        log("INFO: " + message);
    }
    public static void debug(final String message) {
        log("DEBUG: " + message);
    }

    public static void error(@NonNull final String message) {
        error(new RuntimeException(), message);
    }

    public static void error(@NonNull final Exception e) {
        error(e, "");
    }

    public static void error(@NonNull final Error e) {
        error(new RuntimeException(e), "");
    }

    /**
     * Write the exception stacktrace to the error log file
     *
     * @param e       The exception to log
     * @param message extra message (don't pass error.getMessage(), that one is logged automatically)
     */
    public static void error(@NonNull final Exception e, @NonNull final String message) {
        @SuppressLint("SimpleDateFormat")
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String now = dateFormat.format(new Date());

        StringWriter stringWriter = new StringWriter();
        PrintWriter pw = new PrintWriter(stringWriter);

        e.printStackTrace(pw);

        String exMsg = e.getMessage();
        String error = "An Exception/Error Occurred @ " + now + "\n" +
                (exMsg != null ? exMsg + "\n" : "") +
                "In Phone " + Build.MODEL + " (" + Build.VERSION.SDK_INT + ") \n" +
                message + "\n" +
                stringWriter; // contains the exception

        log(error);

        pw.close();
    }

    private static void log(final String message) {
        try {
        // FIXME Remove Log.error! Replace with ACRA?
        Log.e(TAG, message);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(StorageUtils.getErrorLog()), "utf8"), 8192);
        out.write(message);
        out.close();
        } catch (Exception ignored) {
            // do nothing - we can't log an error in the error logger. (and we don't want to FC the app)
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
            // do nothing - we can't log an error in the error logger. (and we don't want to FC the app)
        }
    }
}
