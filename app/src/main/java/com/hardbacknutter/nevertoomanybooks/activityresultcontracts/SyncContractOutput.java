/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Bundle encoding is always an {@link EnumSet}.
 */
public enum SyncContractOutput
        implements Parcelable, ContractOutput {
    /** Data was imported; i.e. local changes were made. */
    Read,
    /** Data was exported/written; no local changes done. */
    Write;

    /** {@link Parcelable}. */
    public static final Creator<SyncContractOutput> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public SyncContractOutput createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public SyncContractOutput[] newArray(final int size) {
            return new SyncContractOutput[size];
        }
    };

    private static final String TAG = "SyncContractOutput";
    private static final String BKEY_RESULT = TAG + ":result";

    @NonNull
    public static EnumSet<SyncContractOutput> fromBundle(@Nullable final Bundle args) {
        if (args != null) {
            // retrieve as a list
            @SuppressWarnings("deprecation")
            final List<SyncContractOutput> list = args.getParcelableArrayList(BKEY_RESULT);
            if (list != null) {
                // and transform back to an EnumSet
                return EnumSet.copyOf(list);
            }
        }
        return EnumSet.noneOf(SyncContractOutput.class);
    }

    @Override
    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(1);
        // We can put a set, transform it to a list
        final ArrayList<SyncContractOutput> list = new ArrayList<>(EnumSet.of(this));
        args.putParcelableArrayList(BKEY_RESULT, list);
        return args;
    }

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
