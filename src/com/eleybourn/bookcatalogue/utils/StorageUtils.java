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
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBHelper;
import com.eleybourn.bookcatalogue.database.CoversDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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

/**
 * Class to wrap common storage related functions.
 *
 * "External Storage" == what Android considers non-application-private storage. i.e the user has access to it
 * ('external' refers to old android phones where Shared Storage *always* was an external sdcard)
 * Sometimes also referred to as "Shared Storage" because all apps have access to it.
 *
 * For the sake of clarity (confusion?) we'll call it "Shared Storage" only.
 *
 * TODO: user message talk about "SD Card"
 *
 * @author Philip Warner
 */
public class StorageUtils {

    /** error result code for {@link #getSharedStorageFreeSpace} */
    public static final int ERROR_CANNOT_STAT = -2;
    /** buffer size for file copy operations */
    private static final int FILE_COPY_BUFFER_SIZE = 8192;
    /** our root directory to be created on the 'external storage' aka Shared Storage*/
    private static final String DIRECTORY_NAME = "bookCatalogue";
    private static final String UTF8 = "utf8";
    /** root external storage aka Shared Storage */
    private static final String SHARED_STORAGE_PATH = Environment.getExternalStorageDirectory() + File.separator + DIRECTORY_NAME;
    /** sub directory for temporary images TOMF: use context.getCacheDir() instead */
    private static final String TEMP_FILE_PATH = SHARED_STORAGE_PATH + File.separator + "tmp_images";
    /** permanent location for cover files. For now hardcoded, but the intention is to allow user-defined. */
    private static final String COVER_FILE_PATH = SHARED_STORAGE_PATH + File.separator + "covers";
    /** permanent location for log files. */
    private static final String LOG_FILE_PATH = SHARED_STORAGE_PATH + File.separator + "log";
    /** serious errors are written to this file */
    private static final String ERROR_LOG_FILE = "error.log";
    /**
     * written to root Shared Storage as 'writable' test + prevent 'detection' by apps who
     * want to 'do things' with media
     */
    private static final String NOMEDIA_FILE_PATH = SHARED_STORAGE_PATH + File.separator + MediaStore.MEDIA_IGNORE_FILENAME;
    /**
     * Filenames *starting* with this prefix are considered purgeable.
     */
    private static final String[] mPurgeableFilePrefixes = new String[]{
            "DbUpgrade", "DbExport", "error.log", "tmp"};
    /**
     * Loop all mount points for '/bookCatalogue' directory and collect a list
     * of all CSV files.
     */
    private static final Pattern MOUNT_POINT_PATH = Pattern.compile("^\\s*[^\\s]+\\s+([^\\s]+)");
    /** error result code for {@link #getSharedStorageFreeSpace} */
    private static final int ERROR_NO_STORAGE = -1;

    private StorageUtils() {
    }

    public static String getErrorLog() throws SecurityException {
        return LOG_FILE_PATH + File.separator + ERROR_LOG_FILE;
    }

