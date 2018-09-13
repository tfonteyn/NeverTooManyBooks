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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to wrap common storage related functions.
 *
 * @author Philip Warner
 */
public class StorageUtils {

    // our root directory to be created on the 'external storage'
    public static final String DIRECTORY_NAME = "bookCatalogue";
    /**
     * Used as: if (DEBUG && BuildConfig.DEBUG) { ... }
     */
    private static final boolean DEBUG = false;
    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 8192;

    private static final String DATABASE_NAME = "book_catalogue";
    // directories
    private static final String EXTERNAL_FILE_PATH = Environment.getExternalStorageDirectory() + File.separator + DIRECTORY_NAME;
    private static final String TEMP_IMAGE_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + "tmp_images";
    // files in above directories
    private static final String ERRORLOG_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + "error.log";
    private static final String NOMEDIA_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + ".nomedia";
    private static final String[] mPurgeableFilePrefixes = new String[]{
            DIRECTORY_NAME + "DbUpgrade",
            DIRECTORY_NAME + "DbExport",
            "error.log",
            "tmp"};

    private StorageUtils() {
    }

    public static String getErrorLog() {
        return ERRORLOG_FILE_PATH;
    }

    public static String getDatabaseName() {
        return DATABASE_NAME;
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
    static public boolean isWritable() {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(NOMEDIA_FILE_PATH), UTF8), BUFFER_SIZE);
            out.write("");
            out.close();
            return true;
        } catch (IOException e) {
            return false;
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
        // quick return
        if (rootDir.exists() && rootDir.isDirectory())
            return;

        createDir(EXTERNAL_FILE_PATH);
        createDir(TEMP_IMAGE_FILE_PATH);

        // * A .nomedia file will be created which will stop the thumbnails showing up in the gallery (thanks Brandon)
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(NOMEDIA_FILE_PATH).createNewFile();
        } catch (IOException e) {
            Logger.logError(e, "Failed to create .media file: " + NOMEDIA_FILE_PATH);
        }
    }

    /**
     * Get a File, don't check on existence or creation
     */
    public static File getFile(@NonNull final String fileName) {
        if (DEBUG && BuildConfig.DEBUG) {
            System.out.println("StorageUtils.getFile: Accessing file: " + EXTERNAL_FILE_PATH + File.separator + fileName);
        }
        return new File(EXTERNAL_FILE_PATH + File.separator + fileName);
    }

    /**
     * @return the shared root Directory object, create if needed
     */
    public static File getSharedStorage() {
        return new File(EXTERNAL_FILE_PATH);
    }

    /**
     * @param fileName in the temp image directory
     *
     * @return the file
     */
    public static File getTempImageFile(@NonNull final String fileName) {
        return getFile(TEMP_IMAGE_FILE_PATH + File.separator + fileName);
    }

    /**
     * @return the temp image directory
     */
    public static File getTempImageDirectory() {
        return new File(TEMP_IMAGE_FILE_PATH);
    }


    /**
     * Get the 'standard' temp file name for new books
     */
    public static File getTempThumbnail() {
        return getTempThumbnail("");
    }

    /**
     * Get the 'standard' temp file name for new books, including a suffix
     */
    public static File getTempThumbnail(@NonNull final String suffix) {
        return getFile("tmp" + suffix + ".jpg");
    }

    /**
     * return the thumbnail (as a File object) for the given hash
     *
     * @param uuid The uuid of the book
     *
     * @return The File object
     */
    public static File getThumbnailByUuid(@Nullable final String uuid) {
        return getThumbnailByUuid(uuid, "");
    }

    /**
     * return the thumbnail (as a File object) for the given id.
     *
     * @param uuid   The id of the book
     * @param suffix Optionally use a suffix on the file name.
     *
     * @return The File object
     */
    public static File getThumbnailByUuid(@Nullable final String uuid, @SuppressWarnings("SameParameterValue") String suffix) {
        return getThumbnailByName(uuid, suffix);
    }

    /**
     * return the thumbnail (as a File object) for the given id.
     *
     * @param prefix Optional on the file name.
     * @param suffix Optional on the file name.
     *
     * @return The File object
     */
    public static File getThumbnailByName(@Nullable final String prefix, @Nullable String suffix) {
        if (suffix == null) {
            suffix = "";
        }

        if (prefix == null || prefix.isEmpty()) {
            return getTempThumbnail(suffix);
        } else {
            final File jpg = getFile(prefix + suffix + ".jpg");
            if (!jpg.exists()) {
                final File png = getFile(prefix + suffix + ".png");
                if (png.exists())
                    return png;
                else {
                    return jpg;
                }
            } else {
                return jpg;
            }
        }
    }




    /**
     * @param db     file to backup
     * @param suffix suffix to apply to the directory name
     */
    public static void backupDbFile(@NonNull final SQLiteDatabase db, String suffix) {
        try {
            final String fileName = DIRECTORY_NAME + suffix;

            //check if it exists
            final File existing = getFile(fileName);
            if (existing.exists()) {
                //noinspection ResultOfMethodCallIgnored
                existing.renameTo(getFile(fileName + ".bak"));
            }

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

    /**
     * Scan all mount points for '/bookCatalogue' directory and collect a list
     * of all CSV files.
     */
    private static final Pattern MOUNT_POINT_PATH = Pattern.compile("^\\s*[^\\s]+\\s+([^\\s]+)");
    public static ArrayList<File> findExportFiles() {




        // Make a filter for files ending in .csv
        FilenameFilter csvFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                final String fl = filename.toLowerCase();
                return (fl.endsWith(".csv"));
                //ENHANCE: Allow for other files? Backups? || fl.endsWith(".csv.bak"));
            }
        };

        StringBuilder debugInfo;
        if (DEBUG && BuildConfig.DEBUG) {
            //noinspection UnusedAssignment
            debugInfo = new StringBuilder("Getting mounted file systems\n");
        }

        // Scan all mounted file systems
        final ArrayList<File> dirs = new ArrayList<>();
        BufferedReader in = null;

        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")), 1024);
            String line;
            while ((line = in.readLine()) != null) {
                if (DEBUG && BuildConfig.DEBUG) {
                    debugInfo.append("   checking ").append(line).append("\n");
                }
                final Matcher m = MOUNT_POINT_PATH.matcher(line);
                // Get the mount point
                if (m.find()) {
                    // See if it has a bookCatalogue directory
                    final File dir = new File(m.group(1) + File.separator + DIRECTORY_NAME);
                    if (DEBUG && BuildConfig.DEBUG) {
                        debugInfo.append("       matched ").append(dir.getAbsolutePath()).append("\n");
                    }
                    dirs.add(dir);
                } else {
                    if (DEBUG && BuildConfig.DEBUG) {
                        debugInfo.append("       NO match\n");
                    }
                }
            }
        } catch (IOException e) {
            Logger.logError(e, "Failed to open/scan/read /proc/mounts");
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (Exception ignored) {
                }
        }

        // Sometimes (Android 6?) the /proc/mount search seems to fail, so we revert to environment vars
        if (DEBUG && BuildConfig.DEBUG) {
            debugInfo.append("Found ").append(dirs.size()).append(" directories\n");
        }

        try {
            final String loc1 = System.getenv("EXTERNAL_STORAGE");
            if (loc1 != null) {
                final File dir = new File(loc1 + File.separator + DIRECTORY_NAME);
                dirs.add(dir);
                if (DEBUG && BuildConfig.DEBUG) {
                    debugInfo.append("EXTERNAL_STORAGE added ").append(dir.getAbsolutePath()).append("\n");
                }
            } else {
                if (DEBUG && BuildConfig.DEBUG) {
                    debugInfo.append("EXTERNAL_STORAGE was null\n");
                }
            }

            final String loc2 = System.getenv("SECONDARY_STORAGE");
            if (loc2 != null && !loc2.equals(loc1)) {
                final File dir = new File(loc2 + File.separator + DIRECTORY_NAME);
                dirs.add(dir);
                if (DEBUG && BuildConfig.DEBUG) {
                    debugInfo.append("SECONDARY_STORAGE added ").append(dir.getAbsolutePath()).append("\n");
                }
            } else {
                if (DEBUG && BuildConfig.DEBUG) {
                    debugInfo.append("SECONDARY_STORAGE ignored: ").append(loc2).append("\n");
                }
            }
        } catch (Exception e) {
            Logger.logError(e, "Failed to get external storage from environment variables");
        }

        final HashSet<String> paths = new HashSet<>();

        if (DEBUG && BuildConfig.DEBUG) {
            debugInfo.append("Looking for files in directories\n");
        }

        final ArrayList<File> files = new ArrayList<>();
        for (File dir : dirs) {
            try {
                if (dir.exists()) {
                    // Scan for csv files
                    final File[] csvFiles = dir.listFiles(csvFilter);
                    if (csvFiles != null) {
                        if (DEBUG && BuildConfig.DEBUG) {
                            debugInfo.append("    found ").append(csvFiles.length).append(" in ").append(dir.getAbsolutePath()).append("\n");
                        }
                        for (File f : csvFiles) {
                            if (DEBUG && BuildConfig.DEBUG) {
                                debugInfo.append("Found: ").append(f.getAbsolutePath());
                            }
                            final String cp = f.getCanonicalPath();
                            if (paths.contains(cp)) {
                                if (DEBUG && BuildConfig.DEBUG) {
                                    debugInfo.append("        already present as ").append(cp).append("\n");
                                }
                            } else {
                                files.add(f);
                                paths.add(cp);
                                if (DEBUG && BuildConfig.DEBUG) {
                                    debugInfo.append("        added as ").append(cp).append("\n");
                                }
                            }
                        }
                    } else {
                        if (DEBUG && BuildConfig.DEBUG) {
                            debugInfo.append("    null returned by listFiles() in ").append(dir.getAbsolutePath()).append("\n");
                        }
                    }
                } else {
                    if (DEBUG && BuildConfig.DEBUG) {
                        debugInfo.append("    ").append(dir.getAbsolutePath()).append(" does not exist\n");
                    }
                }
            } catch (Exception e) {
                Logger.logError(e, "Failed to read directory " + dir.getAbsolutePath());
            }
        }

        if (DEBUG && BuildConfig.DEBUG) {
            Logger.logError(new RuntimeException("INFO"), debugInfo.toString());
        }

        // Sort descending based on modified date
        Collections.sort(files, new FileDateComparator(-1));
        return files;
    }



    /**
     * Count size + (optional) Cleanup any purgeable files.
     *
     * @param reallyDelete  if true, delete files, if false only count bytes
     *
     * @return the total size in bytes of purgeable/purged files.
     */
    public static long cleanupFiles(boolean reallyDelete) {
        long totalSize = 0;
        final File dir = getSharedStorage();
        for (String name : dir.list()) {
            boolean purge = false;
            for (String prefix : mPurgeableFilePrefixes) {
                if (name.startsWith(prefix)) {
                    purge = true;
                    break;
                }
            }
            if (purge) {
                try {
                    final File file = getFile(name);
                    totalSize += file.length();
                    if (reallyDelete) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                } catch (NullPointerException ignored) {
                }
            }
        }
        return totalSize;
    }

//    /**
//     * return the thumbnail (as a File object) for the given id
//     *
//     * @param id The id of the book
//     *
//     * @return The File object
//     */
//    public static File fetchThumbnailById(final long id) {
//        return fetchThumbnailById(id, "");
//    }
//
//    /*
//     * return the thumbnail (as a File object) for the given id. Optionally use a suffix
//     * on the file name.
//     *
//     * @param id The id of the book
//     * @return The File object
//     */
//    @SuppressWarnings("WeakerAccess")
//    public static File fetchThumbnailById(final long id, @Nullable final String suffix) {
//        return getThumbnailByName(Long.toString(id), suffix);
//    }

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
        FileDateComparator(int direction) {
            mDirection = direction < 0 ? -1 : 1;
        }

        /**
         * Compare based on modified date
         */
        @Override
        public int compare(File lhs, File rhs) {
            final long l = lhs.lastModified();
            final long r = rhs.lastModified();
            if (l < r)
                return -mDirection;
            else if (l > r)
                return mDirection;
            else
                return 0;
        }
    }
}
