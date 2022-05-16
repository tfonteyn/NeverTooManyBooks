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
import android.os.Parcel;
import android.os.Parcelable;

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

    @NonNull
    public static Intent createResultIntent(@Nullable final String uuid,
                                            final boolean modified) {
        final Parcelable output = new Output(uuid, modified);
        return new Intent().putExtra(Output.BKEY, output);
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

        return intent.getParcelableExtra(Output.BKEY);
    }

    public static class Output
            implements Parcelable {

        public static final Creator<Output> CREATOR = new Creator<>() {
            @Override
            public Output createFromParcel(@NonNull final Parcel in) {
                return new Output(in);
            }

            @Override
            public Output[] newArray(final int size) {
                return new Output[size];
            }
        };
        private static final String BKEY = TAG + ":Output";
        // Return the currently selected style UUID, so the caller can apply it.
        // This is independent from any modification to this or another style,
        // or the order of the styles.
        @Nullable
        public final String uuid;
        // Same here, this is independent from the returned style
        public final boolean isModified;

        private Output(@Nullable final String uuid,
                       final boolean modified) {
            this.uuid = uuid;
            this.isModified = modified;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private Output(@NonNull final Parcel in) {
            uuid = in.readString();
            isModified = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(uuid);
            dest.writeByte((byte) (isModified ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
