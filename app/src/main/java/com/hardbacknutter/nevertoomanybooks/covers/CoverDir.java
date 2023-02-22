/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * The movable external directory for covers.
 * <p>
 * <strong>Dev. note:</strong> A device might have two (or seldom? more) external
 * file directories of the same type.
 * i.e. {@link Context#getExternalFilesDir} for {@link Environment#DIRECTORY_PICTURES}
 * might internally resolve several paths:
 * <ol>
 *     <li>the "external/shared"memory card,
 *         which is these days the builtin memory (a.k.a. emulated) </li>
 *     <li>a removable sdcard, under a protective cover</li>
 *     <li>other?</li>
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
 */
public final class CoverDir {

    /** Sub directory of the Covers directory. */
    private static final String DIR_TMP = "tmp";

    /** Log tag. */
    private static final String TAG = "CoverDir";

    private CoverDir() {
    }

    /**
     * Get the *permanent* directory where we store covers.
     *
     * @param context Current context
     *
     * @return directory
     *
     * @throws StorageException The covers directory is not available
     */
    @NonNull
    public static File getDir(@NonNull final Context context)
            throws StorageException {

        final int volume = getVolume(context);

        final File[] externalFilesDirs =
                context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES);

        if (externalFilesDirs == null
            || externalFilesDirs.length < volume
            || externalFilesDirs[volume] == null
            || !externalFilesDirs[volume].exists()) {
            throw new CoverStorageException("No volume: " + volume);
        }

        return externalFilesDirs[volume];
    }

    /**
     * Get the *temporary* directory where we store covers.
     * Currently this is a sub directory of the permanent one to facilitate move==renames.
     *
     * @param context Current context
     *
     * @return directory
     *
     * @throws StorageException The covers directory is not available
     */
    @NonNull
    public static File getTemp(@NonNull final Context context)
            throws StorageException {
        return new File(getDir(context), DIR_TMP);
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
     * @throws StorageException The covers directory is not available
     */
    public static int initVolume(@NonNull final Context context,
                                 final int volume)
            throws StorageException {

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
            LoggerFactory.getLogger()
                          .d(TAG, "initVolume", "volume=" + volume
                                                + "|actualVolume=" + actualVolume);
            dumpStorageInfo(context, storage);
        }

        // Make sure we can get the directory, no need to create.
        final File coverDir = getDir(context);

        // Prevent thumbnails showing up in the device Image Gallery.
        final File mif = new File(coverDir, MediaStore.MEDIA_IGNORE_FILENAME);
        if (!mif.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                mif.createNewFile();
            } catch (@NonNull final IOException | SecurityException e) {
                throw new CoverStorageException("Failed to write .nomedia", e);
            }
        }

        // Make sure we can get the directory, and create the sub directory if needed
        final File tmpDir = new File(coverDir, DIR_TMP);
        if (!(tmpDir.isDirectory() || tmpDir.mkdirs())) {
            throw new CoverStorageException("No tmp directory");
        }

        return actualVolume;
    }

    public static void setVolume(@NonNull final Context context,
                                 final int volume) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(Prefs.pk_storage_volume, String.valueOf(volume))
                         .apply();
    }

    public static int getVolume(@NonNull final Context context) {
        return IntListPref.getInt(context, Prefs.pk_storage_volume, 0);
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
        final Logger logger = LoggerFactory.getLogger();
        final List<StorageVolume> storageVolumes = storage.getStorageVolumes();
        for (final StorageVolume sv : storageVolumes) {
            if (Build.VERSION.SDK_INT >= 30) {
                logger.d(TAG, "init",
                         "uuid=" + sv.getUuid()
                         + "|sv.getDescription=" + sv.getDescription(context)
                         + "|getDirectory=" + sv.getDirectory()
                         + "|getMediaStoreVolumeName=" + sv.getMediaStoreVolumeName()
                         + "|isPrimary=" + sv.isPrimary()
                         + "|isEmulated=" + sv.isEmulated()
                         + "|isRemovable=" + sv.isRemovable()
                         + "|getState=" + sv.getState());
            } else {
                logger.d(TAG, "init",
                         "uuid=" + sv.getUuid()
                         + "|sv.getDescription=" + sv.getDescription(context)
                         + "|isPrimary=" + sv.isPrimary()
                         + "|isEmulated=" + sv.isEmulated()
                         + "|isRemovable=" + sv.isRemovable()
                         + "|getState=" + sv.getState());
            }
        }
    }
}
