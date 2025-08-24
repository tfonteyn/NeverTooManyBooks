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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.utils.CameraConfig;
import com.hardbacknutter.tinyzxingwrapper.ScanIntentResult;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeFamily;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Full-screen {@code com.hardbacknutter.tinyzxingwrapper} scanner activity.
 * <ul>
 *     <li>return: the barcode String</li>
 * </ul>
 */
public class ScannerContract
        extends ActivityResultContract<ScanOptions, Optional<String>> {

    private static final String TAG = "ScannerContract";

    /**
     * Create a default {@link ScanOptions} objects using the user-configured
     * camera and a set of barcodes suited for ISBN and UPC codes.
     *
     * @param cameraConfig to use
     *
     * @return options
     */
    @NonNull
    public static ScanOptions createDefaultOptions(@NonNull final CameraConfig cameraConfig) {

        return new ScanOptions()
                .setBarcodeFormats(BarcodeFamily.PRODUCT)
                .setAutoFocus(cameraConfig.isAutoFocus())
                .setUseCameraWithLensFacing(cameraConfig.getLensFacing())
                .setShowZoomControl(cameraConfig.isZoomControlEnabled());
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @Nullable final ScanOptions scanOptions) {
        return Objects.requireNonNullElseGet(scanOptions, ScanOptions::new).build(context);
    }

    @NonNull
    @Override
    public Optional<String> parseResult(final int resultCode,
                                        @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                         .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final String text = ScanIntentResult.parseActivityResultIntent(resultCode, intent)
                                            .getText();
        if (text != null) {
            return Optional.of(text);
        } else {
            return Optional.empty();
        }
    }
}
