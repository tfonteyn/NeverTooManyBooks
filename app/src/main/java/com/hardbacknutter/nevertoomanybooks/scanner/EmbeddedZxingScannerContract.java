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

import com.google.zxing.integration.android.IntentIntegrator;

import com.hardbacknutter.nevertoomanybooks.R;

public class EmbeddedZxingScannerContract
        extends ActivityResultContract<Fragment, String> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Fragment fragment) {
        final IntentIntegrator integrator = IntentIntegrator.forSupportFragment(fragment);
        integrator.setOrientationLocked(false);
        integrator.setPrompt(context.getString(R.string.zxing_msg_default_status));
        integrator.setBeepEnabled(ScannerManager.isBeepOnBarcodeFound(context));
        return integrator.createScanIntent();
    }

    @Nullable
    @Override
    public String parseResult(final int resultCode,
                              @Nullable final Intent intent) {
        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }

        return IntentIntegrator.parseActivityResult(resultCode, intent).getContents();
    }

    static class Factory
            implements ScannerContractFactory {

        @Override
        public boolean isAvailable(@NonNull final Context context) {
            return true;
        }

        @Override
        @NonNull
        public ActivityResultContract<Fragment, String> getContract(
                @NonNull final Context context) {
            return new EmbeddedZxingScannerContract();
        }

        @IdRes
        @Override
        public int getMenuId() {
            return R.id.MENU_SCANNER_EMBEDDED_ZXING;
        }
    }
}
