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
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncStripinfoBinding;
import com.hardbacknutter.nevertoomanybooks.settings.sites.StripInfoBePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

@Keep
public class StripInfoSyncFragment
        extends BaseFragment {

    public static final String TAG = "StripInfoSyncFragment";

    /** View Binding. */
    private FragmentSyncStripinfoBinding mVb;

    private StripInfoSyncViewModel mVm;
    @Nullable
    private ProgressDelegate mProgressDelegate;

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

        mVb.btnImport.setOnClickListener(v -> new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.lbl_import_books)
                .setMessage(R.string.confirm_strip_info_import_collection)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_go, (d, w) -> mVm.startImport())
                .create()
                .show());
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

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.isNewEvent()) {
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(
                        getActivity().findViewById(R.id.progress_frame))
                        .setTitle(getString(R.string.lbl_importing))
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> mVm.cancelTask(message.taskId))
                        .show(getActivity().getWindow());
            }
            mProgressDelegate.onProgress(message);
        }
    }

    private void closeProgressDialog() {
        if (mProgressDelegate != null) {
            //noinspection ConstantConditions
            mProgressDelegate.dismiss(getActivity().getWindow());
            mProgressDelegate = null;
        }
    }

    private void onImportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final Exception e = message.requireResult();

            final Context context = getContext();

            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, e)
                                    .orElse(getString(R.string.error_storage_not_writable));

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
        }
    }

    private void onImportCollectionFinished(
            @NonNull final FinishedMessage<ImportCollectionTask.Outcome> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            onImportCollectionFinished(R.string.progress_end_import_complete,
                                       message.requireResult());
        }
    }

    private void onImportCollectionCancelled(
            @NonNull final FinishedMessage<ImportCollectionTask.Outcome> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final ImportCollectionTask.Outcome result = message.getResult();
            if (result != null) {
                onImportCollectionFinished(R.string.progress_end_import_partially_complete,
                                           result);
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

        final List<String> items = new ArrayList<>();

        if (!result.created.isEmpty()) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_created),
                                String.valueOf(result.created.size())));
        }

        if (!result.updated.isEmpty()) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_updated),
                                String.valueOf(result.updated.size())));
        }

        final String itemList = items
                .stream()
                .map(s -> getString(R.string.list_element, s))
                .collect(Collectors.joining("\n"));

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_info_24)
                .setTitle(titleId)
                .setMessage(itemList)
                .setPositiveButton(R.string.action_done, (d, w) -> {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK);
                    getActivity().finish();
                })
                .create()
                .show();
    }
}
