/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * The main reason that you need to extend this is because each type of encapsulated item
 * will have its own way of storing a label (to display next to the checkbox).
 * Using .toString() is not really a nice solution, hence... extends this class
 * and implement: String {@link CheckListItem#getLabel}
 *
 * @param <T> type of encapsulated item
 */
public abstract class CheckListItemBase<T>
        implements CheckListItem<T>, Parcelable {

    protected T item;
    private boolean mSelected;

    /**
     * Constructor.
     *
     * @param item     to encapsulate
     * @param selected the current status
     */
    protected CheckListItemBase(@NonNull final T item,
                                final boolean selected) {
        this.item = item;
        mSelected = selected;
    }

    /**
     * {@link Parcelable} Constructor.
     * <p>
     * Subclass must handle the {@link #item}.
     *
     * @param in Parcel to construct the object from
     */
    protected CheckListItemBase(@NonNull final Parcel in) {
        mSelected = in.readInt() != 0;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * <br>{@inheritDoc}
     * <br>
     * <p>Subclass must handle the {@link #item}.
     */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mSelected ? 1 : 0);
    }

    @NonNull
    @Override
    public T getItem() {
        return item;
    }

    @Override
    public boolean isChecked() {
        return mSelected;
    }

    @Override
    public void setChecked(final boolean selected) {
        mSelected = selected;
    }

    @Override
    public abstract String getLabel(@NonNull final Context context);
}
