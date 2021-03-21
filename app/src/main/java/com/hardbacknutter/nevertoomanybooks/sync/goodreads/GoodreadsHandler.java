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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import android.view.View;

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
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * A delegate class for handling a Goodreads enabled Book.
 */
public class GoodreadsHandler {

    /** The host view; used for context, resources, Snackbar. */
    private View mView;

    @Nullable
    private ProgressDialogFragment mProgressDialog;
    private FragmentManager mFragmentManager;
    private GoodreadsHandlerViewModel mVm;

    /**
     * Initializer for use from within an Activity.
     *
     * @param activity the hosting Activity
     * @param view     the root view of the Activity (e.g. mVb.getRoot())
     */
    public void onViewCreated(@NonNull final FragmentActivity activity,
                              @NonNull final View view) {
        onViewCreated(view, activity, activity, activity.getSupportFragmentManager());
    }

    /**
     * Initializer for use from within an Fragment.
     *
     * @param fragment the hosting Fragment
     */
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
    private void onViewCreated(@NonNull final View view,
                               @NonNull final LifecycleOwner lifecycleOwner,
                               @NonNull final ViewModelStoreOwner viewModelStoreOwner,
                               @NonNull final FragmentManager fm) {
        mView = view;
        mFragmentManager = fm;

        mVm = new ViewModelProvider(viewModelStoreOwner).get(GoodreadsHandlerViewModel.class);
        mVm.onFinished().observe(lifecycleOwner, this::onFinished);
        mVm.onCancelled().observe(lifecycleOwner, this::onCancelled);
        mVm.onFailure().observe(lifecycleOwner, this::onFailure);
        mVm.onProgress().observe(lifecycleOwner, this::onProgress);
    }

    public void sendBook(@IntRange(from = 1) final long bookId) {
        Snackbar.make(mView, R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();
        mVm.sendBook(bookId);
    }

    private void onFinished(@NonNull final FinishedMessage<GrStatus> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            if (message.result.getStatus() == GrStatus.FAILED_CREDENTIALS) {
                mVm.promptForAuthentication(mView.getContext());

            } else if (message.result.getStatus() != GrStatus.SUCCESS) {
                Snackbar.make(mView, message.result.getMessage(mView.getContext()),
                              Snackbar.LENGTH_LONG).show();
            }
        }
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

    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            // get dialog after a fragment restart
            mProgressDialog = (ProgressDialogFragment)
                    mFragmentManager.findFragmentByTag(ProgressDialogFragment.TAG);
            // not found? create it
            if (mProgressDialog == null) {
                final String dialogTitle;

                if (message.taskId == R.id.TASK_ID_GR_REQUEST_AUTH) {
                    dialogTitle = mView.getContext().getString(
                            R.string.lbl_registration,
                            mView.getContext().getString(R.string.site_goodreads));

                } else if (message.taskId == R.id.TASK_ID_GR_SEND_ONE_BOOK) {
                    dialogTitle = mView.getContext().getString(R.string.gr_title_send_book);

                } else {
                    throw new IllegalArgumentException("id=" + message.taskId);
                }

                mProgressDialog = ProgressDialogFragment.newInstance(dialogTitle, false, true);
                mProgressDialog.show(mFragmentManager, ProgressDialogFragment.TAG);
            }

            // hook the task up.
            mVm.connectProgressDialog(message.taskId, mProgressDialog);
        }

        mProgressDialog.onProgress(message);
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}
