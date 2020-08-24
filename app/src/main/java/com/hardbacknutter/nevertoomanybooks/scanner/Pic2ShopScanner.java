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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * Based on the pic2shop client code at github, this object will start pic2shop and
 * extract the data from the resulting intent when the activity completes.
 * <p>
 * <a href="https://github.com/VisionSmarts/pic2shop-client">pic2shop-client</a>
 *
 * 2020-01-08: disabled in preferences.
 */
public class Pic2ShopScanner
        implements Scanner {

    private static final String MARKET_URL = "market://details?id=com.visionsmarts.pic2shop";

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

    private static boolean isIntentAvailable(@NonNull final Context context,
                                             @NonNull final String action) {
        Intent test = new Intent(action);
        return context.getPackageManager().resolveActivity(test, 0) != null;
    }

    @Override
    public boolean startActivityForResult(@NonNull final Fragment fragment,
                                          final int requestCode) {
        Intent intent;
        //noinspection ConstantConditions
        if (isIntentAvailable(fragment.getContext(), Free.ACTION)) {
            intent = new Intent(Free.ACTION);

        } else if (isIntentAvailable(fragment.getContext(), Pro.ACTION)) {
            intent = new Intent(Pro.ACTION).putExtra(Pro.FORMATS, Pro.BARCODE_TYPES);
        } else {
            return false;
        }
        fragment.startActivityForResult(intent, requestCode);
        return true;
    }

    @Override
    @Nullable
    public String getBarcode(@NonNull final Context context,
                             @Nullable final Intent data) {
        Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);

        String barcode = data.getStringExtra(BARCODE);
        // only for Pro:
        String barcodeFormat = data.getStringExtra(Pro.FORMAT);
        if (barcodeFormat != null && !Arrays.asList(Pro.BARCODE_TYPES).contains(barcodeFormat)) {
            return null;
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
     * <a href="https://en.wikipedia.org/wiki/Barcode#Types_of_barcodes">Types of bar codes</a>
     * The Pro package does not implement all those.
     * The example code at github lists:
     * String[] ALL_BARCODE_TYPES = {"EAN13","EAN8","UPCE","ITF","CODE39","CODE128","CODABAR","QR"};
     * <p>
     * of which only {"EAN13","UPCE"} are useful for our purposes
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

    static class Pic2ShopScannerFactory
            implements ScannerFactory {

        @NonNull
        public String getMarketUrl() {
            return MARKET_URL;
        }

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_PIC2SHOP;
        }

        @NonNull
        @Override
        public Scanner getScanner(@NonNull final Context context) {
            return new Pic2ShopScanner();
        }

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            return isIntentAvailable(context, Free.ACTION)
                   || isIntentAvailable(context, Pro.ACTION);
        }
    }
}
