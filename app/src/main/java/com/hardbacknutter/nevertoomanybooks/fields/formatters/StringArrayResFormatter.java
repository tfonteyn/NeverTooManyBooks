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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Uses a String from a String resource arrays as output based on an index value.
 *
 * <ul>
 *      <li>Multiple fields: <strong>no</strong></li>
 * </ul>
 * <p>
 * Why Long? See Sqlite docs... storage class INTEGER.
 * TLDR: we always get a long from the database even if the column stores an int.
 */
public class StringArrayResFormatter
        implements FieldFormatter<Long> {

    @NonNull
    private final String[] stringArray;

    /**
     * Constructor.
     *
     * @param context    Current context
     * @param arrayResId the string resources
     */
    public StringArrayResFormatter(@NonNull final Context context,
                                   @ArrayRes final int arrayResId) {
        stringArray = context.getResources().getStringArray(arrayResId);
    }

    @Override
    @NonNull
    public String format(@NonNull final Context context,
                         @Nullable final Long rawValue) {
        if (rawValue == null || rawValue < 0 || rawValue >= stringArray.length) {
            return stringArray[0];
        } else {
            return stringArray[rawValue.intValue()];
        }
    }
}
