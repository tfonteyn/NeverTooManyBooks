/*
 * @copyright 2012 Philip Warner
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
package com.eleybourn.bookcatalogue.utils;

import android.Manifest;
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
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
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

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CoversDAO;
import com.eleybourn.bookcatalogue.database.DBHelper;
import com.eleybourn.bookcatalogue.debug.Logger;

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
 * TODO: user messages talk about "SD Card"
 * FIXME: implement the sample code for 'watching'  Environment.getExternalStorageDirectory()
 * and/or isExternalStorageRemovable()
 *
 * @author Philip Warner
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
    private static final String DIRECTORY_NAME = "bookCatalogue2";
    //private static final String DIRECTORY_NAME = "bookCatalogue";

    /** root external storage aka Shared Storage. */
    private static final String SHARED_STORAGE_PATH =
            Environment.getExternalStorageDirectory() + File.separator + DIRECTORY_NAME;
    /**
     * permanent location for cover files.
     * For now hardcoded, but the intention is to allow user-defined.
     */
    private static final String COVER_FILE_PATH = SHARED_STORAGE_PATH + File.separator + "covers";
    /** permanent location for log files. */
    private static final String LOG_FILE_PATH = SHARED_STORAGE_PATH + File.separator + "log";
    /** serious errors are written to this file. */
    private static final String ERROR_LOG_FILE = "error.log";
    /**
     * Filenames *STARTING* with this prefix are considered purgeable.
     */
    private static final String[] PURGEABLE_FILE_PREFIXES = new String[]{
            "DbUpgrade", "DbExport", ERROR_LOG_FILE, "tmp"};
    /**
     * Loop all mount points for '/bookCatalogue' directory and collect a list of all CSV files.
     */
    private static final Pattern MOUNT_POINT_PATH = Pattern.compile("^\\s*[^\\s]+\\s+([^\\s]+)");

    private StorageUtils() {
    }

    public static String getErrorLog()
            throws SecurityException {
        return LOG_FILE_PATH + File.separator + ERROR_LOG_FILE;
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
        if (!createDir(SHARED_STORAGE_PATH)) {
            return R.string.error_storage_not_writable;
        }
        // then log dir!
        if (!createDir(LOG_FILE_PATH)) {
            return R.string.error_storage_not_writable;
        }

        // from here on, we have a log file and if we could create the log directory,
        // this one should never fail... flw
        if (!createDir(COVER_FILE_PATH)) {
            Logger.warn(StorageUtils.class, "Failed to create covers directory");
        }

        // A .nomedia file will be created which will stop the thumbnails showing up
        // in the gallery (thanks Brandon)
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(SHARED_STORAGE_PATH + File.separator
                             + MediaStore.MEDIA_IGNORE_FILENAME).createNewFile();
            //noinspection ResultOfMethodCallIgnored
            new File(COVER_FILE_PATH + File.separator
                             + MediaStore.MEDIA_IGNORE_FILENAME).createNewFile();

            return 0;
        } catch (IOException e) {
            Logger.error(StorageUtils.class, e, "Failed to create .nomedia files");
            return R.string.error_storage_not_writable;
        }
    }

    private static File getTemp() {
        return App.getAppContext().getExternalCacheDir();
    }

    public static File getCoverStorage() {
        return new File(COVER_FILE_PATH);
    }

    public static File getLogStorage() {
        return new File(LOG_FILE_PATH);
    }

    /**
     * @return the Shared Storage root Directory object
     */
    public static File getSharedStorage() {
        return new File(SHARED_STORAGE_PATH);
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
        return new File(SHARED_STORAGE_PATH + File.separator + fileName);
    }

    /**
     * @return the 'standard' temp file name for new books.
     * Is also used as the standard file name for images downloaded from the internet.
     */
    public static File getTempCoverFile() {
        return new File(getTemp() + File.separator + "tmp.jpg");
    }

    /**
     * @return a temp cover file 'name'.
     */
    public static File getTempCoverFile(@NonNull final String name) {
        return new File(getTemp() + File.separator + "tmp" + name + ".jpg");
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
        return new File(COVER_FILE_PATH + File.separator + filename);
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
        final File jpg = new File(COVER_FILE_PATH + File.separator + uuid + ".jpg");
        if (jpg.exists()) {
            return jpg;
        }
        // could be a png
        final File png = new File(COVER_FILE_PATH + File.separator + uuid + ".png");
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
            totalSize += purgeDir(getTemp(), reallyDelete);

        } catch (SecurityException e) {
            Logger.error(StorageUtils.class, e);
        }
        return totalSize;
    }

    private static long purgeDir(@NonNull final File dir,
                                 final boolean reallyDelete) {
        long size = 0;
        for (String name : dir.list()) {
            size += purgeFile(name, reallyDelete);
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            Logger.debug(StorageUtils.class, "purgeDir",
                         "dir=" + dir,
                         "size=" + size);
        }
        return size;
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
            File dir = getTemp();
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteFile(file);
                    }
                }
            }
        } catch (SecurityException e) {
            Logger.error(StorageUtils.class, e);
        }
    }

    /**
     * Find all possible CSV files in all accessible filesystems which
     * have a "bookCatalogue" directory.
     *
     * @return list of csv files
     */
    @NonNull
    public static List<File> findCsvFiles() {
        // Make a filter for files ending in .csv
        FilenameFilter csvFilter = (dir, name) -> {
            return name.toLowerCase(LocaleUtils.getSystemLocale()).endsWith(".csv");
            //ENHANCE: Allow for other files? Backups? || fl.endsWith(".csv.bak"));
        };

        @SuppressWarnings("unused")
        StringBuilder debugInfo;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            debugInfo = new StringBuilder("Getting mounted file systems\n");
        }

        // Loop all mounted file systems
        final List<File> dirs = new ArrayList<>();

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
                    // See if it has a bookCatalogue directory
                    final File dir =
                            new File(matcher.group(1) + File.separator + DIRECTORY_NAME);
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
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(StorageUtils.class, e, "Failed to open/scan/read /proc/mounts");
        }

        // Sometimes (Android 6?) the /proc/mount search seems to fail,
        // so we revert to environment vars
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            debugInfo.append("Found ").append(dirs.size()).append(" directories\n");
        }

        try {
            final String loc1 = System.getenv("EXTERNAL_STORAGE");
            if (loc1 != null) {
                final File dir = new File(loc1 + File.separator + DIRECTORY_NAME);
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
                final File dir = new File(loc2 + File.separator + DIRECTORY_NAME);
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
        } catch (RuntimeException e) {
            Logger.error(StorageUtils.class, e,
                         "Failed to get external storage from environment variables");
        }

        final Set<String> paths = new HashSet<>();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            debugInfo.append("Looking for files in directories\n");
        }

        final List<File> files = new ArrayList<>();
        for (File dir : dirs) {
            try {
                if (dir.exists()) {
                    // Loop for csv files
                    final File[] csvFiles = dir.listFiles(csvFilter);
                    if (csvFiles != null) {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                            debugInfo.append("    found ")
                                     .append(csvFiles.length)
                                     .append(" in ").append(dir.getAbsolutePath()).append('\n');
                        }
                        for (File f : csvFiles) {
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                                debugInfo.append("Found: ").append(f.getAbsolutePath());
                            }
                            final String cp = f.getCanonicalPath();
                            if (paths.contains(cp)) {
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
                                    debugInfo.append("        already present as ")
                                             .append(cp).append('\n');
                                }
                            } else {
                                files.add(f);
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
            } catch (IOException e) {
                Logger.error(StorageUtils.class, e,
                             "Failed to read directory " + dir.getAbsolutePath());
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STORAGE_UTILS) {
            Logger.debug(StorageUtils.class, "findCsvFiles", debugInfo);
        }

        // Sort descending based on modified date
        Collections.sort(files, new FileDateComparator(-1));
        return files;
    }

    /**
     * Given a InputStream, save it to a file.
     *
     * @param in  InputStream to read
     * @param out File to save
     *
     * @return {@code true} if successful
     */
    public static boolean saveInputStreamToFile(@Nullable final InputStream in,
                                                @NonNull final File out) {
        Objects.requireNonNull(in);

        File temp = null;
        try {
            // Get a temp file to avoid overwriting output unless copy works
            temp = File.createTempFile("tmp_is_", ".tmp", getTemp());
            OutputStream tempFos = new FileOutputStream(temp);
            // Copy from input to temp file
            byte[] buffer = new byte[65536];
            int len1;
            while ((len1 = in.read(buffer)) >= 0) {
                tempFos.write(buffer, 0, len1);
            }
            tempFos.close();
            // All OK, so rename to real output file
            renameFile(temp, out);
            return true;
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(StorageUtils.class, e);
        } finally {
            deleteFile(temp);
        }
        return false;
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
                    Logger.debug(StorageUtils.class,
                                 "deleteFile",
                                 "file=" + file.getAbsolutePath());
                }
            } catch (RuntimeException e) {
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
            } catch (RuntimeException e) {
                Logger.error(StorageUtils.class, e);
            }
        }
        return dst.exists();
    }

    /**
     * Create a copy of the databases into the Shared Storage location.
     */
    public static void exportDatabaseFiles() {
        exportFile(DBHelper.getDatabasePath(), "DbExport.db");
        exportFile(CoversDAO.CoversDbHelper.getDatabasePath(), "DbExport-covers.db");
    }

    /**
     * @param sourcePath      absolute path to file to export
     * @param destinationPath destination file name, will be stored in our directory
     *                        in Shared Storage
     */
    public static void exportFile(@NonNull final String sourcePath,
                                  @NonNull final String destinationPath) {
        try {
            // rename the previously copied file
            StorageUtils.renameFile(getFile(destinationPath),
                                    getFile(destinationPath + ".bak"));
            // and create a new copy. Note that the source is a fully qualified name,
            // so NOT using getFile()
            copyFile(new File(sourcePath), getFile(destinationPath));

        } catch (IOException e) {
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
        } catch (IllegalArgumentException e) {
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
     * Compare two files based on date. Used for sorting file list by date.
     */
    static class FileDateComparator
            implements Comparator<File>, Serializable {

        private static final long serialVersionUID = 6004008051110452600L;
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
