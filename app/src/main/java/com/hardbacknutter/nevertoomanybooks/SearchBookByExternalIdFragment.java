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
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByExternalIdBinding;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultIntentOwner;
import com.hardbacknutter.nevertoomanybooks.viewmodels.SearchBookByExternalIdViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.ConstraintRadioGroup;

public class SearchBookByExternalIdFragment
        extends SearchBookBaseFragment {

    /** Log tag. */
    public static final String TAG = "BookSearchByExternalId";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");

    private static final String SIS_SELECTED_RB_ID = TAG + ":selectedResId";
    private static final String SIS_USER_INPUT = TAG + ":externalId";

    /** The currently selected radio button used by onPause/onSaveInstanceState. */
    private int mSisSelectedRbId = View.NO_ID;
    /** The current external id text used by onPause/onSaveInstanceState. */
    private String mSisUserInput;
    /** View Binding. */
    private FragmentBooksearchByExternalIdBinding mVb;
    /** Set when the user selects a site. */
    @Nullable
    private SearchEngine.ByExternalId mSelectedSearchEngine;

    private SearchBookByExternalIdViewModel mVm;

    @NonNull
    @Override
    public ResultIntentOwner getResultIntentOwner() {
        return mVm;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentBooksearchByExternalIdBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.fab_add_book_by_external_id);

        mVm = new ViewModelProvider(this).get(SearchBookByExternalIdViewModel.class);

        if (savedInstanceState != null) {
            final int checkedId = savedInstanceState.getInt(SIS_SELECTED_RB_ID, View.NO_ID);
            if (checkedId != View.NO_ID) {
                final RadioButton btn = mVb.getRoot().findViewById(checkedId);
                if (btn.getVisibility() == View.VISIBLE) {
                    btn.setChecked(true);
                    mVb.externalId.setEnabled(true);
                    mVb.externalId.setText(savedInstanceState.getString(SIS_USER_INPUT, ""));
                }
            }
        }

        mVb.sitesGroup.setOnCheckedChangeListener(this::onSiteSelect);
        mVb.btnSearch.setOnClickListener(v -> startSearch());

        // soft-keyboards 'search' button act as a shortcut to start the search
        mVb.externalId.setOnEditorActionListener((v, actionId, event) -> {
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
            mSelectedSearchEngine = null;
            // disable, but don't clear it
            mVb.externalId.setEnabled(false);
            return;
        }

        // on true->false transition
        final RadioButton btn = mVb.getRoot().findViewById(viewId);
        if (!btn.isChecked()) {
            return;
        }

        // on false->true transition

        //noinspection OptionalGetWithoutIsPresent
        final SearchEngineConfig config = SearchEngineRegistry
                .getInstance().getByViewId(viewId).get();
        mSelectedSearchEngine = (SearchEngine.ByExternalId)
                Site.Type.Data.getSite(config.getEngineId()).getSearchEngine();

        if (!mSelectedSearchEngine.isAvailable()) {
            // If the selected site needs registration, prompt the user.
            //noinspection ConstantConditions
            mSelectedSearchEngine.promptToRegister(getContext(), true, null, action -> {
                if (action == SearchEngine.RegistrationAction.NotNow
                    || action == SearchEngine.RegistrationAction.Cancelled) {
                    // Clear the selection.
                    // Do not disable the button, allowing the user to change their mind.
                    mVb.sitesGroup.clearCheck();
                }
            });
            return;
        }

        final int keyboardIcon;
        final int inputType;
        //noinspection ConstantConditions
        if (config.getExternalIdDomain().isText()) {
            // display an alphanumeric keyboard icon
            keyboardIcon = R.drawable.ic_baseline_keyboard_24;
            inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;

        } else {
            // if the user switched from a text input, clean the input
            if ((mVb.externalId.getInputType() & InputType.TYPE_CLASS_NUMBER) == 0) {
                //noinspection ConstantConditions
                final String text = mVb.externalId.getText().toString().trim();
                if (!DIGITS_PATTERN.matcher(text).matches()) {
                    mVb.externalId.setText("");
                }
            }
            // display a (sort of) numeric keyboard icon
            keyboardIcon = R.drawable.ic_baseline_apps_24;
            inputType = InputType.TYPE_CLASS_NUMBER;
        }

        mVb.externalId.setInputType(inputType);
        mVb.externalId.setCompoundDrawablesRelativeWithIntrinsicBounds(keyboardIcon, 0, 0, 0);
        mVb.externalId.setEnabled(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSisSelectedRbId = mVb.sitesGroup.getCheckedRadioButtonId();
        //noinspection ConstantConditions
        mSisUserInput = mVb.externalId.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SIS_SELECTED_RB_ID, mSisSelectedRbId);
        outState.putString(SIS_USER_INPUT, mSisUserInput);
    }

    @Override
    boolean onPreSearch() {
        //sanity check
        //noinspection ConstantConditions
        if (mVb.externalId.getText().toString().trim().isEmpty()
            || mVb.sitesGroup.getCheckedRadioButtonId() == View.NO_ID) {
            showError(mVb.lblExternalId, R.string.warning_requires_site_and_id);
            return false;
        }
        return true;
    }

    @Override
    boolean onSearch() {
        //noinspection ConstantConditions
        final String externalId = mVb.externalId.getText().toString().trim();
        //noinspection ConstantConditions
        return mCoordinator.searchByExternalId(mSelectedSearchEngine, externalId);
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // A non-empty result will have a title, or at least 3 fields:
        // The external id field for the site should be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = bookData.getString(DBKeys.KEY_TITLE);
        if ((title == null || title.isEmpty()) && bookData.size() <= 2) {
            Snackbar.make(mVb.externalId, R.string.warning_no_matching_book_found,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // edit book
        super.onSearchResults(bookData);
    }

    @Override
    void onClearSearchCriteria() {
        super.onClearSearchCriteria();
        mVb.externalId.setText("");
    }
}
