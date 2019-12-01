/*
 * @Copyright 2019 HardBackNutter
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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

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
 * @param <Result> type of the task result object.
 */
public abstract class TaskListenerModel<Result>
        extends ViewModel {

    private final MutableLiveData<TaskListener.FinishMessage<Result>>
            mTaskFinishedMessage = new MutableLiveData<>();

    private final MutableLiveData<TaskListener.ProgressMessage>
            mTaskProgressMessage = new MutableLiveData<>();

    public MutableLiveData<TaskListener.ProgressMessage> getTaskProgressMessage() {
        return mTaskProgressMessage;
    }

    public MutableLiveData<TaskListener.FinishMessage<Result>> getTaskFinishedMessage() {
        return mTaskFinishedMessage;
    }

    private final TaskListener<Result> mTaskListener = new TaskListener<Result>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Result> message) {
            mTaskFinishedMessage.setValue(message);
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            mTaskProgressMessage.setValue(message);
        }
    };

    public TaskListener<Result> getTaskListener() {
        return mTaskListener;
    }


}
