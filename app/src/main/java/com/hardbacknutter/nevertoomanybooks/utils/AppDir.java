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
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * The set of named directories used and support functions for them.
 * <p>
 * 2021-04: no longer using the dedicated cache dir now that we support multiple storage volumes.
 */
public enum AppDir {
    /** Nothing stored here, just the sub directories. */
    Root(null, null),

    /** The book covers. */
    Covers(Environment.DIRECTORY_PICTURES, null),
    /** Temp files: covers before they become permanent + debug report files. */
    Temp(null, "tmp"),
    /** log files. */
    Log(null, "log"),
    /** Database backup taken during app upgrades. */
    Upgrades(null, "Upgrades");

    /** The length of a UUID string. */
    public static final int UUID_LEN = 32;
    /** Log tag. */
    private static final String TAG = "AppDir";
    @Nullable
    private final String mSubDir;
    @Nullable
    private final String mType;
    /** The physical directory. */
    @Nullable
    private File mDir;

    AppDir(@Nullable final String type,
           @Nullable final String subDir) {
        mType = type;
        mSubDir = subDir;
    }

    /**
     * Initialize storage needs.
     * <p>
     * Dev note: if the desired volume index is not found, then we revert to '0'.
     * Hence the return value will either be the desired==actual volume or '0'.
     *
     * @param context Current context
     * @param volume  the desired volume
     *
     * @return the actual volume
     *
     * @throws ExternalStorageException on any failure.
     */
    public static int initVolume(@NonNull final Context context,
                                 final int volume)
            throws ExternalStorageException {

        final StorageManager storage = (StorageManager)
                context.getSystemService(Context.STORAGE_SERVICE);
        final List<StorageVolume> storageVolumes = storage.getStorageVolumes();

        final int actualVolume;
        if (volume >= storageVolumes.size()) {
            // The most obvious issue:
            // Storage was configured to be on SDCARD (index==1),
            // but the SDCARD was ejected AND removed.
            // We set "0" and go ahead for now, so at least we get a valid Log folder.
            actualVolume = 0;

        } else if (!Environment.MEDIA_MOUNTED.equals(storageVolumes.get(volume).getState())) {
            // The second most obvious issue:
            // Storage was configured to be on SDCARD (index==1),
            // but the SDCARD was eject and NOT removed: status wil be MEDIA_UNMOUNTED.
            // There are plenty of other possible issues but they are not as easy to handle
            // so we won't...
            // We set "0" and go ahead for now, so at least we get a valid Log folder.
            actualVolume = 0;

        } else {
            //FIXME: add one more, elaborate, check for situations where the SDCARD was REPLACED.
            // all fine.
            actualVolume = volume;
        }

        if (BuildConfig.DEBUG /* always */) {
            Logger.d(TAG, "initVolume", "volume=" + volume
                                        + "|actualVolume=" + actualVolume);
            dumpStorageInfo(context, storage);
        }

        for (final AppDir ad : values()) {
            ad.initDir(context, actualVolume);
        }

        // Prevent thumbnails showing up in the device Image Gallery.
        final File mif = new File(Covers.mDir, MediaStore.MEDIA_IGNORE_FILENAME);
        if (!mif.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                mif.createNewFile();
            } catch (@NonNull final IOException | SecurityException e) {
                throw new ExternalStorageException(Covers, "Failed to write .nomedia", e);
            }
        }

