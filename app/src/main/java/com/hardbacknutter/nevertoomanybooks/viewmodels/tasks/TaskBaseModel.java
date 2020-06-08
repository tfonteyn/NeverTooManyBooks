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
package com.hardbacknutter.nevertoomanybooks.viewmodels.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.viewmodels.SingleLiveEvent;

/**
 * ViewModel to keep hold of a pass-through task listener.
 * <p>
 * A ViewModel can not actually have a parameter type. So, we use concrete classes, e.g.
 * <pre>
 *  {@code
 *      public class SomeTypeTaskModel
 *          extends TaskListenerModel<SomeType> {
 *      }
 *  }
 * </pre>
 *
 * <strong>Important</strong> when a {@link TaskListener.FinishMessage} is received,
 * we set the task member variable to {@code null} !
 *
 * @param <Result> type of the task result object.
 */
public abstract class TaskBaseModel<Result>
        extends ViewModel {

    /** Using SingleLiveEvent to prevent multiple delivery after for example a device rotation. */
    private final MutableLiveData<TaskListener.FinishMessage<Result>>
            mTaskFinishedMessage = new SingleLiveEvent<>();
    /** Using MutableLiveData as we actually want re-delivery after a device rotation. */
    private final MutableLiveData<TaskListener.ProgressMessage>
            mTaskProgressMessage = new MutableLiveData<>();

    /** Passthrough listener sending the incoming message to the MutableLiveData. */
    private final TaskListener<Result> mTaskListener = new TaskListener<Result>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Result> message) {
            mTask = null;
            mTaskFinishedMessage.setValue(message);
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            mTaskProgressMessage.setValue(message);
        }
    };
    @Nullable
    private TaskBase<Result> mTask;

    @Nullable
    public TaskBase<Result> getTask() {
        return mTask;
    }

    public void execute(@NonNull final TaskBase<Result> task) {
        mTask = task;
        mTask.execute();
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<TaskListener.ProgressMessage> onTaskProgress() {
        return mTaskProgressMessage;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<TaskListener.FinishMessage<Result>> onTaskFinished() {
        return mTaskFinishedMessage;
    }

    @NonNull
    public TaskListener<Result> getTaskListener() {
        return mTaskListener;
    }
}
