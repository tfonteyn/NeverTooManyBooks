/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesSingleListContract;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.GridDividerItemDecoration;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentUpdateFromInternetBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowUpdateFromInternetBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.MultiColumnRecyclerViewAdapter;

/**
 * Search the internet for one book or a list of books and download/update book data
 * as per user choices.
 * <p>
 * TODO: re-introduce remembering the last id done, and restarting from that id onwards.
 * See {@link SearchBookUpdatesViewModel}
 */
public class SearchBookUpdatesFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "SearchBookUpdatesFragment";

    public static final String BKEY_SCREEN_TITLE = TAG + ":title";
    public static final String BKEY_SCREEN_SUBTITLE = TAG + ":subtitle";

    /** The extended SearchCoordinator. */
    private SearchBookUpdatesViewModel vm;
    private final ActivityResultLauncher<List<Site>> editSitesLauncher =
            registerForActivityResult(new SearchSitesSingleListContract(),
                                      o -> o.ifPresent(sites -> vm.setSiteList(sites)));

    @Nullable
    private ProgressDelegate progressDelegate;
    /** View Binding. */
    private FragmentUpdateFromInternetBinding vb;


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchBookUpdatesViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), getArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentUpdateFromInternetBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Bundle args = getArguments();

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        // optional activity title
        if (args != null && args.containsKey(BKEY_SCREEN_TITLE)) {
            toolbar.setTitle(args.getString(BKEY_SCREEN_TITLE));
        } else {
            toolbar.setTitle(R.string.lbl_select_fields);
        }
        // optional activity subtitle
        if (args != null && args.containsKey(BKEY_SCREEN_SUBTITLE)) {
            toolbar.setSubtitle(args.getString(BKEY_SCREEN_SUBTITLE));
        } else {
            final int nrOfBooks = vm.getTotalBooks();
            toolbar.setSubtitle(getString(R.string.name_colon_value,
                                          getString(R.string.lbl_books),
                                          String.valueOf(nrOfBooks)));
        }

        // Progress from individual searches AND overall progress
        vm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);

        // An individual book search finished.
        vm.onSearchFinished().observe(getViewLifecycleOwner(), this::onOneDone);

        // User cancelled the update
        vm.onSearchCancelled().observe(getViewLifecycleOwner(), message -> {
            // Unlikely to be seen...
            Snackbar.make(vb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                    .show();
            // report up what work did get done + the last book we did.
            onAllDone(message);
        });

        // The full list was processed
        vm.onAllDone().observe(getViewLifecycleOwner(), this::onAllDone);
        // Something really bad happened and we're aborting
        vm.onAbort().observe(getViewLifecycleOwner(), this::onAbort);

        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.ic_baseline_cloud_download_24);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> prepareUpdate());

        //noinspection DataFlowIssue
        final GridDividerItemDecoration columnDivider =
                new GridDividerItemDecoration(getContext(), false, true);
        vb.fieldList.addItemDecoration(columnDivider);
        vb.fieldList.setHasFixedSize(true);
        initAdapter();

        if (savedInstanceState == null) {
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_update_fields_from_internet, () ->
                              EngineId.promptToRegister(getContext(), vm.getSiteList(),
                                                        "update_from_internet",
                                                        this::afterOnViewCreated));
        } else {
            afterOnViewCreated();
        }
    }

    private void initAdapter() {
        final GridLayoutManager layoutManager = (GridLayoutManager) vb.fieldList.getLayoutManager();
        //noinspection DataFlowIssue
        vb.fieldList.setAdapter(new SyncFieldAdapter(getContext(), vm.getSyncFields(),
                                                     layoutManager.getSpanCount()));
    }

    private void afterOnViewCreated() {
        // Warn the user, but don't abort.
        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            Snackbar.make(vb.getRoot(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Do some basic checks; let the user confirm how to handle thumbnails;
     * and start the update process.
     */
    private void prepareUpdate() {
        // sanity check
        if (vm.getSyncFields()
              .stream()
              .map(SyncField::getAction)
              .noneMatch(action -> action != SyncAction.Skip)) {

            Snackbar.make(vb.fieldList, R.string.warning_select_at_least_1_field,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        // Warn the user, AND abort.
        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            Snackbar.make(vb.getRoot(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        // If the user has selected to overwrite thumbnails...
        if (vm.isShowWarningAboutCovers()) {
            // check if the user really wants to overwrite all covers
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.menu_update_books)
                    .setMessage(R.string.confirm_overwrite_cover)
                    .setNeutralButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setNegativeButton(R.string.option_field_usage_copy_if_blank, (d, w) -> {
                        vm.setCoverSyncAction(SyncAction.CopyIfBlank);
                        startUpdate();
                    })
                    .setPositiveButton(R.string.option_field_usage_overwrite, (d, w) -> {
                        vm.setCoverSyncAction(SyncAction.Overwrite);
                        startUpdate();
                    })
                    .create()
                    .show();
            return;
        }
        startUpdate();
    }

    private void startUpdate() {
        //noinspection DataFlowIssue
        if (!vm.startSearch(getContext())) {
            Snackbar.make(vb.getRoot(), R.string.warning_no_search_data_for_active_sites,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onOneDone(@NonNull final LiveDataEvent<Book> message) {
        //noinspection DataFlowIssue
        message.process(book -> vm.processOne(getContext(), book));
    }

    private void onAllDone(@NonNull final LiveDataEvent<Book> message) {
        closeProgressDialog();

        message.process(book -> {
            //noinspection DataFlowIssue
            getActivity().setResult(Activity.RESULT_OK, EditBookOutput.createResultIntent(book));
            getActivity().finish();
        });
    }

    private void onAbort(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();

        message.process(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), e, getString(R.string.error_updates_aborted),
                             (d, w) -> {
                                 d.dismiss();
                                 //noinspection DataFlowIssue
                                 getActivity().finish();
                             });
        });
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.process(progress -> {
            if (progressDelegate == null) {
                //noinspection DataFlowIssue
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.progress_msg_searching)
                        .setIndeterminate(true)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> vm.cancelTask(progress.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(progress);
        });
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }

    private static class Holder
            extends RecyclerView.ViewHolder
            implements BindableViewHolder<SyncField> {

        @NonNull
        private final RowUpdateFromInternetBinding vb;

        Holder(@NonNull final RowUpdateFromInternetBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        @Override
        public void onBind(@Nullable final SyncField syncField) {
            if (syncField == null) {
                vb.field.setVisibility(View.INVISIBLE);
                vb.cbxUsage.setVisibility(View.INVISIBLE);
            } else {
                vb.field.setVisibility(View.VISIBLE);
                vb.cbxUsage.setVisibility(View.VISIBLE);
                vb.field.setText(syncField.getFieldLabel());
                vb.cbxUsage.setChecked(syncField.getAction() != SyncAction.Skip);
                vb.cbxUsage.setText(syncField.getActionLabelResId());
            }
        }
    }

    private static class SyncFieldAdapter
            extends MultiColumnRecyclerViewAdapter<Holder> {

        static final SyncField[] Z_ARRAY_SYNC_FIELD = new SyncField[0];

        @NonNull
        private final SyncField[] syncFields;

        /**
         * Constructor.
         *
         * @param context     Current context.
         * @param syncFields  to show
         * @param columnCount the number of columns to be used
         */
        SyncFieldAdapter(@NonNull final Context context,
                         @NonNull final Collection<SyncField> syncFields,
                         final int columnCount) {
            super(context, columnCount);
            this.syncFields = syncFields.toArray(Z_ARRAY_SYNC_FIELD);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final Holder holder = new Holder(
                    RowUpdateFromInternetBinding.inflate(getInflater(), parent, false));

            holder.vb.cbxUsage.setOnClickListener(v -> {
                final int position = holder.getBindingAdapterPosition();
                final int listIndex = transpose(position);
                if (listIndex == RecyclerView.NO_POSITION) {
                    // Should never get here
                    throw new IllegalStateException("No ListIndex for position=" + position);
                }
                final SyncField fs = syncFields[listIndex];
                fs.nextState();
                holder.vb.cbxUsage.setChecked(fs.getAction() != SyncAction.Skip);
                holder.vb.cbxUsage.setText(fs.getActionLabelResId());
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final int listIndex = transpose(position);
            if (listIndex == RecyclerView.NO_POSITION) {
                holder.onBind(null);
            } else {
                holder.onBind(syncFields[listIndex]);
            }
        }

        @Override
        protected int getRealItemCount() {
            return syncFields.length;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.search_for_updates, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            menu.findItem(R.id.MENU_ISBN_VALIDITY_STRICT)
                .setChecked(vm.isStrictIsbn());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_PREFS_SEARCH_SITES) {
                editSitesLauncher.launch(vm.getSiteList());
                return true;

            } else if (itemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
                final boolean checked = !menuItem.isChecked();
                vm.setStrictIsbn(checked);

            } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET_SKIP_ALL) {
                vm.setAll(SyncAction.Skip);
                initAdapter();
                return true;

            } else if (itemId == R.id.MENU_RESET) {
                vm.resetAll();
                initAdapter();
                return true;
            }

            return false;
        }
    }
}
