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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueListActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GoodreadsTasks;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.ImportTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendBooksTask;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskFinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.AdminModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.GoodreadsTaskModel;

public class GoodreadsAdminFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "GoodreadsAdminFragment";

    private ProgressDialogFragment mProgressDialog;

    /** ViewModel. */
    private AdminModel mModel;

    /** ViewModel for task control. */
    private GoodreadsTaskModel mGoodreadsTaskModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_goodreads_admin, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Activity scope
        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(AdminModel.class);

        mGoodreadsTaskModel = new ViewModelProvider(this).get(GoodreadsTaskModel.class);
        mGoodreadsTaskModel.getTaskProgressMessage()
                           .observe(getViewLifecycleOwner(), this::onTaskProgressMessage);
        mGoodreadsTaskModel.getTaskFinishedMessage()
                           .observe(getViewLifecycleOwner(), this::onTaskFinished);

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTask(mModel.getTask());
        }

        View root = getView();

        //noinspection ConstantConditions
        root.findViewById(R.id.lbl_sync_with_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                TaskBase<Integer> task = new ImportTask(getContext(), true,
                                                        mGoodreadsTaskModel.getTaskListener());
                mModel.setTask(task);
                mProgressDialog.setTask(task);
                task.execute();
            });

        root.findViewById(R.id.lbl_import_all_from_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                TaskBase<Integer> task = new ImportTask(getContext(), false,
                                                        mGoodreadsTaskModel.getTaskListener());
                mModel.setTask(task);
                mProgressDialog.setTask(task);
                task.execute();
            });

        root.findViewById(R.id.lbl_send_updated_books_to_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                TaskBase<Integer> task = new SendBooksTask(getContext(), true,
                                                           mGoodreadsTaskModel.getTaskListener());
                mModel.setTask(task);
                mProgressDialog.setTask(task);
                task.execute();
            });

        root.findViewById(R.id.lbl_send_all_books_to_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                TaskBase<Integer> task = new SendBooksTask(getContext(), false,
                                                           mGoodreadsTaskModel.getTaskListener());
                mModel.setTask(task);
                mProgressDialog.setTask(task);
                task.execute();
            });

        // Start the activity that shows the active GoodReads tasks
        root.findViewById(R.id.lbl_background_tasks)
            .setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), TaskQueueListActivity.class);
                startActivity(intent);
            });
    }

    private void onTaskProgressMessage(@NonNull final TaskProgressMessage message) {
        if (mProgressDialog != null) {
            mProgressDialog.onProgress(message);
        }
    }

    private void onTaskFinished(@NonNull final TaskFinishedMessage<Integer> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        //noinspection ConstantConditions
        @NonNull
        View view = getView();

        switch (message.taskId) {
            case R.id.TASK_ID_GR_IMPORT:
            case R.id.TASK_ID_GR_SEND_BOOKS:
            case R.id.TASK_ID_GR_REQUEST_AUTH: {
                //noinspection ConstantConditions
                String msg = GoodreadsTasks.handleResult(getContext(), message);
                if (msg != null) {
                    UserMessage.show(view, msg);
                } else {
                    RequestAuthTask.needsRegistration(getContext(), mGoodreadsTaskModel
                            .getTaskListener());
                }
                break;
            }
            default: {
                //noinspection ConstantConditions
                Logger.warnWithStackTrace(getContext(), this, "taskId=" + message.taskId);
                break;
            }
        }
    }
}
