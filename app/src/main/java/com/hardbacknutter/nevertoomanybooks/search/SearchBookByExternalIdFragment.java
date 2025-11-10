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
package com.hardbacknutter.nevertoomanybooks.search;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.RadioButton;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.Map;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ConstraintRadioGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByExternalIdBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

public class SearchBookByExternalIdFragment
        extends SearchBookBaseFragment {

    /**
     * NEWTHINGS: adding a new search engine: optional:
     * add a RadioButton to the layout +
     * add mapping between the RadioButton ViewId and the EngineId in the below Map.
     * <p>
     *  Amazon is not added here, users should use the ISBN.
     * <p>
     *  ENHANCE: DNB is HIDDEN: implement DNB sid searches once the site "stabiler link"
     *  points to the (for now) beta website we use to find and parse
     */
    private static final Map<Integer, EngineId> VIEW_TO_ENGINE = Map.ofEntries(
            Map.entry(R.id.site_bedetheque, EngineId.Bedetheque),
            Map.entry(R.id.site_bibliotece, EngineId.BibliotecePl),
            Map.entry(R.id.site_databaze_knih, EngineId.DatabazeKnih),
            Map.entry(R.id.site_dnb, EngineId.Dnb),
            Map.entry(R.id.site_goodreads, EngineId.Goodreads),
            Map.entry(R.id.site_isfdb, EngineId.Isfdb),
            Map.entry(R.id.site_kbnl, EngineId.KbNl),
            Map.entry(R.id.site_last_dodo_nl, EngineId.LastDodoNl),
            Map.entry(R.id.site_open_library, EngineId.OpenLibrary),
            Map.entry(R.id.site_strip_info_be, EngineId.StripInfoBe)
    );

    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");

    /** View Binding. */
    private FragmentBooksearchByExternalIdBinding vb;
    /** Set when the user selects a site. */
    @Nullable
    private EngineId engineId;

    private SearchBookByExternalIdViewModel vm;

    @Override
    @NonNull
    Intent createResultIntent() {
        return vm.createResultIntent();
    }

    /**
     * The user finished editing a book. Store results.
     *
     * @param data from the edit
     */
    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        onClearSearchCriteria();

        vm.onBookEditingDone(data);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchBookByExternalIdViewModel.class);
        vm.init(requireArguments());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentBooksearchByExternalIdBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.fragmentRootView(view);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_add_book_by_external_id);

        modelToView();

        vb.sitesGroup.setOnCheckedChangeListener(this::onSiteSelect);
        vb.btnSearch.setOnClickListener(v -> prepareCriteria());

        autoRemoveError(vb.externalId, vb.lblExternalId);
        vb.externalId.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v);
                prepareCriteria();
                return true;
            }
            return false;
        });
    }

    private void onSiteSelect(@NonNull final ConstraintRadioGroup group,
                              @IdRes final int viewId) {

        // on NOTHING selected
        if (viewId == View.NO_ID) {
            engineId = null;
            // disable, but don't clear it
            vb.externalId.setEnabled(false);
            return;
        }

        // on true->false transition
        final RadioButton btn = vb.getRoot().findViewById(viewId);
        if (!btn.isChecked()) {
            return;
        }

        // on false->true transition

        this.engineId = VIEW_TO_ENGINE.get(viewId);
        // Sanity check
        if (this.engineId == null) {
            throw new IllegalStateException("Bug: View has no Engine defined");
        }

        updateUI(engineId);
    }

    private void updateUI(@NonNull final EngineId engineId) {

        final int keyboardIcon;
        final int inputType;
        //noinspection OptionalGetWithoutIsPresent
        if (engineId.getIdentifier().get().getType() == Identifier.TYPE_STRING) {
            // display an alphanumeric keyboard icon
            keyboardIcon = R.drawable.keyboard_24px;
            inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;

        } else {
            // display a numeric keyboard icon
            keyboardIcon = R.drawable.dialpad_24px;
            inputType = InputType.TYPE_CLASS_NUMBER;

            // if the user switched from a text input, clean the input
            if ((vb.externalId.getInputType() & InputType.TYPE_CLASS_NUMBER) == 0) {
                //noinspection DataFlowIssue
                final String text = vb.externalId.getText().toString().strip();
                if (!DIGITS_PATTERN.matcher(text).matches()) {
                    vb.externalId.setText("");
                }
            }
        }

        vb.externalId.setInputType(inputType);
        vb.externalId.setCompoundDrawablesRelativeWithIntrinsicBounds(keyboardIcon, 0, 0, 0);
        vb.externalId.setEnabled(true);
    }

    protected void modelToView() {
        final int checkedId = vm.getSelectedRbViewId();
        if (checkedId != View.NO_ID) {
            final RadioButton btn = vb.getRoot().findViewById(checkedId);
            if (btn.getVisibility() == View.VISIBLE) {
                btn.setChecked(true);
                vb.externalId.setEnabled(true);
                vb.externalId.setText(vm.getSid());
                return;
            }
        }
        vb.externalId.setEnabled(false);
        vb.externalId.setText("");
    }

    protected void viewToModel() {
        vm.setSelectedRbViewId(vb.sitesGroup.getCheckedRadioButtonId());
        //noinspection DataFlowIssue
        vm.setSid(vb.externalId.getText().toString().strip());
    }

    /**
     * Prepare the criteria object to use for the search.
     * This method can interact with the user,
     * and can reject starting a search.
     */
    private void prepareCriteria() {
        viewToModel();

        // check if we have an active search, if so, quit silently.
        if (coordinator.isSearchActive()) {
            return;
        }

        final String sid = vm.getSid();
        //sanity check
        if (sid == null || sid.isBlank() || vb.sitesGroup.getCheckedRadioButtonId() == View.NO_ID) {
            vb.lblExternalId.setError(getString(R.string.warning_requires_site_and_id));
            return;
        }

        //noinspection DataFlowIssue
        final BookSearchCriteria criteria = new BookSearchCriteria(getContext());
        //noinspection DataFlowIssue
        criteria.addSid(engineId, sid);

        final int searchId = startSearch(criteria);
        if (searchId == 0) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_book_search_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    int startSearch(@NonNull final BookSearchCriteria criteria) {
        // Warn the user, AND abort.
        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
            return 0;
        }
        //noinspection DataFlowIssue
        return coordinator.searchByExternalId(engineId, criteria);
    }

    @Override
    void onSearchResults(@NonNull final BookSearchResult result) {
        final Book book = result.getBook();

        // A non-empty result will have a title, or at least 3 fields:
        // The external id field for the site should be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = book.getString(DBKey.TITLE, null);
        if ((title == null || title.isEmpty()) && book.size() <= 2) {
            Snackbar.make(vb.externalId, R.string.warning_no_matching_book_found,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        editBook(book, vm.getStyle());
    }

    @Override
    void onClearSearchCriteria() {
        vm.setSid(null);

        vb.externalId.setText("");
    }
}
