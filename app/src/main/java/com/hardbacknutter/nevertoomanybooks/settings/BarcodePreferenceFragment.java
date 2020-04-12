/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;

/**
 * Used/defined in xml/preferences.xml
 */
public class BarcodePreferenceFragment
        extends BasePreferenceFragment {

    /**
     * The user modified the scanner in preferences (or not).
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_SCANNER_MODIFIED = "scannerModified";

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState,
                                    final String rootKey) {

        setPreferencesFromResource(R.xml.preferences_barcodes, rootKey);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        switch (key) {
            case Prefs.pk_scanner_preferred:
                //noinspection ConstantConditions
                ScannerManager.installScanner(getActivity(), success -> {
                    if (!success) {
                        //noinspection ConstantConditions
                        ScannerManager.setDefaultScanner(getContext());
                    }
                });
                mResultDataModel.putResultData(BKEY_SCANNER_MODIFIED, true);
                break;

            case Prefs.pk_sounds_scan_found_barcode:
                if (sharedPreferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.zxing_beep);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_valid:
                if (sharedPreferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.beep_high);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_invalid:
                if (sharedPreferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.beep_low);
                }
                break;


            default:
                break;
        }

        super.onSharedPreferenceChanged(sharedPreferences, key);
    }
}
