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

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public class PBooleanFilter
        implements PFilter<Boolean> {

    @StringRes
    private final int mLabelId;
    @ArrayRes
    private final int mAcEntries;

    @NonNull
    private final String mName;
    @NonNull
    private final Domain mDomain;
    @NonNull
    private final TableDefinition mTable;
    @Nullable
    private Boolean mValue;

    PBooleanFilter(@NonNull final String name,
                   @StringRes final int labelId,
                   @ArrayRes final int acEntries,
                   @NonNull final TableDefinition table,
                   @NonNull final Domain domain) {
        mLabelId = labelId;
        mAcEntries = acEntries;
        mDomain = domain;
        mTable = table;
        mName = name;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return mValue != null;
    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        return mTable.dot(mDomain) + '=' + mValue;
    }

    @Override
    @NonNull
    public String getPrefName() {
        return mName;
    }

    @Nullable
    @Override
    public String getValueAsString() {
        return mValue == null ? "-1" : mValue ? "1" : "0";
    }

    @Override
    public void setValueAsString(@Nullable final String value) {
        mValue = value == null ? null : "1".equals(value);
    }

    @Override
    public void setValue(@Nullable final Boolean value) {
        mValue = value;
    }

    @Override
    @NonNull
    public String getValueText(@NonNull final Context context) {
        final CharSequence[] textArray = context.getResources().getTextArray(mAcEntries);
        if (mValue == null) {
            return textArray[0].toString();
        } else {
            return textArray[mValue ? 2 : 1].toString();
        }
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }
}
