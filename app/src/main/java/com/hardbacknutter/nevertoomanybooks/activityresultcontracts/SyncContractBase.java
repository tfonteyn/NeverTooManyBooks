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
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.util.logger.LoggerFactory;

public abstract class SyncContractBase
        extends ActivityResultContract<Void, EnumSet<SyncContractBase.Outcome>> {

    private static final String TAG = "SyncContractBase";
    private static final String BKEY_RESULT = TAG + ":result";

    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @param outcome the result
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(@NonNull final Outcome outcome) {
        return new Intent().putParcelableArrayListExtra(BKEY_RESULT,
                                                        new ArrayList<>(EnumSet.of(outcome)));
    }

    @Override
    @NonNull
    public EnumSet<SyncContractBase.Outcome> parseResult(final int resultCode,
                                                         @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            LoggerFactory.getLogger()
                          .d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return EnumSet.noneOf(Outcome.class);
        }

        final List<Outcome> list = intent.getParcelableArrayListExtra(BKEY_RESULT);
        if (list == null) {
            return EnumSet.noneOf(Outcome.class);
        }
        return EnumSet.copyOf(list);
    }

    public enum Outcome
            implements Parcelable {
        /** Data was imported; i.e. local changes were made. */
        Read,
        /** Data was exported/written; no local changes done. */
        Write;

        /** {@link Parcelable}. */
        public static final Creator<Outcome> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Outcome createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public Outcome[] newArray(final int size) {
                return new Outcome[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(this.ordinal());
        }
    }
}
