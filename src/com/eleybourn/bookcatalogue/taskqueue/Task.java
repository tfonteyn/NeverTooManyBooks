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

package com.eleybourn.bookcatalogue.taskqueue;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.widgets.BindableItemCursorAdapter;

import java.io.Serializable;

/**
 * Abstract base class for all Tasks.
 *
 * A Task MUST be serializable. This means that it can not contain any references to
 * UI components or similar objects.
 *
 * When run, it will have access to the Application context, and can use that to interact with the UI.
 *
 * It it important to note that the run(...) method is NOT called in the main thread.
 * Access to the main thread is provided by ...
 *
 * @author Philip Warner
 */
public abstract class Task implements Serializable, BindableItemCursorAdapter.BindableItem {

    private static final long serialVersionUID = -1735892871810069L;
    private final int mRetryLimit = 17;
    private final String mDescription;
    private TaskState mState;
    private long mId;
    private int mRetries;
    private Exception mException = null;
    private int mRetryDelay = 0;
    private boolean mAbortTask = false;
    Task(String description) {
        mState = TaskState.created;
        mDescription = description;
    }

    /**
     * Return an application-defined category for the task; a default of 0 is provided.
     *
     * The category can be used to lookup queued tasks based on category, for example to
     * allow an application to ensure only one job of a particular category is queued, or
     * to retrieve all jobs of a particular category.
     */
    public abstract int getCategory();

    @NonNull
    public String getDescription() {
        return mDescription;
    }

    public void setState(@NonNull final TaskState state) {
        mState = state;
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

    protected int getRetryLimit() {
        return mRetryLimit;
    }

    protected int getRetryDelay() {
        return mRetryDelay;
    }

    protected void setRetryDelay(final int delay) {
        mRetryDelay = delay;
    }

    void setRetryDelay() {
        setRetryDelay((int) Math.pow(2, (mRetries + 1)));
    }

    protected int getRetries() {
        return mRetries;
    }

    void setRetries(final int retries) {
        mRetries = retries;
    }

    boolean canRetry() {
        return mRetries < mRetryLimit;
    }

    public Exception getException() {
        return mException;
    }

    public void setException(final Exception e) {
        mException = e;
    }

    protected void storeEvent(@NonNull final Event e) {
        QueueManager.getQueueManager().storeTaskEvent(this, e);
    }

    protected void resetRetryCounter() {
        mRetries = 0;
        setRetryDelay();
    }

    public enum TaskState {created, running, failed, successful, waiting}
}
