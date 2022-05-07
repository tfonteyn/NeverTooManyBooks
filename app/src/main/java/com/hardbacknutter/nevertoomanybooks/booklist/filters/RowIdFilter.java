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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * Filter on a specific/single row id.
 */
public class RowIdFilter
        implements Filter {

    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final Domain mDomain;

    private final long mId;

    public RowIdFilter(@NonNull final TableDefinition table,
                       @NonNull final Domain domain,
                       final long id) {
        mTable = table;
        mDomain = domain;
        mId = id;
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final Context context) {
        return '(' + mTable.dot(mDomain) + '=' + mId + ')';
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return mId > 0;
    }
}