    /**
     * Don't log anything, we might not have a log file!
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean createDir(final @NonNull String name) throws SecurityException {
        final File dir = new File(name);
        return dir.isDirectory() || dir.mkdirs();
    }

    /**
     * Check if the Shared Storage is writable
     */
    static public boolean isWriteProtected() throws SecurityException {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(NOMEDIA_FILE_PATH), UTF8), 10); // yes, 10 bytes...
            out.write("");
            out.close();
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Make sure the Shared Storage directory exists
     * Logs failures only if we we're capable of creating a log directory!
     * Abort if we don't get that far.
     *
     * Only called from StartupActivity, after permissions have been granted.
     */
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public static void initSharedDirectories() throws SecurityException {
        // need root first (duh)
        if (!createDir(SHARED_STORAGE_PATH)) {
            return;
        }
        // then log dir!
        if (!createDir(LOG_FILE_PATH)) {
            return;
        }

        // from here on, we have a log file..
        if (!createDir(COVER_FILE_PATH)) {
            Logger.error("Failed to create covers directory");
        }
        if (!createDir(TEMP_FILE_PATH)) {
            Logger.error("Failed to create temp directory");
        }

        // A .nomedia file will be created which will stop the thumbnails showing up in the gallery (thanks Brandon)
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(NOMEDIA_FILE_PATH).createNewFile();
        } catch (IOException e) {
            Logger.error(e, "Failed to create .media file: " + NOMEDIA_FILE_PATH);
        }
    }

    public static File getTempStorage() {
        return new File(TEMP_FILE_PATH);
    }

    public static File getCoverStorage() {
        return new File(COVER_FILE_PATH);
    }

    public static File getLogStorage() {
        return new File(LOG_FILE_PATH);
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * @return the Shared Storage root Directory object
     */
    public static File getSharedStorage() {
        return new File(SHARED_STORAGE_PATH);
    }

    /**
     * return a general purpose File, located in the Shared Storage path (or a sub directory)
     * Don't use this for standard cover management.
     *
     * @param fileName the relative filename (including sub dirs) to the Shared Storage path
     *
     * @return the file
     */
    public static File getFile(final @NonNull String fileName) {
        return new File(SHARED_STORAGE_PATH + File.separator + fileName);
    }

    /**
     * Get the 'standard' temp file name for new books
     * Is also used as the standard file name for images downloaded from the internet.
     */
    public static File getTempCoverFile() {
        return new File(TEMP_FILE_PATH + File.separator + "tmp.jpg");
    }

    /**
     * Delete the 'standard' temp file
     */
    public static void deleteTempCoverFile() {
        deleteFile(getTempCoverFile());
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Get the 'standard' temp file name for new books, including a suffix
     */
    static File getTempCoverFile(final @NonNull String suffix) {
        return new File(TEMP_FILE_PATH + File.separator + "tmp" + suffix + ".jpg");
    }

    /**
     * Get the 'standard' temp file name for new books, including a suffix
     */
    public static File getTempCoverFile(final @NonNull String prefix, final @NonNull String name) {
        return new File(TEMP_FILE_PATH + File.separator + prefix + name + ".jpg");
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * @param filename a generic filename
     *
     * @return a File with that name, located in the covers directory
     */
    @NonNull
    public static File getRawCoverFile(final @NonNull String filename) {
        return new File(COVER_FILE_PATH + File.separator + filename);
    }

    /**
     * return the cover for the given uuid.
     *
     * @param uuid of the book,
     *
     * @return The File object for existing files, or a new (jpg) placeholder.
     */
    @NonNull
    public static File getCoverFile(final @NonNull String uuid) {
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
     * @param reallyDelete if true, delete files, if false only count bytes
     *
     * @return the total size in bytes of purgeable/purged files.
     */
    public static long purgeFiles(final boolean reallyDelete) {
        long totalSize = 0;
        try {
            for (String name : getLogStorage().list()) {
                totalSize += purgeFile(name, reallyDelete);
            }
            for (String name : getTempStorage().list()) {
                totalSize += purgeFile(name, reallyDelete);
            }
            // and the root.
            for (String name : getSharedStorage().list()) {
                totalSize += purgeFile(name, reallyDelete);
            }
            // theoretically this one is not needed.
            for (String name : getCoverStorage().list()) {
                totalSize += purgeFile(name, reallyDelete);
            }
        } catch (SecurityException e) {
            Logger.error(e);
        }
        return totalSize;
    }
    /* ------------------------------------------------------------------------------------------ */

    private static long purgeFile(final @NonNull String name, final boolean reallyDelete) {
        long totalSize = 0;
        for (String prefix : mPurgeableFilePrefixes) {
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

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Standard File management methods

    /**
     * Delete *everything* in the temp file directory
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
        } catch (SecurityException e) {
            Logger.error(e);
        }
    }

    /**
     * Find all possible CSV files in all accessible filesystems which
     * have a "bookCatalogue" directory
     *
     * @return list of csv files
     */
    @NonNull
    public static List<File> findCsvFiles() {
        // Make a filter for files ending in .csv
        FilenameFilter csvFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                final String fl = filename.toLowerCase();
                return (fl.endsWith(".csv"));
                //ENHANCE: Allow for other files? Backups? || fl.endsWith(".csv.bak"));
            }
        };

        @SuppressWarnings("unused") StringBuilder debugInfo = new StringBuilder();
        if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
            //noinspection UnusedAssignment
            debugInfo.append("Getting mounted file systems\n");
        }

        // Loop all mounted file systems
        final List<File> dirs = new ArrayList<>();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")), 1024)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                    debugInfo.append("   checking ").append(line).append("\n");
                }
                final Matcher m = MOUNT_POINT_PATH.matcher(line);
                // Get the mount point
                if (m.find()) {
                    // See if it has a bookCatalogue directory
                    final File dir = new File(m.group(1) + File.separator + DIRECTORY_NAME);
                    if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                        debugInfo.append("       matched ").append(dir.getAbsolutePath()).append("\n");
                    }
                    dirs.add(dir);
                } else {
                    if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                        debugInfo.append("       NO match\n");
                    }
                }
            }
        } catch (IOException e) {
            Logger.error(e, "Failed to open/scan/read /proc/mounts");
        }

        // Sometimes (Android 6?) the /proc/mount search seems to fail, so we revert to environment vars
        if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
            debugInfo.append("Found ").append(dirs.size()).append(" directories\n");
        }

        try {
            final String loc1 = System.getenv("EXTERNAL_STORAGE");
            if (loc1 != null) {
                final File dir = new File(loc1 + File.separator + DIRECTORY_NAME);
                dirs.add(dir);
                if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                    debugInfo.append("EXTERNAL_STORAGE added ").append(dir.getAbsolutePath()).append("\n");
                }
            } else {
                if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                    debugInfo.append("EXTERNAL_STORAGE was null\n");
                }
            }

            final String loc2 = System.getenv("SECONDARY_STORAGE");
            if (loc2 != null && !loc2.equals(loc1)) {
                final File dir = new File(loc2 + File.separator + DIRECTORY_NAME);
                dirs.add(dir);
                if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                    debugInfo.append("SECONDARY_STORAGE added ").append(dir.getAbsolutePath()).append("\n");
                }
            } else {
                if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                    debugInfo.append("SECONDARY_STORAGE ignored: ").append(loc2).append("\n");
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to get external storage from environment variables");
        }

        final Set<String> paths = new HashSet<>();

        if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
            debugInfo.append("Looking for files in directories\n");
        }

        final List<File> files = new ArrayList<>();
        for (File dir : dirs) {
            try {
                if (dir.exists()) {
                    // Loop for csv files
                    final File[] csvFiles = dir.listFiles(csvFilter);
                    if (csvFiles != null) {
                        if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                            debugInfo.append("    found ").append(csvFiles.length).append(" in ").append(dir.getAbsolutePath()).append("\n");
                        }
                        for (File f : csvFiles) {
                            if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                                debugInfo.append("Found: ").append(f.getAbsolutePath());
                            }
                            final String cp = f.getCanonicalPath();
                            if (paths.contains(cp)) {
                                if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                                    debugInfo.append("        already present as ").append(cp).append("\n");
                                }
                            } else {
                                files.add(f);
                                paths.add(cp);
                                if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                                    debugInfo.append("        added as ").append(cp).append("\n");
                                }
                            }
                        }
                    } else {
                        if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                            debugInfo.append("    null returned by listFiles() in ").append(dir.getAbsolutePath()).append("\n");
                        }
                    }
                } else {
                    if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                        debugInfo.append("    ").append(dir.getAbsolutePath()).append(" does not exist\n");
                    }
                }
            } catch (Exception e) {
                Logger.error(e, "Failed to read directory " + dir.getAbsolutePath());
            }
        }

        if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
            Logger.info(StorageUtils.class, debugInfo.toString());
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
     * @return <tt>true</tt>if successful
     */
    public static boolean saveInputStreamToFile(final @Nullable InputStream in, final @NonNull File out) {
        Objects.requireNonNull(in);

        File temp = null;
        try {
            // Get a temp file to avoid overwriting output unless copy works
            temp = File.createTempFile("temp_", null, getTempStorage());
            FileOutputStream tempFos = new FileOutputStream(temp);
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
        } catch (IOException e) {
            Logger.error(e);
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
    public static void deleteFile(final @Nullable File file) {
        if (file != null && file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
                    Logger.info(StorageUtils.class, "deleteFile|file=" + file.getAbsolutePath());
                }
            } catch (Exception e) {
                Logger.error(e);
            }
        }
    }

    /**
     * ENHANCE: make suitable for multiple filesystems using {@link #copyFile(File, File)}
     * from the Android docs {@link File#renameTo(File)}: Both paths be on the same mount point.
     *
     * @return <tt>true</tt>if the rename worked, this is really a ".exists()" call.
     * and not relying on the OS renameTo call.
     */
    public static boolean renameFile(final @NonNull File src, final @NonNull File dst) {
        if (DEBUG_SWITCHES.STORAGE_UTILS && BuildConfig.DEBUG) {
            Logger.info(StorageUtils.class, "renameFile|src=" + src.getAbsolutePath() + "|dst=" + dst.getAbsolutePath());
        }
        if (src.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                src.renameTo(dst);
            } catch (Exception e) {
                Logger.error(e);
            }
        }
        return dst.exists();
    }

    /**
     * Create a copy of the databases into the Shared Storage location
     */
    public static void exportDatabaseFiles(final @NonNull Context context) {
        exportFile(CatalogueDBHelper.getDatabasePath(context), "DbExport.db");
        exportFile(CoversDBAdapter.CoversDbHelper.getDatabasePath(context), "DbExport-covers.db");
    }

    /**
     * @param sourcePath      absolute path to file to export
     * @param destinationPath destination file name, will be stored in our directory in Shared Storage
     */
    public static void exportFile(final @NonNull String sourcePath, final @NonNull String destinationPath) {
        try {
            // rename the previously copied file
            StorageUtils.renameFile(getFile(destinationPath), getFile(destinationPath + ".bak"));
            // and create a new copy. Note that the source is a fully qualified name, so NOT using getFile()
            copyFile(new File(sourcePath), getFile(destinationPath));

        } catch (Exception e) {
            Logger.error(e);
        }
    }

    /**
     * @throws IOException upon failure
     */
    public static void copyFile(final @NonNull File source, final @NonNull File destination) throws IOException {
        try (InputStream is = new FileInputStream(source)) {
            copyFile(is, FILE_COPY_BUFFER_SIZE, destination);
        }
    }

    /**
     * @param is          InputStream, will NOT be closed here. Close it yourself !
     * @param bufferSize  the read buffer
     * @param destination destination file, will be properly closed.
     *
     * @throws IOException at failures
     */
    public static void copyFile(final @NonNull InputStream is,
                                final int bufferSize,
                                final @NonNull File destination) throws IOException {
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
     * Channels are FAST... TODO: replace old method with this one. but need testing, never used it on Android myself
     */

    @SuppressWarnings("unused")
    private static void copyFile2(final @NonNull File source, final @NonNull File destination) throws IOException {
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
            return (stat.getAvailableBlocksLong() * stat.getBlockSizeLong());
        } catch (IllegalArgumentException e) {
            Logger.error(e);
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
        switch (Environment.getExternalStorageState()) {
            case Environment.MEDIA_MOUNTED:
                // all ok
                return 0;
                // should we check on other states ? do we care ?
        }
        return R.string.error_storage_not_available;
    }

    /**
     * Compare two files based on date. Used for sorting file list by date.
     *
     * @author Philip Warner
     */
    static class FileDateComparator implements Comparator<File> {
        /** Ascending is >= 0, Descending is < 0. */
        private final int mDirection;

        /**
         * Constructor
         */
        FileDateComparator(@SuppressWarnings("SameParameterValue") final int direction) {
            mDirection = direction < 0 ? -1 : 1;
        }

        /**
         * Compare based on modified date
         */
        @Override
        public int compare(final @NonNull File lhs, final @NonNull File rhs) {
            final long l = lhs.lastModified();
            final long r = rhs.lastModified();
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
