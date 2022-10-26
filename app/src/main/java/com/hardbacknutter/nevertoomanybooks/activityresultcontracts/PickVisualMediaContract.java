/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.ext.SdkExtensions;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * A replacement for the broken java API of ActivityResultContracts.PickVisualMedia.
 */
public class PickVisualMediaContract
        extends ActivityResultContract<String, Optional<Uri>> {

    private static final String TAG = "PickVisualMediaContract";

    /**
     * Check if the current device has support for the photo picker by checking
     * the running Android version or the SDK extension version.
     */
    @SuppressLint({"ClassVerificationFailure", "NewApi"})
    private static boolean isPhotoPickerAvailable() {
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13
            return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11/12 but with the SdkExtensions level 2 or up
            return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2;
        } else {
            return false;
        }
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final String mimeType) {
        if (isPhotoPickerAvailable()) {
            // MediaStore.ACTION_PICK_IMAGES
            return new Intent("android.provider.action.PICK_IMAGES")
                    .setType(mimeType);
        } else {
            return new Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(mimeType);
        }
    }

    @Override
    public Optional<Uri> parseResult(final int resultCode,
                                     @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final Uri uri = intent.getData();
        if (uri != null) {
            return Optional.of(uri);
        } else {
            return Optional.empty();
        }
    }
}
