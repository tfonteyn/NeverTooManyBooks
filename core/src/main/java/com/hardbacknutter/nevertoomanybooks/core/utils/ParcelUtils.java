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
package com.hardbacknutter.nevertoomanybooks.core.utils;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link #readParcelableList(Parcel, List, ClassLoader)}
 * {@link #writeParcelableList(Parcel, List, int)}
 * are copied from the standard Android API 29 {@link Parcel}.
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
     * @see Parcel#writeParcelableList(List, int)
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
     * @see Parcel#readParcelableList(List, ClassLoader)
     */
    @SuppressWarnings("ForLoopWithMissingComponent")
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

    /**
     * Wrap a {@code List&lt;Long&gt;} in a {@link Parcelable}.
     *
     * @param list to wrap
     *
     * @return single Parcelable
     *
     * @see #unwrap(Bundle, String)
     */
    @NonNull
    public static Parcelable wrap(@NonNull final List<Long> list) {
        return new ParcelableArrayListLong(list);
    }

    /**
     * Extract a {@code List&lt;Long&gt;} from the given arguments.
     *
     * @return the list, or {@code null} if not found
     *
     * @see #wrap(List)
     */
    @Nullable
    public static List<Long> unwrap(@NonNull final Bundle args,
                                    @NonNull final String key) {
        final Parcelable parcelable = args.getParcelable(key);
        return parcelable != null ? ((ParcelableArrayListLong) parcelable).unwrap() : null;
    }

    /**
     * {@link Bundle} has no builtin support for parceling an {@code ArrayList<Long>}
     * other then classic serialisation.
     * <p>
     * Use {@link #wrap(List)} and {@link #unwrap(Bundle, String)} which will use
     * this helper class to get around that limitation.
     */
    public static final class ParcelableArrayListLong
            implements Parcelable {

        public static final Creator<ParcelableArrayListLong> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public ParcelableArrayListLong createFromParcel(@NonNull final Parcel in) {
                return new ParcelableArrayListLong(in);
            }

            @Override
            @NonNull
            public ParcelableArrayListLong[] newArray(final int size) {
                return new ParcelableArrayListLong[size];
            }
        };

        @NonNull
        private final List<Long> list;

        private ParcelableArrayListLong(@NonNull final List<Long> list) {
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
        private List<Long> unwrap() {
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
