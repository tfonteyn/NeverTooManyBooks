/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByExternalIdBinding;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.widgets.ConstraintRadioGroup;

public class BookSearchByExternalIdFragment
        extends BookSearchBaseFragment {

    /** Log tag. */
    public static final String TAG = "BookSearchByExternalId";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private static final String BKEY_SITE_RES_ID = TAG + ":siteResId";
    private static final String BKEY_EXTERNAL_ID = TAG + ":externalId";

    /** The currently selected radio button for onPause/onSaveInstanceState. */
    private int mCheckedSiteResId = View.NO_ID;
    /** The current external id text for onPause/onSaveInstanceState. */
    private String mExternalId;
    /** View Binding. */
    private FragmentBooksearchByExternalIdBinding mVb;
    private SearchEngine.ByExternalId mSelectedSearchEngine;

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

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.fab_add_book_by_external_id);

        final Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            final int checkedId = args.getInt(BKEY_SITE_RES_ID, View.NO_ID);
            if (checkedId != View.NO_ID) {
                final RadioButton btn = mVb.getRoot().findViewById(checkedId);
                if (btn.getVisibility() == View.VISIBLE) {
                    btn.setChecked(true);
                    mVb.externalId.setEnabled(true);
                    mVb.externalId.setText(args.getString(BKEY_EXTERNAL_ID, ""));
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

    @Override
    boolean onPreSearch() {
        //sanity check
        //noinspection ConstantConditions
        if (mVb.externalId.getText().toString().trim().isEmpty()
            || mVb.sitesGroup.getCheckedRadioButtonId() == View.NO_ID) {
            showError(mVb.lblExternalId, getString(R.string.warning_requires_site_and_id));
            return false;
        }
        return true;
    }

    @Override
    boolean onSearch() {
        //noinspection ConstantConditions
        final String externalId = mVb.externalId.getText().toString().trim();
        //noinspection ConstantConditions
        return mCoordinator.searchByExternalId(getContext(), mSelectedSearchEngine, externalId);
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // A non-empty result will have a title, or at least 3 fields:
        // The external id field for the site should be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = bookData.getString(DBDefinitions.KEY_TITLE);
        if ((title == null || title.isEmpty()) && bookData.size() <= 2) {
            Snackbar.make(mVb.externalId, R.string.warning_no_matching_book_found,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // edit book
        super.onSearchResults(bookData);
    }

    @Override
    void onClearPreviousSearchCriteria() {
        super.onClearPreviousSearchCriteria();
        mVb.externalId.setText("");
    }

    @Override
    public void onPause() {
        super.onPause();
        mCheckedSiteResId = mVb.sitesGroup.getCheckedRadioButtonId();
        //noinspection ConstantConditions
        mExternalId = mVb.externalId.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCheckedSiteResId != View.NO_ID) {
            outState.putInt(BKEY_SITE_RES_ID, mCheckedSiteResId);
            outState.putString(BKEY_EXTERNAL_ID, mExternalId);
        }
    }

    private void onSiteSelect(@NonNull final ConstraintRadioGroup group,
                              final int viewId) {

        final Site.Config config = Site.getConfigByViewId(viewId);
        //noinspection ConstantConditions
        mSelectedSearchEngine = (SearchEngine.ByExternalId) config.createSearchEngine();

        if (!mSelectedSearchEngine.isAvailable()) {
            // If the selected site needs registration, prompt the user.
            //noinspection ConstantConditions
            mSelectedSearchEngine.promptToRegister(getContext(), true, "external_id");
            mVb.sitesGroup.clearCheck();
            return;
        }

        final int keyboardIcon;
        final int inputType;
        //noinspection ConstantConditions
        if (config.getExternalIdDomain().isText()) {
            // display an alphanumeric keyboard icon
            keyboardIcon = R.drawable.ic_keyboard;
            inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;

        } else {
            // if the user switched from a text input, clean the input
            if ((mVb.externalId.getInputType() & InputType.TYPE_CLASS_NUMBER) == 0) {
                //noinspection ConstantConditions
                String text = mVb.externalId.getText().toString().trim();
                if (!DIGITS_PATTERN.matcher(text).matches()) {
                    mVb.externalId.setText("");
                }
            }
            // display a (sort of) numeric keyboard icon
            keyboardIcon = R.drawable.ic_apps;
            inputType = InputType.TYPE_CLASS_NUMBER;
        }

        mVb.externalId.setInputType(inputType);
        mVb.externalId.setCompoundDrawablesRelativeWithIntrinsicBounds(keyboardIcon, 0, 0, 0);
        mVb.externalId.setEnabled(true);
    }
}
