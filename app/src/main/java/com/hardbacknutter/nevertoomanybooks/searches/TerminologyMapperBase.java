/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class TerminologyMapperBase {

    /** map to translate site book format terminology with our own. */
    static final Map<String, Integer> MAPPER = new HashMap<>();

    protected final Context mContext;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    TerminologyMapperBase(@NonNull final Context context) {
        mContext = context;
    }

    public abstract String getKey();

    /**
     * The current value is read from the bundle, and replaced by the mapped value if found.
     *
     * @param bookData with {@link #getKey} entry to map
     */
    public void map(@NonNull final Bundle bookData) {

        String value = bookData.getString(getKey());
        if (value != null && !value.isEmpty()) {

            Integer resId = MAPPER.get(value.toLowerCase(Locale.getDefault()));
            value = resId != null ? mContext.getString(resId) : value;

            bookData.putString(getKey(), value);
        }
    }
}
