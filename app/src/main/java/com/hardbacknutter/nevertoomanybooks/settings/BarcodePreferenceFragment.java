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

        //noinspection ConstantConditions
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
        final ListPreference cameraPref = findPreference(Prefs.pk_camera_lens_facing);
        //noinspection ConstantConditions
        cameraPref.setEntries(cameraLabels);
        cameraPref.setEntryValues(cameraValues);
        cameraPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection ConstantConditions
        findPreference(Prefs.pk_sounds_scan_found_barcode)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue instanceof Boolean && (Boolean) newValue) {
                        SoundManager.beep(SoundManager.EVENT);
                    }
                    return true;
                });
        //noinspection ConstantConditions
        findPreference(Prefs.pk_sounds_scan_isbn_valid)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue instanceof Boolean && (Boolean) newValue) {
                        SoundManager.beep(SoundManager.POSITIVE);
                    }
                    return true;
                });
        //noinspection ConstantConditions
        findPreference(Prefs.pk_sounds_scan_isbn_invalid)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue instanceof Boolean && (Boolean) newValue) {
                        SoundManager.beep(SoundManager.NEGATIVE);
                    }
                    return true;
                });
    }

}
