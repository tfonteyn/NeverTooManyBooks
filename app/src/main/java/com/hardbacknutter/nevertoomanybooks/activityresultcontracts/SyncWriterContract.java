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
import android.os.Parcelable;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterFragment;

public class SyncWriterContract
        extends ActivityResultContract<SyncServer, Boolean> {

    private static final String TAG = "SyncWriterContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final SyncServer syncServer) {
        final Intent intent = new Intent(context, FragmentHostActivity.class)
                .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, SyncWriterFragment.TAG);
        intent.putExtra(SyncServer.BKEY_SITE, (Parcelable) syncServer);
        return intent;
    }

    @Override
    @NonNull
    public Boolean parseResult(final int resultCode,
                               @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        return intent != null && resultCode == Activity.RESULT_OK;
    }
}
