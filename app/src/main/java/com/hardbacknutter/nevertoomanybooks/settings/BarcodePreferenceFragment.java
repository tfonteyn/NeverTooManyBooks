/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.search.ScanMode;
import com.hardbacknutter.nevertoomanybooks.utils.CameraConfig;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;


@Keep
public class BarcodePreferenceFragment
        extends BaseSettingsFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "BarcodePreferenceFrg";

    @SuppressWarnings("CodeBlock2Expr")
    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(R.string.pt_barcode_scanner);

        factory.singleChoice(ScanMode.PK_SCANNER_MODE_SINGLE,
                             R.string.pt_scan_mode,
                             R.array.pe_scan_mode_single,
                             R.array.pv_scan_mode_single, null, p -> {
                    p.setIcon(R.drawable.barcode_24px);
                    p.setSelectedIndex(1);
                });

        factory.singleChoice(CameraConfig.PK_CAMERA_LENS_FACING,
                             R.string.pt_camera_lens_facing, null, p -> {
                    p.setIcon(R.drawable.photo_camera_24px);

                    @SuppressWarnings("DataFlowIssue")
                    final CameraConfig cameraConfig = new CameraConfig(getContext());
                    final List<Integer> cameras = cameraConfig.getAvailableLensFacingIds();
                    // Add 1 for the system-default value
                    final int size = cameras.size() + 1;
                    final CharSequence[] labels = new CharSequence[size];
                    final CharSequence[] values = new CharSequence[size];
                    labels[0] = getString(R.string.lbl_system_default);
                    values[0] = "-1";

                    int i = 0;
                    for (final Integer value : cameras) {
                        i++;
                        values[i] = String.valueOf(value);
                        if (value == CameraMetadata.LENS_FACING_FRONT) {
                            labels[i] = getString(R.string.pe_camera_front);
                        } else if (value == CameraMetadata.LENS_FACING_BACK) {
                            labels[i] = getString(R.string.pe_camera_back);
                        }
                    }

                    p.setEntries(labels);
                    p.setEntryValues(values);
                    p.setSelectedIndex(0);
                });

        factory.bool(CameraConfig.PK_CAMERA_ZOOM_CONTROL_SHOW,
                     R.string.pt_barcode_zoom_control,
                     R.string.disabled, R.string.enabled, null, p -> {
                    p.setIcon(R.drawable.loupe_24px);
                });
        factory.bool(CameraConfig.PK_CAMERA_AUTO_FOCUS,
                     R.string.pt_camera_auto_focus,
                     R.string.disabled, R.string.enabled, null, p -> {
                    p.setIcon(R.drawable.center_focus_weak_24px);
                    p.setChecked(true);
                });

        factory.header(R.string.pt_barcode_sounds);

        factory.bool(SoundManager.PK_SOUNDS_SCAN_FOUND_BARCODE,
                     R.string.pt_scanning_beep_on_barcode_found,
                     (s, newValue) -> onChangeSound(newValue, SoundManager.EVENT),
                     p -> {
                    p.setIcon(R.drawable.surround_sound_24px);
                    p.setChecked(true);
                });
        factory.bool(SoundManager.PK_SOUNDS_SCAN_ISBN_VALID,
                     R.string.pt_scanning_beep_on_valid,
                     (s, newValue) -> onChangeSound(newValue, SoundManager.POSITIVE),
                     p -> {
                    p.setIcon(R.drawable.surround_sound_24px);
                });
        factory.bool(SoundManager.PK_SOUNDS_SCAN_ISBN_INVALID,
                     R.string.pt_scanning_beep_on_invalid,
                     (s, newValue) -> onChangeSound(newValue, SoundManager.NEGATIVE),
                     p -> {
                    p.setIcon(R.drawable.surround_sound_24px);
                    p.setChecked(true);
                });

        return factory;
    }

    private boolean onChangeSound(@Nullable final Object newValue,
                                  @SoundManager.Tone final int event) {
        final boolean enable = newValue != null && (boolean) newValue;
        if (enable) {
            SoundManager.beep(event);
        }
        return true;
    }
}
