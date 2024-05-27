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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesFragment;
import com.hardbacknutter.util.logger.LoggerFactory;

public class PreferredStylesContract
        extends ActivityResultContract<String, Optional<PreferredStylesContract.Output>> {

    private static final String TAG = "PreferredStylesContract";

    private static final String BKEY_MODIFIED = TAG + ":m";

    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @param styleUuid Return the currently selected style UUID, so the caller can apply it.
     *                  This is independent from any modification to this or another style,
     *                  or the order of the styles.
     * @param modified  flag indicating if <strong>anything at all</strong> was modified.
     *                  This is independent from the returned style
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(@Nullable final String styleUuid,
                                      final boolean modified) {
        return new Intent().putExtra(Style.BKEY_UUID, styleUuid)
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
    @NonNull
    public Optional<Output> parseResult(final int resultCode,
                                        @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                         .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final String uuid = intent.getStringExtra(Style.BKEY_UUID);
        final boolean modified = intent.getBooleanExtra(BKEY_MODIFIED, false);
        return Optional.of(new Output(uuid, modified));
    }

    public static final class Output {

        @Nullable
        private final String uuid;
        private final boolean modified;

        private Output(@Nullable final String uuid,
                       final boolean modified) {
            this.uuid = uuid;
            this.modified = modified;
        }

        /**
         * Get the UUID.
         *
         * @return {@link Optional} with a non-blank UUID
         */
        @NonNull
        public Optional<String> getUuid() {
            if (uuid == null || uuid.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(uuid);
        }

        public boolean isModified() {
            return modified;
        }
    }
}
