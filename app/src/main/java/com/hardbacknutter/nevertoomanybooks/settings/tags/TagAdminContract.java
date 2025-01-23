/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings.tags;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsOutput;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Use {@link SettingsOutput#createResult(boolean, boolean)} to construct the output.
 */
public class TagAdminContract
        extends ActivityResultContract<Void, Optional<SettingsOutput>> {

    private static final String TAG = "TagAdminContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               final Void unused) {
        return FragmentHostActivity.createIntent(context, R.layout.activity_main_tabbar,
                                                 TagAdminFragment.class);
    }

    @Override
    public Optional<SettingsOutput> parseResult(final int resultCode,
                                                @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger().d(TAG, "parseResult", "|resultCode=" + resultCode
                                                            + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }
        final Bundle result = intent.getExtras();
        if (result == null) {
            return Optional.empty();
        }

        return Optional.of(new SettingsOutput(result));
    }
}
