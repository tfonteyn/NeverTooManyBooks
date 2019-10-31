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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * Used for {@link androidx.preference.ListPreference} mimicking a nullable Boolean
 * and supporting an SQL WHERE clause expression.
 * <ul>
 * <li>value==1 -> true</li>
 * <li>value==0 -> false</li>
 * <li>value==-1 -> do not use the filter</li>
 * </ul>
 * <p>
 * Syntax sugar for {@link IntStringFilter}.
 */
public class BooleanFilter
        extends IntStringFilter {

    public BooleanFilter(@StringRes final int labelId,
                         @NonNull final String key,
                         @NonNull final String uuid,
                         final boolean isPersistent,
                         @SuppressWarnings("SameParameterValue") @NonNull
                         final TableDefinition table,
                         @NonNull final DomainDefinition domain) {
        super(labelId, key, uuid, isPersistent, table, domain);
    }

    public void set(final boolean value) {
        set(value ? 1 : 0);
    }
}
