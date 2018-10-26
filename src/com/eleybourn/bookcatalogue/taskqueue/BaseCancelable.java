/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.taskqueue;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * An abstract class for the interface {@link Cancelable}. Subclass can simply override
 * the <code>execute()</code> function to provide an implementation of <code>Cancelable</code>.
 */
public abstract class BaseCancelable<T> implements Cancelable<T> {

    /**
     * The state of the task, possible transitions are:
     *
     * <pre>
     *     INITIAL -> CANCELED
     *     EXECUTING -> COMPLETE, CANCELING, ERROR, CANCELED
     *     CANCELING -> CANCELED
     * </pre>
     *
     * When the task stop, it must be end with one of the following states:
     * COMPLETE, CANCELED, or ERROR;
     */
    private static final int STATE_INITIAL = 1;
    private static final int STATE_EXECUTING = (1 << 1);
    private static final int STATE_CANCELING = (1 << 2);
    private static final int STATE_CANCELED = (1 << 3);
    private static final int STATE_ERROR = (1 << 4);
    private static final int STATE_COMPLETE = (1 << 5);

    private int mState = STATE_INITIAL;

    private Throwable mError;
    private T mResult;
    @Nullable
    private Cancelable<?> mCurrentTask;
    private Thread mThread;

    @NonNull
    protected abstract T execute();

    protected synchronized void interruptNow() {
        if (isInStates(STATE_CANCELING | STATE_EXECUTING)) {
            mThread.interrupt();
        }
    }

    /**
     * Frees the result (which is not null) when the task has been canceled.
     */
    @SuppressWarnings("EmptyMethod")
    protected void freeCanceledResult(T result) {
        // Do nothing by default;
    }

    private boolean isInStates(final int states) {
        return (states & mState) != 0;
    }

    private T handleTerminalStates() throws ExecutionException {
        switch (mState) {
            case STATE_CANCELED:
                throw new CancellationException();
            case STATE_ERROR:
                throw new ExecutionException(mError);
            case STATE_COMPLETE:
                return mResult;
            default:
                throw new RTE.IllegalTypeException("" + mState);
        }
    }

    public synchronized void await() throws InterruptedException {
        while (!isInStates(STATE_COMPLETE | STATE_CANCELED | STATE_ERROR)) {
            wait();
        }
    }

    public final T get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            if (mState != STATE_INITIAL) {
                await();
                return handleTerminalStates();
            }
            mThread = Thread.currentThread();
            mState = STATE_EXECUTING;
        }
        try {
            mResult = execute();
        } catch (CancellationException e) {
            mState = STATE_CANCELED;
        } catch (Throwable error) {
            synchronized (this) {
                if (mState != STATE_CANCELING) {
                    mError = error;
                    mState = STATE_ERROR;
                }
            }
        }
        synchronized (this) {
            if (mState == STATE_CANCELING) {
                mState = STATE_CANCELED;
            }
            if (mState == STATE_EXECUTING) {
                mState = STATE_COMPLETE;
            }
            notifyAll();
            if (mState == STATE_CANCELED && mResult != null) {
                freeCanceledResult(mResult);
            }
            return handleTerminalStates();
        }
    }

    /**
     * Requests the task to be canceled.
     *
     * @return <tt>true</tt>if the task is running and has not been canceled; false
     * otherwise
     */

    public synchronized boolean requestCancel() {
        if (mState == STATE_INITIAL) {
            mState = STATE_CANCELED;
            notifyAll();
            return false;
        }
        if (mState == STATE_EXECUTING) {
            if (mCurrentTask != null) {
                mCurrentTask.requestCancel();
            }
            mState = STATE_CANCELING;
            return true;
        }
        return false;
    }

    /**
     * Whether the task's has been requested for onCancel.
     */
    protected synchronized boolean isCanceling() {
        return mState == STATE_CANCELING;
    }

    /**
     * Runs a <code>Cancelable</code> sub task. This method is helpful, if the
     * task can be composed of several cancelable tasks. By using this function,
     * it will pass <code>requestCancel</code> message to those sub-tasks.
     *
     * @param <X>        the return type of the sub task
     * @param cancelable the sub task
     *
     * @return the result of the sub task
     */
    protected <X> X runSubTask(@NonNull final Cancelable<X> cancelable)
            throws InterruptedException, ExecutionException {
        synchronized (this) {
            if (mCurrentTask != null) {
                throw new IllegalStateException("cannot run two sub-tasks at the same time");
            }
            if (mState == STATE_CANCELING) {
                throw new CancellationException();
            }
            mCurrentTask = cancelable;
        }
        try {
            return cancelable.get();
        } finally {
            synchronized (this) {
                mCurrentTask = null;
            }
        }
    }

}