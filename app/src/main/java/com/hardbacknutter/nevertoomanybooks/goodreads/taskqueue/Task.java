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
import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 * ENHANCE: child classes mixing UI with background tasks. Postponing to WorkManager implementation.
 * <p>
 * Abstract base class for all Tasks.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
public abstract class Task<
        BICursor extends BindableItemCursor,
        BIViewHolder extends BindableItemViewHolder>
        implements BindableItem<BICursor, BIViewHolder>,
                   Serializable {

    static final int CAT_LEGACY = 0;

    static final String COMPLETED = "S";
    static final String FAILED = "F";
    static final String QUEUED = "Q";

    private static final int RETRY_LIMIT = 15;
    private static final long serialVersionUID = 8778331354471500293L;

    @NonNull
    private final String mDescription;
    private long mId;
    @Nullable
    private Exception mLastException;

    private int mRetries;
    private int mRetryDelay;
    private boolean mIsAborting;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    Task(@NonNull final String description) {
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

    /**
     * Return an application-defined category for the task.
     * <p>
     * The category can be used to lookup queued tasks based on category, for example to
     * allow an application to ensure only one job of a particular category is queued, or
     * to retrieve all jobs of a particular category.
     */
    public abstract int getCategory();

    @Nullable
    Exception getLastException() {
        return mLastException;
    }

    protected void setLastException(@Nullable final Exception e) {
        mLastException = e;
    }

    /**
     * There is little that can be done to abort a task; we trust the implementations to
     * check this flag periodically on long tasks.
     */
    protected boolean isAborting() {
        return mIsAborting;
    }

    void abortTask() {
        mIsAborting = true;
    }

    int getRetryLimit() {
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

    int getRetries() {
        return mRetries;
    }

    void setRetries(final int retries) {
        mRetries = retries;
    }

    boolean canRetry() {
        return mRetries < RETRY_LIMIT;
    }

    protected void storeEvent(@NonNull final Event e) {
        QueueManager.getQueueManager().storeTaskEvent(mId, e);
    }

    protected void resetRetryCounter() {
        mRetries = 0;
        setRetryDelay();
    }
}