        return actualVolume;
    }

    private static void dumpStorageInfo(@NonNull final Context context,
                                        @NonNull final StorageManager storage) {
        // Typical emulator output:
        // 0    uuid=null
        //      Description=Internal shared storage
        //      Directory=/storage/emulated/0
        //      MediaStoreVolumeName=external_primary
        //      isPrimary=true
        //      isEmulated=true
        //      isRemovable=false
        //      getState=mounted
        //
        // 1    uuid=17FE-1508
        //      Description="SDCARD"
        //      Directory=/storage/17FE-1508
        //      MediaStoreVolumeName=17fe-1508
        //      isPrimary=false
        //      isEmulated=false
        //      isRemovable=true
        //      getState=mounted
        final List<StorageVolume> storageVolumes = storage.getStorageVolumes();
        for (final StorageVolume sv : storageVolumes) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Logger.d(TAG, "init",
                         "uuid=" + sv.getUuid()
                         + "|sv.getDescription=" + sv.getDescription(context)
                         + "|getDirectory=" + sv.getDirectory()
                         + "|getMediaStoreVolumeName=" + sv.getMediaStoreVolumeName()
                         + "|isPrimary=" + sv.isPrimary()
                         + "|isEmulated=" + sv.isEmulated()
                         + "|isRemovable=" + sv.isRemovable()
                         + "|getState=" + sv.getState());
            } else {
                Logger.d(TAG, "init",
                         "uuid=" + sv.getUuid()
                         + "|sv.getDescription=" + sv.getDescription(context)
                         + "|isPrimary=" + sv.isPrimary()
                         + "|isEmulated=" + sv.isEmulated()
                         + "|isRemovable=" + sv.isRemovable()
                         + "|getState=" + sv.getState());
            }
        }
    }

    public static void setVolume(@NonNull final Context context,
                                 final int volume) {
        // User is ok with the change.
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(Prefs.pk_storage_volume, String.valueOf(volume))
                         .apply();
    }

    public static int getVolume(@NonNull final Context context) {
        return Prefs.getIntListPref(PreferenceManager.getDefaultSharedPreferences(context),
                                    Prefs.pk_storage_volume, 0);
    }

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
                             final boolean reallyDelete)
            throws ExternalStorageException {

        // check for orphaned cover files
        final FileFilter coverFilter = file -> {
            if (file.getName().length() > UUID_LEN) {
                // not in the list? then we can purge it
                return !bookUuidList.contains(file.getName().substring(0, UUID_LEN));
            }
            // not a uuid base file ? be careful and leave it.
            return false;
        };

        final long totalSize;
        try {
            if (reallyDelete) {
                totalSize = FileUtils.deleteDirectory(Temp.getDir(), null, null)
                            + FileUtils.deleteDirectory(Log.getDir(), null, null)
                            + FileUtils.deleteDirectory(Upgrades.getDir(), null, null)
                            + FileUtils.deleteDirectory(Covers.getDir(), coverFilter, null);
            } else {
                totalSize = FileUtils.getUsedSpace(Temp.getDir(), null)
                            + FileUtils.getUsedSpace(Log.getDir(), null)
                            + FileUtils.getUsedSpace(Upgrades.getDir(), null)
                            + FileUtils.getUsedSpace(Covers.getDir(), coverFilter);
            }
        } catch (@NonNull final SecurityException e) {
            // not critical, just log it.
            Logger.error(TAG, e);
            return 0;
        }
        return totalSize;
    }

    /**
     * Get the File object for this directory.
     *
     * @return Directory (File) object
     *
     * @throws ExternalStorageException if the Shared Storage media is not available
     */
    // keep the ExternalStorageException declaration for future usage!
    @SuppressWarnings("RedundantThrows")
    @NonNull
    public File getDir()
            throws ExternalStorageException {
        return Objects.requireNonNull(mDir, "mDir");
    }

    public boolean exists() {
        try {
            return mDir != null && mDir.exists();
        } catch (@NonNull final SecurityException e) {
            return false;
        }
    }

    /**
     * Make sure the directory and any other conditions for this dir are fulfilled.
     * <p>
     * Dev. notes: A device might have two (or more) external file directories of the same type.
     * i.e. {@link Context#getExternalFilesDir} for {@link Environment#DIRECTORY_PICTURES}
     * might internally resolve several paths:
     * <ol>
     *     <li>the "external/shared" memory card, which is these days the builtin memory</li>
     *     <li>a removable sdcard</li>
     *     <li>other</li>
     * </ol>
     * <p>
     * Android will "ensure" those before returning them,
     * which (at least in the emulator) can result in log messages like this:
     * <pre>
     *     W/ContextImpl: Failed to ensure /storage/14ED-381E/Android/data/com.hardbacknutter
     *     .nevertoomanybooks/files/Pictures: java.lang.IllegalStateException:
     *     Failed to resolve /storage/14ED-381E/Android/data/com.hardbacknutter
     *     .nevertoomanybooks/files/Pictures:
     *     java.io.IOException: I/O error
     * </pre>
     * These can mostly be ignored.
     * The internal "ensured" path will be returned for use as normal. flw...
     * {@link Context#getExternalFilesDir} will always return element [0] (or {@code null})
     * <p>
     * To make debugging easier, we actually use {@link Context#getExternalFilesDirs}
     * and handle the index ourselves.
     *
     * @throws ExternalStorageException if the Shared Storage media is not available
     */
    private void initDir(@NonNull final Context context,
                         final int index)
            throws ExternalStorageException {

        final File[] externalFilesDirs = context.getExternalFilesDirs(mType);

        if (externalFilesDirs == null
            || externalFilesDirs.length < index
            || externalFilesDirs[index] == null
            || !externalFilesDirs[index].exists()) {
            throw new ExternalStorageException(this, "No volume: " + index);
        }

        if (mSubDir == null) {
            mDir = externalFilesDirs[index];

        } else {
            final File dir = new File(externalFilesDirs[index], mSubDir);
            if (!(dir.isDirectory() || dir.mkdirs())) {
                throw new ExternalStorageException(this, "No directory: " + mSubDir);
            }
            mDir = dir;
        }
    }

    /**
     * Get the space free in this directory.
     *
     * @return Space in bytes free, or {@code 0} on any failure
     */
    public long getFreeSpace() {
        try {
            return FileUtils.getFreeSpace(getDir());
        } catch (@NonNull final IOException e) {
            return 0;
        }
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
            return FileUtils.collectFiles(getDir(), filter);

        } catch (@NonNull final SecurityException e) {
            // not critical, just log it.
            Logger.error(TAG, e);
            // just return an empty list
            return new ArrayList<>();
        }
    }
}
