package com.eleybourn.bookcatalogue.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.eleybourn.bookcatalogue.tasks.TaskBase;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.tasks.TaskListener.TaskFinishedMessage;
import com.eleybourn.bookcatalogue.tasks.TaskListener.TaskProgressMessage;

/**
 * ViewModel to keep hold of a task and a passthrough task listener.
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
public class TaskModel<Result>
        extends ViewModel {

    private final MutableLiveData<Integer> mTaskCancelledMessage = new MutableLiveData<>();
    private final MutableLiveData<TaskProgressMessage> mTaskProgressMessage = new MutableLiveData<>();
    private final MutableLiveData<TaskFinishedMessage<Result>> mTaskFinishedMessage = new MutableLiveData<>();
    private final TaskListener<Result> mTaskListener = new TaskListener<Result>() {
        @Override
        public void onTaskCancelled(@Nullable final Integer taskId,
                                    @Nullable final Result result) {
            mTaskCancelledMessage.setValue(taskId);
        }

        @Override
        public void onTaskProgress(@NonNull final TaskProgressMessage message) {
            mTaskProgressMessage.setValue(message);
        }

        @Override
        public void onTaskFinished(@NonNull final TaskFinishedMessage<Result> message) {
            mTaskFinishedMessage.setValue(message);
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
