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
package com.hardbacknutter.nevertoomanybooks.booklist.style.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;

public class BooleanFilter
        extends IntStringFilter {

    /**
     * Constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param labelId          string resource id to use as a display label
     * @param key              preference key
     * @param virtualDomain    to use by the expression
     */
    BooleanFilter(final boolean isPersistent,
                  @NonNull final StylePersistenceLayer persistenceLayer,
                  @StringRes final int labelId,
                  @NonNull final String key,
                  @SuppressWarnings("SameParameterValue")
                  @NonNull final VirtualDomain virtualDomain) {
        super(isPersistent, persistenceLayer, labelId, key, virtualDomain);
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param filter           to copy from
     */
    BooleanFilter(final boolean isPersistent,
                  @NonNull final StylePersistenceLayer persistenceLayer,
                  @NonNull final BooleanFilter filter) {
        super(isPersistent, persistenceLayer, filter);
    }

    @Override
    @Nullable
    public String getExpression(@NonNull final Context context) {
        final Integer value = getValue();
        if (isActive(context)) {
            return mVirtualDomain.getExpression() + '=' + value;
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
