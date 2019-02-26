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

package com.eleybourn.bookcatalogue.goodreads.taskqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 * Abstract base class for all Tasks.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 * <p>
 * When run, it will have access to the Application context, and can use that to interact
 * with the UI.
 * <p>
 * It it important to note that the run(...) method is NOT called in the main thread.
 * Access to the main thread is provided by ...
 *
 * @author Philip Warner
 */
public abstract class Task
        implements Serializable, BindableItemCursorAdapter.BindableItem {

    static final int CAT_LEGACY = 0;
    public static final int CAT_GOODREADS_AUTH_RESULT = 2;
    public static final int CAT_GOODREADS_IMPORT_ALL = 3;
    public static final int CAT_GOODREADS_EXPORT_ALL = 4;
    public static final int CAT_GOODREADS_EXPORT_ONE = 5;

    static final String STATUS_COMPLETE = "S";
    static final String STATUS_FAILED = "F";
    static final String STATUS_QUEUED = "Q";


    private static final long serialVersionUID = -1735892871810069L;
    private final int mRetryLimit = 15;
    @NonNull
    private final String mDescription;
    private long mId;
    private int mRetries;

    @Nullable
    private Exception mException;
    private int mRetryDelay;
    private boolean mAbortTask;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    Task(@NonNull final String description) {
        mDescription = description;
    }

    /**
     * Return an application-defined category for the task.
     * <p>
     * The category can be used to lookup queued tasks based on category, for example to
     * allow an application to ensure only one job of a particular category is queued, or
     * to retrieve all jobs of a particular category.
     */
    public abstract int getCategory();

    @NonNull
    public String getDescription() {
        return mDescription;
    }

    /**
     * There is little that can be done to abort a task; we trust the implementations to
     * check this flag periodically on long tasks.
     */
    protected boolean isAborting() {
        return mAbortTask;
    }

    void abortTask() {
        mAbortTask = true;
    }

    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    int getRetryLimit() {
        return mRetryLimit;
    }

    protected int getRetryDelay() {
        return mRetryDelay;
    }

    protected void setRetryDelay(final int delay) {
        mRetryDelay = delay;
    }

    void setRetryDelay() {
        setRetryDelay((int) Math.pow(2, mRetries + 1));
    }

    int getRetries() {
        return mRetries;
    }

    void setRetries(final int retries) {
        mRetries = retries;
    }

    boolean canRetry() {
        return mRetries < mRetryLimit;
    }

    @Nullable
    public Exception getException() {
        return mException;
    }

    public void setException(@Nullable final Exception e) {
        mException = e;
    }

    protected void storeEvent(@NonNull final Event e) {
        QueueManager.getQueueManager().storeTaskEvent(this, e);
    }

    protected void resetRetryCounter() {
        mRetries = 0;
        setRetryDelay();
    }
}
