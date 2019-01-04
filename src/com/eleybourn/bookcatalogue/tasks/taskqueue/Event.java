/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.tasks.taskqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter;

import java.io.Serializable;


/**
 * Base class for capturing and storing exceptions that occur during task processing but which
 * do not prevent the task from completing. Examples might include a long running export job
 * in which 3 items fail, but 1000 succeed -- in this case it is useful to export the successful
 * ones and report the failures later.
 *
 * The Task object has a 'saveException()' method that stores the exception in the database
 * for later retrieval.
 *
 * Client applications should consider subclassing this object.
 *
 * @author Philip Warner
 */
public abstract class Event implements Serializable, BindableItemCursorAdapter.BindableItem {
    private static final long serialVersionUID = 5209097408979831308L;

    @NonNull
    private final String mDescription;
    private long mId = 0;
    private Exception mException = null;

    protected Event(@NonNull final String description) {
        mDescription = description;
    }

    public Event(@NonNull final String description, @Nullable final Exception e) {
        mDescription = description;
        mException = e;
    }
    @NonNull
    public String getDescription() {
        return mDescription;
    }

    public Exception getException() {
        return mException;
    }

    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }
}
