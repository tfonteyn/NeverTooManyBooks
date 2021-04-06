/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * The set of named directories used and support functions for them.
 */
public enum AppDir {
    /** Nothing stored here, just the sub directories. */
    Root,
    /** Database backup taken during app upgrades. */
    Upgrades,
    /** temp files. */
    Cache,
    /** log files. */
    Log,
    /** The book covers. This is the app external Pictures folder. */
    Covers;

    /** error result code for {@link #getFreeSpace}. */
    public static final int ERROR_CANNOT_STAT = -2;
    /** The length of a UUID string. */
    public static final int UUID_LEN = 32;
    /** Log tag. */
    private static final String TAG = "AppDir";
    /** Sub directory of Root : Upgrade files. */
    private static final String UPGRADES_SUB_DIR = "Upgrades";
    /** Sub directory of Root : log files. */
    private static final String LOG_SUB_DIR = "log";
    /** The cached directory. */
    @Nullable
    private File mDir;

    /**
     * Count size + (optional) Cleanup any purgeable files.
     *
     * @param bookUuidList a list of book uuid to check for orphaned covers
     * @param reallyDelete {@code true} to actually delete files,
     *                     {@code false} to only sum file sizes in bytes
     *
     * @return the total size in bytes of purgeable/purged files.
     */
    public static long purge(@NonNull final Collection<String> bookUuidList,
                             final boolean reallyDelete) {
        long totalSize = 0;

        try {
            // just trash these dirs
            totalSize += Cache.purge(reallyDelete, null);
            totalSize += Log.purge(reallyDelete, null);
            totalSize += Upgrades.purge(reallyDelete, null);

            // check for orphaned cover files
            totalSize += Covers.purge(reallyDelete, file -> {
                if (file.getName().length() > UUID_LEN) {
                    // not in the list? then we can purge it
                    return !bookUuidList.contains(file.getName().substring(0, UUID_LEN));
                }
                // not a uuid base file ? be careful and leave it.
                return false;
            });

        } catch (@NonNull final SecurityException e) {
            // not critical, just log it.
            Logger.error(TAG, e);
            return 0;
        }

        return totalSize;
    }

    /**
     * Initialize storage needs. Called from StartupActivity.
     * <p>
     * All exceptions are suppressed but logged.
     *
     * @param context Current context
     *
     * @return {@code null} for all ok, or a user displayable error.
     */
    @Nullable
    public static String init(@NonNull final Context context) {

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return context.getString(R.string.error_storage_not_accessible);
        }

