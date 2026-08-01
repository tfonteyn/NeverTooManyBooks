/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.provider.MediaStore;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.provider.GenericFileProvider;

/**
 * A replacement for
 * {@link androidx.activity.result.contract.ActivityResultContracts.TakePicture}.
 * <p>
 * Allows us to handle the result transparently and use an Optional as the return type.
 * <p>
 * ENHANCE: support OpenCamera on Android 11+
 * https://developer.android.com/about/versions/11/behavior-changes-11#media-capture
 * https://www.opencamera.org.uk/
 */
public class TakePictureContract
        extends ActivityResultContract<TakePictureContract.Input, Boolean> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input args) {
        return new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, args.dstUri);
    }

    @Override
    @NonNull
    public Boolean parseResult(final int resultCode,
                                      @Nullable final Intent intent) {

        // GitHub #11: the Google camera app returns an empty Intent, while
        // OpenCamera returns a null for the Intent.
        // Hence, ONLY test on the resultCode here.
        return resultCode == Activity.RESULT_OK;
    }

    public static final class Input {

        private static final String ERROR_GENERIC_FILE_PROVIDER =
                "GenericFileProvider/IllegalArgumentException";

        @NonNull
        final Uri dstUri;

        private Input(@NonNull final Uri dstUri) {
            this.dstUri = dstUri;
        }

        /**
         * Constructor.
         * <p>
         * Make sure to keep a reference to the {@code dstFile}
         * as {@link MediaStore#ACTION_IMAGE_CAPTURE}, and hence,
         * this contract does not produce output.
         *
         * @param dstFile the output file (name)
         *
         * @return instance
         *
         * @throws CoverStorageException When a given {@link File} is outside
         *                               the paths supported by the provider.
         */
        @NonNull
        public static Input create(@NonNull final File dstFile)
                throws CoverStorageException {
            try {
                final Uri dstUri = GenericFileProvider.createUri(dstFile);
                return new Input(dstUri);

            } catch (@NonNull final IllegalArgumentException e) {
                // This would be a bug; a permission issue with the GenericFileProvider
                throw new CoverStorageException(ERROR_GENERIC_FILE_PROVIDER, e);
            }
        }
    }
}
