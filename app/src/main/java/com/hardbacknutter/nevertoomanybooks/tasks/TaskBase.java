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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.content.Context;
import android.os.Process;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

abstract class TaskBase<Result>
        implements Canceller, ProgressListener {

    /** Identifies the task. Passed back in all messages. */
    private final int mTaskId;
    /** Identifies the task. */
    @NonNull
    private final String mTaskName;

    /**
     * Set by a client or from within the task.
     * It's a <strong>request</strong> to cancel while running.
     */
    private final AtomicBoolean mIsCancelled = new AtomicBoolean();

    /** State of this task. */
    @NonNull
    private Status mStatus = Status.Idle;
    /** Use {@link #setExecutor(Executor)} to override. */
    @NonNull
    private Executor mExecutor = ASyncExecutor.SERIAL;

    /** If progress is not indeterminate, the current position. */
    private int mProgressCurrentPos;
    /** If progress is not indeterminate, the maximum position. */
    private int mProgressMaxPos;
    /** Flag. */
    @Nullable
    private Boolean mIndeterminate;

    /**
     * Constructor.
     *
     * @param taskId   a unique task identifier, returned with each message
     * @param taskName a (preferably unique) name used for identification of this task
     */
    TaskBase(final int taskId,
             @NonNull final String taskName) {
        mTaskId = taskId;
        mTaskName = taskName;
    }

    @UiThread
    public void setExecutor(@NonNull final Executor executor) {
        mExecutor = executor;
    }

    /**
     * Access for other classes.
     *
     * @return task ID
     */
    @AnyThread
    public int getTaskId() {
        return mTaskId;
    }

    @NonNull
    String getTaskName() {
        return mTaskName;
    }

    /**
     * Execute the task.
     * Protected access to force implementations to have a "good method name" to start the task.
     *
     * @return {@code true} if the task was started.
     *
     * @throws IllegalStateException if already/still running.
     */
    @UiThread
    protected boolean execute() {
        synchronized (this) {
            if (mStatus != Status.Idle && mStatus != Status.Finished) {
                throw new IllegalStateException("task already running");
            }

            mStatus = Status.Pending;
        }
        mExecutor.execute(() -> {
            mStatus = Status.Running;
            Thread.currentThread().setName(mTaskName);
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            final Context context = AppLocale.getInstance().apply(ServiceLocator.getAppContext());
            try {
                final Result result = doWork(context);
                final FinishedMessage<Result> message = new FinishedMessage<>(getTaskId(), result);
                if (isCancelled()) {
                    onCancelled(message);
                } else {
                    onFinished(message);
                }
            } catch (@NonNull final Exception e) {
                Logger.error(context, mTaskName, e);
                onFailure(e);
            }

            mStatus = Status.Finished;
        });

        return true;
    }

    /**
     * The actual 'work' method.
     *
     * @param context a localized application context
     *
     * @return task result
     *
     * @throws Exception depending on implementation
     */
    @Nullable
    @WorkerThread
    protected abstract Result doWork(@NonNull Context context)
            throws Exception;

    protected abstract void onFinished(@NonNull FinishedMessage<Result> message);

    protected abstract void onCancelled(@NonNull FinishedMessage<Result> message);

    protected abstract void onFailure(@NonNull Exception e);

    @Override
    @AnyThread
    public boolean cancel(final boolean mayInterruptIfRunning) {
        mIsCancelled.set(true);
        return true;
    }

    @Override
    @AnyThread
    public boolean isCancelled() {
        return mIsCancelled.get();
    }

    public boolean isRunning() {
        return mStatus == Status.Running;
    }

    /**
     * Send a progress update.
     * Convenience method which builds the {@link ProgressMessage} based
     * on the current progress counters and the passed data.
     * <p>
     * Can be called from inside {@link #doWork}.
     *
     * @param delta the relative step in the overall progress count.
     * @param text  (optional) text message
     */
    @WorkerThread
    @Override
    public void publishProgress(final int delta,
                                @Nullable final String text) {
        mProgressCurrentPos += delta;
        publishProgress(new ProgressMessage(mTaskId, text,
                                            mProgressCurrentPos, mProgressMaxPos,
                                            mIndeterminate));
    }

    /**
     * Only takes effect when the next ProgressMessage is send to the client.
     *
     * @param indeterminate true/false to enable/disable the indeterminate mode
     *                      or {@code null} to tell the receiver to revert back to its initial mode.
     */
    @AnyThread
    @Override
    public void setIndeterminate(@Nullable final Boolean indeterminate) {
        mIndeterminate = indeterminate;
    }

    @AnyThread
    @Override
    public int getMaxPos() {
        return mProgressMaxPos;
    }

    /**
     * Only takes effect when the next ProgressMessage is send to the client.
     *
     * @param maxPosition value
     */
    @AnyThread
    @Override
    public void setMaxPos(final int maxPosition) {
        mProgressMaxPos = maxPosition;
    }

    /**
     * Indicates the current status of the task.
     * Idle -> Pending -> Running -> Finished.
     * <p>
     * A task is {@link Status#Idle} when it has never been queued.
     */
    public enum Status {
        Idle, Pending, Running, Finished
    }
}
