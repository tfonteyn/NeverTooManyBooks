/*
 * @Copyright 2020 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.backup.ImportViewModel;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public class ImportFromUriContract
        extends ActivityResultContract<String, ImportResults> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @Nullable final String uri) {
        final Intent intent = new Intent(context, FragmentHostActivity.class)
                .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, ImportFragment.TAG);
        if (uri != null) {
            intent.putExtra(ImportViewModel.BKEY_URI, uri);
        }
        return intent;
    }

    @Override
    @Nullable
    public ImportResults parseResult(final int resultCode,
                                     @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(ImportFragment.TAG, "parseResult",
                     "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }
        return intent.getParcelableExtra(ImportResults.BKEY_IMPORT_RESULTS);
    }
}
