/*
 * @Copyright 2019 HardBackNutter
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

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

public class BookSearchByNativeIdFragment
        extends BookSearchBaseFragment {

    /** log tag. */
    public static final String TAG = "BookSearchByNativeId";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private static final String BKEY_SITE_RES_ID = TAG + ":siteResId";

    /** User input field. */
    private EditText mEntryView;
    private Button mSearchBtn;

    private RadioGroup mRadioGroup;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_booksearch_by_native_id, container, false);
        mEntryView = view.findViewById(R.id.native_id);
        mSearchBtn = view.findViewById(R.id.btn_search);
        mRadioGroup = view.findViewById(R.id.sites_group);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.fab_add_book_by_native_id);

        if (savedInstanceState != null) {
            int siteResId = savedInstanceState.getInt(BKEY_SITE_RES_ID, View.NO_ID);
            if (siteResId != View.NO_ID) {
                RadioButton btn = mRadioGroup.findViewById(siteResId);
                btn.setChecked(true);
                mEntryView.setEnabled(true);
            }

        }
        mEntryView.setText(mSearchCoordinator.getNativeIdSearchText());

        mRadioGroup.setOnCheckedChangeListener(this::onSiteSelect);

        mSearchBtn.setOnClickListener(v -> {
            //sanity check
            if (mEntryView.getText().toString().trim().isEmpty()
                || mRadioGroup.getCheckedRadioButtonId() == View.NO_ID) {
                Snackbar.make(mEntryView, R.string.warning_requires_site_and_id,
                              Snackbar.LENGTH_LONG).show();
                return;
            }

            startSearch();
        });
    }

    @Override
    protected boolean onSearch() {
        int siteId = SearchSites.getSiteIdFromResId(mRadioGroup.getCheckedRadioButtonId());
        mSearchCoordinator.setNativeIdSearchText(mEntryView.getText().toString().trim());
        return mSearchCoordinator.searchByNativeId(Site.createDataSite(siteId));
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        Intent intent = new Intent(getContext(), EditBookActivity.class)
                .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
        startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
        clearPreviousSearchCriteria();
    }

    @Override
    void clearPreviousSearchCriteria() {
        super.clearPreviousSearchCriteria();
        mEntryView.setText("");
    }

    @Override
    public void onPause() {
        super.onPause();
        int checkedId = mRadioGroup.getCheckedRadioButtonId();
        if (checkedId != View.NO_ID) {
            mSearchCoordinator.setNativeIdSearchText(mEntryView.getText().toString().trim());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        int checkedId = mRadioGroup.getCheckedRadioButtonId();
        if (checkedId != View.NO_ID) {
            outState.putInt(BKEY_SITE_RES_ID, checkedId);
        }
    }

    private void onSiteSelect(@NonNull final RadioGroup group,
                              final int checkedId) {

        Site site = Site.createDataSite(SearchSites.getSiteIdFromResId(checkedId));
        SearchEngine searchEngine = site.getSearchEngine();
        //noinspection ConstantConditions
        if (!searchEngine.isAvailable(getContext())) {
            // If the selected site needs registration, prompt the user.
            searchEngine.promptToRegister(getContext(), true, "native_id");
            mRadioGroup.clearCheck();
            return;
        }

        //NEWTHINGS: add new site specific ID:
        switch (checkedId) {
            // 'long' id
            case R.id.site_goodreads:
            case R.id.site_isfdb:
            case R.id.site_library_thing:
            case R.id.site_strip_info_be:
                // if the user switched from a text input, clean the input
                if ((mEntryView.getInputType() & InputType.TYPE_CLASS_NUMBER) == 0) {
                    String text = mEntryView.getText().toString().trim();
                    if (!DIGITS_PATTERN.matcher(text).matches()) {
                        mEntryView.setText("");
                    }
                }
                // display a (sort of) numeric keyboard icon
                mEntryView.setInputType(InputType.TYPE_CLASS_NUMBER);
                mEntryView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_apps, 0, 0, 0);
                break;

            // 'String' id
            case R.id.site_amazon:
            case R.id.site_open_library:
                mEntryView.setInputType(InputType.TYPE_CLASS_TEXT
                                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                // display an alphanumeric keyboard icon
                mEntryView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_keyboard, 0, 0, 0);
                break;

            default:
                throw new UnexpectedValueException(checkedId);
        }

        mEntryView.setEnabled(true);
    }
}
