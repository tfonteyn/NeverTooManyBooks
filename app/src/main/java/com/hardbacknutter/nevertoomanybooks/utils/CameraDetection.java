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
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public class CameraDetection {

    private static final String TAG = "CameraDetection";

    /**
     * Pairs of cameraId and lens-facing ID.
     * <ul>
     *     <li>{@link CameraMetadata#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_BACK}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_EXTERNAL}</li>
     * </ul>
     *
     * @param context Current context
     *
     * @return list of Pairs with camera-id and the lens-facing id
     */
    @NonNull
    public static List<Pair<String, Integer>> getCameras(@NonNull final Context context) {

        final List<Pair<String, Integer>> list = new ArrayList<>();

        final CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            //noinspection ConstantConditions
            final String[] cameraIdList = cm.getCameraIdList();
            for (final String cameraId : cameraIdList) {
                final CameraCharacteristics cc = cm.getCameraCharacteristics(cameraId);
                list.add(new Pair<>(cameraId, cc.get(CameraCharacteristics.LENS_FACING)));
            }
        } catch (@NonNull final CameraAccessException e) {
            Logger.error(context, TAG, e);
        }

        return list;
    }
}
