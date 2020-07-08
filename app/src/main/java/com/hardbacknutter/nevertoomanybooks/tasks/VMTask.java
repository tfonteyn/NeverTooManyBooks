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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
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
import com.hardbacknutter.nevertoomanybooks.viewmodels.SingleLiveEvent;

public abstract class VMTask<Result>
        extends ViewModel
        implements Canceller, ProgressListener {

    private static final String TAG = "VMTask";
    private final AtomicBoolean mIsCancelled = new AtomicBoolean();

    /** Using SingleLiveEvent to prevent multiple delivery after for example a device rotation. */
    private final MutableLiveData<FinishedMessage<Result>> mFinished = new SingleLiveEvent<>();
    /** Using SingleLiveEvent to prevent multiple delivery after for example a device rotation. */
    private final MutableLiveData<FinishedMessage<Result>> mCancelled = new SingleLiveEvent<>();

    /** Using SingleLiveEvent to prevent multiple delivery after for example a device rotation. */
    private final MutableLiveData<FinishedMessage<Exception>> mFailure = new SingleLiveEvent<>();

    /** Using MutableLiveData as we actually want re-delivery after a device rotation. */
    private final MutableLiveData<ProgressMessage> mProgress = new MutableLiveData<>();

    /** id set at construction time, passed back in all messages. */
    private int mTaskId;
    private int mProgressCurrentPos;
    private int mProgressMaxPos;
    @Nullable
    private Boolean mIndeterminate;

    private Executor mExecutor;

    @NonNull
    public MutableLiveData<FinishedMessage<Result>> onFinished() {
        return mFinished;
    }

    @NonNull
    public MutableLiveData<FinishedMessage<Result>> onCancelled() {
        return mCancelled;
    }

    @NonNull
    public MutableLiveData<FinishedMessage<Exception>> onFailure() {
        return mFailure;
    }

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

    @MainThread
    public void execute(final int taskId) {
        if (mTaskId != 0) {
            throw new IllegalStateException("task already running");
        }

        mTaskId = taskId;

        if (mExecutor == null) {
            mExecutor = ASyncExecutor.SERIAL;
        }
        mExecutor.execute(() -> {
            try {
                final Result result = doWork();
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
    }

    @WorkerThread
    @Nullable
    protected abstract Result doWork()
            throws Exception;

    @WorkerThread
    public final void onProgress(final int position,
                                 @Nullable final String text) {
        mProgressCurrentPos = position;
        mProgress.postValue(new ProgressMessage(mTaskId, text,
                                                mProgressCurrentPos, mProgressMaxPos,
                                                mIndeterminate));
    }

    @WorkerThread
    public final void onProgressStep(final int step,
                                     @Nullable final String text) {
        mProgressCurrentPos += step;
        mProgress.postValue(new ProgressMessage(mTaskId, text,
                                                mProgressCurrentPos, mProgressMaxPos,
                                                mIndeterminate));
    }

    @AnyThread
    @Override
    public void setProgressIsIndeterminate(@Nullable final Boolean indeterminate) {
        mIndeterminate = indeterminate;
    }

    @AnyThread
    @Override
    public int getProgressMaxPos() {
        return mProgressMaxPos;
    }

    @AnyThread
    @Override
    public void setProgressMaxPos(final int progressMaxPos) {
        mProgressMaxPos = progressMaxPos;
    }

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

}
