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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public class BitmaskFilter
        extends PBitmask
        implements Filter<Integer> {

    /** See {@link com.hardbacknutter.nevertoomanybooks.widgets.BitmaskPreference}. */
    private static final String ACTIVE = ".active";

    @StringRes
    private final int mLabelId;

    private final TableDefinition mTable;
    private final DomainDefinition mDomain;

    /**
     * Constructor.
     * Default value is {@code 0}, i.e. no bits set.
     *
     * @param labelId      string resource id to use as a display label
     * @param key          of the preference
     * @param uuid         UUID of the style
     * @param isPersistent {@code true} to have the value persisted.
     *                     {@code false} for in-memory only.
     * @param table        to use by the expression
     * @param domain       to use by the expression
     */
    public BitmaskFilter(@StringRes final int labelId,
                         @NonNull final String key,
                         @NonNull final String uuid,
                         final boolean isPersistent,
                         @SuppressWarnings("SameParameterValue") @NonNull
                         final TableDefinition table,
                         @NonNull final DomainDefinition domain) {
        super(key, uuid, isPersistent, 0);
        mLabelId = labelId;
        mTable = table;
        mDomain = domain;
    }

    /**
     * If the bitmask has <strong>at least one bit set</strong>,
     * the filter looks for values having <strong>those</strong> bits set;
     * other bits being ignored.
     * If the bitmask is {@code == 0}, the filter looks for values {@code == 0} only.
     *
     * @return filter SQL expression, or {@code null} if not active.
     */
    @Override
    @Nullable
    public String getExpression() {
        if (isActive()) {
            int value = get();
            if (value > 0) {
                return "((" + mTable.dot(mDomain) + " & " + get() + ") <> 0)";
            } else {
                return "(" + mTable.dot(mDomain) + "=0)";

            }
        }
        return null;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }

    @Override
    public boolean isActive() {
        return getPrefs().getBoolean(getKey() + ACTIVE, false);
    }

    @Override
    @NonNull
    public String toString() {
        return "BitmaskFilter{"
               + "table=" + mTable.getName()
               + ", domain=" + mDomain
               + ", mLabelId=" + mLabelId
               + ", isActive=" + isActive()
               + ", " + super.toString()
               + "}\n";
    }
}
