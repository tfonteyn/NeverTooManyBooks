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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Encapsulate an {@link Entity} and a boolean 'isSelected' flag.
 */
public class SelectableEntity
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<SelectableEntity> CREATOR = new Creator<SelectableEntity>() {
        @Override
        public SelectableEntity createFromParcel(@NonNull final Parcel source) {
            return new SelectableEntity(source);
        }

        @Override
        public SelectableEntity[] newArray(final int size) {
            return new SelectableEntity[size];
        }
    };

    /** The item we're encapsulating. */
    private final Entity mEntity;
    /** Status of this item. */
    private boolean mIsSelected;

    /**
     * Constructor.
     *
     * @param isSelected the current status
     */
    public SelectableEntity(@NonNull final Entity entity,
                            final boolean isSelected) {
        mEntity = entity;
        mIsSelected = isSelected;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SelectableEntity(@NonNull final Parcel in) {
        mEntity = in.readParcelable(getClass().getClassLoader());
        mIsSelected = in.readInt() != 0;
    }

    public Entity getEntity() {
        return mEntity;
    }

    public boolean isSelected() {
        return mIsSelected;
    }

    public void setSelected(final boolean selected) {
        mIsSelected = selected;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(mEntity, flags);
        dest.writeInt(mIsSelected ? 1 : 0);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }
}
