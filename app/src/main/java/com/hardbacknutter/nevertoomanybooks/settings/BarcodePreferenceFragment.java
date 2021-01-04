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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.SharedPreferences;
import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import java.util.Map;

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

        final ListPreference preference = findPreference(Prefs.pk_camera_id_scan_barcode);
        if (preference != null) {
            //noinspection ConstantConditions
            final Map<String, Integer> cameras = CameraDetection.getCameras(getContext());
            final int max = cameras.size() + 1;

            // the camera lens-facing values in text
            final CharSequence[] entries = new CharSequence[max];
            // the camera id
            final CharSequence[] entryValues = new CharSequence[max];

            int i = 0;
            entries[0] = getString(R.string.system_default);
            entryValues[0] = "-1";
            for (final Map.Entry<String, Integer> camera : cameras.entrySet()) {
                i++;
                // the camera id
                entryValues[i] = camera.getKey();
                // We're assuming there will only be one front and/or one back camera.
                switch (camera.getValue()) {
                    case CameraMetadata.LENS_FACING_FRONT:
                        entries[i] = getString(R.string.camera_front);
                        break;

                    case CameraMetadata.LENS_FACING_BACK:
                        entries[i] = getString(R.string.camera_back);
                        break;

                    case CameraMetadata.LENS_FACING_EXTERNAL:
                    default:
                        // append the id for all other cameras.
                        entries[i] = getString(R.string.a_bracket_b_bracket,
                                               getString(R.string.camera_other),
                                               String.valueOf(i));
                        break;
                }
            }

            preference.setEntries(entries);
            preference.setEntryValues(entryValues);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mToolbar.setTitle(R.string.lbl_settings);
        mToolbar.setSubtitle(R.string.pg_barcode_scanner);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        switch (key) {
            case Prefs.pk_sounds_scan_found_barcode:
                if (preferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.zxing_beep);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_valid:
                if (preferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.beep_high);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_invalid:
                if (preferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.beep_low);
                }
                break;

            default:
                break;
        }

        super.onSharedPreferenceChanged(preferences, key);
    }
}
