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

import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to wrap common storage related functions.
 *
 * @author Philip Warner
 */
public class StorageUtils {

    /** our root directory to be created on the 'external storage' */
    private static final String DIRECTORY_NAME = "bookCatalogue";

    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 8192;
    /** root external storage */
    private static final String EXTERNAL_FILE_PATH = Environment.getExternalStorageDirectory() + File.separator + DIRECTORY_NAME;
    /** sub directory for temporary images */
    private static final String TEMP_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + "tmp_images";

    /** permanent location for cover files. For now hardcoded, but the intention is to allow user-defined. */
    private static final String COVER_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + "covers";
    /** serious errors are written to this file */
    private static final String ERROR_LOG_FILE = "error.log";
    /** written to root external storage as 'writable' test + prevent 'detection' by apps who want to 'do things' with media */
    private static final String NOMEDIA_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + ".nomedia";


    private static final String[] mPurgeableFilePrefixes = new String[]{
            "DbUpgrade", "DbExport", "error.log", "tmp"};

    /**
     * Scan all mount points for '/bookCatalogue' directory and collect a list
     * of all CSV files.
     */
    private static final Pattern MOUNT_POINT_PATH = Pattern.compile("^\\s*[^\\s]+\\s+([^\\s]+)");

    private StorageUtils() {
    }

    public static String getErrorLog() {
        return EXTERNAL_FILE_PATH + File.separator + ERROR_LOG_FILE;
    }

    private static void createDir(@NonNull final String name) {
        final File dir = new File(name);
        boolean ok = dir.mkdirs() || dir.isDirectory();
        if (!ok) {
            Logger.logError("Could not write to storage. No permission on: " + name);
        }
    }

