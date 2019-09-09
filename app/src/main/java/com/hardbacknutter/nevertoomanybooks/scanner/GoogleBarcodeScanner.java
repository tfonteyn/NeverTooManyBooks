/*
 * @Copyright 2019 HardBackNutter
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
import android.graphics.Bitmap;
import android.util.SparseArray;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;

/**
 * Using the Google Play Services to scan a barcode.
 * <a href="https://developers.google.com/android/guides/overview">
 * https://developers.google.com/android/guides/overview</a>
 * <a href="https://codelabs.developers.google.com/codelabs/bar-codes/#0">
 * https://codelabs.developers.google.com/codelabs/bar-codes/#0</a>
 */
public class GoogleBarcodeScanner
        implements Scanner {

    private final BarcodeDetector mDetector;

    @Nullable
    private CameraHelper mCameraHelper;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    private GoogleBarcodeScanner(@NonNull final Context context) {
        mDetector = new BarcodeDetector.Builder(context.getApplicationContext())
                            .setBarcodeFormats(Barcode.ISBN
                                               | Barcode.EAN_13 | Barcode.EAN_8
                                               | Barcode.UPC_A | Barcode.UPC_E)
                            .build();
    }

    @Nullable
    public String decode(@NonNull final Bitmap bm) {
        if (mDetector.isOperational()) {
            Frame frame = new Frame.Builder().setBitmap(bm).build();
            SparseArray<Barcode> barCodes = mDetector.detect(frame);
            if (barCodes.size() > 0) {
                Barcode code = barCodes.valueAt(0);
                if (code != null) {
                    return code.rawValue;
                }
            }
        }
        return null;
    }

    /**
     * Start the camera to get an image.
     */
    @Override
    public boolean startActivityForResult(@NonNull final Fragment fragment,
                                          final int requestCode) {
        if (!mDetector.isOperational()) {
            return false;
        }

        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setUseFullSize(true);
        }
        mCameraHelper.startCamera(fragment, requestCode);
        return true;
    }

    @Nullable
    @Override
    public String getBarcode(@NonNull final Intent data) {
        //noinspection ConstantConditions
        Bitmap bm = mCameraHelper.getBitmap(data);
        if (bm != null) {
            return decode(bm);
        }
        return null;
    }

    @Override
    @NonNull
    public String toString() {
        return "GoogleBarcodeScanner{"
               + "mDetector=" + mDetector
               + ", isOperational=" + (mDetector != null ? mDetector.isOperational() : "false")
               + ", mCameraHelper=" + mCameraHelper
               + '}';
    }

    static class GoogleBarcodeScannerFactory
            implements ScannerFactory {

        @NonNull
        public String getMarketUrl() {
            return "";
        }

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_GOOGLE_PLAY;
        }

        @NonNull
        @Override
        public Scanner newInstance(@NonNull final Context context) {
            return new GoogleBarcodeScanner(context);
        }

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            GoogleApiAvailability instance = GoogleApiAvailability.getInstance();
            return ConnectionResult.SUCCESS == instance.isGooglePlayServicesAvailable(context);
        }
    }
}
