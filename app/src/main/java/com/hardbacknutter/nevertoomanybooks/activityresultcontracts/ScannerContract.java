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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.CameraDetection;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

/**
 * <ul>
 *     <li>param: the hosting Fragment</li>
 *     <li>return: the barcode as a String, can be {@code null}</li>
 * </ul>
 */
public class ScannerContract
        extends ActivityResultContract<Fragment, Optional<String>> {

    private static final String TAG = "ScannerContract";

    /**
     * Optionally beep if the scan succeeded.
     *
     * @param context Current context
     */
    public static void onValidBarcodeBeep(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.pk_sounds_scan_isbn_valid, false)) {
            SoundManager.beep(SoundManager.POSITIVE);
        }
    }

    /**
     * Optionally beep if the scan failed.
     *
     * @param context Current context
     */
    public static void onInvalidBarcodeBeep(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.pk_sounds_scan_isbn_invalid, true)) {
            SoundManager.beep(SoundManager.NEGATIVE);
        }
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Fragment fragment) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Beep when a barcode was recognised
        final boolean beep = prefs.getBoolean(Prefs.pk_sounds_scan_found_barcode, true);

        return new ScanOptions()
                .setCameraId(CameraDetection.getPreferredCameraId(context))
                .setOrientationLocked(false)
                .setPrompt(context.getString(R.string.zxing_msg_default_status))
                .setBeepEnabled(beep)
                .createScanIntent(context);
    }

    @NonNull
    @Override
    public Optional<String> parseResult(final int resultCode,
                                        @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.FAKE_BARCODE_SCANNER) {
            final String[] testCodes = {
                    // random == 0 -> cancel scanning
                    null,
                    "9780316310420",
                    "9781250762849",
                    "9781524763169",
                    "9780062868930",
                    "9781786892713",
                    "9780349701462",
                    "9781635574043",
                    "9781538719985",
                    "9780593230251",
                    "9780063032491",
                    };
            final int random = (int) Math.floor(Math.random() * 10);
            final String barCode = testCodes[random];
            Log.d(TAG, "onBarcodeScanned|Faking barcode=" + barCode);
            if (barCode == null) {
                return Optional.empty();
            } else {
                return Optional.of(barCode);
            }
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }
        // parse and return the barcode
        return Optional.of(ScanIntentResult.parseActivityResult(resultCode, intent).getContents());
    }
}
