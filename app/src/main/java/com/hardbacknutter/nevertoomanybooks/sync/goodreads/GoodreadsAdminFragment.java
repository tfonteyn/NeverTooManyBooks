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
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentGoodreadsAdminBinding;
import com.hardbacknutter.nevertoomanybooks.settings.sites.GoodreadsPreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.admin.TasksAdminActivity;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * Starting point for sending and importing books with Goodreads.
 */
public class GoodreadsAdminFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "GoodreadsAdminFragment";

    private GoodreadsAdminViewModel mVm;

    /** View Binding. */
    private FragmentGoodreadsAdminBinding mVb;
    private Toolbar mToolbar;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
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
        //noinspection ConstantConditions
        mToolbar = getActivity().findViewById(R.id.toolbar);

        mVm = new ViewModelProvider(this).get(GoodreadsAdminViewModel.class);
        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onCancelled().observe(getViewLifecycleOwner(), this::onCancelled);
        mVm.onFailure().observe(getViewLifecycleOwner(), this::onGrFailure);
        mVm.onFinished().observe(getViewLifecycleOwner(), this::onGrFinished);

        mVb.btnSync.setOnClickListener(v -> importBooks(true));
        mVb.btnImport.setOnClickListener(v -> importBooks(false));
        mVb.btnSendUpdatedBooks.setOnClickListener(v -> sendBooks(true));
        mVb.btnSendAllBooks.setOnClickListener(v -> sendBooks(false));
    }

    @Override
    public void onResume() {
        super.onResume();
        mToolbar.setTitle(R.string.site_goodreads);
        mToolbar.setSubtitle("");
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.text != null) {
            Snackbar.make(mVb.getRoot(), message.text, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onCancelled(@NonNull final LiveDataEvent message) {
        if (message.isNewEvent()) {
            Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private void onGrFailure(@NonNull final FinishedMessage<Exception> message) {
        if (message.isNewEvent()) {
            //noinspection ConstantConditions
            Snackbar.make(mVb.getRoot(), GrStatus.getMessage(getContext(), message.result),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFinished(@NonNull final FinishedMessage<GrStatus> message) {
        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            if (message.result.getStatus() == GrStatus.FAILED_CREDENTIALS) {
                //noinspection ConstantConditions
                mVm.promptForAuthentication(getContext());
            } else {
                //noinspection ConstantConditions
                Snackbar.make(mVb.getRoot(), message.result.getMessage(getContext()),
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_GOODREADS_TASKS, 0, R.string.gr_tq_menu_background_tasks)
            .setIcon(R.drawable.ic_baseline_format_list_bulleted_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        menu.add(Menu.NONE, R.id.MENU_SETTINGS, 0, R.string.lbl_settings)
            .setIcon(R.drawable.ic_baseline_settings_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_GOODREADS_TASKS) {
            // Start the activity that shows the active GoodReads tasks
            startActivity(new Intent(getContext(), TasksAdminActivity.class));
            return true;

        } else if (itemId == R.id.MENU_SETTINGS) {
            final Fragment fragment = new GoodreadsPreferencesFragment();
            final FragmentManager fm = getParentFragmentManager();
            fm.beginTransaction()
              .setReorderingAllowed(true)
              .addToBackStack(GoodreadsPreferencesFragment.TAG)
              .replace(R.id.main_fragment, fragment, GoodreadsPreferencesFragment.TAG)
              .commit();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void importBooks(final boolean sync) {
        Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();
        mVm.startImport(sync);
    }

    private void sendBooks(final boolean updatesOnly) {
        Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();
        mVm.startSend(false, updatesOnly);
    }
}
