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
package com.hardbacknutter.nevertoomanybooks.scanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.SparseArray;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;

/**
 * Using the Google Play Services to scan a barcode.
 * <a href="https://developers.google.com/android/guides/overview">overview</a>
 * <a href="https://codelabs.developers.google.com/codelabs/bar-codes/#0">bar-codes</a>
 */
public class GoogleBarcodeScanner
        implements Scanner {

    /** The file name we'll use. */
    private static final String TEMP_COVER_FILENAME = "GoogleBarcodeScanner.jpg";

    @NonNull
    private final BarcodeDetector mDetector;

    private ActivityResultLauncher<String> mCameraPermissionLauncher;


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
            final Frame frame = new Frame.Builder().setBitmap(bm).build();
            final SparseArray<Barcode> barCodes = mDetector.detect(frame);
            if (barCodes.size() > 0) {
                final Barcode code = barCodes.valueAt(0);
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
    @SuppressLint("MissingPermission")
    @Override
    public boolean startActivityForResult(@NonNull final Fragment fragment,
                                          final int requestCode) {
        if (!mDetector.isOperational()) {
            return false;
        }

        if (mCameraPermissionLauncher == null) {
            mCameraPermissionLauncher = fragment.registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), (isGranted) -> {
                        if (isGranted) {
                            takePicture(fragment, requestCode, true);
                        }
                    });
        }

        takePicture(fragment, requestCode, false);
        return true;
    }


    /**
     * Start the camera to get an image.
     *
     * @param alreadyGranted set to {@code true} if we already got granted access.
     *                       i.e. when called from the {@link #mCameraPermissionLauncher}
     */
    private void takePicture(@NonNull final Fragment fragment,
                             final int requestCode,
                             final boolean alreadyGranted) {
        //noinspection ConstantConditions
        if (alreadyGranted ||
            ContextCompat.checkSelfPermission(fragment.getContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {

            //noinspection ConstantConditions
            final File dstFile = getTempFile(fragment.getContext());
            FileUtils.delete(dstFile);
            final Uri uri = GenericFileProvider.createUri(fragment.getContext(), dstFile);
            final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .putExtra(MediaStore.EXTRA_OUTPUT, uri);
            fragment.startActivityForResult(intent, requestCode);

        } else {
            mCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Nullable
    @Override
    public String getBarcode(@NonNull final Context context,
                             @Nullable final Intent data) {
        final File file;
        // detect emulator for testing
        if (BuildConfig.DEBUG && Build.PRODUCT.startsWith("sdk")) {
            // when used, the file must be in the root external app dir.
            file = AppDir.Root.getFile(context, "barcode.jpg");
        } else {
            file = getTempFile(context);
        }

        if (file.exists()) {
            final Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bm != null) {
                final String barcode = decode(bm);
                FileUtils.delete(getTempFile(context));
                return barcode;
            }
        }
        return null;
    }

    /**
     * Get the temporary file.
     *
     * @param context Current context
     *
     * @return file
     */
    @NonNull
    private File getTempFile(@NonNull final Context context) {
        return AppDir.Cache.getFile(context, TEMP_COVER_FILENAME);
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
            final GoogleApiAvailability instance = GoogleApiAvailability.getInstance();
            return ConnectionResult.SUCCESS == instance.isGooglePlayServicesAvailable(context);
        }
    }

    /**
     * If the Google barcode scanner factory can be loaded, create a temp instance to
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
            implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("PreloadGoogleScanner");
            final Context context = App.getTaskContext();
            final ScannerFactory factory = new GoogleBarcodeScannerFactory();
            if (factory.isAvailable(context)) {
                // trigger the download if needed.
                factory.getScanner(context);
            }
        }
    }
}
