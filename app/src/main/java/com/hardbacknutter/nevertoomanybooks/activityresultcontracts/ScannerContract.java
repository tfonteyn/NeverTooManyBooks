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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.zxing.integration.android.IntentIntegrator;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * <ul>
 *     <li>param: the hosting Fragment</li>
 *     <li>return: the barcode as a String, can be {@code null}</li>
 * </ul>
 */
public class ScannerContract
        extends ActivityResultContract<Fragment, String> {

    /** Log tag. */
    private static final String TAG = "ScannerContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Fragment fragment) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // By default -1, which for the IntentIntegrator call means 'no preference'
        final int cameraId = ParseUtils.getIntListPref(prefs, Prefs.pk_camera_id_scan_barcode, -1);
        // Beep when a barcode was recognised
        final boolean beep = prefs.getBoolean(Prefs.pk_sounds_scan_found_barcode, true);

        final IntentIntegrator integrator = IntentIntegrator.forSupportFragment(fragment);
        integrator.setCameraId(cameraId);
        integrator.setOrientationLocked(false);
        integrator.setPrompt(context.getString(R.string.zxing_msg_default_status));
        integrator.setBeepEnabled(beep);
        return integrator.createScanIntent();
    }

    @Nullable
    @Override
    public String parseResult(final int resultCode,
                              @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }
        // parse and return the barcode
        return IntentIntegrator.parseActivityResult(resultCode, intent).getContents();
    }
}
