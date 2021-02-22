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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin.ContextDialogItem;


/**
 * Base class for capturing and storing exceptions that occur during task processing but which
 * do not prevent the task from completing. Examples might include a long running export job
 * in which 3 items fail, but 678 succeed -- in this case it is useful to export the successful
 * ones and report the failures later.
 */
public class TQEvent
        implements TQItem {

    private static final long serialVersionUID = -8207960724623016221L;
    @NonNull
    private final String mDescription;
    /** Row ID. */
    private long mId;

    /**
     * Constructor.
     *
     * @param description for this event
     */
    protected TQEvent(@NonNull final String description) {
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
    public String getDescription(@NonNull final Context context) {
        return mDescription;
    }

    @Override
    @CallSuper
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final List<ContextDialogItem> menuItems,
                                    @NonNull final BookDao bookDao) {
        menuItems.add(new ContextDialogItem(
                context.getString(R.string.gr_tq_menu_delete_event),
                () -> QueueManager.getInstance().deleteEvent(getId())));
    }

    @Override
    @NonNull
    public String toString() {
        return "TQEvent{"
               + "mId=" + mId
               + ", mDescription=`" + mDescription + '`'
               + '}';
    }
}
