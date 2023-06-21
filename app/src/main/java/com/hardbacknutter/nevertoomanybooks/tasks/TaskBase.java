/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.os.Process;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.UncheckedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hardbacknutter.nevertoomanybooks.core.database.UncheckedDaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.UncheckedStorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;

/**
 * Common base for MutableLiveData / TaskListener driven tasks.
 *
 * @param <Result> the type of the result of the background computation.
 */
abstract class TaskBase<Result>
        implements ProgressListener {

    /** Identifies the task. Passed back in all messages. */
    private final int taskId;
    /** Identifies the task. */
    @NonNull
    private final String taskName;

    /** @see #cancel() */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    /** State of this task. */
    @NonNull
    private Status status = Status.Created;
    /** Use {@link #setExecutor(Executor)} to override. */
    @NonNull
    private Executor executor = ASyncExecutor.SERIAL;

    /** If progress is not indeterminate, the current position. */
    private int progressCurrentPos;
    /** If progress is not indeterminate, the maximum position. */
    private int progressMaxPos;
    /** Flag. */
    @Nullable
    private Boolean indeterminate;

    /**
     * Constructor.
     *
     * @param taskId   a unique task identifier, returned with each message
     * @param taskName a (preferably unique) name used for identification of this task
     */
    TaskBase(final int taskId,
             @NonNull final String taskName) {
        this.taskId = taskId;
        this.taskName = taskName;
    }

    @UiThread
    public void setExecutor(@NonNull final Executor executor) {
        this.executor = executor;
    }

    /**
     * Access for other classes.
     *
     * @return task ID
     */
    @AnyThread
    public int getTaskId() {
        return taskId;
    }

    @NonNull
    String getTaskName() {
        return taskName;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    /**
     * Execute the task.
     * Protected access to force implementations to have a "good method name" to start the task.
     *
     * @throws IllegalStateException if already/still running.
     */
    @UiThread
    protected void execute() {
        synchronized (this) {
            if (status == Status.Pending || status == Status.Running) {
                throw new IllegalStateException("task already running");
            }

            status = Status.Pending;
        }
        executor.execute(() -> {
            status = Status.Running;
            Thread.currentThread().setName(taskName);
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            //noinspection CheckStyle
            try {
                final Result result = doWork();
                if (isCancelled()) {
                    status = Status.Cancelled;
                    setTaskCancelled(result);
                } else {
                    status = Status.Finished;
                    setTaskFinished(result);
                }
            } catch (@NonNull final CancellationException e) {
                status = Status.Cancelled;
                setTaskCancelled(null);

            } catch (@NonNull final UncheckedStorageException
                                    | UncheckedDaoWriteException
                                    | UncheckedIOException e) {
                status = Status.Failed;
                //noinspection DataFlowIssue
                setTaskFailure(e.getCause());

            } catch (@NonNull final Exception e) {
                status = Status.Failed;
                setTaskFailure(e);
            }
        });
    }

    /**
     * The actual 'work' method.
     *
     * @return task result
     *
     * @throws CancellationException if the user cancelled us
     * @throws Exception             depending on implementation
     */
    @Nullable
    @WorkerThread
    protected abstract Result doWork()
            throws CancellationException, Exception;

    /**
     * Called when the task successfully finishes.
     */
    protected abstract void setTaskFinished(@Nullable Result result);

    /**
     * Called when the task was cancelled.
     */
    protected abstract void setTaskCancelled(@Nullable Result result);

    /**
     * Called when the task fails with an Exception.
     */
    protected abstract void setTaskFailure(@NonNull Throwable e);

    @Override
    @CallSuper
    @AnyThread
    public void cancel() {
        cancelRequested.set(true);
    }

    @Override
    @AnyThread
    public boolean isCancelled() {
        return cancelRequested.get();
    }

    /**
     * Check if the task has been queued or already running.
     *
     * @return {@code true} if the task is in an active state
     */
    @AnyThread
    public boolean isActive() {
        return status == Status.Running || status == Status.Pending;
    }

    /**
     * Send a progress update.
     * Convenience method which builds the {@link TaskProgress} based
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
        progressCurrentPos += delta;
        publishProgress(new TaskProgress(taskId, text,
                                         progressCurrentPos, progressMaxPos,
                                         indeterminate));
    }

    /**
     * Only takes effect when the next {@link TaskProgress} is send to the client.
     *
     * @param indeterminate true/false to enable/disable the indeterminate mode
     *                      or {@code null} to tell the receiver to revert back to its initial mode.
     */
    @AnyThread
    @Override
    public void setIndeterminate(@Nullable final Boolean indeterminate) {
        this.indeterminate = indeterminate;
    }

    @AnyThread
    @Override
    public int getMaxPos() {
        return progressMaxPos;
    }

    /**
     * Only takes effect when the next {@link TaskProgress} is send to the client.
     *
     * @param maxPosition value
     */
    @AnyThread
    @Override
    public void setMaxPos(final int maxPosition) {
        progressMaxPos = maxPosition;
    }

    /**
     * Indicates the current status of the task.
     * <p>
     * Created -> Pending -> Running -> Finished.
     * <p>
     * Finished -> Pending -> Running -> Finished.
     */
    public enum Status {
        /** initial status before the task has been queued. */
        Created,

        /** The task has been submitted, and is scheduled to start. */
        Pending,
        /** The task is actively doing work. */
        Running,

        /** The task has finished successfully. */
        Finished,
        /** The task failed (usually with an Exception). */
        Failed,
        /** The task was cancelled. It <strong>might</strong> have a (partial) result. */
        Cancelled
    }
}
