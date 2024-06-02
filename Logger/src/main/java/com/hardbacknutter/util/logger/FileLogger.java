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

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

@SuppressWarnings({"WeakerAccess", "Unused"})
public class FileLogger
        implements Logger {

    private static final String TAG = "FileLogger";
    /** Keep the last 3 log files. */
    private static final int DEFAULT_LOGFILE_COPIES = 3;

    /** Prefix for logfile entries. Not used on the console. */
    private static final String ERROR = "ERROR";
    private static final String WARN = "WARN";
    private static final String DEBUG = "DEBUG";
    private static final String ERROR_SOURCE_MISSING = "Source does not exist: ";
    private static final String ERROR_FAILED_TO_RENAME = "Failed to rename: ";
    @NonNull
    private final File logDir;
    @NonNull
    private final File backupDir;
    @NonNull
    private final String logFilename;

    private int copies = DEFAULT_LOGFILE_COPIES;

    /**
     * Constructor.
     *
     * @param logDir   the directory where logs will be written
     * @param filename the base name for the logfile
     */
    public FileLogger(@NonNull final File logDir,
                      @NonNull final String filename) {
        this(logDir, filename, logDir);
    }

    /**
     * Constructor.
     *
     * @param logDir    the directory where logs will be written
     * @param filename  the base name for the logfile
     * @param backupDir Where to put the backup files.
     *                  Must be on the same volume as the file(s).
     */
    public FileLogger(@NonNull final File logDir,
                      @NonNull final String filename,
                      @NonNull final File backupDir) {
        this.logDir = logDir;
        this.logFilename = filename;
        this.backupDir = backupDir;
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
     * Set the number of copies to keep.
     * <p>
     * Defaults to {@link #DEFAULT_LOGFILE_COPIES}.
     *
     * @param copies Number of copies to keep.
     */
    public void setCopies(final int copies) {
        this.copies = copies;
    }

    @NonNull
    public String getErrorLog()
            throws IOException {
        return String.join("\n", Files.readAllLines(
                Paths.get(logDir.getAbsolutePath(), logFilename), StandardCharsets.UTF_8));
    }

    @NonNull
    public File getLogDir() {
        return logDir;
    }

    public void cycleLogs() {
        //noinspection CheckStyle,OverlyBroadCatchBlock
        try {
            final File logFile = new File(logDir, logFilename);
            if (logFile.exists()) {
                if (logFile.length() > 0) {
                    final File backup = new File(logFile.getPath() + ".bak");
                    // Move/rename the previous/original file
                    makeBackup(backup);
                    // and write the new copy.
                    try (FileInputStream fis = new FileInputStream(logFile);
                         FileOutputStream fos = new FileOutputStream(backup);
                         FileChannel inChannel = fis.getChannel();
                         FileChannel outChannel = fos.getChannel()) {
                        inChannel.transferTo(0, inChannel.size(), outChannel);
                    }
                }
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }
        } catch (@NonNull final Exception ignore) {
            // do nothing - we can't log an error in the logger
        }
    }

    /**
     * Rename the given "file" to "file.1", keeping {@code copies} of the old file,
     * i.e. the number of the copy is added as a SUFFIX to the name.
     * <p>
     * Upon success, the "file" is no longer available.
     * Any exception is ignored. The presence of "file" is not defined, and should be assume
     * to be no longer available.
     * <p>
     * <strong>Important:</strong> it's a 'rename', so single volume use only!
     *
     * @param file file to rename
     */
    private void makeBackup(@NonNull final File file) {

        final String backupFilePath = new File(backupDir, file.getName()).getPath();

        // remove the oldest copy (if there is one)
        File previous = new File(backupFilePath + "." + copies);
        //noinspection OverlyBroadCatchBlock
        try {
            if (previous.exists()) {
                //noinspection ResultOfMethodCallIgnored
                previous.delete();
            }

            // now bump each copy up one suffix.
            for (int i = copies - 1; i > 0; i--) {
                final File current = new File(backupFilePath + "." + i);
                if (current.exists()) {
                    rename(current, previous);
                }
                previous = current;
            }

            // Rename the current file giving it a suffix.
            if (file.exists()) {
                rename(file, previous);
            }
        } catch (@NonNull final Exception e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    /**
     * ENHANCE: make suitable for multiple filesystems.
     * Android docs {@link File#renameTo(File)}: Both paths be on the same mount point.
     *
     * @param source      File to rename
     * @param destination new name
     *
     * @throws FileNotFoundException if the source does not exist
     * @throws IOException           on generic/other IO failures
     */
    private void rename(@NonNull final File source,
                        @NonNull final File destination)
            throws IOException {

        //sanity check
        if (source.getAbsolutePath().equals(destination.getAbsolutePath())) {
            return;
        }

        if (!source.exists()) {
            throw new FileNotFoundException(ERROR_SOURCE_MISSING + source);
        }

        try {
            if (source.renameTo(destination)) {
                return;
            }
            throw new IOException(ERROR_FAILED_TO_RENAME + source + " TO " + destination);

        } catch (@NonNull final SecurityException e) {
            throw new IOException(ERROR_FAILED_TO_RENAME + source + " TO " + destination, e);
        }
    }

    @Override
    public void e(@NonNull final String tag,
                  @Nullable final Throwable e,
                  @Nullable final Object... params) {

        final String message = concat(params);

        writeToLog(tag, ERROR, message, e);

        if (BuildConfig.DEBUG /* always */) {
            Log.e(tag, message, e);
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
            final File logFile = new File(logDir, logFilename);
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
