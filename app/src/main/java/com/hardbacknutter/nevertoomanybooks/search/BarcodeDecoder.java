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

package com.hardbacknutter.nevertoomanybooks.search;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.entities.codes.Barcode;

/**
 * DEBUG usage.
 */
final class BarcodeDecoder {

    private BarcodeDecoder() {
    }

    @NonNull
    static Optional<Barcode> decodeBarcodeFromBitmap(@NonNull final Bitmap bitmap) {
        // Convert the Bitmap into an int array of ARGB pixels
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        // Create an RGBLuminanceSource using the pixel array
        final LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        // Convert to a BinaryBitmap using HybridBinarizer (ZXing's recommended binarizer)
        final BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

        final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(
                BarcodeFormat.EAN_13,
                BarcodeFormat.UPC_A,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_EAN_EXTENSION));

        final MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);

        try {
            final Result result = reader.decodeWithState(binaryBitmap);
            return Barcode.from(result);

        } catch (@NonNull final NotFoundException e) {
            return Optional.empty();
        }
    }
}
