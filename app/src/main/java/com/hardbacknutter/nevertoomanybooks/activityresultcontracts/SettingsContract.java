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

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsFragment;

public class SettingsContract
        extends ActivityResultContract<String, Boolean> {

    private static final String TAG = "SettingsContract";

    /** Something changed (or not) that requires a recreation of the caller Activity. */
    private static final String BKEY_RECREATE_ACTIVITY = SettingsFragment.TAG + ":recreate";

    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @param requiresRecreation flag indicating if the BoB should be recreated
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(final boolean requiresRecreation) {
        return new Intent().putExtra(BKEY_RECREATE_ACTIVITY, requiresRecreation);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @Nullable final String scrollToKey) {
        final Intent intent = FragmentHostActivity.createIntent(context, SettingsFragment.class);
        if (scrollToKey != null) {
            intent.putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY, scrollToKey);
        }
        return intent;
    }

    @Override
    @NonNull
    public Boolean parseResult(final int resultCode,
                               @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                          .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return false;
        }
        return intent.getBooleanExtra(BKEY_RECREATE_ACTIVITY, false);
    }
}
