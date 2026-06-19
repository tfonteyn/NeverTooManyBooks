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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultMetadataType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.utils.CameraConfig;
import com.hardbacknutter.tinyzxingwrapper.ScanIntentResult;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Full-screen {@code com.hardbacknutter.tinyzxingwrapper} scanner activity.
 * <ul>
 *     <li>return: the barcode String</li>
 * </ul>
 */
public class ScannerContract
        extends ActivityResultContract<ScanOptions, Optional<ScannerResult>> {

    private static final String TAG = "ScannerContract";

    /**
     * The barcode formats we read.
     */
    public static final List<BarcodeFormat> BARCODES = List.of(
            BarcodeFormat.EAN_13,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_EAN_EXTENSION,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_8
    );

    /**
     * The default metadata we want.
     * Used by the {@link ScanIntentResult} to filter/reduce the amount of metadata returned.
     */
    private static final List<ResultMetadataType> METADATA = List.of(
            ResultMetadataType.ISSUE_NUMBER,
            ResultMetadataType.SUGGESTED_PRICE,
            ResultMetadataType.UPC_EAN_EXTENSION);

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
                .setBarcodeFormats(BARCODES)
                .setReturnMetadata(METADATA)
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
    public Optional<ScannerResult> parseResult(final int resultCode,
                                               @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                         .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final ScanIntentResult scanResult = ScanIntentResult
                .parseActivityResultIntent(resultCode, intent);

        final String barcode = scanResult.getText();
        if (barcode != null) {
            final ScannerResult value = new ScannerResult(barcode,
                                                          scanResult.getFormat(),
                                                          scanResult.getIssueNumber(),
                                                          scanResult.getSuggestedPrice(),
                                                          scanResult.getUpcEanExtension());
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }
}
