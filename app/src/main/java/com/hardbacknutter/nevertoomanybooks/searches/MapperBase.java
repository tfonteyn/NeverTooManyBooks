/*
 * @Copyright 2020 HardBackNutter
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
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public abstract class MapperBase
        implements Mapper {

    /** map to translate site book format terminology with our own. */
    static final Map<String, Integer> MAPPER = new HashMap<>();

    @Override
    public void map(@NonNull final Context context,
                    @NonNull final Bundle bookData) {

        String value = bookData.getString(getKey());
        if (value != null && !value.isEmpty()) {
            Integer resId = null;
            String lcValue = value.toLowerCase(LocaleUtils.getUserLocale(context));
            int len = 0;
            for (final Map.Entry<String, Integer> entry : MAPPER.entrySet()) {
                if (lcValue.startsWith(entry.getKey())) {
                    resId = entry.getValue();
                    len = entry.getKey().length();
                    break;
                }
            }

            if (resId != null) {
                value = (context.getString(resId) + ' ' + value.substring(len).trim()).trim();
            }

            bookData.putString(getKey(), value);
        }
    }
}
