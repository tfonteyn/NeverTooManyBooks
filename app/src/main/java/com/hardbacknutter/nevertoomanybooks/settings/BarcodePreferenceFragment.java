/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.CameraDetection;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class BarcodePreferenceFragment
        extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_barcodes, rootKey);

        //noinspection DataFlowIssue
        final List<Integer> cameras = CameraDetection.getCameras(getContext());
        final int max = cameras.size() + 1;
        final CharSequence[] cameraLabels = new CharSequence[max];
        final CharSequence[] cameraValues = new CharSequence[max];
        cameraLabels[0] = getString(R.string.lbl_system_default);
        cameraValues[0] = "-1";

        int i = 0;
        for (final Integer value : cameras) {
            i++;
            cameraValues[i] = String.valueOf(value);
            if (value == CameraMetadata.LENS_FACING_FRONT) {
                cameraLabels[i] = getString(R.string.pe_camera_front);
            } else if (value == CameraMetadata.LENS_FACING_BACK) {
                cameraLabels[i] = getString(R.string.pe_camera_back);
            }
        }
        final ListPreference cameraPref = findPreference(Prefs.PK_CAMERA_LENS_FACING);
        //noinspection DataFlowIssue
        cameraPref.setEntries(cameraLabels);
        cameraPref.setEntryValues(cameraValues);

        //noinspection DataFlowIssue
        findPreference(SoundManager.PK_SOUNDS_SCAN_FOUND_BARCODE)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue instanceof Boolean && (Boolean) newValue) {
                        SoundManager.beep(SoundManager.EVENT);
                    }
                    return true;
                });
        //noinspection DataFlowIssue
        findPreference(SoundManager.PK_SOUNDS_SCAN_ISBN_VALID)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue instanceof Boolean && (Boolean) newValue) {
                        SoundManager.beep(SoundManager.POSITIVE);
                    }
                    return true;
                });
        //noinspection DataFlowIssue
        findPreference(SoundManager.PK_SOUNDS_SCAN_ISBN_INVALID)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue instanceof Boolean && (Boolean) newValue) {
                        SoundManager.beep(SoundManager.NEGATIVE);
                    }
                    return true;
                });
    }

}
