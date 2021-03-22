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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesSingleListContract;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentUpdateFromInternetBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowUpdateFromInternetBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.fields.syncing.FieldSync;
import com.hardbacknutter.nevertoomanybooks.fields.syncing.SyncAction;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.SearchBookUpdatesViewModel;

/**
 * Search the internet for one book or a list of books and download/update book data
 * as per user choices.
 * <p>
 * TODO: re-introduce remembering the last id done, and restarting from that id onwards.
 * See {@link SearchBookUpdatesViewModel} mFromBookIdOnwards
 */
public class SearchBookUpdatesFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "SearchBookUpdatesFragment";

    public static final String BKEY_SCREEN_TITLE = TAG + ":title";
    public static final String BKEY_SCREEN_SUBTITLE = TAG + ":subtitle";

    /** The extended SearchCoordinator. */
    private SearchBookUpdatesViewModel mVm;
    private final ActivityResultLauncher<ArrayList<Site>> mEditSitesLauncher =
            registerForActivityResult(new SearchSitesSingleListContract(),
                                      sites -> {
                                          if (sites != null) {
                                              // no changes committed, temporary usage only
                                              mVm.setSiteList(sites);
                                          }
                                      });
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** View Binding. */
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
        if (args != null && args.containsKey(BKEY_SCREEN_TITLE)) {
            setTitle(args.getString(BKEY_SCREEN_TITLE));
        } else {
            setTitle(R.string.lbl_select_fields);
        }

        // optional activity subtitle
        if (args != null && args.containsKey(BKEY_SCREEN_SUBTITLE)) {
            setSubtitle(args.getString(BKEY_SCREEN_SUBTITLE));
        }

        mVm = new ViewModelProvider(this).get(SearchBookUpdatesViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), args);

        // Progress from individual searches AND overall progress
        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        // An individual book search finished.
        mVm.onSearchFinished().observe(getViewLifecycleOwner(), message ->
                mVm.processOne(getContext(), message.result));
        // User cancelled the update
        mVm.onSearchCancelled().observe(getViewLifecycleOwner(), message -> {
            // Unlikely to be seen...
            Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                    .show();
            // report up what work did get done + the last book we did.
            onAllDone(message);
        });
        // The full list was processed
        mVm.onAllDone().observe(getViewLifecycleOwner(), this::onAllDone);
        // Something really bad happened and we're aborting
        mVm.onCatastrophe().observe(getViewLifecycleOwner(), this::onCatastrophe);

        // The FAB lives in the activity.
        //noinspection ConstantConditions
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_baseline_cloud_download_24);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> prepareUpdate());

        populateFields();

        if (savedInstanceState == null) {
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_update_fields_from_internet, () ->
                              Site.promptToRegister(getContext(), mVm.getSiteList(),
                                                    "update_from_internet",
                                                    this::afterOnViewCreated));
        } else {
            afterOnViewCreated();
        }
    }

    private void afterOnViewCreated() {
        // Warn the user, but don't abort.
        if (!NetworkUtils.isNetworkAvailable()) {
            Snackbar.make(mVb.getRoot(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Display the list of fields.
     */
    private void populateFields() {
        for (final FieldSync fieldSync : mVm.getFieldSyncList()) {
            final RowUpdateFromInternetBinding rowVb = RowUpdateFromInternetBinding
                    .inflate(getLayoutInflater(), mVb.fieldList, false);

            rowVb.field.setText(fieldSync.getFieldLabelId());

            rowVb.cbxUsage.setChecked(fieldSync.isWanted());
            rowVb.cbxUsage.setText(fieldSync.getActionLabelId());
            rowVb.cbxUsage.setTag(R.id.TAG_FIELD_USAGE, fieldSync);
            rowVb.cbxUsage.setOnClickListener(v -> {
                final FieldSync fs = (FieldSync) rowVb.cbxUsage.getTag(R.id.TAG_FIELD_USAGE);
                fs.nextState();
                rowVb.cbxUsage.setChecked(fs.isWanted());
                rowVb.cbxUsage.setText(fs.getActionLabelId());
            });

            mVb.fieldList.addView(rowVb.getRoot());
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
            .setIcon(R.drawable.ic_baseline_find_in_page_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.action_reset_to_default)
            .setIcon(R.drawable.ic_baseline_undo_24);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_PREFS_SEARCH_SITES) {
            mEditSitesLauncher.launch(mVm.getSiteList());
            return true;

        } else if (itemId == R.id.MENU_RESET) {
            mVm.resetPreferences();
            mVb.fieldList.removeAllViews();
            populateFields();
            return true;
        }

        return super.onOptionsItemSelected(item);
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
                final FieldSync fieldSync = (FieldSync) cb.getTag(R.id.TAG_FIELD_USAGE);
                if (fieldSync.isWanted()) {
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

        if (!NetworkUtils.isNetworkAvailable()) {
            Snackbar.make(mVb.getRoot(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        // If the user has selected to overwrite thumbnails...
        if (mVm.isShowWarningAboutCovers()) {
            // check if the user really wants to overwrite all covers
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.menu_update_fields)
                    .setMessage(R.string.confirm_overwrite_cover)
                    .setNeutralButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setNegativeButton(R.string.lbl_field_usage_copy_if_blank, (d, w) -> {
                        mVm.setCoverSyncAction(SyncAction.CopyIfBlank);
                        startUpdate();
                    })
                    .setPositiveButton(R.string.lbl_field_usage_overwrite, (d, w) -> {
                        mVm.setCoverSyncAction(SyncAction.Overwrite);
                        startUpdate();
                    })
                    .create()
                    .show();
            return;
        }
        startUpdate();
    }

    private void startUpdate() {
        mVm.writePreferences();

        //noinspection ConstantConditions
        if (!mVm.startSearch(getContext())) {
            Snackbar.make(mVb.getRoot(), R.string.warning_no_search_data_for_active_sites,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onAllDone(@NonNull final FinishedMessage<Bundle> message) {
        closeProgressDialog();

        if (message.result != null) {
            // The result will contain:
            // SearchBookUpdatesViewModel.BKEY_LAST_BOOK_ID, long
            // UniqueId.BKEY_BOOK_MODIFIED, boolean
            // DBDefinitions.KEY_PK_ID, long (can be absent)
            final Intent resultIntent = new Intent().putExtras(message.result);
            //noinspection ConstantConditions
            getActivity().setResult(Activity.RESULT_OK, resultIntent);
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
            msg = getString(R.string.error_unknown_long);
        }

        //noinspection ConstantConditions
        msg = StandardDialogs.createBadError(getContext(), msg);

        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_error_24)
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
            final FragmentManager fm = getChildFragmentManager();
            // get dialog after a fragment restart
            mProgressDialog = (ProgressDialogFragment)
                    fm.findFragmentByTag(ProgressDialogFragment.TAG);
            // not found? create it
            if (mProgressDialog == null) {
                mProgressDialog = ProgressDialogFragment.newInstance(
                        getString(R.string.progress_msg_searching), true, true);
                mProgressDialog.show(fm, ProgressDialogFragment.TAG);
            }

            // hook the task up.
            mProgressDialog.setCanceller(mVm);
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
