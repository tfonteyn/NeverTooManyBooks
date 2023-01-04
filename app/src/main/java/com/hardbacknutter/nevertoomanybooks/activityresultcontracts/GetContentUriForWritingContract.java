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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * A replacement for
 * {@link androidx.activity.result.contract.ActivityResultContracts.CreateDocument}.
 * <p>
 * Allows us to set the mimeType properly, and use an Optional as the return type.
 */
public class GetContentUriForWritingContract
        extends ActivityResultContract<GetContentUriForWritingContract.Input, Optional<Uri>> {

    private static final String TAG = "GetContentUriForWriting";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType(input.mimeType)
                .putExtra(Intent.EXTRA_TITLE, input.fileName);
    }

    @Override
    @NonNull
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

    public static class Input {

        @NonNull
        final String mimeType;
        @NonNull
        final String fileName;

        public Input(@NonNull final String mimeType,
                     @NonNull final String fileName) {
            this.mimeType = mimeType;
            this.fileName = fileName;
        }
    }
}
