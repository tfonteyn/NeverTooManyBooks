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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncStripinfoBinding;
import com.hardbacknutter.nevertoomanybooks.settings.sites.StripInfoBePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

@Keep
public class StripInfoSyncFragment
        extends BaseFragment {

    public static final String TAG = "StripInfoSyncFragment";

    /** View Binding. */
    private FragmentSyncStripinfoBinding mVb;

    private StripInfoSyncViewModel mVm;
    @Nullable
    private ProgressDialogFragment mProgressDialog;

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
        mVb = FragmentSyncStripinfoBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.action_synchronize);

        mVm = new ViewModelProvider(this).get(StripInfoSyncViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), getArguments());
        mVm.onImportFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        mVm.onImportCollectionProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onImportCollectionFinished().observe(getViewLifecycleOwner(),
                                                 this::onImportCollectionFinished);
        mVm.onImportCollectionCancelled().observe(getViewLifecycleOwner(),
                                                  this::onImportCollectionCancelled);

        mVm.onImportQueueProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onImportQueueFinished().observe(getViewLifecycleOwner(),
                                            this::onImportQueueFinished);
        mVm.onImportQueueCancelled().observe(getViewLifecycleOwner(),
                                             this::onImportQueueCancelled);

        mVb.btnGetUpdatedBooks.setOnClickListener(v -> mVm.fetchCollection());

        mVb.btnGetQueuedBooks.setOnClickListener(v -> new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.lbl_import_books)
                .setMessage(R.string.confirm_strip_info_import_queued_books)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_go, (d, w) -> mVm.fetchQueuedBooks())
                .create()
                .show());
        updateQueuedBooks(mVm.getNrOfQueuedBooks());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(R.id.MENU_GROUP_STRIPINFO, R.id.MENU_STRIP_INFO_SETTING, 0,
                 R.string.lbl_settings)
            .setIcon(R.drawable.ic_baseline_settings_24);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_STRIP_INFO_SETTING) {
            openSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openSettings() {
        final Fragment fragment = new StripInfoBePreferencesFragment();
        final FragmentManager fm = getParentFragmentManager();
        fm.beginTransaction()
          .setReorderingAllowed(true)
          .addToBackStack(StripInfoBePreferencesFragment.TAG)
          .replace(R.id.main_fragment, fragment, StripInfoBePreferencesFragment.TAG)
          .commit();
    }

    private void updateQueuedBooks(final int size) {
        if (size > 0) {
            mVb.booksQueued.setText(getString(R.string.txt_x_books_queued, size));
            mVb.booksQueued.setVisibility(View.VISIBLE);
            mVb.btnGetQueuedBooks.setVisibility(View.VISIBLE);
        } else {
            mVb.booksQueued.setVisibility(View.GONE);
            mVb.btnGetQueuedBooks.setVisibility(View.GONE);
        }
    }


    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            final FragmentManager fm = getChildFragmentManager();
            // get dialog after a fragment restart
            mProgressDialog = (ProgressDialogFragment)
                    fm.findFragmentByTag(ProgressDialogFragment.TAG);
            // not found? create it
            if (mProgressDialog == null) {
                mProgressDialog = ProgressDialogFragment.newInstance(
                        getString(R.string.lbl_importing), false, true);
                mProgressDialog.show(fm, ProgressDialogFragment.TAG);
            }

            mVm.linkTaskWithDialog(message.taskId, mProgressDialog);
        }

        mProgressDialog.onProgress(message);
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    private void onImportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(createErrorReport(getContext(), message.result))
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
        }
    }

    @NonNull
    private String createErrorReport(@NonNull final Context context,
                                     @Nullable final Exception e) {
        final String msg = ExMsg.map(context, TAG, e);
        if (msg != null) {
            return msg;
        }

        return ExMsg.ioExFallbackMsg(context, e, context.getString(
                R.string.error_storage_not_writable));
    }


    private void onImportCollectionFinished(
            @NonNull final FinishedMessage<ImportCollectionTask.Outcome> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

            onImportCollectionFinished(R.string.progress_end_import_complete, message.result);
        }
    }

    private void onImportCollectionCancelled(
            @NonNull final FinishedMessage<ImportCollectionTask.Outcome> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            if (message.result != null) {
                onImportCollectionFinished(R.string.progress_end_import_partially_complete,
                                           message.result);
            } else {
                Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                        .show();
                //noinspection ConstantConditions
                getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
            }
        }
    }

    private void onImportCollectionFinished(@StringRes final int titleId,
                                            @NonNull final ImportCollectionTask.Outcome result) {
        updateQueuedBooks(result.queued.size());

        //noinspection ConstantConditions
        final MaterialAlertDialogBuilder dialogBuilder =
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(titleId);

        String msg = getString(R.string.name_colon_value,
                               getString(R.string.lbl_updated),
                               String.valueOf(result.updated.size()));

        if (result.queued.isEmpty()) {
            dialogBuilder.setPositiveButton(R.string.action_done, (d, w) -> {
                //noinspection ConstantConditions
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();
            });

        } else {
            msg += "\n" + getString(R.string.list_element,
                                    getString(R.string.name_colon_value,
                                              getString(R.string.lbl_queued),
                                              String.valueOf(result.queued.size())))
                   + "\n\n" + getString(R.string.confirm_strip_info_import_queued_books);

            dialogBuilder.setPositiveButton(R.string.action_go, (d, w) -> mVm.fetchQueuedBooks());
            dialogBuilder.setNegativeButton(R.string.action_not_now, (d, w) -> d.dismiss());
        }

        dialogBuilder.setMessage(msg)
                     .create()
                     .show();
    }

    private void onImportQueueCancelled(@NonNull final FinishedMessage<ArrayList<Long>> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            if (message.result != null) {
                onImportQueueFinished(R.string.progress_end_import_partially_complete,
                                      message.result);
            } else {
                Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                        .show();
                //noinspection ConstantConditions
                getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
            }
        }
    }

    private void onImportQueueFinished(@NonNull final FinishedMessage<ArrayList<Long>> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

            onImportQueueFinished(R.string.progress_end_import_complete, message.result);
        }
    }

    private void onImportQueueFinished(@StringRes final int titleId,
                                       @NonNull final List<Long> result) {
        updateQueuedBooks(mVm.getNrOfQueuedBooks());

        final String msg = getString(R.string.list_element_name_colon_value,
                                     getString(R.string.lbl_books),
                                     String.valueOf(result.size()));

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_info_24)
                .setTitle(titleId)
                .setMessage(msg)
                .setPositiveButton(R.string.action_done, (d, w) -> {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK);
                    getActivity().finish();
                })
                .create()
                .show();
    }
}
