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
package com.hardbacknutter.nevertoomanybooks.booklist.style.prefs;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;

/**
 * Passthrough support for a {@code PPref<ArrayList<Integer>>}
 * <p>
 * Note that the methods are the same as in PPref itself but with the Integer type set.
 * In contrast to {@link PInt} we need a transformation step.
 */
public interface PIntList {

    String DELIM = ",";

    default void setStringCollection(@NonNull final Collection<String> values) {
        try {
            set(values.stream()
                      .map(Integer::parseInt)
                      .collect(Collectors.toCollection(ArrayList::new)));
        } catch (@NonNull final NumberFormatException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d("PIntList", "values=`" + values + '`', e);
            }
            throw new IllegalStateException("bad input");
        }
    }

    void set(@Nullable List<Integer> value);

    @NonNull
    ArrayList<Integer> getValue();
}
