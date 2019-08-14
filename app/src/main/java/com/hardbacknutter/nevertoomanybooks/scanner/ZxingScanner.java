/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.scanner;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * This object will start a Zxing compatible scanner and extract the data
 * from the resulting intent when the activity completes.
 * <p>
 * <a href="https://github.com/zxing">https://github.com/zxing</a>
 * <p>
 * Supported formats:
 * <a href="https://github.com/zxing/zxing/blob/master/core/src/main/java/com/google/zxing/BarcodeFormat.java">
 * https://github.com/zxing/zxing/blob/master/core/src/main/java/com/google/zxing/BarcodeFormat.java</a>
 * <p>
 * It also has a static method to check if the intent is present.
 */
public class ZxingScanner
        implements Scanner {

    public static final String ACTION = "com.google.zxing.client.android.SCAN";
    private static final String PACKAGE = "com.google.zxing.client.android";

    private static final String SCAN_RESULT = "SCAN_RESULT";

    /** Set to {@code true} if the Zxing package is required. */
    private final boolean mMustBeZxing;

    /**
     * Constructor.
     *
     * @param mustBeZxingPackage Set to {@code true} if the Zxing scanner app MUST be used
     */
    ZxingScanner(final boolean mustBeZxingPackage) {
        mMustBeZxing = mustBeZxingPackage;
    }

    /**
     * Check if we have a valid intent available.
     *
     * @return {@code true} if present
     */
    static boolean isIntentAvailable(@NonNull final Context context,
                                     final boolean mustBeZxing) {
        return isIntentAvailable(context, mustBeZxing ? PACKAGE : null);
    }

    /**
     * Check if the passed intent action is available.
     *
     * @return {@code true} if present
     */
    private static boolean isIntentAvailable(@NonNull final Context context,
                                             @Nullable final String packageName) {
        Intent intent = new Intent(ACTION);
        if (packageName != null && !packageName.isEmpty()) {
            intent.setPackage(packageName);
        }
        return context.getPackageManager().resolveActivity(intent, 0) != null;
    }

    @Override
    public void startActivityForResult(@NonNull final Fragment fragment,
                                       final int requestCode) {
        Intent intent = new Intent(ACTION);
        if (mMustBeZxing) {
            intent.setPackage(PACKAGE);
        }
        // not limiting the format, just grab anything supported.
        fragment.startActivityForResult(intent, requestCode);
    }

    @NonNull
    @Override
    public String getBarcode(@NonNull final Intent data) {
        return data.getStringExtra(SCAN_RESULT);
    }
}
