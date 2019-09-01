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
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Arrays;

/**
 * Based on the pic2shop client code at github, this object will start pic2shop and
 * extract the data from the resulting intent when the activity completes.
 * <p>
 * https://github.com/VisionSmarts/pic2shop-client
 * <p>
 * It also has a static method to check if the intent is present.
 */
public class Pic2ShopScanner
        implements Scanner {

    static final String DISPLAY_NAME = "Pic2shop";

    static final String MARKET_URL = "market://details?id=com.visionsmarts.pic2shop";

    /**
     * When a barcode is read, pic2shop returns Activity.RESULT_OK in
     * {@link Activity}#onActivityResult of the activity which requested the scan using
     * {@link #startActivityForResult}.
     * The barcode can be retrieved with intent.getStringExtra("BARCODE").
     * <p>
     * If the user exits pic2shop by pressing Back before a barcode is read, the
     * result code will be Activity.RESULT_CANCELED in onActivityResult().
     */
    private static final String BARCODE = "BARCODE";

    /**
     * Check if we have a valid intent available.
     *
     * @return {@code true} if present
     */
    static boolean isIntentAvailable(@NonNull final Context context) {
        return isFreeScannerAppInstalled(context) || isProScannerAppInstalled(context);
    }

    private static boolean isFreeScannerAppInstalled(@NonNull final Context context) {
        return isIntentAvailable(context, Free.ACTION);
    }

    private static boolean isProScannerAppInstalled(@NonNull final Context context) {
        return isIntentAvailable(context, Pro.ACTION);
    }

    private static boolean isIntentAvailable(@NonNull final Context context,
                                             @NonNull final String action) {
        Intent test = new Intent(action);
        return context.getPackageManager().resolveActivity(test, 0) != null;
    }

    /**
     * <br>{@inheritDoc}
     * <br>
     * <p>Note that we always send an intent; the caller should have checked that
     * one of the intents is valid, or catch the resulting errors.
     */
    @Override
    public void startActivityForResult(@NonNull final Fragment fragment,
                                       final int requestCode) {
        Intent intent;
        //noinspection ConstantConditions
        if (isFreeScannerAppInstalled(fragment.getContext())) {
            intent = new Intent(Free.ACTION);
        } else {
            intent = new Intent(Pro.ACTION).putExtra(Pro.FORMATS, Pro.BARCODE_TYPES);
        }
        fragment.startActivityForResult(intent, requestCode);
    }

    @Override
    @NonNull
    public String getBarcode(@NonNull final Intent data) {
        String barcode = data.getStringExtra(BARCODE);
        // only for Pro:
        String barcodeFormat = data.getStringExtra(Pro.FORMAT);
        if (barcodeFormat != null && !Arrays.asList(Pro.BARCODE_TYPES).contains(barcodeFormat)) {
            throw new IllegalStateException("Unexpected format for barcode: " + barcodeFormat);
        }

        return barcode;
    }

    public interface Free {

        String PACKAGE = "com.visionsmarts.pic2shop";
        String ACTION = PACKAGE + ".SCAN";
    }

    /**
     * Pro version of the package.
     *
     * <a href="https://en.wikipedia.org/wiki/Barcode#Types_of_barcodes">
     * https://en.wikipedia.org/wiki/Barcode#Types_of_barcodes</a>
     * The Pro package does not implement all those.
     * The example code at github lists:
     * String[] ALL_BARCODE_TYPES = {"EAN13","EAN8","UPCE","ITF","CODE39","CODE128","CODABAR","QR"};
     * <p>
     * of which only {"EAN13","UPCE"} are useful for ou purposes
     */
    public interface Pro {

        String PACKAGE = "com.visionsmarts.pic2shoppro";
        String ACTION = PACKAGE + ".SCAN";
        /** request Intent:  barcode types wanted. */
        String[] BARCODE_TYPES = {"EAN13", "UPCE"};
        /** request Intent: formats wanted/accepted. */
        String FORMATS = "formats";
        /** response Intent: format returned. */
        String FORMAT = "format";
    }

//    public static void launchMarketToInstallFreeScannerApp(@NonNull final Context context) {
//        launchMarketToInstallApp(context, Free.PACKAGE);
//    }
//
//    public static void launchMarketToInstallProScannerApp(@NonNull final Context context) {
//        launchMarketToInstallApp(context, Pro.PACKAGE);
//    }

//    private static void launchMarketToInstallApp(@NonNull final Context context,
//                                                 @NonNull final String packageName) {
//        try {
//            Intent intent = new Intent(Intent.ACTION_VIEW,
//                                       Uri.parse("market://details?id=" + packageName));
//            context.startActivity(intent);
//        } catch (@NonNull final ActivityNotFoundException e) {
//            Logger.warnWithStackTrace(e, "Google Play not installed.");
//        }
//    }

    static class Pic2ShopScannerFactory
            implements ScannerManager.ScannerFactory {

        @NonNull
        @Override
        public Scanner newInstance() {
            return new Pic2ShopScanner();
        }

        @Override
        public boolean isIntentAvailable(@NonNull final Context context) {
            return Pic2ShopScanner.isIntentAvailable(context);
        }
    }
}
