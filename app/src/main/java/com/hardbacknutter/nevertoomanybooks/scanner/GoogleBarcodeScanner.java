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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
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
import com.hardbacknutter.nevertoomanybooks.UniqueId;

/**
 * Using the Google Play Services to scan a barcode.
 * <a href="https://developers.google.com/android/guides/overview">
 * https://developers.google.com/android/guides/overview</a>
 * <a href="https://codelabs.developers.google.com/codelabs/bar-codes/#0">
 * https://codelabs.developers.google.com/codelabs/bar-codes/#0</a>
 */
public class GoogleBarcodeScanner
        implements Scanner {

    static final String ACTION = MediaStore.ACTION_IMAGE_CAPTURE;

    private final BarcodeDetector mDetector;

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

    /**
     * Check availability of the Google Play Services API.
     * <ul>Possible outcomes at 'return' time:
     * <li>Installed</li>
     * <li>Not supported on device</li>
     * <li>A dialog is being shown to the user to install/upgrade.<br>
     * If the user cancels the dialog, the passed cancelListener is called.</li>
     * </ul>
     *
     * @param activity to which we'll redirect if we have to prompt the user.
     *                 Activity#onActivityResult will get called with
     *                 requestCode == {@link UniqueId#REQ_INSTALL_GOOGLE_PLAY_SERVICES}
     */
    static GooglePSInstall installGooglePS(@NonNull final Activity activity,
                                           @NonNull final DialogInterface.OnCancelListener
                                                   cancelListener) {

        GoogleApiAvailability gApi = GoogleApiAvailability.getInstance();
        int status = gApi.isGooglePlayServicesAvailable(activity);
        // Api docs say it will be one of these:
        // ConnectionResult.SUCCESS
        // ConnectionResult.SERVICE_MISSING
        // ConnectionResult.SERVICE_UPDATING,
        // ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED
        // ConnectionResult.SERVICE_DISABLED
        // ConnectionResult.SERVICE_INVALID
        switch (status) {
            case ConnectionResult.SUCCESS:
                return GooglePSInstall.Success;

            case ConnectionResult.SERVICE_INVALID:
                return GooglePSInstall.NotAvailable;

            default:
                Dialog dialog = gApi.getErrorDialog(activity, status,
                                                    UniqueId.REQ_INSTALL_GOOGLE_PLAY_SERVICES,
                                                    cancelListener);
                // Paranoia...
                if (dialog == null) {
                    return GooglePSInstall.Success;
                }

                dialog.show();
                // we prompted the user to install
                return GooglePSInstall.PromptedUser;
        }
    }

    public boolean isOperational() {
        return mDetector.isOperational();
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

        Intent intent = new Intent(ACTION);
        fragment.startActivityForResult(intent, UniqueId.REQ_IMAGE_FROM_SCANNER);
        return true;
    }

    @Nullable
    @Override
    public String getBarcode(@NonNull final Intent data) {
        Bitmap bm = data.getParcelableExtra("data");
        return decode(bm);
    }

    public enum GooglePSInstall {
        Success, NotAvailable, PromptedUser
    }

    static class GooglePlayScannerFactory
            implements ScannerManager.ScannerFactory {

        @NonNull
        public String getMarketUrl() {
            return "";
        }

        @IdRes
        @Override
        public int getResId() {
            return R.id.MENU_SCANNER_GOOGLE_PLAY;
        }

        @NonNull
        @Override
        public String getLabel(@NonNull final Context context) {
            return context.getString(R.string.google_play_services);
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
