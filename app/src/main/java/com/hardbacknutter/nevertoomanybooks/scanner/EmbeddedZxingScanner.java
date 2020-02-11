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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * This object will start the embedded Zxing compatible scanner and extract the data
 * from the resulting intent when the activity completes.
 * <p>
 * <a href="https://github.com/journeyapps/zxing-android-embedded">zxing-android-embedded</a>
 * <p>
 * <a href="https://github.com/zxing/zxing/blob/master/core/src/main/java/com/google/zxing/BarcodeFormat.java">Supported formats</a>
 */
public final class EmbeddedZxingScanner
        implements Scanner {

    @Override
    public boolean startActivityForResult(@NonNull final Fragment fragment,
                                          final int requestCode) {
        IntentIntegrator integrator = new IntentIntegrator(fragment.getActivity());
        integrator.setOrientationLocked(false);
        // we want to use the apps locale.
        integrator.setPrompt(fragment.getString(R.string.zxing_msg_default_status));
        //noinspection ConstantConditions
        integrator.setBeepEnabled(ScannerManager.isBeepOnBarcodeFound(fragment.getContext()));

        Intent intent = integrator.createScanIntent();
        fragment.startActivityForResult(intent, requestCode);
        return true;
    }

    @Nullable
    @Override
    public String getBarcode(@NonNull final Context context,
                             @Nullable final Intent data) {
        Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
        // resultCode: we wouldn't be here unless it was RESULT_OK
        IntentResult result = IntentIntegrator.parseActivityResult(Activity.RESULT_OK, data);

        return result.getContents();
    }

    static class EmbeddedZxingScannerFactory
            implements ScannerFactory {

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_EMBEDDED_ZXING;
        }

        @NonNull
        @Override
        public Scanner getScanner(@NonNull final Context context) {
            return new EmbeddedZxingScanner();
        }

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            return true;
        }
    }
}
