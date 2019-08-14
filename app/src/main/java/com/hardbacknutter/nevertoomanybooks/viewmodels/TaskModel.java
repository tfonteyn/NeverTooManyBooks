/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskFinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskProgressMessage;

/**
 * ViewModel to keep hold of a task and a pass-through task listener.
 * <p>
 * A ViewModel can not actually have a parameter type. So, we use concrete classes, e.g.
 * <pre>
 *  {@code
 *      public class IntegerTaskModel
 *          extends TaskModel<Integer> {
 *      }
 *  }
 * </pre>
 *
 * @param <Result> type of the task result object.
 */
public abstract class TaskModel<Result>
        extends ViewModel {

    private final MutableLiveData<Integer> mTaskCancelledMessage = new MutableLiveData<>();
    private final MutableLiveData<TaskProgressMessage>
            mTaskProgressMessage = new MutableLiveData<>();
    private final MutableLiveData<TaskFinishedMessage<Result>>
            mTaskFinishedMessage = new MutableLiveData<>();
    private final TaskListener<Result> mTaskListener = new TaskListener<Result>() {
        @Override
        public void onTaskFinished(@NonNull final TaskFinishedMessage<Result> message) {
            mTaskFinishedMessage.setValue(message);
        }

        @Override
        public void onTaskCancelled(@Nullable final Integer taskId,
                                    @Nullable final Result result) {
            mTaskCancelledMessage.setValue(taskId);
        }

        @Override
        public void onTaskProgress(@NonNull final TaskProgressMessage message) {
            mTaskProgressMessage.setValue(message);
        }
    };
    @Nullable
    private TaskBase mTask;

    @Nullable
    public TaskBase getTask() {
        return mTask;
    }

    public void setTask(@Nullable final TaskBase task) {
        mTask = task;
    }

    public TaskListener<Result> getTaskListener() {
        return mTaskListener;
    }

    public MutableLiveData<TaskProgressMessage> getTaskProgressMessage() {
        return mTaskProgressMessage;
    }

    public MutableLiveData<TaskFinishedMessage<Result>> getTaskFinishedMessage() {
        return mTaskFinishedMessage;
    }

    public MutableLiveData<Integer> getTaskCancelledMessage() {
        return mTaskCancelledMessage;
    }
}
