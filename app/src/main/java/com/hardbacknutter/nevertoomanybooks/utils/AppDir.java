/*
 * @Copyright 2020 HardBackNutter
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
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * The set of named directories used and support functions for them.
 * <p>
 * TODO: ExternalStorageException added were appropriate, but other than here we don't catch them.
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
     * @param context      Current context
     * @param bookUuidList a list of book uuid to check for orphaned covers
     * @param reallyDelete {@code true} to actually delete files,
     *                     {@code false} to only sum file sizes in bytes
     *
     * @return the total size in bytes of purgeable/purged files.
     */
    public static long purge(@NonNull final Context context,
                             @NonNull final Collection<String> bookUuidList,
                             final boolean reallyDelete) {
        long totalSize = 0;

        try {
            // just trash these dirs
            totalSize += Cache.purge(context, reallyDelete, null);
            totalSize += Log.purge(context, reallyDelete, null);
            totalSize += Upgrades.purge(context, reallyDelete, null);

            // check for orphaned cover files
            totalSize += Covers.purge(context, reallyDelete, file -> {
                if (file.getName().length() > UUID_LEN) {
                    // not in the list? then we can purge it
                    return !bookUuidList.contains(file.getName().substring(0, UUID_LEN));
                }
                // not a uuid base file ? be careful and leave it.
                return false;
            });

        } catch (@NonNull final SecurityException | ExternalStorageException e) {
            // not critical, just log it.
            Logger.error(context, TAG, e);
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
            return context.getString(R.string.error_storage_not_accessible,
                                     context.getString(R.string.unknown));
        }

        try {
            // check we can get our root.
            Root.get(context);

            // create sub directories if needed
            final AppDir[] appDirs = {Log, Upgrades};
            for (AppDir appDir : appDirs) {
                final File dir = appDir.get(context);
                if (!(dir.isDirectory() || dir.mkdirs())) {
                    return context.getString(R.string.error_storage_not_writable);
                }
            }

            // Prevent thumbnails showing up in the device Image Gallery.
            //noinspection ResultOfMethodCallIgnored
            Covers.getFile(context, MediaStore.MEDIA_IGNORE_FILENAME).createNewFile();

            return null;

        } catch (@NonNull final ExternalStorageException e) {
            // Don't log, we don't have a log!
            return context.getString(R.string.error_storage_not_writable);

        } catch (@NonNull final IOException | SecurityException e) {
            Logger.error(context, TAG, e, "init failed");
            return context.getString(R.string.error_storage_not_writable);
        }
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
     * @param context Current context
     *
     * @return Directory (File) object
     *
     * @throws ExternalStorageException if the Shared Storage media is not available
     */
    @NonNull
    public File get(@NonNull final Context context)
            throws ExternalStorageException {

        if (mDir != null && mDir.exists()) {
            return mDir;
        }

        switch (this) {
            case Covers:
                mDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                break;

            case Root:
                mDir = context.getExternalFilesDir(null);
                break;

            case Upgrades:
                mDir = new File(context.getExternalFilesDir(null), UPGRADES_SUB_DIR);
                break;

            case Log:
                mDir = new File(context.getExternalFilesDir(null), LOG_SUB_DIR);
                break;

            case Cache:
                mDir = context.getExternalCacheDir();
                break;
        }

        if (mDir == null) {
            final String msg = context.getString(R.string.error_storage_not_accessible,
                                                 this.toString());
            throw new ExternalStorageException(msg);
        }
        return mDir;
    }

    /**
     * Get a file with the given name in this directory.
     *
     * @param context Current context
     * @param name    file name
     *
     * @return Directory (File) object
     *
     * @throws ExternalStorageException if the Shared Storage media is not available
     */
    @NonNull
    public File getFile(@NonNull final Context context,
                        @NonNull final String name)
            throws ExternalStorageException {
        return new File(get(context), name);
    }

    /**
     * Get a file with a random/temporary name in this directory.
     *
     * @param context Current context
     *
     * @return Directory (File) object
     *
     * @throws ExternalStorageException if the Shared Storage media is not available
     */
    @NonNull
    public File getFile(@NonNull final Context context)
            throws ExternalStorageException {
        return new File(get(context), System.nanoTime() + ".tmp");
    }

    /**
     * Get the space free in this directory.
     *
     * @param context Current context
     *
     * @return Space in bytes free or {@link #ERROR_CANNOT_STAT} on error
     */
    public long getFreeSpace(@NonNull final Context context) {
        try {
            StatFs stat = new StatFs(get(context).getPath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();

        } catch (@NonNull final IllegalArgumentException | ExternalStorageException e) {
            Logger.error(context, TAG, e);
            return ERROR_CANNOT_STAT;
        }
    }

    /**
     * Purge applicable files for this directory.
     * Recursively visits sub directories.
     *
     * @param context      Current context
     * @param reallyDelete {@code true} to actually delete files,
     *                     {@code false} to only sum file sizes in bytes
     * @param filter       (optional) to apply; {@code null} for all files.
     *
     * @return number of bytes (potentially) deleted
     *
     * @throws ExternalStorageException if the Shared Storage media is not available
     */
    private long purge(@NonNull final Context context,
                       final boolean reallyDelete,
                       @SuppressWarnings("SameParameterValue")
                       @Nullable final FileFilter filter)
            throws ExternalStorageException {

        final List<File> files = collectFiles(get(context), filter);
        long totalSize = 0;
        for (File file : files) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.d(TAG, "purge", this + "|" + file.getName());
            }
            totalSize += file.length();
            if (reallyDelete) {
                FileUtils.delete(file);
            }
        }
        return totalSize;
    }

    /**
     * Collect applicable files for this directory.
     * Recursively visits sub directories.
     *
     * @param context Current context
     * @param filter  (optional) to apply; {@code null} for all files.
     *
     * @return list of files
     */
    @NonNull
    public List<File> collectFiles(@NonNull final Context context,
                                   @Nullable final FileFilter filter) {
        try {
            return collectFiles(get(context), filter);
        } catch (@NonNull final SecurityException | ExternalStorageException e) {
            // not critical, just log it.
            Logger.error(context, TAG, e);
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
        List<File> files = new ArrayList<>();
        // sanity check
        if (root.isDirectory()) {
            //noinspection ConstantConditions
            for (File file : root.listFiles(filter)) {
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
