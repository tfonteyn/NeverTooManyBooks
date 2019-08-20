/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.Manifest;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.StringRes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.ProtocolException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Class to wrap common storage related functions.
 * <p>
 * "External Storage" == what Android considers non-application-private storage.
 * i.e the user has access to it.
 * ('external' refers to old android phones where Shared Storage *always* was on an sdcard)
 * Also referred to as "Shared Storage" because all apps have access to it.
 * <p>
 * For the sake of clarity (confusion?) we'll call it "Shared Storage" only.
 * <p>
 * FIXME: implement the sample code for 'watching'  Environment.getExternalStorageDirectory()
 * and/or isExternalStorageRemovable()
 */
public final class StorageUtils {

    /** error result code for {@link #getSharedStorageFreeSpace}. */
    public static final int ERROR_CANNOT_STAT = -2;
    /** buffer size for file copy operations. */
    private static final int FILE_COPY_BUFFER_SIZE = 32768;

    /**
     * Our root directory to be created on the 'external storage' aka Shared Storage.
     * IMPORTANT: this must stay in sync with res/xml/provider_paths.xml
     */
    private static final String EXT_ROOT_DIR = "NeverTooManyBooks";
    //private static final String EXT_ROOT_DIR = "bookCatalogue";

    /**
     * Filenames *STARTING* with this prefix are considered purgeable.
     */
    private static final String[] PURGEABLE_FILE_PREFIXES = new String[]{
            "DbUpgrade", "DbExport", Logger.ERROR_LOG_FILE, "tmp"};
    /**
     * Loop all mount points for our directory and collect a list of all CSV files.
     */
    private static final Pattern MOUNT_POINT_PATH = Pattern.compile("^\\s*[^\\s]+\\s+([^\\s]+)");

    private StorageUtils() {
    }

