/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * ALWAYS call methods like this:
 * * if (BuildConfig.DEBUG ) {
 * *     Logger.blah(...);
 * * }
 * <p>
 * The second check on DEBUG build in this class is only to catch the lack-of in other places.
 * <p>
 * <ul>{@link #error}, {@link #warn} and {@link #warnWithStackTrace} will write to
 *      <li>the log file when NOT in debug/junit mode</li>
 *      <li>redirect to the below {@link #e} and {@link #w} methods otherwise</li>
 * </ul>
 * <p>
 * <ul>{@link #e}, {@link #w} and {@link #d} will write to
 *      <li>the console when in debug mode</li>
 *      <li>System.out when running in JUnit</li>
 * </ul>
 */
public final class Logger {

    /** Full log path name. Used by ACRA configuration which only seems to accepts a constant. */
    public static final String LOG_PATH = "log/error.log";
    //public static final String LOG_PATH = AppDir.LOG_SUB_DIR + "/" + ERROR_LOG_FILE;

    /** serious errors are written to this file. Stored in {@link AppDir#Log}. */
    static final String ERROR_LOG_FILE = "error.log";

    /** Keep the last 5 log files. */
    private static final int LOGFILE_COPIES = 5;

    /** Prefix for logfile entries. Not used on the console. */
    private static final String ERROR = "ERROR";
    private static final String WARN = "WARN";
    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    private Logger() {
    }

    /**
     * ERROR message. Send to the logfile (always) and the console (when in DEBUG mode).
     *
     * @param context Current context
     * @param tag     log tag
     * @param e       cause
     * @param params  to concat
     */
    public static void error(@NonNull final Context context,
                             @NonNull final String tag,
                             @NonNull final Throwable e,
                             @Nullable final Object... params) {
        String msg;
        if (params != null) {
            msg = '|' + concat(params);
        } else {
            msg = "";
        }
        writeToLog(context, tag, ERROR, msg, e);

        if (BuildConfig.DEBUG /* always */) {
            e(tag, msg, e);
        }
    }

    /**
     * WARN message with a generated StackTrace.
     * Send to the logfile (always) and the console (when in DEBUG mode).
     * <p>
     * Use sparingly, writing to the log is expensive.
     * <p>
     * Use when an error or unusual result should be noted, but will not affect the flow of the app.
     *
     * @param context Current context
     * @param tag     log tag
     * @param params  to concat
     */
    public static void warnWithStackTrace(@NonNull final Context context,
                                          @NonNull final String tag,
                                          @NonNull final Object... params) {
        Throwable e = new Throwable();
        String msg = concat(params);
        writeToLog(context, tag, WARN, msg, e);

        if (BuildConfig.DEBUG /* always */) {
            w(tag, msg, e);
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
     * @param context    Current context
     * @param tag        log tag
     * @param params     to concat
     */
    public static void warn(@NonNull final Context context,
                            @NonNull final String tag,
                            @NonNull final Object... params) {
        String msg = concat(params);
        writeToLog(context, tag, WARN, msg, null);

        if (BuildConfig.DEBUG /* always */) {
            w(tag, msg);
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
    private static String concat(@NonNull final Object... params) {
        StringBuilder message = new StringBuilder();
        Exception e = null;
        for (Object parameter : params) {
            if (parameter instanceof Exception) {
                e = (Exception) parameter;
                continue;
            }
            message.append(parameter.toString()).append('|');
        }
        message.append('.');
        if (e != null) {
            message.append('\n').append(getStackTraceString(e));
        }
        return message.toString();
    }

    /**
     * This is an expensive call... file open+close... BOOOO!
     *
     * @param context Current context
     * @param tag     log tag
     * @param type    warn,error,...
     * @param message to write
     * @param e       optional Throwable
     */
    private static void writeToLog(@NonNull final Context context,
                                   @NonNull final String tag,
                                   @NonNull final String type,
                                   @NonNull final String message,
                                   @Nullable final Throwable e) {
        // do not write to the file if we're running in a JUnit test.
        if (isJUnitTest()) {
            return;
        }

        String exMsg;
        if (e != null) {
            exMsg = '|' + e.getLocalizedMessage() + '\n' + getStackTraceString(e);
        } else {
            exMsg = "";
        }
        String fullMessage = DATE_FORMAT.format(new Date())
                             + '|' + tag + '|' + type + '|' + message + exMsg;


        File logFile = AppDir.Log.getFile(context, ERROR_LOG_FILE);
        //noinspection ImplicitDefaultCharsetUsage
        try (FileWriter fw = new FileWriter(logFile, true);
             Writer out = new BufferedWriter(fw)) {
            out.write(fullMessage);
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignored) {
            // do nothing - we can't log an error in the logger
        }
    }

    /**
     * Use instead of {@link Log#getStackTraceString(Throwable)} so we can use it in unit tests.
     * <p>
     * Handy function to get a loggable stack trace from a Throwable
     *
     * @param e An exception to log
     */
    private static String getStackTraceString(@Nullable final Throwable e) {
        if (e == null) {
            return "";
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /**
     * DEBUG only.
     * Dump an InputStream to the console.
     */
    @SuppressLint("LogConditional")
    @SuppressWarnings("unused")
    public static void dump(@NonNull final String tag,
                            @NonNull final Object object,
                            @NonNull final InputStream inputStream) {
        try {
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int result = bis.read();
            while (result != -1) {
                buf.write((byte) result);
                result = bis.read();
            }
            d(tag, buf.toString("UTF-8"));
        } catch (@NonNull final IOException e) {
            d(tag, "dumping failed: ", e);
        }
    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private static void debugArguments(@NonNull final String tag,
                                       @NonNull final Object fragmentOrActivity,
                                       @SuppressWarnings("SameParameterValue")
                                       @NonNull final String methodName) {
        if (fragmentOrActivity instanceof Activity) {
            Bundle extras = ((Activity) fragmentOrActivity).getIntent().getExtras();
            if (extras != null) {
                d(tag, methodName + "|extras=" + extras);
                if (extras.containsKey(UniqueId.BKEY_BOOK_DATA)) {
                    d(tag, methodName + "|extras=" + extras.getBundle(UniqueId.BKEY_BOOK_DATA));
                }
            }
        } else if (fragmentOrActivity instanceof Fragment) {
            Bundle args = ((Fragment) fragmentOrActivity).getArguments();
            if (args != null) {
                d(tag, methodName + "|args=" + args);
                if (args.containsKey(UniqueId.BKEY_BOOK_DATA)) {
                    d(tag, methodName + "|args=" + args.getBundle(UniqueId.BKEY_BOOK_DATA));
                }
            }
        }
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
        StringBuilder sb = new StringBuilder();
        for (String k : bundle.keySet()) {
            sb.append(k).append("->");
            Object o = bundle.get(k);
            if (o != null) {
                sb.append(o.toString().trim());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Cycle the log each time the app is started; preserve previous if non-empty.
     *
     * @param context Current context
     */
    public static void cycleLogs(@NonNull final Context context) {
        try {
            File logFile = AppDir.Log.getFile(context, ERROR_LOG_FILE);
            if (logFile.exists() && logFile.length() > 0) {
                File backup = new File(logFile.getPath() + ".bak");
                FileUtils.copyWithBackup(logFile, backup, LOGFILE_COPIES);
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
            // ignore
        }
    }

    /**
     * Dump all information from an onActivityResult method.
     */
    public static void enterOnActivityResult(@NonNull final String tag,
                                             final int requestCode,
                                             final int resultCode,
                                             @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            d(tag, "ENTER|onActivityResult"
                   + "|requestCode=" + requestCode
                   + "|resultCode=" + resultCode
                   + "|data=" + data);
        }
    }

    public static void d(@NonNull final String tag,
                         @NonNull final String msg) {
        if (BuildConfig.DEBUG /* always */) {
            if (isJUnitTest()) {
                System.out.println("isJUnitTest|DEBUG|" + tag + "|" + msg);
            } else {
                Log.d(tag, msg);
            }
        }
    }

    public static void d(@NonNull final String tag,
                         @NonNull final String msg,
                         @Nullable final Throwable e) {
        if (BuildConfig.DEBUG /* always */) {
            if (isJUnitTest()) {
                System.out.println("isJUnitTest|DEBUG|" + tag + "|" + msg
                                   + "\n" + getStackTraceString(e));
            } else {
                Log.d(tag, msg, e);
            }
        }
    }

    public static void e(@NonNull final String tag,
                         @NonNull final String msg,
                         @Nullable final Throwable e) {
        if (BuildConfig.DEBUG /* always */) {
            if (isJUnitTest()) {
                System.out.println("isJUnitTest|ERROR|" + tag + "|" + msg
                                   + "\n" + getStackTraceString(e));
            } else {
                Log.e(tag, msg, e);
            }
        }
    }

    public static void w(@NonNull final String tag,
                         @NonNull final String msg) {
        if (BuildConfig.DEBUG /* always */) {
            if (isJUnitTest()) {
                System.out.println("isJUnitTest|WARN|" + tag + "|" + msg);
            } else {
                Log.w(tag, msg);
            }
        }
    }

    public static void w(@NonNull final String tag,
                         @NonNull final String msg,
                         @Nullable final Throwable e) {
        if (BuildConfig.DEBUG /* always */) {
            if (isJUnitTest()) {
                System.out.println("isJUnitTest|WARN|" + tag + "|" + msg
                                   + "\n" + getStackTraceString(e));
            } else {
                Log.w(tag, msg, e);
            }
        }
    }

    /**
     * DEBUG only.
     *
     * @return {@code true} if the current run is a JUnit test.
     */
    public static boolean isJUnitTest() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }
}
