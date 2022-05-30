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
import com.hardbacknutter.nevertoomanybooks.backup.ImportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public class ImportContract
        extends ActivityResultContract<Void, ImportResults> {

    private static final String TAG = "ImportContract";

    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(@NonNull final ImportResults result) {
        return new Intent().putExtra(ImportResults.BKEY, result);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @Nullable final Void aVoid) {
        return FragmentHostActivity.createIntent(context, ImportFragment.class);
    }

    @Override
    @Nullable
    public ImportResults parseResult(final int resultCode,
                                     @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }
        return intent.getParcelableExtra(ImportResults.BKEY);
    }
}
