/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.util.logger.LoggerFactory;

public class EditPictureContract
        extends ActivityResultContract<EditPictureContract.Input, Optional<File>> {

    private static final String TAG = "EditPictureContract";

    private static final String IMAGE_MIME_TYPE = "image/*";

    private File dstFile;

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        Objects.requireNonNull(input.srcFile, "srcFile");
        // Intent.ACTION_EDIT does not produce output, so keep a reference here
        this.dstFile = Objects.requireNonNull(input.dstFile, "dstFile");

        final Uri srcUri = GenericFileProvider.createUri(context, input.srcFile);
        final Uri dstUri = GenericFileProvider.createUri(context, input.dstFile);
        final Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(srcUri, IMAGE_MIME_TYPE)
                // read access to the input uri
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // write access see below
                .putExtra(MediaStore.EXTRA_OUTPUT, dstUri);

        final List<ResolveInfo> resInfoList =
                context.getPackageManager()
                       .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if (resInfoList.isEmpty()) {
            throw new ActivityNotFoundException();
        }

        // We do not know which app will be used, so need to grant permission to all.
        for (final ResolveInfo resolveInfo : resInfoList) {
            context.grantUriPermission(resolveInfo.activityInfo.packageName,
                                       dstUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        return Intent.createChooser(intent, context.getString(R.string.whichEditApplication));
    }

    @Override
    @NonNull
    public Optional<File> parseResult(final int resultCode,
                                      @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                          .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        return Optional.of(dstFile);
    }

    public static class Input {

        @NonNull
        final File srcFile;
        @NonNull
        final File dstFile;

        public Input(@NonNull final File srcFile,
                     @NonNull final File dstFile) {
            this.srcFile = srcFile;
            this.dstFile = dstFile;
        }
    }
}
