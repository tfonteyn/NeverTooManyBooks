/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesFragment;

public class PreferredStylesContract
        extends ActivityResultContract<String, PreferredStylesContract.Output> {

    private static final String TAG = "PreferredStylesContract";

    private static final String BKEY_MODIFIED = TAG + ":m";

    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(@Nullable final String uuid,
                                      final boolean modified) {
        return new Intent().putExtra(Style.BKEY_UUID, uuid)
                           .putExtra(BKEY_MODIFIED, modified);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final String styleUuid) {
        return FragmentHostActivity
                .createIntent(context, PreferredStylesFragment.class)
                .putExtra(Style.BKEY_UUID, styleUuid);
    }

    @Override
    @Nullable
    public Output parseResult(final int resultCode,
                              @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }

        final String uuid = intent.getStringExtra(Style.BKEY_UUID);
        final boolean modified = intent.getBooleanExtra(BKEY_MODIFIED, false);
        return new Output(uuid, modified);
    }

    public static final class Output {

        // Return the currently selected style UUID, so the caller can apply it.
        // This is independent from any modification to this or another style,
        // or the order of the styles.
        @Nullable
        public final String uuid;
        // Same here, this is independent from the returned style
        public final boolean modified;

        private Output(@Nullable final String uuid,
                       final boolean modified) {
            this.uuid = uuid;
            this.modified = modified;
        }
    }
}
