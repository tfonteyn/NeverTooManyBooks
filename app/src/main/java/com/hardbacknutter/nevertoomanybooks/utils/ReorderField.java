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

package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * The supported fields on which we can reorder.
 */
public enum ReorderField {
    Title(R.string.pv_reformat_titles_prefixes, "sort.title.reordered", true),
    Publisher(R.string.pv_reformat_publisher_prefixes, "sort.publisher.reordered", false);

    private final int prefixResId;
    private final boolean defValue;
    @NonNull
    private final String prefKey;

    ReorderField(final int prefixResId,
                 @NonNull final String prefKey,
                 final boolean defValue) {
        this.prefixResId = prefixResId;
        this.defValue = defValue;
        this.prefKey = prefKey;
    }

    @StringRes
    public int getPrefixResId() {
        return prefixResId;
    }

    /**
     * Get the preference key associated with this type.
     *
     * @return pref. key
     */
    @NonNull
    public String getPrefKey() {
        return prefKey;
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if the field should be reordered. e.g. "The title" -> "title, The"
     */
    public boolean isSortReorder(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(prefKey, defValue);

    }
}
