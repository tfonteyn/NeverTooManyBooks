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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrSendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

/**
 * A delegate class for handling a Goodreads enabled Book.
 */
public class GoodreadsHandler {

    /** The host view; used for context, resources, Snackbar. */
    private View mView;

    @Nullable
    private ProgressDialogFragment mProgressDialog;

    /** Goodreads authorization task. */
    private GrAuthTask mAuthTask;
    /** Goodreads send-book task. */
    private GrSendOneBookTask mSendOneBookTask;

    private String mRegistrationText;
    private String mSendBookText;

    private FragmentManager mFragmentManager;

    public void onViewCreated(@NonNull final FragmentActivity activity,
                              @NonNull final View view) {
        onViewCreated(view, activity, activity, activity.getSupportFragmentManager());
    }

    public void onViewCreated(@NonNull final Fragment fragment) {
        //noinspection ConstantConditions
        onViewCreated(fragment.getView(), fragment.getViewLifecycleOwner(),
                      fragment, fragment.getChildFragmentManager());
    }

    /**
     * Host (Fragment/Activity) independent initializer.
     *
     * @param view the hosting component root view
     */
    public void onViewCreated(@NonNull final View view,
                              @NonNull final LifecycleOwner lifecycleOwner,
                              @NonNull final ViewModelStoreOwner viewModelStoreOwner,
                              @NonNull final FragmentManager fm) {
        mView = view;
        mFragmentManager = fm;

        final Context context = view.getContext();
        mSendBookText = context.getString(R.string.gr_title_send_book);
        mRegistrationText = context.getString(R.string.lbl_registration,
                                              context.getString(R.string.site_goodreads));

        mAuthTask = new ViewModelProvider(viewModelStoreOwner).get(GrAuthTask.class);
        mAuthTask.onProgressUpdate().observe(lifecycleOwner, this::onProgress);
        mAuthTask.onCancelled().observe(lifecycleOwner, this::onCancelled);
        mAuthTask.onFailure().observe(lifecycleOwner, this::onFailure);
        mAuthTask.onFinished().observe(lifecycleOwner, this::onFinished);

        mSendOneBookTask = new ViewModelProvider(viewModelStoreOwner)
                .get(GrSendOneBookTask.class);
        mSendOneBookTask.onProgressUpdate().observe(lifecycleOwner, this::onProgress);
        mSendOneBookTask.onCancelled().observe(lifecycleOwner, this::onCancelled);
        mSendOneBookTask.onFailure().observe(lifecycleOwner, this::onFailure);
        mSendOneBookTask.onFinished().observe(lifecycleOwner, this::onFinished);
    }

    public void sendBook(@IntRange(from = 1) final long bookId) {
        Snackbar.make(mView, R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();
        mSendOneBookTask.start(bookId);
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog(message.taskId);
        }
        mProgressDialog.onProgress(message);
    }

    private void onCancelled(@NonNull final LiveDataEvent message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            Snackbar.make(mView, R.string.cancelled, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            Snackbar.make(mView, GrStatus.getMessage(mView.getContext(), message.result),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onFinished(@NonNull final FinishedMessage<GrStatus> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            if (message.result.getStatus() == GrStatus.FAILED_CREDENTIALS) {
                mAuthTask.prompt(mView.getContext());

            } else if (message.result.getStatus() != GrStatus.SUCCESS) {
                Snackbar.make(mView, message.result.getMessage(mView.getContext()),
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog(@IdRes final int taskId) {
        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                mFragmentManager.findFragmentByTag(ProgressDialogFragment.TAG);

        // not found? create it
        if (dialog == null) {
            if (taskId == R.id.TASK_ID_GR_REQUEST_AUTH) {
                dialog = ProgressDialogFragment.newInstance(mRegistrationText, false, true);

            } else if (taskId == R.id.TASK_ID_GR_SEND_ONE_BOOK) {
                dialog = ProgressDialogFragment.newInstance(mSendBookText, false, true);

            } else {
                throw new IllegalArgumentException("id=" + taskId);
            }
            dialog.show(mFragmentManager, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        if (taskId == R.id.TASK_ID_GR_REQUEST_AUTH) {
            dialog.setCanceller(mAuthTask);

        } else if (taskId == R.id.TASK_ID_GR_SEND_ONE_BOOK) {
            dialog.setCanceller(mSendOneBookTask);

        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
        return dialog;
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}
