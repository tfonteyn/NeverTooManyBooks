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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.Serializable;


/**
 * Base class for capturing and storing exceptions that occur during task processing but which
 * do not prevent the task from completing. Examples might include a long running export job
 * in which 3 items fail, but 678 succeed -- in this case it is useful to export the successful
 * ones and report the failures later.
 * <p>
 * The {@link Task#setLastException(Exception)} method stores the exception in the database
 * for later retrieval.
 * <p>
 * Client applications should consider subclassing this object.
 * <p>
 * An Event *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
public abstract class Event<
        BICursor extends BindableItemCursor,
        BIViewHolder extends BindableItemViewHolder>
        implements BindableItem<BICursor, BIViewHolder>,
                   Serializable {

    private static final long serialVersionUID = 7879945038246273501L;
    @NonNull
    private final String mDescription;
    private long mId;

    protected Event(@NonNull final String description) {
        mDescription = description;
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    @NonNull
    protected String getDescription(@NonNull final Context context) {
        return mDescription;
    }

    @Override
    @NonNull
    public String toString() {
        return "Event{"
               + "mId=" + mId
               + ", mDescription=`" + mDescription + '`'
               + '}';
    }
}
