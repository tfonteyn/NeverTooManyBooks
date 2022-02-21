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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link #readParcelableList(Parcel, List, ClassLoader)}
 * {@link #writeParcelableList(Parcel, List, int)}
 * are copied from the standard Android API 29 libraries.
 */
public final class ParcelUtils {

    private ParcelUtils() {
    }

    /**
     * Flatten a {@code List} containing arbitrary {@code Parcelable} objects into this parcel
     * at the current position. They can later be retrieved using
     * {@link #readParcelableList(Parcel, List, ClassLoader)} if required.
     *
     * @see #readParcelableList(Parcel, List, ClassLoader)
     */
    public static <T extends Parcelable> void writeParcelableList(@NonNull final Parcel out,
                                                                  @Nullable final List<T> val,
                                                                  final int flags) {
        if (val == null) {
            out.writeInt(-1);
            return;
        }

        final int N = val.size();
        int i = 0;
        out.writeInt(N);
        while (i < N) {
            out.writeParcelable(val.get(i), flags);
            i++;
        }
    }

    /**
     * Read the list of {@code Parcelable} objects at the current data position into the
     * given {@code list}. The contents of the {@code list} are replaced. If the serialized
     * list was {@code null}, {@code list} is cleared.
     *
     * @see #writeParcelableList(Parcel, List, int)
     */
    @NonNull
    public static <T extends Parcelable> List<T> readParcelableList(
            @NonNull final Parcel in,
            @NonNull final List<T> list,
            @Nullable final ClassLoader cl) {

        final int N = in.readInt();
        if (N == -1) {
            list.clear();
            return list;
        }

        final int M = list.size();
        int i = 0;
        for (; i < M && i < N; i++) {
            list.set(i, in.readParcelable(cl));
        }
        for (; i < N; i++) {
            list.add(in.readParcelable(cl));
        }
        for (; i < M; i++) {
            list.remove(N);
        }
        return list;
    }

    @NonNull
    public static Parcelable wrap(@NonNull final ArrayList<Long> list) {
        return new ParcelableArrayListLong(list);
    }

    @NonNull
    public static ArrayList<Long> unwrap(@NonNull final Bundle args,
                                         @NonNull final String key) {
        return ((ParcelableArrayListLong)
                        Objects.requireNonNull(args.getParcelable(key), key)).unwrap();
    }

    public static final class ParcelableArrayListLong
            implements Parcelable {

        public static final Creator<ParcelableArrayListLong> CREATOR = new Creator<>() {
            @Override
            public ParcelableArrayListLong createFromParcel(@NonNull final Parcel in) {
                return new ParcelableArrayListLong(in);
            }

            @Override
            public ParcelableArrayListLong[] newArray(final int size) {
                return new ParcelableArrayListLong[size];
            }
        };

        @NonNull
        private final ArrayList<Long> list;

        private ParcelableArrayListLong(@NonNull final ArrayList<Long> list) {
            this.list = list;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private ParcelableArrayListLong(@NonNull final Parcel in) {
            list = new ArrayList<>();
            in.readList(list, getClass().getClassLoader());
        }

        @NonNull
        private ArrayList<Long> unwrap() {
            return list;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeList(list);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
