/*
 * @Copyright 2018-2026 HardBackNutter
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.GridDividerItemDecoration;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncfieldConfigBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.settings.searchsites.SearchSitesSingleListContract;
import com.hardbacknutter.nevertoomanybooks.sync.SyncAction;
import com.hardbacknutter.nevertoomanybooks.sync.SyncField;
import com.hardbacknutter.nevertoomanybooks.sync.SyncFieldAdapter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;

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

    /** Optional argument to set a Toolbar title. */
    public static final String BKEY_SCREEN_TITLE = TAG + ":title";
    /** Optional argument to set a Toolbar subtitle. */
    public static final String BKEY_SCREEN_SUBTITLE = TAG + ":subtitle";

    /** The extended SearchCoordinator. */
    private SearchBookUpdatesViewModel vm;
    private final ActivityResultLauncher<List<Site>> editSitesLauncher =
            registerForActivityResult(new SearchSitesSingleListContract(),
                                      o -> o.ifPresent(sites -> vm.setSiteList(sites)));

    @Nullable
    private ProgressDelegate progressDelegate;
    /** View Binding. */
    private FragmentSyncfieldConfigBinding vb;


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
        vb = FragmentSyncfieldConfigBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(vb.fieldList);

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
        vm.onSearchProgress().observe(getViewLifecycleOwner(), this::onProgress);

        // An individual book search finished.
        vm.onSearchFinished().observe(getViewLifecycleOwner(), this::onOneDone);

        // User cancelled the update
        vm.onSearchCancelled().observe(getViewLifecycleOwner(), this::onCancelled);

        // The full list was processed
        vm.onAllDone().observe(getViewLifecycleOwner(), this::onAllDone);
        // There was an Exception thrown during the search and we're aborting
        vm.onAbort().observe(getViewLifecycleOwner(), this::onAbort);

        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.cloud_download_24px);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> prepareUpdate());

        //noinspection DataFlowIssue
        final GridDividerItemDecoration columnDivider =
                new GridDividerItemDecoration(getContext(), false, true);
        vb.fieldList.addItemDecoration(columnDivider);
        vb.fieldList.setHasFixedSize(true);

        initAdapter();

        // Warn the user, but don't abort.
        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            Snackbar.make(vb.getRoot(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void initAdapter() {
        final GridLayoutManager layoutManager = (GridLayoutManager) vb.fieldList.getLayoutManager();
        //noinspection DataFlowIssue
        vb.fieldList.setAdapter(new SyncFieldAdapter(vm.getSyncFields(),
                                                     layoutManager.getSpanCount()));
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
                    .setIcon(R.drawable.warning_24px)
                    .setTitle(R.string.menu_update_books)
                    .setMessage(R.string.confirm_overwrite_cover)
                    .setNeutralButton(R.string.cancel, (d, w) -> d.dismiss())
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

    private void onOneDone(@NonNull final LiveDataEvent<Boolean> message) {
        message.process(ignoreAlwaysTrue -> vm.processOne());
    }

    private void onAllDone(@NonNull final LiveDataEvent<BookSearchResult> message) {
        closeProgressDialog();

        message.process(result -> {
            @Nullable
            final EditBookOutput editBookOutput = result.getEditBookOutput();
            if (editBookOutput != null) {
                //noinspection DataFlowIssue
                getActivity().setResult(Activity.RESULT_OK, editBookOutput.createResultIntent());
                getActivity().finish();
            } else {
                // We should never get here, flw...
                //noinspection DataFlowIssue
                getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
            }
        });
    }

    private void onCancelled(@NonNull final LiveDataEvent<Boolean> message) {
        closeProgressDialog();

        message.process(ignoreAlwaysTrue -> {
            // We *should* get the last result which was pushed onto the queue when
            // the user tapped cancel, but due to LiveData, we *might* come here
            // twice and find an empty queue. Hence, we *must* guard against
            // not only the queue being empty, but also against the item polled
            // having no EditBookOutput (it being of a single result instead of the list-result)
            // ==> see the BookSearchResult class docs
            final BookSearchResult result = vm.pollCancelledQueue();
            if (result != null) {
                @Nullable
                final EditBookOutput editBookOutput = result.getEditBookOutput();
                if (editBookOutput != null) {
                    // We should not get here, but adding this code makes us future proof.
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK,
                                            editBookOutput.createResultIntent());
                    getActivity().finish();
                    return;
                }
            }
            showMessageAndFinishActivity(getString(R.string.cancelled));
        });
    }

    private void onAbort(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();

        message.process(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e, getString(R.string.error_updates_aborted),
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
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.progress_msg_searching)
                        .setIndeterminate(true)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> vm.cancel())
                        .show();
            }
            progressDelegate.onProgress(progress);
        });
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            progressDelegate.dismiss();
            progressDelegate = null;
        }
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.search_for_updates, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            menu.findItem(R.id.MENU_PRODUCT_CODE_VALIDITY_STRICT)
                .setChecked(BookSearchCriteria.isStrictIsbnGlobal());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_PREFS_SEARCH_SITES) {
                editSitesLauncher.launch(vm.getSiteList());
                return true;

            } else if (menuItemId == R.id.MENU_PRODUCT_CODE_VALIDITY_STRICT) {
                final boolean checked = !menuItem.isChecked();
                BookSearchCriteria.setStrictIsbnDefault(checked);

            } else if (menuItemId == R.id.MENU_UPDATE_FROM_INTERNET_SKIP_ALL) {
                vm.setSyncAction(SyncAction.Skip);
                initAdapter();
                return true;

            } else if (menuItemId == R.id.MENU_RESET) {
                vm.resetSyncProcessor();
                initAdapter();
                return true;
            }

            return false;
        }
    }
}
