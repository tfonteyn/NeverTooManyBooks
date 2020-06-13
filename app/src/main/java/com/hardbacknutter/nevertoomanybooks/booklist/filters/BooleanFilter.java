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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * Used for {@link androidx.preference.ListPreference} mimicking a nullable Boolean
 * and supporting an SQL WHERE clause expression.
 * <ul>
 *      <li>value==1 -> true</li>
 *      <li>value==0 -> false</li>
 *      <li>value==-1 -> do not use the filter</li>
 * </ul>
 * <p>
 * Syntax sugar for {@link IntStringFilter}.
 */
public class BooleanFilter
        extends IntStringFilter {

    /**
     * Constructor.
     *
     * @param labelId      string resource id to use as a display label
     * @param key          of the preference
     * @param uuid         UUID of the style
     * @param isPersistent {@code true} to have the value persisted.
     *                     {@code false} for in-memory only.
     * @param table        to use by the expression
     * @param domainKey    to use by the expression
     */
    public BooleanFilter(@StringRes final int labelId,
                         @NonNull final String key,
                         @NonNull final String uuid,
                         final boolean isPersistent,
                         @SuppressWarnings("SameParameterValue") @NonNull
                         final TableDefinition table,
                         @NonNull final String domainKey) {
        super(labelId, key, uuid, isPersistent, table, domainKey);
    }

    @Override
    @Nullable
    public String getExpression(@NonNull final Context context) {
        Integer value = getValue(context);
        if (!P_NOT_USED.equals(value)) {
            return mTable.dot(mDomainKey) + '=' + value;
        }
        return null;
    }

    /**
     * syntax sugar.
     *
     * @param value to set
     */
    public void set(final boolean value) {
        set(value ? 1 : 0);
    }
}
