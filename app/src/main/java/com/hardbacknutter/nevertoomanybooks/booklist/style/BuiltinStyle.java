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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;

public class BuiltinStyle
        extends BooklistStyle {

    /** {@link Parcelable}. */
    public static final Creator<BuiltinStyle> CREATOR = new Creator<BuiltinStyle>() {
        @Override
        public BuiltinStyle createFromParcel(@NonNull final Parcel source) {
            return new BuiltinStyle(source);
        }

        @Override
        public BuiltinStyle[] newArray(final int size) {
            return new BuiltinStyle[size];
        }
    };

    /**
     * Display name of this style.
     * Used for builtin styles.
     * Always {@code 0} for a user-defined style
     */
    @StringRes
    private final int mNameResId;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param id           a negative int
     * @param uuid         UUID for the builtin style.
     * @param nameId       the resource id for the name
     * @param isPreferred  flag
     * @param menuPosition to set
     * @param groupIds     a list of groups to attach to this style
     */
    BuiltinStyle(@NonNull final Context context,
                 @IntRange(from = StyleDAO.BuiltinStyles.MAX_ID, to = -1) final long id,
                 @NonNull final String uuid,
                 @StringRes final int nameId,
                 final boolean isPreferred,
                 final int menuPosition,
                 @NonNull final int... groupIds) {
        super(context, uuid);
        mId = id;

        mNameResId = nameId;

        mIsPreferred = isPreferred;
        mMenuPosition = menuPosition;

        initPrefs(context);

        for (@BooklistGroup.Id final int groupId : groupIds) {
            getGroups().add(BooklistGroup.newInstance(context, groupId, this));
        }
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private BuiltinStyle(@NonNull final Parcel in) {
        super(in);
        mNameResId = in.readInt();
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getString(mNameResId);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mNameResId);
    }
}
