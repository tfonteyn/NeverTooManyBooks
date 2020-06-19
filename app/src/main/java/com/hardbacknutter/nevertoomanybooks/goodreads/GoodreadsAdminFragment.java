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

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentGoodreadsAdminBinding;
import com.hardbacknutter.nevertoomanybooks.goodreads.admin.TasksAdminActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.ImportTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendBooksTask;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.settings.sites.GoodreadsPreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.GoodreadsTaskModel;

/**
 * Starting point for sending and importing books with Goodreads.
 */
public class GoodreadsAdminFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "GoodreadsAdminFragment";

    /** ViewModel for task control. */
    private GoodreadsTaskModel mGrTaskModel;
    /** View binding. */
    private FragmentGoodreadsAdminBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mGrTaskModel = new ViewModelProvider(this).get(GoodreadsTaskModel.class);
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

        mGrTaskModel.onTaskFinished().observe(getViewLifecycleOwner(), message -> {
            if (message.result != null && message.result == GrStatus.FAILED_CREDENTIALS) {
                //noinspection ConstantConditions
                RequestAuthTask.prompt(getContext(), mGrTaskModel.getTaskListener());
            } else {
                //noinspection ConstantConditions
                Snackbar.make(mVb.getRoot(), GoodreadsHandler.digest(getContext(), message),
                              Snackbar.LENGTH_LONG).show();
            }
        });

        mVb.btnSync.setOnClickListener(v -> importBooks(true));
        mVb.btnImport.setOnClickListener(v -> importBooks(false));
        mVb.btnSendUpdatedBooks.setOnClickListener(v -> sendBooks(true));
        mVb.btnSendAllBooks.setOnClickListener(v -> sendBooks(false));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_GOODREADS_TASKS, 0, R.string.gr_tq_menu_background_tasks)
            .setIcon(R.drawable.ic_format_list_bulleted)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        menu.add(Menu.NONE, R.id.MENU_GOODREADS_SETTINGS, 0, R.string.lbl_settings)
            .setIcon(R.drawable.ic_settings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_GOODREADS_TASKS: {
                // Start the activity that shows the active GoodReads tasks
                startActivity(new Intent(getContext(), TasksAdminActivity.class));
                return true;
            }

            case R.id.MENU_GOODREADS_SETTINGS: {
                final Intent intent = new Intent(getContext(), SettingsActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, GoodreadsPreferencesFragment.TAG);
                startActivity(intent);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void importBooks(final boolean sync) {
        Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();
        mGrTaskModel.execute(new ImportTask(sync, mGrTaskModel.getTaskListener()));
    }

    private void sendBooks(final boolean updatesOnly) {
        Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();
        mGrTaskModel.execute(new SendBooksTask(false, updatesOnly, mGrTaskModel.getTaskListener()));
    }
}