        try {
            // check we can get our root.
            Root.getDir();

            // create sub directories if needed
            final AppDir[] appDirs = {Log, Upgrades};
            for (final AppDir appDir : appDirs) {
                final File dir = appDir.getDir();
                if (!(dir.isDirectory() || dir.mkdirs())) {
                    return context.getString(R.string.error_storage_not_writable);
                }
            }

            // Prevent thumbnails showing up in the device Image Gallery.
            //noinspection ResultOfMethodCallIgnored
            new File(Covers.getDir(), MediaStore.MEDIA_IGNORE_FILENAME).createNewFile();

            return null;

        } catch (@NonNull final ExternalStorageException e) {
            // Don't log, we don't have a log!
            return e.getLocalizedMessage(context);

        } catch (@NonNull final IOException | SecurityException e) {
            Logger.error(TAG, e, "init failed");
            return context.getString(R.string.error_storage_not_writable);
        }
    }

    /**
     * Delete <strong>ALL</strong> files in our private directories.
     */
    public static void deleteAllContent()
            throws ExternalStorageException {

        File[] files;

        files = Upgrades.getDir().listFiles();
        if (files != null) {
            //noinspection ResultOfMethodCallIgnored
            Arrays.stream(files).forEach(File::delete);
        }
        files = Cache.getDir().listFiles();
        if (files != null) {
            //noinspection ResultOfMethodCallIgnored
            Arrays.stream(files).forEach(File::delete);
        }
        files = Covers.getDir().listFiles();
        if (files != null) {
            //noinspection ResultOfMethodCallIgnored
            Arrays.stream(files).forEach(File::delete);
        }
        files = Log.getDir().listFiles();
        if (files != null) {
            //noinspection ResultOfMethodCallIgnored
            Arrays.stream(files).forEach(File::delete);
        }

        // we don't delete root, there are no files there
    }

    /**
     * Get the File object for this directory.
     * <p>
     * Dev. note: a device might have two (or more) external file directories of the same type.
     * i.e. {@link Context#getExternalFilesDir} for {@link Environment#DIRECTORY_PICTURES}
     * might internally resolve to two paths. Android will "ensure" those before returning them,
     * which (at least in the emulator) can result in log messages like this:
     * <pre>
     *     W/ContextImpl: Failed to ensure /storage/14ED-381E/Android/data/com.hardbacknutter
     *     .nevertoomanybooks/files/Pictures: java.lang.IllegalStateException:
     *     Failed to resolve /storage/14ED-381E/Android/data/com.hardbacknutter
     *     .nevertoomanybooks/files/Pictures:
     *     java.io.IOException: I/O error
     * </pre>
     * These can be ignored. The other "ensured" path will be returned for use as normal. flw...
     *
     * @return Directory (File) object
     *
     * @throws ExternalStorageException if the Shared Storage media is not available
     */
    @NonNull
    public File getDir()
            throws ExternalStorageException {

        if (mDir != null && mDir.exists()) {
            return mDir;
        }
        final Context appContext = ServiceLocator.getAppContext();

        switch (this) {
            case Covers:
                mDir = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                break;

            case Root:
                mDir = appContext.getExternalFilesDir(null);
                break;

            case Upgrades:
                mDir = new File(appContext.getExternalFilesDir(null), UPGRADES_SUB_DIR);
                break;

            case Log:
                mDir = new File(appContext.getExternalFilesDir(null), LOG_SUB_DIR);
                break;

            case Cache:
                mDir = appContext.getExternalCacheDir();
                break;
        }

        if (mDir == null) {
            throw new ExternalStorageException(this);
        }
        return mDir;
    }

    /**
     * Get the space free in this directory.
     *
     * @return Space in bytes free or {@link #ERROR_CANNOT_STAT} on error
     */
    public long getFreeSpace() {
        try {
            final StatFs stat = new StatFs(getDir().getPath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();

        } catch (@NonNull final IllegalArgumentException | ExternalStorageException e) {
            Logger.error(TAG, e);
            return ERROR_CANNOT_STAT;
        }
    }

    /**
     * Purge applicable files for this directory.
     * Recursively visits sub directories.
     *
     * @param reallyDelete {@code true} to actually delete files,
     *                     {@code false} to only sum file sizes in bytes
     * @param filter       (optional) to apply; {@code null} for all files.
     *
     * @return number of bytes (potentially) deleted
     */
    public long purge(final boolean reallyDelete,
                      @Nullable final FileFilter filter) {

        long totalSize = 0;
        try {
            for (final File file : collectFiles(getDir(), filter)) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.d(TAG, "purge", this + "|" + file.getName());
                }
                totalSize += file.length();
                if (reallyDelete) {
                    FileUtils.delete(file);
                }
            }
        } catch (@NonNull final ExternalStorageException ignore) {
            // ignore
        }

        return totalSize;
    }

    /**
     * Collect applicable files for this directory.
     * Recursively visits sub directories.
     *
     * @param filter (optional) to apply; {@code null} for all files.
     *
     * @return list of files
     */
    @NonNull
    public List<File> collectFiles(@Nullable final FileFilter filter)
            throws ExternalStorageException {
        try {
            return collectFiles(getDir(), filter);

        } catch (@NonNull final SecurityException e) {
            // not critical, just log it.
            Logger.error(TAG, e);
            // just return an empty list
            return new ArrayList<>();
        }
    }

    /**
     * Collect applicable files for the given directory.
     * Recursively visits sub directories.
     *
     * @param root   directory to collect from
     * @param filter (optional) to apply; {@code null} for all files.
     *
     * @return list of files
     */
    @NonNull
    private List<File> collectFiles(@NonNull final File root,
                                    @Nullable final FileFilter filter) {
        final List<File> files = new ArrayList<>();
        // sanity check
        if (root.isDirectory()) {
            //noinspection ConstantConditions
            for (final File file : root.listFiles(filter)) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    files.addAll(collectFiles(file, filter));
                }
            }
        }
        return files;
    }
}
