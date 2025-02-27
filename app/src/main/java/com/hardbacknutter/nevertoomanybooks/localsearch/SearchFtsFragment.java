/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.localsearch;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAdvancedSearchBinding;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * Search using the SQLite FTS engine with separate text for the author, title, series,...
 * <p>
 * A real-time search results with title/author is shown on-screen.
 * <p>
 * Either use "select all" menu item to create a book-list containing all the results,
 * or tap individual results to see the book.
 */
public class SearchFtsFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "SearchFtsFragment";

    private SearchFtsViewModel vm;

    private TextWatcher textWatcher;
    private SearchAdapter searchAdapter;
    private ActivityResultLauncher<ShowBookPagerContract.Input> displayBookLauncher;
    /** View Binding. */
    private FragmentAdvancedSearchBinding vb;
    private MenuItem menuBtnApply;
    private View.OnTouchListener touchListener;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchFtsViewModel.class);
        vm.init(requireArguments());

        displayBookLauncher = registerForActivityResult(
                new ShowBookPagerContract(), o -> {
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentAdvancedSearchBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.fragmentRootView(view);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_local_search);
        toolbar.addMenuProvider(new ToolbarMenuProvider());
        menuBtnApply = toolbar.getMenu().findItem(R.id.MENU_APPLY);

        // callback for the initial load from the viewmodel
        vm.onInitSearchCriteria().observe(getViewLifecycleOwner(), this::onSearchCriteriaUpdate);

        vm.onSearchStart().observe(getViewLifecycleOwner(), aVoid -> {
            viewToModel();
            vm.search();
        });
        vm.onSearchFinished().observe(getViewLifecycleOwner(), aVoid -> onSearchFinished());

        // Detect when user touches something outside of the EditText
        touchListener = (v, event) -> {
            vm.userIsActive(false);
            return false;
        };

        textWatcher = (ExtTextWatcher) s -> vm.userIsActive(true);

        //noinspection DataFlowIssue
        searchAdapter = new SearchAdapter(getContext(), vm.getSearchResults(), id ->
                displayBookLauncher.launch(new ShowBookPagerContract.Input(id, vm.getStyleUuid())));
        vb.searchResults.setAdapter(searchAdapter);
        // Timer will be started in OnResume().
    }

    @SuppressLint("ClickableViewAccessibility")
    private void onSearchCriteriaUpdate(@NonNull final SearchCriteria criteria) {
        vb.title.setText(criteria.getFtsBookTitle());
        vb.seriesTitle.setText(criteria.getFtsSeriesTitle());
        vb.author.setText(criteria.getFtsAuthor());
        vb.publisher.setText(criteria.getFtsPublisher());
        vb.keywords.setText(criteria.getFtsKeywords());

        // Detect when user types something.
        vb.title.addTextChangedListener(textWatcher);
        vb.seriesTitle.addTextChangedListener(textWatcher);
        vb.author.addTextChangedListener(textWatcher);
        vb.publisher.addTextChangedListener(textWatcher);
        vb.keywords.addTextChangedListener(textWatcher);
        // Detect when user touches something outside of the EditText
        vb.contentBody.setOnTouchListener(touchListener);

        // trigger a search if needed
        if (!criteria.isEmpty()) {
            vm.userIsActive(true);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onSearchFinished() {
        final int count = vm.getSearchResults().size();
        final String s = getResources().getQuantityString(R.plurals.n_books_found, count, count);
        getToolbar().setSubtitle(s);
        menuBtnApply.setEnabled(count > 0);

        searchAdapter.notifyDataSetChanged();
    }

    /**
     * When the show results buttons is tapped, return and show the resulting booklist.
     */
    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void showFullResults() {
        final Intent resultIntent = new Intent().putExtra(SearchCriteria.BKEY, vm.getCriteria());
        //noinspection DataFlowIssue
        getActivity().setResult(Activity.RESULT_OK, resultIntent);
        getActivity().finish();
    }

    /**
     * When activity resumes, set search as dirty + start the timer.
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        vm.userIsActive(true);
    }

    /**
     * When activity pauses, stop timer and store the search fields content.
     */
    @Override
    @CallSuper
    public void onPause() {
        vm.shutdownTimer();
        viewToModel();

        super.onPause();
    }

    private void viewToModel() {
        final SearchCriteria criteria = vm.getCriteria();
        //noinspection DataFlowIssue
        criteria.setFtsBookTitle(vb.title.getText().toString().trim());
        //noinspection DataFlowIssue
        criteria.setFtsSeriesTitle(vb.seriesTitle.getText().toString().trim());
        //noinspection DataFlowIssue
        criteria.setFtsAuthor(vb.author.getText().toString().trim());
        //noinspection DataFlowIssue
        criteria.setFtsPublisher(vb.publisher.getText().toString().trim());
        //noinspection DataFlowIssue
        criteria.setFtsKeywords(vb.keywords.getText().toString().trim());
    }

    @Override
    @CallSuper
    public void onDestroy() {
        // onPause is not always called! Hence making absolutely sure to shutdown the timer.
        vm.shutdownTimer();
        super.onDestroy();
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {
        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(Menu.NONE, R.id.MENU_APPLY, 0, R.string.btn_show_list)
                .setIcon(R.drawable.select_all_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_APPLY) {
                showFullResults();
                return true;
            }
            return false;
        }
    }
}
