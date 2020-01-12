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
package com.hardbacknutter.nevertoomanybooks.dialogs.checklist;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Default implementation of {@link CheckListItem} encapsulating a {@code long}.
 */
public class SelectableItem
        implements CheckListItem {

    /** {@link Parcelable}. */
    public static final Creator<SelectableItem> CREATOR =
            new Creator<SelectableItem>() {
                @Override
                public SelectableItem createFromParcel(@NonNull final Parcel source) {
                    return new SelectableItem(source);
                }

                @Override
                public SelectableItem[] newArray(final int size) {
                    return new SelectableItem[size];
                }
            };

    /** Label to display. */
    private final String mLabel;
    /** The item we're encapsulating. */
    private final long mItemId;
    /** Status of this item. */
    private boolean mSelected;

    /**
     * Constructor.
     *
     * @param label    to display
     * @param itemId   the item to represent
     * @param selected the current status
     */
    public SelectableItem(@NonNull final String label,
                          final long itemId,
                          final boolean selected) {
        mItemId = itemId;
        mSelected = selected;
        mLabel = label;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SelectableItem(@NonNull final Parcel in) {
        mItemId = in.readLong();
        mLabel = in.readString();
        mSelected = in.readInt() != 0;
    }

    public long getItemId() {
        return mItemId;
    }

    @Override
    public String getLabel(@NonNull final Context context) {
        return mLabel;
    }

    @Override
    public boolean isSelected() {
        return mSelected;
    }

    @Override
    public void setSelected(final boolean selected) {
        mSelected = selected;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mItemId);
        dest.writeString(mLabel);
        dest.writeInt(mSelected ? 1 : 0);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }
}
