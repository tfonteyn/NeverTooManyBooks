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
import android.content.res.Configuration;
import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.utils.AttrUtils;
import com.hardbacknutter.util.logger.LoggerFactory;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropActivity;

public class EditImageContract
        extends ActivityResultContract<EditImageContract.Input, Optional<File>> {

    private static final String TAG = "EditImageContract";

    @CallSuper
    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {

        return UCrop.of(input.srcFile, input.dstFile)
                    .withOptions(getStandardOptions(context))
                    .getIntent(context);
    }

    /**
     * Standard UI configuration for uCrop to keep it looking clean.
     *
     * @param context Current context
     *
     * @return options
     */
    private UCrop.Options getStandardOptions(@NonNull final Context context) {
        final UCrop.Options options = new UCrop.Options();

        final int nightModeFlags = context.getResources().getConfiguration().uiMode
                                   & Configuration.UI_MODE_NIGHT_MASK;
        final boolean isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        final int colorPrimary = AttrUtils.getColorInt(
                context, androidx.appcompat.R.attr.colorPrimary);
        final int colorSurface = AttrUtils.getColorInt(
                context, com.google.android.material.R.attr.colorSurface);
        final int colorOnSurface = AttrUtils.getColorInt(
                context, com.google.android.material.R.attr.colorOnSurface);

        options.setToolbarColor(colorPrimary);
        options.setToolbarWidgetColor(colorOnSurface);
        options.setActiveControlsWidgetColor(colorPrimary);
        options.setRootViewBackgroundColor(colorSurface);

        options.setLogoColor(colorOnSurface);

        options.setStatusBarLight(!isDarkMode);
        options.setNavigationBarLight(!isDarkMode);

        // Tab 1 (Crop)  : Scale only
        // Tab 2 (Rotate): Scale + Rotate (ALL)
        // Tab 3 (Scale) : Scale only
        options.setAllowedGestures(UCropActivity.SCALE,
                                   UCropActivity.ALL,
                                   UCropActivity.SCALE);

        options.setFreeStyleCropEnabled(true);

        // These are the defaults:
        // options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        // options.setCompressionQuality(90);

        return options;
    }

    @NonNull
    @Override
    public final Optional<File> parseResult(final int resultCode,
                                            @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger().d(TAG, "parseResult",
                                        "resultCode=" + resultCode, "intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final Uri resultUri = UCrop.getOutput(intent);

        if (resultUri != null && resultUri.getPath() != null) {
            return Optional.of(new File(resultUri.getPath()));
        } else {
            return Optional.empty();
        }
    }

    public static class Input {

        @NonNull
        final Uri srcFile;
        @NonNull
        final Uri dstFile;

        public Input(@NonNull final File srcFile,
                     @NonNull final File dstFile) {
            this.srcFile = Uri.fromFile(srcFile);
            this.dstFile = Uri.fromFile(dstFile);
        }
    }
}
