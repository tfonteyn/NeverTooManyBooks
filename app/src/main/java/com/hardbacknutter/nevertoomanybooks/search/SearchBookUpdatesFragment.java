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
package com.hardbacknutter.nevertoomanybooks.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesSingleListContract;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentUpdateFromInternetBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowUpdateFromInternetBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

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
    private static final String TAG = "SearchBookUpdatesFragment";

    public static final String BKEY_SCREEN_TITLE = TAG + ":title";
    public static final String BKEY_SCREEN_SUBTITLE = TAG + ":subtitle";

    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider mToolbarMenuProvider;

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
    private ProgressDelegate mProgressDelegate;
    /** View Binding. */
    private FragmentUpdateFromInternetBinding mVb;


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(SearchBookUpdatesViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), getArguments());
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

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());

        // optional activity title
        if (args != null && args.containsKey(BKEY_SCREEN_TITLE)) {
            toolbar.setTitle(args.getString(BKEY_SCREEN_TITLE));
        } else {
            toolbar.setTitle(R.string.lbl_select_fields);
        }
        // optional activity subtitle
        if (args != null && args.containsKey(BKEY_SCREEN_SUBTITLE)) {
            toolbar.setSubtitle(args.getString(BKEY_SCREEN_SUBTITLE));
        }

        // Progress from individual searches AND overall progress
        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);

        // An individual book search finished.
        mVm.onSearchFinished().observe(getViewLifecycleOwner(), this::onOneDone);

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
        mVm.onAbort().observe(getViewLifecycleOwner(), this::onAbort);

        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.ic_baseline_cloud_download_24);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> prepareUpdate());

        mVb.fieldList.setHasFixedSize(true);
        initAdapter();

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_update_fields_from_internet, () ->
                              Site.promptToRegister(getContext(), mVm.getSiteList(),
                                                    "update_from_internet",
                                                    this::afterOnViewCreated));
        } else {
            afterOnViewCreated();
        }
    }

    private void initAdapter() {
        //noinspection ConstantConditions
        mVb.fieldList.setAdapter(new SyncFieldAdapter(getContext(), mVm.getSyncFields()));
    }

    private void afterOnViewCreated() {
        // Warn the user, but don't abort.
        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            Snackbar.make(mVb.getRoot(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Do some basic checks; let the user confirm how to handle thumbnails;
     * and start the update process.
     */
    private void prepareUpdate() {
        // sanity check
        if (mVm.getSyncFields()
               .stream()
               .map(SyncField::getAction)
               .noneMatch(action -> action != SyncAction.Skip)) {

            Snackbar.make(mVb.fieldList, R.string.warning_select_at_least_1_field,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            Snackbar.make(mVb.getRoot(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        // If the user has selected to overwrite thumbnails...
        if (mVm.isShowWarningAboutCovers()) {
            // check if the user really wants to overwrite all covers
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

    private void onOneDone(final LiveDataEvent<TaskResult<Bundle>> message) {
        //noinspection ConstantConditions
        message.getData().ifPresent(data -> mVm.processOne(getContext(), data.getResult()));
    }

    private void onAllDone(@NonNull final LiveDataEvent<TaskResult<Bundle>> message) {
        closeProgressDialog();
        message.getData().ifPresent(data -> {
            final Bundle result = data.getResult();

            if (result != null) {
                final long lastBookId =
                        result.getLong(SearchBookUpdatesViewModel.BKEY_LAST_BOOK_ID, 0);

                final Intent resultIntent = EditBookOutput
                        .createResultIntent(
                                result.getLong(DBKey.FK_BOOK, 0),
                                result.getBoolean(SearchBookUpdatesViewModel.BKEY_MODIFIED, false))
                        .putExtra(SearchBookUpdatesViewModel.BKEY_LAST_BOOK_ID, lastBookId);

                //noinspection ConstantConditions
                getActivity().setResult(Activity.RESULT_OK, resultIntent);
            }

            //noinspection ConstantConditions
            getActivity().finish();
        });
    }

    private void onAbort(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, data.getResult())
                                    .orElse(getString(R.string.error_unknown_long,
                                                      getString(R.string.pt_maintenance)));

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        d.dismiss();
                        //noinspection ConstantConditions
                        getActivity().finish();
                    })
                    .create()
                    .show();
        });
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.progress_msg_searching)
                        .setIndeterminate(true)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> mVm.cancelTask(data.taskId))
                        .show(() -> getActivity().getWindow());
            }
            mProgressDelegate.onProgress(data);
        });
    }

    private void closeProgressDialog() {
        if (mProgressDelegate != null) {
            //noinspection ConstantConditions
            mProgressDelegate.dismiss(getActivity().getWindow());
            mProgressDelegate = null;
        }
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowUpdateFromInternetBinding vb;

        Holder(@NonNull final RowUpdateFromInternetBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }

    private static class SyncFieldAdapter
            extends RecyclerView.Adapter<Holder> {

        /** Cached inflater. */
        @NonNull
        private final LayoutInflater mInflater;

        private final SyncField[] mSyncFields;

        /**
         * Constructor.
         *
         * @param context    Current context.
         * @param syncFields to show
         */
        SyncFieldAdapter(@NonNull final Context context,
                         @NonNull final Collection<SyncField> syncFields) {
            mInflater = LayoutInflater.from(context);
            //noinspection ZeroLengthArrayAllocation
            mSyncFields = syncFields.toArray(new SyncField[0]);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowUpdateFromInternetBinding vb = RowUpdateFromInternetBinding
                    .inflate(mInflater, parent, false);
            final Holder holder = new Holder(vb);

            holder.vb.cbxUsage.setOnClickListener(v -> {
                final SyncField fs = mSyncFields[holder.getBindingAdapterPosition()];
                fs.nextState();
                vb.cbxUsage.setChecked(fs.getAction() != SyncAction.Skip);
                vb.cbxUsage.setText(fs.getActionLabelResId());
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final SyncField syncField = mSyncFields[position];

            holder.vb.field.setText(syncField.getFieldLabelResId());
            holder.vb.cbxUsage.setChecked(syncField.getAction() != SyncAction.Skip);
            holder.vb.cbxUsage.setText(syncField.getActionLabelResId());
        }

        @Override
        public int getItemCount() {
            return mSyncFields.length;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            final Resources r = getResources();
            menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                     r.getInteger(R.integer.MENU_ORDER_SEARCH_SITES),
                     R.string.lbl_websites)
                .setIcon(R.drawable.ic_baseline_find_in_page_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

            menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.action_reset_to_default)
                .setIcon(R.drawable.ic_baseline_undo_24);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_PREFS_SEARCH_SITES) {
                mEditSitesLauncher.launch(mVm.getSiteList());
                return true;

            } else if (itemId == R.id.MENU_RESET) {
                mVm.resetPreferences();
                initAdapter();
                return true;
            }

            return false;
        }
    }
}
