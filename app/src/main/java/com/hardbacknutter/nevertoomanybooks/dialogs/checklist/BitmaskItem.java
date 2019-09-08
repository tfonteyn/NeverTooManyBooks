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
package com.hardbacknutter.nevertoomanybooks.dialogs.checklist;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public class BitmaskItem
        extends CheckListItemBase<Integer>
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<BitmaskItem> CREATOR =
            new Creator<BitmaskItem>() {
                @Override
                public BitmaskItem createFromParcel(@NonNull final Parcel source) {
                    return new BitmaskItem(source);
                }

                @Override
                public BitmaskItem[] newArray(final int size) {
                    return new BitmaskItem[size];
                }
            };

    @StringRes
    private final int mLabelId;

    /**
     * Constructor.
     *
     * @param bitMask  the item to encapsulate
     * @param labelId  resource id for the label to display
     * @param selected the current status
     */
    public BitmaskItem(@NonNull final Integer bitMask,
                       @StringRes final int labelId,
                       final boolean selected) {
        super(bitMask, selected);
        mLabelId = labelId;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private BitmaskItem(@NonNull final Parcel in) {
        super(in);
        item = in.readInt();
        mLabelId = in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(item);
        dest.writeInt(mLabelId);
    }

    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }
}
