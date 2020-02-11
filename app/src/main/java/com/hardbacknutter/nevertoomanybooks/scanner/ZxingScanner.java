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
package com.hardbacknutter.nevertoomanybooks.scanner;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.zxing.client.android.Intents;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * This object will start a Zxing compatible scanner and extract the data
 * from the resulting intent when the activity completes.
 * <p>
 * <a href="https://github.com/zxing">zxing</a>
 * <p>
 * <a href="https://github.com/zxing/zxing/blob/master/core/src/main/java/com/google/zxing/BarcodeFormat.java">
 *     Supported formats</a>
 */
public final class ZxingScanner
        implements Scanner {

    private static final String MARKET_URL = "market://details?id=com.google.zxing.client.android";
    private static final String PACKAGE = "com.google.zxing.client.android";

    /** Set to {@code true} if the Zxing package is required. */
    private final boolean mMustBeZxing;

    /**
     * Constructor.
     *
     * @param mustBeZxingPackage Set to {@code true} if the Zxing scanner app MUST be used
     */
    private ZxingScanner(final boolean mustBeZxingPackage) {
        mMustBeZxing = mustBeZxingPackage;
    }

    /**
     * Check if the passed intent action is available.
     *
     * @param context     Current context
     * @param packageName to check
     *
     * @return {@code true} if present
     */
    private static boolean isIntentAvailable(@NonNull final Context context,
                                             @Nullable final String packageName) {
        Intent intent = new Intent(Intents.Scan.ACTION);
        if (packageName != null && !packageName.isEmpty()) {
            intent.setPackage(packageName);
        }
        return context.getPackageManager().resolveActivity(intent, 0) != null;
    }

    @Override
    public boolean startActivityForResult(@NonNull final Fragment fragment,
                                          final int requestCode) {
        Intent intent = new Intent(Intents.Scan.ACTION);
        if (mMustBeZxing) {
            intent.setPackage(PACKAGE);
        }
        //noinspection ConstantConditions
        intent.putExtra(Intents.Scan.BEEP_ENABLED,
                        ScannerManager.isBeepOnBarcodeFound(fragment.getContext()));
        // not limiting the format, just grab anything supported.
        fragment.startActivityForResult(intent, requestCode);
        return true;
    }

    @Nullable
    @Override
    public String getBarcode(@NonNull final Context context,
                             @Nullable final Intent data) {
        Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
        return data.getStringExtra(Intents.Scan.RESULT);
    }

    @Override
    @NonNull
    public String toString() {
        return "ZxingScanner{"
               + "mMustBeZxing=" + mMustBeZxing
               + '}';
    }

    static class ZxingScannerFactory
            implements ScannerFactory {

        @NonNull
        public String getMarketUrl() {
            return MARKET_URL;
        }

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_ZXING;
        }

        @NonNull
        @Override
        public Scanner getScanner(@NonNull final Context context) {
            return new ZxingScanner(true);
        }

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            return ZxingScanner.isIntentAvailable(context, PACKAGE);
        }
    }

    static class ZxingCompatibleScannerFactory
            implements ScannerFactory {

        @NonNull
        public String getMarketUrl() {
            return MARKET_URL;
        }

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_ZXING_COMPATIBLE;
        }

        @NonNull
        @Override
        public Scanner getScanner(@NonNull final Context context) {
            return new ZxingScanner(false);
        }

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            return ZxingScanner.isIntentAvailable(context, null);
        }
    }
}
