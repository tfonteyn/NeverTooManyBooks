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

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * The base for a task which uses {@link MutableLiveData} for the results.
 *
 * <strong>Limited to executing a SINGLE task at a time.</strong>
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class VMTask<Result>
        extends ViewModel
        implements Canceller, ProgressListener {

    /** Log tag. */
    private static final String TAG = "VMTask";
    private final MutableLiveData<FinishedMessage<Result>> mFinished = new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Result>> mCancelled = new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Exception>> mFailure = new MutableLiveData<>();
    private final MutableLiveData<ProgressMessage> mProgress = new MutableLiveData<>();
    private final AtomicBoolean mIsCancelled = new AtomicBoolean();
    /** id set at construction time, passed back in all messages. */
    @IdRes
    private int mTaskId;
    private int mProgressCurrentPos;
    private int mProgressMaxPos;
    @Nullable
    private Boolean mIndeterminate;

    @Nullable
    private Executor mExecutor;

    /**
     * Called when the task successfully finishes.
     *
     * @return the {@link Result} which can be considered to be complete and correct.
     */
    @NonNull
    public MutableLiveData<FinishedMessage<Result>> onFinished() {
        return mFinished;
    }

    /**
     * Called when the task was cancelled.
     *
     * @return the {@link Result}. It will depend on the implementation how
     * complete/correct (if at all) this result is.
     */
    @NonNull
    public MutableLiveData<FinishedMessage<Result>> onCancelled() {
        return mCancelled;
    }

    /**
     * Called when the task fails with an Exception.
     *
     * @return the result is the Exception
     */
    @NonNull
    public MutableLiveData<FinishedMessage<Exception>> onFailure() {
        return mFailure;
    }

    /**
     * Forwards progress messages for the client to display.
     *
     * @return a {@link ProgressMessage} with the progress counter, a text message, ...
     */
    @NonNull
    public MutableLiveData<ProgressMessage> onProgressUpdate() {
        return mProgress;
    }


    @Override
    @CallSuper
    protected void onCleared() {
        cancel(true);
    }


    @SuppressWarnings("unused")
    @MainThread
    public void setExecutor(@Nullable final Executor executor) {
        mExecutor = executor;
    }

    /**
     * Execute the task.
     *
     * @param taskId identifier, must be unique
     *
     * @return {@code true} if the task was started;
     * {@code false} if a task with the same taskId is already running.
     *
     * @throws IllegalStateException if an attempt is made to start a new task (with a new id)
     *                               while a previous task is still running.
     */
    @MainThread
    public boolean execute(@IdRes final int taskId) {
        synchronized (this) {
            if (mTaskId == taskId) {
                // Duplicate request to start the same task..
                // Probably due to a fragment/activity restart.
                // Reject re-execution gracefully.
                // The client should not call execute without checking isRunning() first really.
                return false;
            }

            if (mTaskId != 0) {
                // Can't start a new task while the previous one is still running. BUG.
                throw new IllegalStateException("task already running");
            }

            mTaskId = taskId;
        }

        if (mExecutor == null) {
            mExecutor = ASyncExecutor.SERIAL;
        }
        mExecutor.execute(() -> {
            try {
                final Context context = AppLocale.getInstance().apply(App.getTaskContext());
                final Result result = doWork(context);

                final FinishedMessage<Result> message = new FinishedMessage<>(mTaskId, result);
                if (mIsCancelled.get()) {
                    mCancelled.postValue(message);
                } else {
                    mFinished.postValue(message);
                }
            } catch (@NonNull final Exception e) {
                Logger.error(App.getAppContext(), TAG, e);
                mFailure.postValue(new FinishedMessage<>(mTaskId, e));
            }
            mTaskId = 0;
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
    protected abstract Result doWork(@NonNull final Context context)
            throws Exception;

    public boolean isRunning() {
        return mTaskId != 0;
    }

    /**
     * Allows an external/client to <strong>request</strong> cancellation.
     *
     * @param mayInterruptIfRunning IGNORED (here for compatibility with an ASyncTask)
     *
     * @return {@code true}
     */
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

    /**
     * Can be called from the Callable.
     * Forwards the progress info to the MutableLiveData.
     *
     * @param position the absolute position of the overall progress count.
     * @param text     (optional) text message
     */
    @WorkerThread
    @Override
    public final void publishPosition(final int position,
                                      @Nullable final String text) {
        mProgressCurrentPos = position;
        mProgress.postValue(new ProgressMessage(mTaskId, text,
                                                mProgressCurrentPos, mProgressMaxPos,
                                                mIndeterminate));
    }

    /**
     * Can be called from the Callable.
     * Forwards the progress info to the MutableLiveData.
     *
     * @param delta the relative step in the overall progress count.
     * @param text  (optional) text message
     */
    @WorkerThread
    @Override
    public final void publishProgressStep(final int delta,
                                          @Nullable final String text) {
        mProgressCurrentPos += delta;
        mProgress.postValue(new ProgressMessage(mTaskId, text,
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

}
