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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentGoodreadsAdminBinding;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.ImportTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendBooksTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.GoodreadsTaskModel;

/**
 * Disabled the progress dialog as it's not actually needed for now.
 * But leaving the code for future use.
 */
public class GoodreadsAdminFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "GoodreadsAdminFragment";

//    @Nullable
//    private ProgressDialogFragment mProgressDialog;

    /** ViewModel for task control. */
    private GoodreadsTaskModel mGoodreadsTaskModel;

    private FragmentGoodreadsAdminBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mGoodreadsTaskModel = new ViewModelProvider(this).get(GoodreadsTaskModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentGoodreadsAdminBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

//        mGoodreadsTaskModel.onTaskProgress().observe(getViewLifecycleOwner(), message -> {
//            if (mProgressDialog == null) {
//                mProgressDialog = getOrCreateProgressDialog();
//            }
//            mProgressDialog.onProgress(message);
//        });
        mGoodreadsTaskModel.onTaskFinished().observe(getViewLifecycleOwner(), message -> {
//            if (mProgressDialog != null) {
//                mProgressDialog.dismiss();
//            }

            if (GoodreadsHandler.authNeeded(message)) {
                //noinspection ConstantConditions
                RequestAuthTask.prompt(getContext(), mGoodreadsTaskModel.getTaskListener());
            } else {
                //noinspection ConstantConditions
                Snackbar.make(mVb.getRoot(), GoodreadsHandler.digest(getContext(), message),
                              Snackbar.LENGTH_LONG).show();
            }
        });

        mVb.btnSync.setOnClickListener(v -> onImport(true));
        mVb.btnImport.setOnClickListener(v1 -> onImport(false));
        mVb.btnSendUpdatedBooks.setOnClickListener(v -> onSend(true));
        mVb.btnSendAllBooks.setOnClickListener(v -> onSend(false));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_GOODREADS_TASKS, 0, R.string.gr_tq_menu_background_tasks)
            .setIcon(R.drawable.ic_format_list_bulleted)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_GOODREADS_TASKS: {
                // Start the activity that shows the active GoodReads tasks
                final Intent intent = new Intent(getContext(), TasksAdminActivity.class);
                startActivity(intent);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onImport(final boolean isSync) {
        Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();

        final TaskBase<Integer> task =
                new ImportTask(isSync, mGoodreadsTaskModel.getTaskListener());

//        mProgressDialog = ProgressDialogFragment
//                .newInstance(R.string.gr_title_sync_with_goodreads, false, false, 0);
//        mProgressDialog.show(getChildFragmentManager(), ProgressDialogFragment.TAG);

        mGoodreadsTaskModel.setTask(task);
//        mProgressDialog.setCanceller(task);
        task.execute();
    }

    private void onSend(final boolean updatesOnly) {
        Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();

        final TaskBase<Integer> task =
                new SendBooksTask(updatesOnly, mGoodreadsTaskModel.getTaskListener());
        mGoodreadsTaskModel.setTask(task);
        task.execute();
    }

//    @SuppressWarnings("unused")
//    @NonNull
//    private ProgressDialogFragment getOrCreateProgressDialog() {
//        final FragmentManager fm = getChildFragmentManager();
//
//        // get dialog after a fragment restart
//        ProgressDialogFragment dialog = (ProgressDialogFragment)
//                fm.findFragmentByTag(ProgressDialogFragment.TAG);
//        // not found? create it
//        if (dialog == null) {
//            dialog = ProgressDialogFragment
//                      .newInstance(R.string.gr_title_send_book, false, false, 0);
//            dialog.show(fm, ProgressDialogFragment.TAG);
//        }
//
//        // hook the task up.
//        dialog.setCanceller(mGoodreadsTaskModel.getTask());
//        return dialog;
//    }
}
