/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;

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
        extends ActivityResultContract<File, Optional<File>> {

    private static final String TAG = "TakePictureContract";

    private File dstFile;

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final File dstFile) {
        // MediaStore.ACTION_IMAGE_CAPTURE does not produce output, so keep a reference here
        this.dstFile = Objects.requireNonNull(dstFile, "dstFile");

        final Uri dstUri = GenericFileProvider.createUri(context, dstFile);
        return new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, dstUri);
    }

    @Override
    @NonNull
    public Optional<File> parseResult(final int resultCode,
                                      @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                         .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        // GitHub #11: the google camera app returns an empty Intent, while
        // OpenCamera returns a null for the Intent.
        // Hence ONLY test on the resultCode here.
        if (resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        return Optional.of(dstFile);
    }
}
