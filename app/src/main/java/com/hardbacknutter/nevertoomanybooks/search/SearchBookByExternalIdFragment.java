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

import java.util.List;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ConstraintRadioGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByExternalIdBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

public class SearchBookByExternalIdFragment
        extends SearchBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "BookSearchByExternalId";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");

    private static final String SIS_SELECTED_RB_ID = TAG + ":selectedResId";
    private static final String SIS_USER_INPUT = TAG + ":externalId";

    /** The currently selected radio button used by onPause/onSaveInstanceState. */
    private int selectedRbViewId = View.NO_ID;
    /** The current external id text used by onPause/onSaveInstanceState. */
    private String currentInput;
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
        vm.onBookEditingDone(data);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchBookByExternalIdViewModel.class);
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

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_add_book_by_external_id);

        if (savedInstanceState != null) {
            final int checkedId = savedInstanceState.getInt(SIS_SELECTED_RB_ID, View.NO_ID);
            if (checkedId != View.NO_ID) {
                final RadioButton btn = vb.getRoot().findViewById(checkedId);
                if (btn.getVisibility() == View.VISIBLE) {
                    btn.setChecked(true);
                    vb.externalId.setEnabled(true);
                    vb.externalId.setText(savedInstanceState.getString(SIS_USER_INPUT, ""));
                }
            }
        }

        vb.sitesGroup.setOnCheckedChangeListener(this::onSiteSelect);
        vb.btnSearch.setOnClickListener(v -> startSearch());

        autoRemoveError(vb.externalId, vb.lblExternalId);
        vb.externalId.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                BaseActivity.hideKeyboard(v);
                startSearch();
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

        final SearchEngineConfig config = SearchEngineConfig
                .getByViewId(viewId).orElseThrow(IllegalStateException::new);

        this.engineId = config.getEngineId();
        //noinspection ConstantConditions
        EngineId.promptToRegister(getContext(), List.of(Site.Type.Data.getSite(engineId)),
                                  "searchByExternalId", () -> updateUI(config));
    }

    private void updateUI(@NonNull final SearchEngineConfig config) {

        final int keyboardIcon;
        final int inputType;
        //noinspection ConstantConditions
        if (config.getExternalIdDomain().getSqLiteDataType() == SqLiteDataType.Text) {
            // display an alphanumeric keyboard icon
            keyboardIcon = R.drawable.ic_baseline_keyboard_24;
            inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;

        } else {
            // display a (sort of) numeric keyboard icon
            keyboardIcon = R.drawable.ic_baseline_apps_24;
            inputType = InputType.TYPE_CLASS_NUMBER;

            // if the user switched from a text input, clean the input
            if ((vb.externalId.getInputType() & InputType.TYPE_CLASS_NUMBER) == 0) {
                //noinspection ConstantConditions
                final String text = vb.externalId.getText().toString().trim();
                if (!DIGITS_PATTERN.matcher(text).matches()) {
                    vb.externalId.setText("");
                }
            }
        }

        vb.externalId.setInputType(inputType);
        vb.externalId.setCompoundDrawablesRelativeWithIntrinsicBounds(keyboardIcon, 0, 0, 0);
        vb.externalId.setEnabled(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        selectedRbViewId = vb.sitesGroup.getCheckedRadioButtonId();
        //noinspection ConstantConditions
        currentInput = vb.externalId.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SIS_SELECTED_RB_ID, selectedRbViewId);
        outState.putString(SIS_USER_INPUT, currentInput);
    }

    @Override
    boolean onPreSearch() {
        //sanity check
        //noinspection ConstantConditions
        if (vb.externalId.getText().toString().trim().isEmpty()
            || vb.sitesGroup.getCheckedRadioButtonId() == View.NO_ID) {
            vb.lblExternalId.setError(getString(R.string.warning_requires_site_and_id));
            return false;
        }
        return true;
    }

    @Override
    boolean onSearch() {
        //noinspection ConstantConditions
        final String externalId = vb.externalId.getText().toString().trim();
        //noinspection ConstantConditions
        return coordinator.searchByExternalId(engineId, externalId);
    }

    @Override
    void onSearchResults(@NonNull final Book book) {
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
        // edit book
        super.onSearchResults(book);
    }

    @Override
    void onClearSearchCriteria() {
        super.onClearSearchCriteria();
        vb.externalId.setText("");
    }
}
