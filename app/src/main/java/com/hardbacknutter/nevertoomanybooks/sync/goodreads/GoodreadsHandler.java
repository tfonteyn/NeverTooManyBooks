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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * A delegate class for handling a Goodreads enabled Book.
 */
public class GoodreadsHandler {

    /** Whether to show any Goodreads sync menus at all. */
    private static final String PK_ENABLED = GoodreadsManager.PREF_KEY + ".enabled";

    /** The hosting Activity. */
    private Activity mActivity;

    /** The host view; used for context, resources, Snackbar. */
    private View mView;

    @Nullable
    private ProgressDelegate mProgressDelegate;

    private GoodreadsHandlerViewModel mVm;

    /**
     * Check if SYNC menus should be shown at all. This does not affect searching.
     *
     * @param global Global preferences
     *
     * @return {@code true} if menus should be shown
     */
    public static boolean isSyncEnabled(@NonNull final SharedPreferences global) {
        return global.getBoolean(PK_ENABLED, false);
    }

    /**
     * Initializer for use from within an Activity.
     *
     * @param activity the hosting Activity
     * @param view     the root view of the Activity (e.g. mVb.getRoot())
     */
    public void onViewCreated(@NonNull final FragmentActivity activity,
                              @NonNull final View view) {
        onViewCreated(activity, view,
                      activity, activity);
    }

    /**
     * Initializer for use from within an Fragment.
     *
     * @param fragment the hosting Fragment
     */
    public void onViewCreated(@NonNull final Fragment fragment) {
        //noinspection ConstantConditions
        onViewCreated(fragment.getActivity(), fragment.getView(),
                      fragment, fragment.getViewLifecycleOwner());
    }

    /**
     * Host (Fragment/Activity) independent initializer.
     *
     * @param activity the hosting Activity
     * @param view     the hosting component root view
     */
    private void onViewCreated(@NonNull final Activity activity,
                               @NonNull final View view,
                               @NonNull final ViewModelStoreOwner viewModelStoreOwner,
                               @NonNull final LifecycleOwner lifecycleOwner) {
        mActivity = activity;
        mView = view;

        mVm = new ViewModelProvider(viewModelStoreOwner).get(GoodreadsHandlerViewModel.class);
        mVm.onFinished().observe(lifecycleOwner, this::onFinished);
        mVm.onCancelled().observe(lifecycleOwner, this::onCancelled);
        mVm.onFailure().observe(lifecycleOwner, this::onFailure);
        mVm.onProgress().observe(lifecycleOwner, this::onProgress);
    }

    public void prepareMenu(@NonNull final Menu menu,
                            @NonNull final Book book) {
        menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
            .setVisible(true);
    }

    public boolean onItemSelected(@IdRes final int menuItemId,
                                  @NonNull final Book book) {
        if (menuItemId == R.id.MENU_BOOK_SEND_TO_GOODREADS) {
            sendBook(book.getId());
            return true;
        }
        return false;
    }

    public boolean onItemSelected(@IdRes final int menuItemId,
                                  @NonNull final DataHolder rowData) {
        if (menuItemId == R.id.MENU_BOOK_SEND_TO_GOODREADS) {
            final long bookId = rowData.getLong(DBKey.FK_BOOK);
            sendBook(bookId);
            return true;
        }
        return false;
    }


    public void sendBook(@IntRange(from = 1) final long bookId) {
        Snackbar.make(mView, R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();
        mVm.sendBook(bookId);
    }

    private void onFinished(@NonNull final FinishedMessage<GrStatus> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            final GrStatus result = message.requireResult();

            if (result.getStatus() == GrStatus.CREDENTIALS_MISSING) {
                mVm.promptForAuthentication(mView.getContext());

            } else if (result.getStatus() != GrStatus.SUCCESS) {
                Snackbar.make(mView, result.getMessage(mView.getContext()),
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
            final Context context = mView.getContext();
            final Exception e = message.getResult();
            final String msg = ExMsg
                    .map(context, e)
                    .orElse(context.getString(R.string.error_network_site_access_failed,
                                              context.getString(R.string.site_goodreads)));
            Snackbar.make(mView, msg, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.isNewEvent()) {
            if (mProgressDelegate == null) {
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

                mProgressDelegate = new ProgressDelegate(
                        mActivity.findViewById(R.id.progress_frame))
                        .setTitle(dialogTitle)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> mVm.cancelTask(message.taskId))
                        .show(mActivity.getWindow());
            }
            mProgressDelegate.onProgress(message);
        }
    }

    private void closeProgressDialog() {
        if (mProgressDelegate != null) {
            mProgressDelegate.dismiss(mActivity.getWindow());
            mProgressDelegate = null;
        }
    }
}
