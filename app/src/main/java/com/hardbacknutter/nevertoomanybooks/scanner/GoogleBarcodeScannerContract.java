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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.SparseArray;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;

public class GoogleBarcodeScannerContract
        extends ActivityResultContract<Fragment, String> {

    /** The file name we'll use. */
    private static final String TEMP_COVER_FILENAME = "GoogleBarcodeScanner.jpg";

    @NonNull
    private final Context mAppContext;

    @NonNull
    private final BarcodeDetector mDetector;

    private GoogleBarcodeScannerContract(@NonNull final Context context) {
        mAppContext = context.getApplicationContext();
        mDetector = new BarcodeDetector.Builder(mAppContext)
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

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               final Fragment input) {

        final File dstFile = getTempFile();
        FileUtils.delete(dstFile);
        final Uri uri = GenericFileProvider.createUri(context, dstFile);
        return new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri);
    }

    @Nullable
    @Override
    public String parseResult(final int resultCode,
                              @Nullable final Intent intent) {
        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }

        final File file;
        // detect emulator for testing
        if (BuildConfig.DEBUG && Build.PRODUCT.startsWith("sdk")) {
            // when used, the file must be in the root external app dir.
            file = AppDir.Root.getFile(mAppContext, "barcode.jpg");
        } else {
            file = getTempFile();
        }

        if (file.exists()) {
            final Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bm != null) {
                final String barcode = decode(bm);
                FileUtils.delete(getTempFile());
                return barcode;
            }
        }
        return null;
    }

    /**
     * Get the temporary file.
     *
     * @return file
     */
    @NonNull
    private File getTempFile() {
        return AppDir.Cache.getFile(mAppContext, TEMP_COVER_FILENAME);
    }


    static class Factory
            implements ScannerContractFactory {

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            final GoogleApiAvailability instance = GoogleApiAvailability.getInstance();
            return ConnectionResult.SUCCESS == instance.isGooglePlayServicesAvailable(context);
        }

        @NonNull
        @Override
        public ActivityResultContract<Fragment, String> getContract(
                @NonNull final Context context) {
            return new GoogleBarcodeScannerContract(context);
        }

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_GOOGLE_PLAY;
        }
    }
}
