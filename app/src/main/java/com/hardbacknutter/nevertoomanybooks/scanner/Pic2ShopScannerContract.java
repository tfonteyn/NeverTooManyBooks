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

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.R;

public class Pic2ShopScannerContract
        extends ActivityResultContract<Fragment, String> {

    private final boolean mPro;

    private Pic2ShopScannerContract(final boolean pro) {
        mPro = pro;
    }

    private static boolean isIntentAvailable(@NonNull final Context context,
                                             @NonNull final String action) {
        final Intent test = new Intent(action);
        return context.getPackageManager().resolveActivity(test, 0) != null;
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               final Fragment input) {
        if (mPro) {
            return new Intent(Pro.ACTION)
                    .putExtra(Pro.FORMATS, Pro.BARCODE_TYPES);
        } else {
            return new Intent(Free.ACTION);
        }
    }

    @Nullable
    @Override
    public String parseResult(final int resultCode,
                              @Nullable final Intent intent) {
        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }

        final String barcode = intent.getStringExtra("BARCODE");
        // only for Pro:
        final String barcodeFormat = intent.getStringExtra(Pro.FORMAT);
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

    static class Factory
            implements ScannerContractFactory {

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            return isIntentAvailable(context, Free.ACTION)
                   || isIntentAvailable(context, Pro.ACTION);
        }

        @NonNull
        @Override
        public ActivityResultContract<Fragment, String> getContract(
                @NonNull final Context context) {

            if (isIntentAvailable(context, Pro.ACTION)) {
                return new Pic2ShopScannerContract(true);
            } else if (isIntentAvailable(context, Free.ACTION)) {
                return new Pic2ShopScannerContract(true);
            } else {
                throw new IllegalStateException("Should have called isAvailable() before");
            }
        }

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_PIC2SHOP;
        }

        @NonNull
        public String getMarketUrl() {
            return "market://details?id=com.visionsmarts.pic2shop";
        }
    }
}
