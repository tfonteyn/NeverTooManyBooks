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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;

/**
 * Using the Google Play Services to scan a barcode.
 * <a href="https://developers.google.com/android/guides/overview">overview</a>
 * <a href="https://codelabs.developers.google.com/codelabs/bar-codes/#0">bar-codes</a>
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
    private String decode(@NonNull final Bitmap bm) {
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

    @Override
    public boolean isOperational() {
        return mDetector.isOperational();
    }

    /**
     * Start the camera to get an image.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public boolean startActivityForResult(@NonNull final Fragment fragment,
                                          final int requestCode) {
        if (!mDetector.isOperational()) {
            return false;
        }

        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            // preview pictures might work... but will be very dependent on the camera quality
            mCameraHelper.setUseFullSize(true);
        }
        mCameraHelper.startCamera(fragment, requestCode);
        return true;
    }

    @Nullable
    @Override
    public String getBarcode(@NonNull final Context context,
                             @Nullable final Intent data) {
        //noinspection ConstantConditions
        Bitmap bm = mCameraHelper.getBitmap(context, data);
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

    public static class GoogleBarcodeScannerFactory
            implements ScannerFactory {

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_GOOGLE_PLAY;
        }

        @NonNull
        @Override
        public Scanner getScanner(@NonNull final Context context) {
            return new GoogleBarcodeScanner(context);
        }

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            GoogleApiAvailability instance = GoogleApiAvailability.getInstance();
            return ConnectionResult.SUCCESS == instance.isGooglePlayServicesAvailable(context);
        }
    }

    /**
     * If the Google barcode scanner can be loaded, create a dummy instance to
     * force it to download the native library if not done already.
     * <p>
     * This can be seen in the device logs as:
     * <p>
     * I/Vision: Loading library libbarhopper.so
     * I/Vision: Library not found: /data/user/0/com.google.android.gms/app_vision/barcode/
     * libs/x86/libbarhopper.so
     * I/Vision: libbarhopper.so library load status: false
     * Request download for engine barcode
     */
    public static class PreloadGoogleScanner
            extends TaskBase<Boolean> {

        /** Log tag. */
        private static final String TAG = "PreloadGoogleScanner";

        public PreloadGoogleScanner(final int taskId,
                                    @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
        }

        @Override
        protected Boolean doInBackground(@Nullable final Void... voids) {
            Thread.currentThread().setName(TAG);
            final Context context = App.getTaskContext();

            ScannerFactory factory = new GoogleBarcodeScannerFactory();
            if (factory.isAvailable(context)) {
                // trigger the download if needed.
                factory.getScanner(context);
            }
            return true;
        }
    }
}
