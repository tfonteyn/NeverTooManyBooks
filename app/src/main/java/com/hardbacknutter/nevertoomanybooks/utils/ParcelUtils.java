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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

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
            //noinspection unchecked
            list.set(i, (T) in.readParcelable(cl));
        }
        for (; i < N; i++) {
            //noinspection unchecked
            list.add((T) in.readParcelable(cl));
        }
        for (; i < M; i++) {
            list.remove(N);
        }
        return list;
    }
}
