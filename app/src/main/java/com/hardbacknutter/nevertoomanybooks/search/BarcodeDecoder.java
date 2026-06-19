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
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerResult;

public final class BarcodeDecoder {

    private BarcodeDecoder() {
    }

    @NonNull
    public static Optional<ScannerResult> decodeBarcodeFromBitmap(@NonNull final Bitmap bitmap) {
        // 1. Convert the Bitmap into an int array of ARGB pixels
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // 2. Create an RGBLuminanceSource using the pixel array
        final LuminanceSource source = new RGBLuminanceSource(width, height, pixels);

        // 3. Convert to a BinaryBitmap using HybridBinarizer (ZXing's recommended binarizer)
        final BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

        // 1. Set up explicit hints for the decoder
        final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

        // 2. Specify exactly what formats to look for (including the extension format)
        final List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.EAN_13);
        formats.add(BarcodeFormat.UPC_A);
        formats.add(BarcodeFormat.EAN_8);
        formats.add(BarcodeFormat.UPC_EAN_EXTENSION);

        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);

        // 3. CRITICAL FOR MAGAZINES: Tell ZXing to try hard and look for extensions
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        // 4. Use MultiFormatReader to attempt decoding the barcode
        final MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);

        try {
            final Result result = reader.decodeWithState(binaryBitmap);
            return ScannerResult.from(result);

        } catch (@NonNull final NotFoundException e) {
            return Optional.empty();
        }
    }
}
