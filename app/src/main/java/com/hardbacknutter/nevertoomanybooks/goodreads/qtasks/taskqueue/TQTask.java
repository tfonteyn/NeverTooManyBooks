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
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin.ContextDialogItem;

/**
 * Abstract base class for all Tasks.
 */
public abstract class TQTask
        implements TQItem {

    /**
     * See {@link #getCategory()}.
     * <p>
     * {@code 0} is used for legacy tasks.
     */
    static final int CAT_UNKNOWN = 0;
    /** We're only allowing a single import task to be scheduled/run at any time. */
    public static final int CAT_IMPORT = 1;
    /** We're only allowing a single export task to be scheduled/run at any time. */
    public static final int CAT_EXPORT = 2;
    /** But we can schedule multiple single-book export times at any time. */
    public static final int CAT_EXPORT_ONE_BOOK = 3;

    public static final String COMPLETED = "S";
    public static final String FAILED = "F";
    public static final String QUEUED = "Q";

    private static final int RETRY_LIMIT = 15;
    private static final long serialVersionUID = 6663611438001508755L;

    @NonNull
    private final String mDescription;
    /** Row ID. */
    private long mId;
    @Nullable
    private Exception mLastException;

    private int mRetries;
    private int mRetryDelay;
    private boolean mIsCancelled;

    /**
     * Constructor.
     *
     * @param description for the task; should be localized for display to the user.
     */
    public TQTask(@NonNull final String description) {
        mDescription = description;
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    /**
     * Get the user-displayable description for this task.
     *
     * @param context Current context
     *
     * @return localized description string
     */
    @NonNull
    public String getDescription(@NonNull final Context context) {
        return mDescription;
    }

    /**
     * Return an application-defined category for the task.
     * <p>
     * The category can be used to lookup queued tasks based on category, for example to
     * allow an application to ensure only one job of a particular category is queued, or
     * to retrieve all jobs of a particular category.
     */
    public abstract int getCategory();

    @Nullable
    public Exception getLastException() {
        return mLastException;
    }

    protected void setLastException(@Nullable final Exception e) {
        mLastException = e;
    }

    /**
     * There is little that can be done to abort a task; we trust the implementations to
     * check this flag periodically on long tasks.
     */
    public boolean isCancelled() {
        return mIsCancelled;
    }

    public void cancel() {
        mIsCancelled = true;
    }

    public int getRetryLimit() {
        return RETRY_LIMIT;
    }

    protected int getRetryDelay() {
        return mRetryDelay;
    }

    protected void setRetryDelay(@SuppressWarnings("SameParameterValue") final int delay) {
        mRetryDelay = delay;
    }

    void setRetryDelay() {
        mRetryDelay = (int) Math.pow(2, mRetries + 1);
    }

    public int getRetries() {
        return mRetries;
    }

    void setRetries(final int retries) {
        mRetries = retries;
    }

    boolean canRetry() {
        return mRetries < RETRY_LIMIT;
    }

    protected void storeEvent(@NonNull final TQEvent event) {
        QueueManager.getInstance().storeTaskEvent(mId, event);
    }

    protected void resetRetryCounter() {
        mRetries = 0;
        setRetryDelay();
    }

    @Override
    @CallSuper
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final List<ContextDialogItem> menuItems,
                                    @NonNull final BookDao bookDao) {
        menuItems.add(new ContextDialogItem(
                context.getString(R.string.gr_tq_menu_delete_task),
                () -> QueueManager.getInstance().deleteTask(getId())));
    }

    @Override
    @NonNull
    public String toString() {
        return "TQTask{"
               + "mDescription=`" + mDescription + '`'
               + ", mId=" + mId
               + ", mLastException=" + mLastException
               + ", mRetries=" + mRetries
               + ", mRetryDelay=" + mRetryDelay
               + ", mIsAborting=" + mIsCancelled
               + '}';
    }
}
