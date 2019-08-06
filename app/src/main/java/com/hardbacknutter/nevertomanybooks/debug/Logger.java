/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.debug;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

/**
 * ALWAYS call methods like this:
 * * if (BuildConfig.DEBUG) {
 * *     Logger.blah(...);
 * * }
 * <p>
 * The second check on DEBUG build in this class is only to catch the lack-of in other places.
 */
public final class Logger {

    /** Widely used DEBUG error message. */
    public static final String WEAK_REFERENCE_TO_LISTENER_WAS_DEAD = "Listener was dead";

    /** Prefix for logfile entries. Not used on the console. */
    private static final String ERROR = "ERROR";
    private static final String WARN = "WARN";
    private static final String INFO = "INFO";

    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", App.getSystemLocale());

    private Logger() {
    }

    public static void error(@NonNull final Object tag,
                             @NonNull final Throwable e,
                             @Nullable final Object... params) {
        String msg;
        if (params != null) {
            msg = '|' + concat(params);
        } else {
            msg = "";
        }
        writeToLog(ERROR, msg, e);
        if (BuildConfig.DEBUG /* always */) {
            Log.e(tag(tag), Tracker.State.Running.toString() + '|' + msg, e);
        }
    }

    /**
     * WARN message. Send to the logfile (always) and the console (when in DEBUG mode).
     * <p>
     * Use sparingly, writing to the log is expensive.
     * <p>
     * Use when an error or unusual result should be noted, but will not affect the flow of the app.
     * No stacktrace!
     */
    public static void warn(@NonNull final Object object,
                            @NonNull final String methodName,
                            @NonNull final Object... params) {
        String msg = methodName + '|' + concat(params);
        writeToLog(WARN, msg, null);
        if (BuildConfig.DEBUG /* always */) {
            Log.w(tag(object), Tracker.State.Running.toString() + '|' + msg);
        }
    }

    /**
     * WARN message with a generated StackTrace.
     * Send to the logfile (always) and the console (when in DEBUG mode).
     * <p>
     * Use sparingly, writing to the log is expensive.
     * <p>
     * Use when an error or unusual result should be noted, but will not affect the flow of the app.
     */
    public static void warnWithStackTrace(@NonNull final Object object,
                                          @NonNull final Object... params) {
        Throwable e = new Throwable();
        String msg = concat(params);
        writeToLog(WARN, msg, e);
        if (BuildConfig.DEBUG /* always */) {
            Log.w(tag(object), Tracker.State.Running.toString() + '|' + msg, e);
        }
    }

    /**
     * INFO message. Send to the logfile (always) and the console (when in DEBUG mode).
     * <p>
     * Use very sparingly, writing to the log is expensive.
     */
    public static void info(@NonNull final Object object,
                            @NonNull final String methodName,
                            @NonNull final Object... params) {
        String msg = methodName + '|' + concat(params);
        writeToLog(INFO, msg, null);
        if (BuildConfig.DEBUG /* always */) {
            Log.i(tag(object), Tracker.State.Running.toString() + '|' + msg);
        }
    }

    public static void debugEnter(@NonNull final Object object,
                                  @NonNull final String methodName,
                                  @NonNull final Object... params) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag(object),
                  Tracker.State.Enter.toString() + '|' + methodName + '|' + concat(params));
        }
    }

    public static void debugExit(@NonNull final Object object,
                                 @NonNull final String methodName,
                                 @NonNull final Object... params) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag(object),
                  Tracker.State.Exit.toString() + '|' + methodName + '|' + concat(params));
        }
    }

    public static void debug(@NonNull final Object object,
                             @NonNull final String methodName,
                             @NonNull final Object... params) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag(object),
                  Tracker.State.Running.toString() + '|' + methodName + '|' + concat(params));
        }
    }

    public static void debugWithStackTrace(@NonNull final Object object,
                                           @NonNull final String methodName,
                                           @NonNull final Object... params) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag(object),
                  Tracker.State.Running.toString() + '|' + methodName + '|' + concat(params),
                  new Throwable());
        }
    }

    /**
     * Tracking debug.
     */
    public static void debugWithStackTrace(@NonNull final Object object,
                                           @NonNull final Throwable e,
                                           @NonNull final Object... params) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag(object),
                  Tracker.State.Running.toString() + '|' + concat(params), e);
        }
    }

    private static String tag(@NonNull final Object object) {
        return object.getClass().isAnonymousClass() ? "AnonymousClass"
                                                    : object.getClass().getCanonicalName();
    }

    private static String concat(@NonNull final Object[] params) {
        StringBuilder message = new StringBuilder();
        for (Object parameter : params) {
            message.append(parameter.toString()).append('|');
        }
        message.append('.');
        return message.toString();
    }

    private static String buildHeaderMessage(@NonNull final Throwable e,
                                             @Nullable final String message) {
        return "An Exception/Error Occurred "
               + (message != null ? message + '\n' : "")
               + e.getLocalizedMessage() + '\n'
               + "In Phone " + Build.MODEL + " (" + Build.VERSION.SDK_INT + ")\n"
               + Log.getStackTraceString(e);
    }

    /**
     * This is an expensive call... file open+close... BOOOO!
     *
     * @param type    prefix tag
     * @param message to write
     * @param e       optional Throwable
     */
    private static void writeToLog(@NonNull final String type,
                                   @NonNull final String message,
                                   @Nullable final Throwable e) {
        //noinspection ImplicitDefaultCharsetUsage
        try (FileWriter fw = new FileWriter(StorageUtils.getErrorLog(), true);
             BufferedWriter out = new BufferedWriter(fw)) {
            String exMsg;
            if (e != null) {
                exMsg = '|' + e.getLocalizedMessage() + '\n' + Log.getStackTraceString(e);
            } else {
                exMsg = "";
            }

            out.write(DATE_FORMAT.format(new Date()) + '|' + type + '|' + message + exMsg);
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception ignored) {
            // do nothing - we can't log an error in the logger (and we don't want to CF the app).
        }
    }

    /**
     * Clear the log each time the app is started; preserve previous if non-empty.
     */
    public static void clearLog() {
        try {
            File logFile = new File(StorageUtils.getErrorLog());
            if (logFile.exists() && logFile.length() > 0) {
                File backup = new File(StorageUtils.getErrorLog() + ".bak");
                StorageUtils.renameFile(logFile, backup);
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
            // Ignore all backup failure...
        }
    }

    static void debugArguments(@NonNull final Object a,
                               @NonNull final String methodName) {
        if (a instanceof Activity) {
            Bundle extras = ((Activity) a).getIntent().getExtras();
            if (extras != null) {
                debug(a, methodName, "extras=" + extras);
                if (extras.containsKey(UniqueId.BKEY_BOOK_DATA)) {
                    debug(a, methodName, "extras=" + extras.getBundle(UniqueId.BKEY_BOOK_DATA));
                }
            }
        } else if (a instanceof Fragment) {
            Bundle args = ((Fragment) a).getArguments();
            if (args != null) {
                Logger.debug(a, methodName, "args=" + args);
                if (args.containsKey(UniqueId.BKEY_BOOK_DATA)) {
                    Logger.debug(a, methodName, "args=" + args.getBundle(UniqueId.BKEY_BOOK_DATA));
                }
            }
        }
    }

    /**
     * DEBUG
     * <p>
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
}
