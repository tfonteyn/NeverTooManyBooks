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
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public final class CameraDetection {

    /** Log tag. */
    private static final String TAG = "CameraDetection";

    private CameraDetection() {
    }

    /**
     * Get available cameraId and lens-facing ID.
     *
     * <ul>
     *     <li>{@link CameraMetadata#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_BACK}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_EXTERNAL}</li>
     * </ul>
     *
     * @param context Current context
     *
     * @return Map: key: camera-id, value: the lens-facing id
     */
    @NonNull
    public static Map<String, Integer> getCameras(@NonNull final Context context) {

        final Map<String, Integer> map = new HashMap<>();

        final CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            final String[] cameraIdList = cm.getCameraIdList();
            for (final String cameraId : cameraIdList) {
                final CameraCharacteristics cc = cm.getCameraCharacteristics(cameraId);
                map.put(cameraId, cc.get(CameraCharacteristics.LENS_FACING));
            }
        } catch (@NonNull final CameraAccessException e) {
            Logger.error(context, TAG, e);
        }

        return map;
    }

    /**
     * Get the user preferred camera id.
     *
     * @param context Current context
     * @param global  Global preferences
     *
     * @return camera id, or {@code -1} for no-preference
     */
    public static int getPreferredCameraId(@NonNull final Context context,
                                           @NonNull final SharedPreferences global) {
        // By default -1, which for the scanner IntentIntegrator call means 'no preference'
        int cameraId = ParseUtils.getIntListPref(global, Prefs.pk_camera_id_scan_barcode, -1);
        // we must verify the id, as the preference could have been imported from another device
        if (!getCameras(context).containsKey(String.valueOf(cameraId))) {
            cameraId = -1;
        }

        return cameraId;
    }
}