    /**
     * Don't log anything, we might not have a log file.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean createDir(@NonNull final String name)
            throws SecurityException {
        final File dir = new File(name);
        return dir.isDirectory() || dir.mkdirs();
    }

    /**
     * Make sure the Shared Storage directory exists.
     * Logs failures only if we we're capable of creating a log directory!
     * Abort if we don't get that far.
     * <p>
     * Only called from StartupActivity, after permissions have been granted.
     */
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    @StringRes
    public static int initSharedDirectories()
            throws SecurityException {

        int msgId = StorageUtils.getMediaStateMessageId();
        // tell user if needed.
        if (msgId != 0) {
            return msgId;
        }

        // need root first (duh)
        if (!createDir(getSharedStoragePath())) {
            return R.string.error_storage_not_writable;
        }
        // then log dir!
        if (!createDir(getLogStoragePath())) {
            return R.string.error_storage_not_writable;
        }

        // from here on, we have a log file and if we could create the log directory,
        // this one should never fail... flw
        if (!createDir(getCoverStoragePath())) {
            Logger.warn(StorageUtils.class, "Failed to create covers directory="
                                            + getCoverStoragePath());
        }

        // A .nomedia file will be created which will stop the thumbnails showing up
        // in the gallery (thanks Brandon)
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(getSharedStoragePath() + File.separator
                     + MediaStore.MEDIA_IGNORE_FILENAME).createNewFile();
            //noinspection ResultOfMethodCallIgnored
            new File(getCoverStoragePath() + File.separator
                     + MediaStore.MEDIA_IGNORE_FILENAME).createNewFile();

            return 0;
        } catch (@NonNull final IOException e) {
            Logger.error(StorageUtils.class, e, "Failed to create .nomedia files");
            return R.string.error_storage_not_writable;
        }
    }

    /**
     * Root external storage aka Shared Storage.
     *
     * @return the Shared Storage root Directory object
     */
    private static String getSharedStoragePath() {
        return Environment.getExternalStorageDirectory() + File.separator + EXT_ROOT_DIR;
    }

    /**
     * @return the Shared Storage root Directory object
     */
    public static File getSharedStorage() {
        return new File(getSharedStoragePath());
    }

    private static String getCoverStoragePath() {
        return getSharedStoragePath() + File.separator + "covers";
    }

    public static File getCoverStorage() {
        return new File(getCoverStoragePath());
    }

    public static String getLogStoragePath() {
        return getSharedStoragePath() + File.separator + "log";
    }

    public static File getLogStorage() {
        return new File(getLogStoragePath());
    }

    private static File getTempStorage() {
        return App.getAppContext().getExternalCacheDir();
    }

    /**
     * return a general purpose File, located in the Shared Storage path (or a sub directory).
     * Don't use this for standard cover management.
     *
     * @param fileName the relative filename (including sub dirs) to the Shared Storage path
     *
     * @return the file
     */
    public static File getFile(@NonNull final String fileName) {
        return new File(getSharedStoragePath() + File.separator + fileName);
    }

    /**
     * @return the 'standard' temp file name for new books.
     * Is also used as the standard file name for images downloaded from the internet.
     */
    public static File getTempCoverFile() {
        return new File(getTempStorage() + File.separator + "tmp.jpg");
    }

    /**
     * @param name   for the file.
     * @param suffix optional suffix
     *
     * @return a temp cover file spec.
     */
    public static File getTempCoverFile(@NonNull final String name,
                                        @Nullable final String suffix) {
        return new File(getTempStorage() + File.separator
                        + "tmp" + name + (suffix != null ? suffix : "")
                        + ".jpg");
    }

    /**
     * Delete the 'standard' temp file.
     */
    public static void deleteTempCoverFile() {
        deleteFile(getTempCoverFile());
    }

    /**
     * @param filename a generic filename.
     *
     * @return a File with that name, located in the covers directory
     */
    @NonNull
    public static File getRawCoverFile(@NonNull final String filename) {
        return new File(getCoverStoragePath() + File.separator + filename);
    }

    /**
     * return the cover for the given uuid.
     *
     * @param uuid of the book, must be valid.
     *
     * @return The File object for existing files, or a new (jpg) placeholder.
     */
    @NonNull
    public static File getCoverFile(@NonNull final String uuid) {
        final File jpg = new File(getCoverStoragePath() + File.separator + uuid + ".jpg");
        if (jpg.exists()) {
            return jpg;
        }
        // could be a png
        final File png = new File(getCoverStoragePath() + File.separator + uuid + ".png");
        if (png.exists()) {
            return png;
        }

        // we need a new file, return a placeholder
        return jpg;
    }

    /**
     * Count size + (optional) Cleanup any purgeable files.
     *
     * @param reallyDelete if {@code true}, delete files, if {@code false} only count bytes
     *
     * @return the total size in bytes of purgeable/purged files.
     */
    public static long purgeFiles(final boolean reallyDelete) {
        long totalSize = 0;
        try {
            totalSize += purgeDir(getLogStorage(), reallyDelete);
            totalSize += purgeDir(getCoverStorage(), reallyDelete);
            totalSize += purgeDir(getSharedStorage(), reallyDelete);
            totalSize += purgeDir(getTempStorage(), reallyDelete);

        } catch (@NonNull final SecurityException e) {
            Logger.error(StorageUtils.class, e);
        }
        return totalSize;
    }

    private static long purgeDir(@NonNull final File dir,
                                 final boolean reallyDelete) {
        long totalSize = 0;
        for (String name : dir.list()) {
            totalSize += purgeFile(name, reallyDelete);
        }
        return totalSize;
    }

    private static long purgeFile(@NonNull final String name,
                                  final boolean reallyDelete) {
        long totalSize = 0;
        for (String prefix : PURGEABLE_FILE_PREFIXES) {
            if (name.startsWith(prefix)) {
                final File file = getFile(name);
                if (file.isFile()) {
                    totalSize += file.length();
                    if (reallyDelete) {
                        deleteFile(file);
                    }
                }
            }
        }
        return totalSize;
    }

    /**
     * Delete *everything* in the temp file directory.
     */
    public static void purgeTempStorage() {
        try {
            File dir = getTempStorage();
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteFile(file);
                    }
                }
            }
        } catch (@NonNull final SecurityException e) {
            Logger.error(StorageUtils.class, e);
        }
    }

    /**
     * Find all possible CSV files in all accessible filesystems which
     * have a {@link #EXT_ROOT_DIR} directory.
     * <p>
     * ENHANCE: Allow for other files? Backups? || fl.endsWith(".csv.bak"));
     *
     * @return list of csv files
     */
    @NonNull
    public static List<File> findCsvFiles() {
        FilenameFilter csvFilter = (dir, name) ->
                                           name.toLowerCase(App.getSystemLocale()).endsWith(".csv");
        return findFiles(csvFilter);
    }

    /**
     * Find all files in all accessible filesystems which match the given filter
     * and have a {@link #EXT_ROOT_DIR} directory.
     *
     * @return list of csv files
     */
    @NonNull
    private static List<File> findFiles(@NonNull final FilenameFilter filenameFilter) {
        @SuppressWarnings("unused")
        StringBuilder debugInfo;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            debugInfo = new StringBuilder("Getting mounted file systems\n");
        }

        // Loop all mounted file systems
        final List<File> dirs = new ArrayList<>();

        //noinspection ImplicitDefaultCharsetUsage
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/mounts")), 1024)) {

            String line;
            while ((line = in.readLine()) != null) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                    debugInfo.append("   checking ").append(line).append('\n');
                }
                final Matcher matcher = MOUNT_POINT_PATH.matcher(line);
                // Get the mount point
                if (matcher.find()) {
                    // See if it has our directory
                    final File dir =
                            new File(matcher.group(1) + File.separator + EXT_ROOT_DIR);
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                        debugInfo.append("       matched ")
                                 .append(dir.getAbsolutePath())
                                 .append('\n');
                    }
                    dirs.add(dir);
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                        debugInfo.append("       NO match\n");
                    }
                }
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
            Logger.error(StorageUtils.class, e, "Failed to open/scan/read /proc/mounts");
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            debugInfo.append("Found ").append(dirs.size()).append(" directories\n");
        }

        // Sometimes (Android 6?) the /proc/mount search seems to fail,
        // so we revert to environment vars
        try {
            final String loc1 = System.getenv("EXTERNAL_STORAGE");
            if (loc1 != null) {
                final File dir = new File(loc1 + File.separator + EXT_ROOT_DIR);
                dirs.add(dir);
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                    debugInfo.append("EXTERNAL_STORAGE added ").append(
                            dir.getAbsolutePath()).append('\n');
                }
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                    debugInfo.append("EXTERNAL_STORAGE was null\n");
                }
            }

            final String loc2 = System.getenv("SECONDARY_STORAGE");
            if (loc2 != null && !loc2.equals(loc1)) {
                final File dir = new File(loc2 + File.separator + EXT_ROOT_DIR);
                dirs.add(dir);
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                    debugInfo.append("SECONDARY_STORAGE added ").append(
                            dir.getAbsolutePath()).append('\n');
                }
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                    debugInfo.append("SECONDARY_STORAGE ignored: ").append(loc2).append('\n');
                }
            }
        } catch (@NonNull final RuntimeException e) {
            Logger.error(StorageUtils.class, e,
                         "Failed to get external storage from environment variables");
        }

        final Set<String> paths = new HashSet<>();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            debugInfo.append("Looking for files in directories\n");
        }

        final List<File> filesFound = new ArrayList<>();
        for (File dir : dirs) {
            try {
                if (dir.exists()) {
                    final File[] files = dir.listFiles(filenameFilter);
                    if (files != null) {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                            debugInfo.append("    found ")
                                     .append(files.length)
                                     .append(" in ").append(dir.getAbsolutePath()).append('\n');
                        }

                        for (File file : files) {
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                                debugInfo.append("Found: ").append(file.getAbsolutePath());
                            }
                            final String cp = file.getCanonicalPath();
                            if (paths.contains(cp)) {
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                                    debugInfo.append("        already present as ")
                                             .append(cp).append('\n');
                                }
                            } else {
                                filesFound.add(file);
                                paths.add(cp);
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                                    debugInfo.append("        added as ").append(cp).append('\n');
                                }
                            }
                        }
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                            debugInfo.append("    null returned by listFiles() in ")
                                     .append(dir.getAbsolutePath()).append('\n');
                        }
                    }
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                        debugInfo.append("    ").append(dir.getAbsolutePath())
                                 .append(" does not exist\n");
                    }
                }
            } catch (@NonNull final IOException e) {
                Logger.error(StorageUtils.class, e,
                             "Failed to read directory " + dir.getAbsolutePath());
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            Logger.debug(StorageUtils.class, "findFiles", debugInfo);
        }

        // Sort descending based on modified date
        Collections.sort(filesFound, new FileDateComparator(-1));
        return filesFound;
    }

    /**
     * Given a InputStream, write it to a file.
     * We first write to a temporary file, so an existing 'out' file is not destroyed
     * if the stream somehow fails.
     *
     * @param is  InputStream to read
     * @param out File to write to
     *
     * @return number of bytes read; -1 on failure but could be 0 on if the stream was empty.
     */
    public static int saveInputStreamToFile(@Nullable final InputStream is,
                                            @NonNull final File out) {
        Objects.requireNonNull(is);

        File tmpFile = null;
        try {
            // Use the getTempStorage() location, which is on the same physical storage
            // as the destination (SharedStorage)
            tmpFile = File.createTempFile("tmp_is_", ".tmp", getTempStorage());
            OutputStream tempFos = new FileOutputStream(tmpFile);
            // Copy from input to temp file
            byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];
            int total = 0;
            int len;
            while ((len = is.read(buffer)) >= 0) {
                tempFos.write(buffer, 0, len);
                total += len;
            }
            tempFos.close();
            // All OK, so rename to real output file
            renameFile(tmpFile, out);
            return total;

        } catch (@NonNull final ProtocolException e) {
            // typically happens when the server hangs up: unexpected end of stream
            if (BuildConfig.DEBUG /* always */) {
                Logger.debug(StorageUtils.class, "saveInputStreamToFile",
                             e.getLocalizedMessage());
            }
        } catch (@NonNull final IOException e) {
            Logger.error(StorageUtils.class, e);
        } finally {
            deleteFile(tmpFile);
        }
        return -1;
    }

    /**
     * just to avoid boilerplate coding.
     *
     * @param file to delete
     */
    public static void deleteFile(@Nullable final File file) {
        if (file != null && file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                    Logger.debug(StorageUtils.class, "deleteFile",
                                 "file=" + file.getAbsolutePath());
                }
            } catch (@NonNull final RuntimeException e) {
                Logger.error(StorageUtils.class, e);
            }
        }
    }

    /**
     * ENHANCE: make suitable for multiple filesystems using {@link #copyFile(File, File)}
     * from the Android docs {@link File#renameTo(File)}: Both paths be on the same mount point.
     *
     * @return {@code true} if the rename worked, this is really a ".exists()" call.
     * and not relying on the OS renameTo call.
     */
    public static boolean renameFile(@NonNull final File src,
                                     @NonNull final File dst) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            Logger.debug(StorageUtils.class, "renameFile",
                         "src=" + src.getAbsolutePath(),
                         "dst=" + dst.getAbsolutePath());
        }
        if (src.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                src.renameTo(dst);
            } catch (@NonNull final RuntimeException e) {
                Logger.error(StorageUtils.class, e);
            }
        }
        return dst.exists();
    }

    /**
     * Rename the source file to the destFilename; keeping 'copies' of the old file.
     *
     * @param source       file to rename
     * @param destFilename name to use
     * @param copies       #copies of the previous one to keep
     */
    public static void renameFileWithBackup(@NonNull final File source,
                                            @NonNull final String destFilename,
                                            final int copies) {
        // remove to oldest copy
        File previous = getFile(destFilename + "." + copies);
        deleteFile(previous);

        // now bump each copy up one index.
        for (int i = copies - 1; i > 0; i--) {
            File current = getFile(destFilename + "." + i);
            renameFile(current, previous);
            previous = current;
        }

        // Give the previous file an index.
        File destination = getFile(destFilename);
        renameFile(destination, previous);
        // and write the new copy.
        renameFile(source, destination);
    }

    /**
     * Create a copy of the databases into the Shared Storage location.
     */
    public static void exportDatabaseFiles(@NonNull final Context context) {
        exportFile(DBHelper.getDatabasePath(context), "DbExport.db");
        exportFile(CoversDAO.CoversDbHelper.getDatabasePath(context), "DbExport-covers.db");
    }

    /**
     * @param source          file to export
     * @param destinationPath destination file name, will be stored in our directory
     *                        in Shared Storage
     */
    public static void exportFile(@NonNull final File source,
                                  @NonNull final String destinationPath) {
        try {
            // rename the previously copied file
            renameFile(getFile(destinationPath),
                       getFile(destinationPath + ".bak"));
            // and create a new copy. Note that the source is a fully qualified name,
            // so NOT using getFile()
            copyFile(source, getFile(destinationPath));

        } catch (@NonNull final IOException e) {
            Logger.error(StorageUtils.class, e);
        }
    }

    /**
     * @throws IOException on failure
     */
    public static void copyFile(@NonNull final File source,
                                @NonNull final File destination)
            throws IOException {
        try (InputStream is = new FileInputStream(source)) {
            copyFile(is, FILE_COPY_BUFFER_SIZE, destination);
        } catch (@NonNull final FileNotFoundException ignore) {
        }
    }

    /**
     * @param is          InputStream, will NOT be closed here. Close it yourself !
     * @param bufferSize  the read buffer
     * @param destination destination file, will be properly closed.
     *
     * @throws IOException on failure
     */
    public static void copyFile(@NonNull final InputStream is,
                                final int bufferSize,
                                @NonNull final File destination)
            throws IOException {
        try (OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[bufferSize];
            int nRead;
            while ((nRead = is.read(buffer)) > 0) {
                out.write(buffer, 0, nRead);
            }
            out.flush();
        }
        // let any IOException escape for the caller to deal with
        //IMPORTANT: DO **NOT** CLOSE THE INPUT STREAM. IT WILL BREAK 'RESTORE BACKUP'
    }

    /**
     * Channels are FAST... TODO: replace old method with this one.
     * but needs testing, never used it on Android myself
     *
     * @throws IOException on failure
     */
    @SuppressWarnings("unused")
    private static void copyFile2(@NonNull final File source,
                                  @NonNull final File destination)
            throws IOException {
        FileInputStream fis = new FileInputStream(source);
        FileOutputStream fos = new FileOutputStream(destination);
        FileChannel inChannel = fis.getChannel();
        FileChannel outChannel = fos.getChannel();

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null) {
                inChannel.close();
            }
            outChannel.close();
            fis.close();
            fos.close();
        }
    }

    /**
     * @return Space in bytes free on Shared Storage,
     * or {@link #ERROR_CANNOT_STAT} on error accessing it
     */
    public static long getSharedStorageFreeSpace() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (@NonNull final IllegalArgumentException e) {
            Logger.error(StorageUtils.class, e);
            return ERROR_CANNOT_STAT;
        }
    }

    /**
     * Check the current state of the primary shared/external storage media.
     *
     * @return a string resource id matching the state; 0 for no issue found.
     */
    @StringRes
    public static int getMediaStateMessageId() {
        /*
         * Returns the current state of the primary Shared Storage media.
         *
         * @see #getExternalStorageDirectory()
         * @return one of {@link #MEDIA_UNKNOWN}, {@link #MEDIA_REMOVED},
         *         {@link #MEDIA_UNMOUNTED}, {@link #MEDIA_CHECKING},
         *         {@link #MEDIA_NOFS}, {@link #MEDIA_MOUNTED},
         *         {@link #MEDIA_MOUNTED_READ_ONLY}, {@link #MEDIA_SHARED},
         *         {@link #MEDIA_BAD_REMOVAL}, or {@link #MEDIA_UNMOUNTABLE}.
         */
        //noinspection SwitchStatementWithTooFewBranches
        switch (Environment.getExternalStorageState()) {
            case Environment.MEDIA_MOUNTED:
                // all ok
                return 0;
            // should we check on other states ? do we care ?

            default:
                return R.string.error_storage_not_accessible;
        }
    }

    /**
     * Format a number of bytes in a human readable form.
     * <p>
     * 2019-03-16: decimalize as per IEC: <a href="https://en.wikipedia.org/wiki/File_size">
     * https://en.wikipedia.org/wiki/File_size</a>
     *
     * @param context Current context
     * @param bytes   to format
     *
     * @return formatted # bytes
     */
    @NonNull
    public static String formatFileSize(@NonNull final Context context,
                                        final float bytes) {
        if (bytes < 3_000) {
            // Show 'bytes' if < 3k
            return context.getString(R.string.bytes, bytes);
        } else if (bytes < 250_000) {
            // Show Kb if less than 250kB
            return context.getString(R.string.kilobytes, bytes / 1_000);
        } else {
            // Show MB otherwise...
            return context.getString(R.string.megabytes, bytes / 1_000_000);
        }
    }

    /**
     * Compare two files based on date. Used for sorting file list by date.
     *
     * <a href="https://docs.oracle.com/javase/10/docs/api/java/util/Comparator.html">
     * https://docs.oracle.com/javase/10/docs/api/java/util/Comparator.html</a>
     * Note: It is generally a good idea for comparators to also implement java.io.Serializable
     */
    static class FileDateComparator
            implements Comparator<File>, Serializable {

        private static final long serialVersionUID = -1173177810355471106L;
        /** Ascending is >= 0, Descending is < 0. */
        private final int mDirection;

        /**
         * Constructor.
         */
        FileDateComparator(@SuppressWarnings("SameParameterValue") final int direction) {
            mDirection = direction < 0 ? -1 : 1;
        }

        /**
         * Compare based on modified date.
         */
        @Override
        public int compare(@NonNull final File o1,
                           @NonNull final File o2) {
            final long l = o1.lastModified();
            final long r = o2.lastModified();
            if (l < r) {
                return -mDirection;
            } else if (l > r) {
                return mDirection;
            } else {
                return 0;
            }
        }
    }
}
