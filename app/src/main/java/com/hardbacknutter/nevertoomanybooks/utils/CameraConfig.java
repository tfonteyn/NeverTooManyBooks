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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.util.logger.LoggerFactory;

public final class CameraConfig {

    /**
     * The user preferred lens.
     * <ul>
     *     <li>{@link #NO_LENS_FACING_PREFERENCE}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_BACK}</li>
     * </ul>
     * int, default {@link #NO_LENS_FACING_PREFERENCE}
     */
    public static final String PK_CAMERA_LENS_FACING = "camera.lens.facing";
    private static final int NO_LENS_FACING_PREFERENCE = -1;

    /**
     * Show or hide the zoom-control slider.
     * <p>
     * 2025-08-18 see github #181:
     * User reported that their Xiaomi Note 12 Pro+, Android 13
     * was having trouble focusing and more than half of the time, when
     * focus finally worked, the image was not properly decoded.
     * It seems Xiaomi devices have a known issue with focussing on close-up
     * objects and tend to take bad/blurry (?) images at that range.
     * Ultimate solution was to add a zoom-control slider.
     * As this only affects Xiaomi users and the slider takes up quite
     * some space when using the embedded scanner, we've made this a setting.
     * TODO: allow finger-pinch gesture to zoom instead
     * <p>
     * boolean, default {@code false}.
     */
    public static final String PK_CAMERA_ZOOM_CONTROL_SHOW = "camera.zoom.control.show";
    /** Stores the current value. */
    public static final String PK_CAMERA_ZOOM_CONTROL_VALUE = "camera.zoom.control.value";
    private static final float DEFAULT_ZOOM_VALUE = 0.0f;

    /** boolean, default {@code true}. */
    private static final String PK_CAMERA_AUTO_FOCUS = "camera.auto.focus";

    /** Stores the current status. */
    private static final String PK_CAMERA_TORCH_STATUS = "camera.torch.status";

    /** Log tag. */
    private static final String TAG = "CameraConfig";

    private final List<Integer> lensIds = new ArrayList<>();

    private boolean torchEnabled;
    @FloatRange(from = 0.0, to = 1.0)
    private float zoomValue;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public CameraConfig(@NonNull final Context context) {
        final CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            final String[] cameraIdList = cm.getCameraIdList();
            for (final String cameraId : cameraIdList) {
                final CameraCharacteristics characteristics = cm.getCameraCharacteristics(cameraId);
                lensIds.add(characteristics.get(CameraCharacteristics.LENS_FACING));
            }
        } catch (@NonNull final CameraAccessException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }

        final SharedPreferences p = ServiceLocator.getInstance().getSharedPreferences();

        torchEnabled = p.getBoolean(PK_CAMERA_TORCH_STATUS, false);
        zoomValue = p.getFloat(PK_CAMERA_ZOOM_CONTROL_VALUE, DEFAULT_ZOOM_VALUE);
    }

    /**
     * Get available lens-facing id's.
     *
     * <ul>
     *     <li>{@link CameraMetadata#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_BACK}</li>
     * </ul>
     *
     * @return list with lens-facing id
     */
    @NonNull
    public List<Integer> getAvailableLensFacingIds() {
        return lensIds;
    }

    /**
     * Get the user preferred camera lens-facing identifier.
     * <p>
     * One of:
     * <ul>
     *     <li>{@link #NO_LENS_FACING_PREFERENCE} for no-preference</li>
     *     <li>{@link CameraMetadata#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraMetadata#LENS_FACING_BACK}</li>
     * </ul>
     *
     * @return lens-facing identifier, or {@link #NO_LENS_FACING_PREFERENCE} for no-preference
     */
    public int getLensFacing() {
        final SharedPreferences p = ServiceLocator.getInstance().getSharedPreferences();
        // By default -1, which for the scanner contract call means 'no preference'
        final int lensFacing = IntListPref.getInt(p, PK_CAMERA_LENS_FACING,
                                                  NO_LENS_FACING_PREFERENCE);
        // we must verify the id, as the preference could have been imported from another device
        if (lensIds.contains(lensFacing)) {
            return lensFacing;
        }
        return NO_LENS_FACING_PREFERENCE;
    }

    /**
     * Desired state of the torch.
     *
     * @return flag
     */
    public boolean isTorchEnabled() {
        return torchEnabled;
    }

    /**
     * Desired state of the torch.
     *
     * @param enabled flag
     */
    public void setTorchEnabled(final boolean enabled) {
        this.torchEnabled = enabled;
    }

    /**
     * Whether a zoom-control slider should be shown or hidden in the UI.
     * <p>
     * Default: {@code false}.
     *
     * @return flag
     */
    public boolean isZoomControlEnabled() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_CAMERA_ZOOM_CONTROL_SHOW, false);
    }

    /**
     * Whether auto-focus should be
     * {@code false}: left to the device,
     * or {@code true}: controlled by the app .
     * <p>
     * Default: {@code true}
     *
     * @return flag
     */
    public boolean isAutoFocus() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_CAMERA_AUTO_FOCUS, true);
    }

    /**
     * Get the current value to apply for linear zooming.
     *
     * @return value
     */
    @FloatRange(from = 0.0, to = 1.0)
    public float getZoomValue() {
        return zoomValue;
    }

    /**
     * Set the current value to apply for linear zooming.
     *
     * @param value to store
     */
    public void setZoomValue(@FloatRange(from = 0.0, to = 1.0) final float value) {
        this.zoomValue = MathUtils.clamp(value, 0f, 1f);
    }

    /**
     * Store the user preferred settings for zoom and torch.
     */
    public void saveSettings() {
        // set via preference screen only
        // lensFacing, showZoomControl
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(PK_CAMERA_TORCH_STATUS, torchEnabled)
                      .putFloat(PK_CAMERA_ZOOM_CONTROL_VALUE, zoomValue)
                      .apply();
    }
}
