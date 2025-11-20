/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * The movable external volume for covers.
 * <p>
 * This class encapsulates interactions with {@code Context.STORAGE_SERVICE}
 * and the preferences for the volume to use.
 * <p>
 * <strong>Dev. note:</strong> A device might have two (or seldom? more) external
 * file directories of the same type.
 * i.e. {@link Context#getExternalFilesDir} for {@link Environment#DIRECTORY_PICTURES}
 * might internally resolve several paths:
 * <ol>
 *     <li>the "external/shared"memory card,
 *         which is these days the built-in memory (a.k.a. emulated) </li>
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
public final class CoverVolume {

    /**
     * Preference key with the <strong>index</strong> of the volume to store covers on.
     * <p>
     * {@code int}
     */
    public static final String PK_VOLUME_INDEX = "storage.volume.index";

    /** Log tag. */
    private static final String TAG = "CoverVolume";

    private CoverVolume() {
    }

    /**
     * Set the user preferred volume.
     *
     * @param context Current context
     * @param volume  to set
     */
    public static void setVolume(@NonNull final Context context,
                                 final int volume) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(PK_VOLUME_INDEX, String.valueOf(volume))
                         .apply();
    }

    /**
     * Get the currently configured (user preferred) volume.
     *
     * @param context Current context
     *
     * @return the volume
     */
    public static int getVolume(@NonNull final Context context) {
        return IntListPref.getInt(context, PK_VOLUME_INDEX, 0);
    }

    /**
     * Check if the given volume index exists and is mounted/accessible.
     *
     * @param context Current context
     * @param volume  of the volume
     *
     * @return flag
     */
    public static boolean isAvailable(@NonNull final Context context,
                                      final int volume) {
        final StorageManager manager = (StorageManager)
                context.getSystemService(Context.STORAGE_SERVICE);

        final List<StorageVolume> storageVolumes = manager.getStorageVolumes();

        final boolean available;
        if (volume >= storageVolumes.size()) {
            // The most obvious issue:
            // Storage was configured to be on SDCARD (index==1),
            // but the SDCARD was ejected AND removed.
            available = false;

        } else if (volume < 0) {
            // Paranoia... in case we got a broken setting from somewhere
            available = false;

        } else //noinspection RedundantIfStatement
            if (!Environment.MEDIA_MOUNTED.equals(storageVolumes.get(volume).getState())) {
                // The second most obvious issue:
                // Storage was configured to be on SDCARD (index==1),
                // but the SDCARD was eject and NOT removed: status wil be MEDIA_UNMOUNTED.
                // There are plenty of other possible issues but they are not as easy to handle
                // so we won't...
                available = false;

            } else {
                //FIXME: add one more, elaborate, check for situations
                // where the SDCARD was REPLACED.
                available = true;
            }

        if (!available || BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, "isAvailable",
                                        "volumeWanted=" + volume
                                        + "|available=" + available);
            dumpStorageInfo(context, manager);
        }

        return available;
    }

    /**
     * Get the list of available/mounted volumes.
     *
     * @param context Current context
     *
     * @return list
     */
    @NonNull
    public static List<StorageVolume> getAvailable(@NonNull final Context context) {
        final StorageManager storage = (StorageManager)
                context.getSystemService(Context.STORAGE_SERVICE);

        return storage.getStorageVolumes()
                      .stream()
                      .filter(sv -> Environment.MEDIA_MOUNTED.equals(sv.getState()))
                      .collect(Collectors.toList());
    }

    /**
     * Get a reference to the volume at the given index.
     *
     * @param context Current context
     * @param volume  of the volume
     *
     * @return volume
     *
     * @throws CoverStorageException if the indexed volume is not available
     */
    @NonNull
    public static StorageVolume getStorageVolume(@NonNull final Context context,
                                                 final int volume)
            throws CoverStorageException {
        final StorageManager storage = (StorageManager) context.getSystemService(
                Context.STORAGE_SERVICE);
        final List<StorageVolume> volumes = storage.getStorageVolumes();
        if (volume >= volumes.size()) {
            throw new CoverStorageException("Volume not available");
        }
        return volumes.get(volume);
    }

    private static void dumpStorageInfo(@NonNull final Context context,
                                        @NonNull final StorageManager manager) {
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
        final List<StorageVolume> storageVolumes = manager.getStorageVolumes();
        for (final StorageVolume sv : storageVolumes) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                logger.d(TAG, "dumpStorageInfo",
                         "uuid=" + sv.getUuid()
                         + "|sv.getDescription=" + sv.getDescription(context)
                         + "|getDirectory=" + sv.getDirectory()
                         + "|getMediaStoreVolumeName=" + sv.getMediaStoreVolumeName()
                         + "|isPrimary=" + sv.isPrimary()
                         + "|isEmulated=" + sv.isEmulated()
                         + "|isRemovable=" + sv.isRemovable()
                         + "|getState=" + sv.getState());
            } else {
                logger.d(TAG, "dumpStorageInfo",
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
