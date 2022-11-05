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
 *     <li>return: the barcode String</li>
 * </ul>
 */
public class ScannerContract
        extends ActivityResultContract<Void, Optional<String>> {

    private static final String TAG = "ScannerContract";

    private boolean beep;

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @Nullable final Void aVoid) {
        // Beep in parseResult if a barcode was returned
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        beep = prefs.getBoolean(Prefs.pk_sounds_scan_found_barcode, true);

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

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final String text = ScanIntentResult.parseActivityResult(resultCode, intent).getText();
        if (text != null && !text.isBlank()) {
            if (beep) {
                SoundManager.beep(SoundManager.EVENT);
            }
            return Optional.of(text);
        } else {
            return Optional.empty();
        }
    }
}
