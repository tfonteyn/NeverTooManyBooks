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
package com.hardbacknutter.nevertoomanybooks.booklist.style.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public class NotEmptyFilter
        extends IntStringFilter {

    /**
     * Constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param labelId          string resource id to use as a display label
     * @param key              preference key
     * @param tableDefinition  to use by the expression
     */
    NotEmptyFilter(final boolean isPersistent,
                   @Nullable final StylePersistenceLayer persistenceLayer,
                   @StringRes final int labelId,
                   @NonNull final String key,
                   @NonNull final TableDefinition tableDefinition,
                   @NonNull final Domain domain) {
        super(isPersistent, persistenceLayer, labelId, key, domain, tableDefinition);
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param filter           to copy from
     */
    private NotEmptyFilter(final boolean isPersistent,
                           @Nullable final StylePersistenceLayer persistenceLayer,
                           @NonNull final NotEmptyFilter filter) {
        super(isPersistent, persistenceLayer, filter);
    }

    @Override
    @NonNull
    public NotEmptyFilter clone(final boolean isPersistent,
                                @Nullable final StylePersistenceLayer persistenceLayer) {
        return new NotEmptyFilter(isPersistent, persistenceLayer, this);
    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        final Integer value = getValue();
        if (value == 0) {
            return "((" + mTable.dot(mDomain) + " IS NULL)"
                   + " OR (" + mTable.dot(mDomain) + "=''))";
        } else {
            return "((" + mTable.dot(mDomain) + " IS NOT NULL)"
                   + " AND (" + mTable.dot(mDomain) + "<>''))";
        }
    }
}
