/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public final class CameraDetection {

    /** Log tag. */
    private static final String TAG = "CameraDetection";

    private static final int NO_PREFERENCE = -1;

    private CameraDetection() {
    }

    /**
     * Get available lens-facing id's.
     *
     * <ul>
     *     <li>{@link CameraMetadata#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_BACK}</li>
     * </ul>
     *
     * @param context Current context
     *
     * @return list with lens-facing id
     */
    @NonNull
    public static List<Integer> getCameras(@NonNull final Context context) {

        final List<Integer> list = new ArrayList<>();

        final CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            final String[] cameraIdList = cm.getCameraIdList();
            for (final String cameraId : cameraIdList) {
                final CameraCharacteristics cc = cm.getCameraCharacteristics(cameraId);
                list.add(cc.get(CameraCharacteristics.LENS_FACING));
            }
        } catch (@NonNull final CameraAccessException e) {
            ServiceLocator.getInstance().getLogger().error(TAG, e);
        }

        return list;
    }

    /**
     * Get the user preferred camera lens-facing identifier.
     * <p>
     * One of:
     * <ul>
     *     <li>{@link #NO_PREFERENCE} for no-preference</li>
     *     <li>{@link CameraMetadata#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_BACK}</li>
     * </ul>
     *
     * @param context Current context
     *
     * @return lens-facing identifier, or {@link #NO_PREFERENCE} for no-preference
     */
    public static int getPreferredCameraLensFacing(@NonNull final Context context) {
        // By default -1, which for the scanner contract call means 'no preference'
        int lensFacing = Prefs.getIntListPref(context, Prefs.pk_camera_lens_facing, NO_PREFERENCE);
        // we must verify the id, as the preference could have been imported from another device
        if (!getCameras(context).contains(lensFacing)) {
            lensFacing = NO_PREFERENCE;
        }

        return lensFacing;
    }
}
