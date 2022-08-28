/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public abstract class SyncContractBase
        extends ActivityResultContract<Void, EnumSet<SyncContractBase.Outcome>> {

    /** No result / cancelled / failed. */
    private static final int RESULT_NONE = 0;
    /** Data was exported/written; no local changes done. */
    public static final int RESULT_WRITE_DONE = 1;
    /** Data was imported; i.e. local changes were made. */
    public static final int RESULT_READ_DONE = 1 << 1;

    private static final String TAG = "SyncContractBase";
    public static final String BKEY_RESULT = TAG + ":result";

    @Override
    @NonNull
    public EnumSet<SyncContractBase.Outcome> parseResult(final int resultCode,
                                                         @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        final EnumSet<Outcome> outcome = EnumSet.noneOf(Outcome.class);

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return outcome;
        }

        final int bits = intent.getIntExtra(BKEY_RESULT, RESULT_NONE);
        if ((bits & RESULT_READ_DONE) != 0) {
            outcome.add(Outcome.Read);
        }
        if ((bits & RESULT_WRITE_DONE) != 0) {
            outcome.add(Outcome.Write);
        }
        return outcome;
    }

    public enum Outcome {
        Read,
        Write
    }
}
