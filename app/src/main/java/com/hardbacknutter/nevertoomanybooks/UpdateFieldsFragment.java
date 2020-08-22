/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentUpdateFromInternetBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowUpdateFromInternetBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.FieldUsage;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminModel;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.UpdateFieldsModel;

/**
 * Search the internet for one book or a list of books and download/update book data
 * as per user choices.
 * <p>
 * TODO: re-introduce remembering the last id done, and restarting from that id onwards.
 * See {@link UpdateFieldsModel} mFromBookIdOnwards
 */
public class UpdateFieldsFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "UpdateFieldsFragment";

    /** The extended SearchCoordinator. */
    private UpdateFieldsModel mUpdateFieldsModel;
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** View binding. */
    private FragmentUpdateFromInternetBinding mVb;

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
        mVb = FragmentUpdateFromInternetBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Bundle args = getArguments();

        // optional activity title
        if (args != null && args.containsKey(StandardDialogs.BKEY_DIALOG_TITLE)) {
            //noinspection ConstantConditions
            getActivity().setTitle(args.getString(StandardDialogs.BKEY_DIALOG_TITLE));
        } else {
            //noinspection ConstantConditions
            getActivity().setTitle(R.string.lbl_select_fields);
        }

        mUpdateFieldsModel = new ViewModelProvider(this).get(UpdateFieldsModel.class);
        //noinspection ConstantConditions
        mUpdateFieldsModel.init(getContext(), args);

        // Progress from individual searches AND overall progress
        mUpdateFieldsModel.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        // An individual book search finished.
        mUpdateFieldsModel.onSearchFinished().observe(getViewLifecycleOwner(), message ->
                mUpdateFieldsModel.processSearchResults(getContext(), message.result));
        // User cancelled the update
        mUpdateFieldsModel.onSearchCancelled().observe(getViewLifecycleOwner(), message -> {
            // Unlikely to be seen...
            Snackbar.make(mVb.getRoot(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG)
                    .show();
            // report up what work did get done + the last book we did.
            onAllDone(message);
        });
        // The full list was processed
        mUpdateFieldsModel.onAllDone().observe(getViewLifecycleOwner(), this::onAllDone);
        // Something really bad happened and we're aborting
        mUpdateFieldsModel.onCatastrophe().observe(getViewLifecycleOwner(), this::onCatastrophe);

        // The FAB lives in the activity.
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_cloud_download);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> prepareUpdate());

        populateFields();

        if (savedInstanceState == null) {
            TipManager.display(getContext(), R.string.tip_update_fields_from_internet, () ->
                    Site.promptToRegister(getContext(), mUpdateFieldsModel.getSiteList(),
                                          "update_from_internet", this::afterOnViewCreated));
        } else {
            afterOnViewCreated();
        }
    }

    private void afterOnViewCreated() {
        // Warn the user, but don't abort.
        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            Snackbar.make(mVb.getRoot(), R.string.error_please_connect_to_internet,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Display the list of fields.
     */
    private void populateFields() {
        for (FieldUsage usage : mUpdateFieldsModel.getFieldUsages()) {
            final RowUpdateFromInternetBinding rowVb = RowUpdateFromInternetBinding
                    .inflate(getLayoutInflater(), mVb.fieldList, false);

            //noinspection ConstantConditions
            rowVb.field.setText(usage.getLabel(getContext()));

            rowVb.cbxUsage.setChecked(usage.isWanted());
            rowVb.cbxUsage.setText(usage.getUsageLabel(getContext()));
            rowVb.cbxUsage.setTag(R.id.TAG_FIELD_USAGE, usage);
            rowVb.cbxUsage.setOnClickListener(v -> {
                final FieldUsage fu = (FieldUsage) rowVb.cbxUsage.getTag(R.id.TAG_FIELD_USAGE);
                fu.nextState();
                rowVb.cbxUsage.setChecked(fu.isWanted());
                rowVb.cbxUsage.setText(fu.getUsageLabel(getContext()));
            });

            mVb.fieldList.addView(rowVb.getRoot());
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case RequestCode.PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    final ArrayList<Site> sites =
                            data.getParcelableArrayListExtra(Site.Type.Data.getBundleKey());
                    if (sites != null) {
                        mUpdateFieldsModel.setSiteList(sites);
                    }
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        final Resources r = getResources();
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 r.getInteger(R.integer.MENU_ORDER_SEARCH_SITES),
                 R.string.lbl_websites)
            .setIcon(R.drawable.ic_find_in_page)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.menu_reset_to_default)
            .setIcon(R.drawable.ic_undo)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES: {
                final Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminModel.BKEY_LIST, mUpdateFieldsModel.getSiteList());
                startActivityForResult(intent, RequestCode.PREFERRED_SEARCH_SITES);
                return true;
            }
            case R.id.MENU_RESET: {
                //noinspection ConstantConditions
                mUpdateFieldsModel.resetPreferences(getContext());
                mVb.fieldList.removeAllViews();
                populateFields();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Count the checked fields, we need at least one selected to make sense.
     *
     * @return {@code true} if at least one field is selected
     */
    private boolean hasSelections() {
        final int nChildren = mVb.fieldList.getChildCount();
        for (int i = 0; i < nChildren; i++) {
            final View view = mVb.fieldList.getChildAt(i);
            final CompoundButton cb = view.findViewById(R.id.cbx_usage);
            if (cb != null) {
                final FieldUsage fieldUsage = (FieldUsage) cb.getTag(R.id.TAG_FIELD_USAGE);
                if (fieldUsage.isWanted()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Do some basic checks; let the user confirm how to handle thumbnails;
     * and start the update process.
     */
    private void prepareUpdate() {
        // sanity check
        if (!hasSelections()) {
            Snackbar.make(mVb.fieldList, R.string.warning_select_at_least_1_field,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            Snackbar.make(mVb.getRoot(), R.string.error_please_connect_to_internet,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        // If the user has selected to overwrite thumbnails...
        if (mUpdateFieldsModel.isShowWarningAboutCovers()) {
            // check if the user really wants to overwrite all covers
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.menu_update_fields)
                    .setMessage(R.string.confirm_overwrite_thumbnail)
                    .setNeutralButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setNegativeButton(R.string.lbl_field_usage_copy_if_blank, (d, w) -> {
                        mUpdateFieldsModel.setFieldUsage(DBDefinitions.PREFS_IS_USED_THUMBNAIL,
                                                         FieldUsage.Usage.CopyIfBlank);
                        startUpdate();
                    })
                    .setPositiveButton(R.string.lbl_field_usage_overwrite, (d, w) -> {
                        mUpdateFieldsModel.setFieldUsage(DBDefinitions.PREFS_IS_USED_THUMBNAIL,
                                                         FieldUsage.Usage.Overwrite);
                        startUpdate();
                    })
                    .create()
                    .show();
            return;
        }
        startUpdate();
    }

    private void startUpdate() {
        //noinspection ConstantConditions
        mUpdateFieldsModel.writePreferences(getContext());

        if (!mUpdateFieldsModel.startSearch(getContext())) {
            Snackbar.make(mVb.getRoot(), R.string.warning_no_search_data_for_active_sites,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onAllDone(@NonNull final FinishedMessage<Bundle> message) {
        closeProgressDialog();

        if (message.result != null) {
            // The result will contain:
            // UpdateFieldsModel.BKEY_LAST_BOOK_ID, long
            // UniqueId.BKEY_BOOK_MODIFIED, boolean
            // DBDefinitions.KEY_PK_ID, long (can be absent)
            final Intent data = new Intent().putExtras(message.result);
            //noinspection ConstantConditions
            getActivity().setResult(Activity.RESULT_OK, data);
        }

        //noinspection ConstantConditions
        getActivity().finish();
    }

    private void onCatastrophe(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        String msg = null;
        if (message.result != null) {
            msg = message.result.getLocalizedMessage();
        }
        if (msg == null) {
            msg = getString(R.string.error_unexpected_error);
        }

        //noinspection ConstantConditions
        msg = StandardDialogs.createBadError(getContext(), msg);

        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_error)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    d.dismiss();
                    //noinspection ConstantConditions
                    getActivity().finish();
                })
                .create()
                .show();
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog();
        }
        mProgressDialog.onProgress(message);
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog() {
        final FragmentManager fm = getChildFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        // not found? create it
        if (dialog == null) {
            dialog = ProgressDialogFragment.newInstance(
                    getString(R.string.progress_msg_searching), true, true);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCanceller(mUpdateFieldsModel);

        return dialog;
    }
}
