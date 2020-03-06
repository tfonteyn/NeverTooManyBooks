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
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.R;
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

//    private ProgressDialogFragment mProgressDialog;

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

        setHasOptionsMenu(true);

        mGoodreadsTaskModel = new ViewModelProvider(this).get(GoodreadsTaskModel.class);
        mGoodreadsTaskModel.onTaskProgress().observe(getViewLifecycleOwner(), message -> {
//            if (mProgressDialog != null) {
//                mProgressDialog.onProgress(message);
//            }
        });
        mGoodreadsTaskModel.onTaskFinished().observe(getViewLifecycleOwner(), message -> {
//            if (mProgressDialog != null) {
//                mProgressDialog.dismiss();
//            }

            //noinspection ConstantConditions
            String msg = GoodreadsHandler.handleResult(getContext(), message);
            if (msg != null) {
                //noinspection ConstantConditions
                Snackbar.make(getView(), msg, Snackbar.LENGTH_LONG).show();
            } else {
                RequestAuthTask.prompt(getContext(), mGoodreadsTaskModel.getTaskListener());
            }
        });

        FragmentManager fm = getChildFragmentManager();
//        mProgressDialog = (ProgressDialogFragment)
//               fm.findFragmentByTag(ProgressDialogFragment.TAG);
//        if (mProgressDialog != null) {
//            // reconnect after a fragment restart
//            mProgressDialog.setCancellable(mGoodreadsTaskModel.getTask());
//        }

        View root = getView();

        //noinspection ConstantConditions
        root.findViewById(R.id.lbl_sync_with_goodreads)
            .setOnClickListener(v -> onImport(true));

        root.findViewById(R.id.lbl_import_all_from_goodreads)
            .setOnClickListener(v1 -> onImport(false));

        root.findViewById(R.id.lbl_send_updated_books_to_goodreads)
            .setOnClickListener(v -> onSend(true));

        root.findViewById(R.id.lbl_send_all_books_to_goodreads)
            .setOnClickListener(v -> onSend(false));
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
                Intent intent = new Intent(getContext(), TasksAdminActivity.class);
                startActivity(intent);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onImport(final boolean isSync) {
        //noinspection ConstantConditions
        Snackbar.make(getView(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();

        TaskBase<Void, GrStatus> task =
                new ImportTask(isSync, mGoodreadsTaskModel.getTaskListener());

//        mProgressDialog = ProgressDialogFragment
//                .newInstance(R.string.gr_title_sync_with_goodreads, false, false, 0);
//        mProgressDialog.show(getChildFragmentManager(), ProgressDialogFragment.TAG);

        mGoodreadsTaskModel.setTask(task);
//        mProgressDialog.setCancellable(task);
        task.execute();
    }

    private void onSend(final boolean updatesOnly) {
        //noinspection ConstantConditions
        Snackbar.make(getView(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();

        TaskBase<Void, GrStatus> task =
                new SendBooksTask(updatesOnly, mGoodreadsTaskModel.getTaskListener());

//        mProgressDialog = ProgressDialogFragment
//                .newInstance(R.string.gr_title_send_book, false, false, 0);
//        mProgressDialog.show(getChildFragmentManager(), ProgressDialogFragment.TAG);

        mGoodreadsTaskModel.setTask(task);
//        mProgressDialog.setCancellable(task);
        task.execute();
    }
}
