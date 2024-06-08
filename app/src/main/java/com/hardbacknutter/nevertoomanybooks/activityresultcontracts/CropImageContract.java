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
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.CropImageFragment;
import com.hardbacknutter.util.logger.LoggerFactory;

public class CropImageContract
        extends ActivityResultContract<CropImageContract.Input, Optional<File>> {

    private static final String TAG = "CropImageContract";

    public static final String BKEY_SOURCE = TAG + ":src";
    public static final String BKEY_DESTINATION = TAG + ":dst";


    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @param destinationPath of the modified image
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(@NonNull final String destinationPath) {
        return new Intent().putExtra(BKEY_DESTINATION, destinationPath);
    }

    @CallSuper
    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {

        return FragmentHostActivity
                .createIntent(context, R.layout.activity_fullscreen,
                              CropImageFragment.class)
                .putExtra(BKEY_SOURCE, input.srcFile.getAbsolutePath())
                .putExtra(BKEY_DESTINATION, input.dstFile.getAbsolutePath());
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
        final String filename = intent.getStringExtra(BKEY_DESTINATION);
        if (filename != null && !filename.isEmpty()) {
            return Optional.of(new File(filename));
        } else {
            return Optional.empty();
        }
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