    /**
     * Check if the external storage is writable
     *
     * @return success or failure
     */
    static public boolean isWriteProtected() {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(NOMEDIA_FILE_PATH), UTF8), BUFFER_SIZE);
            out.write("");
            out.close();
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Make sure the external shared directory exists
     * Logs failures themselves, but does NOT fail function
     *
     * Only called from StartupActivity, after permissions have been granted.
     */
    public static void initSharedDirectories() {
        File rootDir = new File(EXTERNAL_FILE_PATH);
        if (rootDir.exists() && rootDir.isDirectory()) {
            return;
        }

        createDir(EXTERNAL_FILE_PATH);
        createDir(COVER_FILE_PATH);
        createDir(TEMP_FILE_PATH);

        // * A .nomedia file will be created which will stop the thumbnails showing up in the gallery (thanks Brandon)
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(NOMEDIA_FILE_PATH).createNewFile();
        } catch (IOException e) {
            Logger.logError(e, "Failed to create .media file: " + NOMEDIA_FILE_PATH);
        }
    }

    private static File getTempStorage() {
        return new File(TEMP_FILE_PATH);
    }

    /**
     * @return the shared root Directory object
     */
    public static File getSharedStorage() {
        return new File(EXTERNAL_FILE_PATH);
    }
    /**
     *
     * return a general purpose File, located in the Shared Storage path (or a sub directory)
     * Don't use this for standard cover management.
     *
     * @param fileName the relative filename (including sub dirs) to the Shared Storage path
     * @return the file
     */
    public static File getFile(@NonNull final String fileName) {
        return new File(EXTERNAL_FILE_PATH + File.separator + fileName);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static File getCoverStorage() {
        return new File(COVER_FILE_PATH);
    }

    /**
     * Get the 'standard' temp file name for new books
     */
    public static File getTempCoverFile() {
        if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
            Logger.printStackTrace("Someone wants a bare tmp.jpg ? why ?");
        }
        return getTempCoverFile("tmp", "");
    }

    /**
     * Get the 'standard' temp file name for new books, including a suffix
     */
    static File getTempCoverFile(@NonNull final String name) {
        return getTempCoverFile("tmp", name);
    }

    /**
     * Get the 'standard' temp file name for new books, including a suffix
     * Located in the normal Covers directory
     */
    public static File getTempCoverFile(@NonNull final String prefix, @NonNull final String name) {
        return new File(COVER_FILE_PATH + File.separator + prefix + name + ".jpg");
    }

    /**
     * return the cover for the given uuid.
     *
     * @param uuid of the book,
     *
     * @return The File object for existing files, or a new placeholder.
     */
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

        // we need a new file,
        // return new File(COVER_FILE_PATH + File.separator + uuid + ".jpg");
        return jpg;
    }


    /**
     * Delete *everything* in the temp file directory
     */
    public static void cleanupTempDirectory() {
        File dir = getTempStorage();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFile(file);
                }
            }
        }
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
        for (String name : getCoverStorage().list()) {
            for (String prefix : mPurgeableFilePrefixes) {
                if (name.startsWith(prefix)) {
                    final File file = getFile(name);
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
     * @param db     file to backup
     * @param toFile suffix to apply to the directory name
     */
    public static void backupDbFile(@NonNull final SQLiteDatabase db, @NonNull final String toFile) {
        try {
            final String fileName = DIRECTORY_NAME + toFile;

            final File existing = getFile(fileName);
            StorageUtils.renameFile(existing, getFile(fileName + ".bak"));

            final InputStream dbOrig = new FileInputStream(db.getPath());
            final OutputStream dbCopy = new FileOutputStream(getFile(fileName));

            final byte[] buffer = new byte[1024];
            int length;
            while ((length = dbOrig.read(buffer)) > 0) {
                dbCopy.write(buffer, 0, length);
            }

            dbCopy.flush();
            dbCopy.close();
            dbOrig.close();

        } catch (Exception e) {
            Logger.logError(e);
        }
    }

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

        @SuppressWarnings("unused") StringBuilder debugInfo;
        if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
            //noinspection UnusedAssignment
            debugInfo = new StringBuilder("Getting mounted file systems\n");
        }

        // Scan all mounted file systems
        final List<File> dirs = new ArrayList<>();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")), 1024)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                    debugInfo.append("   checking ").append(line).append("\n");
                }
                final Matcher m = MOUNT_POINT_PATH.matcher(line);
                // Get the mount point
                if (m.find()) {
                    // See if it has a bookCatalogue directory
                    final File dir = new File(m.group(1) + File.separator + DIRECTORY_NAME);
                    if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                        debugInfo.append("       matched ").append(dir.getAbsolutePath()).append("\n");
                    }
                    dirs.add(dir);
                } else {
                    if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                        debugInfo.append("       NO match\n");
                    }
                }
            }
        } catch (IOException e) {
            Logger.logError(e, "Failed to open/scan/read /proc/mounts");
        }

        // Sometimes (Android 6?) the /proc/mount search seems to fail, so we revert to environment vars
        if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
            debugInfo.append("Found ").append(dirs.size()).append(" directories\n");
        }

        try {
            final String loc1 = System.getenv("EXTERNAL_STORAGE");
            if (loc1 != null) {
                final File dir = new File(loc1 + File.separator + DIRECTORY_NAME);
                dirs.add(dir);
                if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                    debugInfo.append("EXTERNAL_STORAGE added ").append(dir.getAbsolutePath()).append("\n");
                }
            } else {
                if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                    debugInfo.append("EXTERNAL_STORAGE was null\n");
                }
            }

            final String loc2 = System.getenv("SECONDARY_STORAGE");
            if (loc2 != null && !loc2.equals(loc1)) {
                final File dir = new File(loc2 + File.separator + DIRECTORY_NAME);
                dirs.add(dir);
                if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                    debugInfo.append("SECONDARY_STORAGE added ").append(dir.getAbsolutePath()).append("\n");
                }
            } else {
                if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                    debugInfo.append("SECONDARY_STORAGE ignored: ").append(loc2).append("\n");
                }
            }
        } catch (Exception e) {
            Logger.logError(e, "Failed to get external storage from environment variables");
        }

        final HashSet<String> paths = new HashSet<>();

        if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
            debugInfo.append("Looking for files in directories\n");
        }

        final List<File> files = new ArrayList<>();
        for (File dir : dirs) {
            try {
                if (dir.exists()) {
                    // Scan for csv files
                    final File[] csvFiles = dir.listFiles(csvFilter);
                    if (csvFiles != null) {
                        if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                            debugInfo.append("    found ").append(csvFiles.length).append(" in ").append(dir.getAbsolutePath()).append("\n");
                        }
                        for (File f : csvFiles) {
                            if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                                debugInfo.append("Found: ").append(f.getAbsolutePath());
                            }
                            final String cp = f.getCanonicalPath();
                            if (paths.contains(cp)) {
                                if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                                    debugInfo.append("        already present as ").append(cp).append("\n");
                                }
                            } else {
                                files.add(f);
                                paths.add(cp);
                                if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                                    debugInfo.append("        added as ").append(cp).append("\n");
                                }
                            }
                        }
                    } else {
                        if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                            debugInfo.append("    null returned by listFiles() in ").append(dir.getAbsolutePath()).append("\n");
                        }
                    }
                } else {
                    if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
                        debugInfo.append("    ").append(dir.getAbsolutePath()).append(" does not exist\n");
                    }
                }
            } catch (Exception e) {
                Logger.logError(e, "Failed to read directory " + dir.getAbsolutePath());
            }
        }

        if (DEBUG_SWITCHES.STORAGEUTILS && BuildConfig.DEBUG) {
            Logger.logError(debugInfo.toString());
        }

        // Sort descending based on modified date
        Collections.sort(files, new FileDateComparator(-1));
        return files;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Standard File management methods

    /**
     * Given a InputStream, save it to a file.
     *
     * @param in  InputStream to read
     * @param out File to save
     *
     * @return true if successful
     */
    public static boolean saveInputStreamToFile(@NonNull final InputStream in, @NonNull final File out) {
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
            Logger.logError(e);
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
            } catch (Exception e) {
                Logger.logError(e);
            }
        }
    }

    /**
     * ENHANCE: make suitable for multiple filesystems using {@link #copyFile(File, File)}
     * from the Android docs {@link File#renameTo(File)}: Both paths be on the same mount point.
     *
     * @return true if the rename worked, this is really a ".exists()" call.
     *              and not relying on the OS renameTo call.
     */
    public static boolean renameFile(@NonNull final File src, @NonNull final File dst) {
        if (src.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                src.renameTo(dst);
            } catch (Exception e) {
                Logger.logError(e);
            }
        }
        return dst.exists();
    }

    public static void copyFile(@NonNull final File src, @NonNull final File dst) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            // Open in & out
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            // Get a buffer
            byte[] buffer = new byte[8192];
            int nRead;
            // Copy
            while ((nRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, nRead);
            }
            // Close both. We close them here so exceptions are signalled
            in.close();
            in = null;
            out.close();
            out = null;
        } finally {
            // If not already closed, close.
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Channels are FAST... TODO: replace old method with this one.
     */
    @SuppressWarnings("unused")
    private static void copyFile2(@NonNull final File src, @NonNull final File dst) throws IOException {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
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
        FileDateComparator(final int direction) {
            mDirection = direction < 0 ? -1 : 1;
        }

        /**
         * Compare based on modified date
         */
        @Override
        public int compare(@NonNull final File lhs, @NonNull final File rhs) {
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
