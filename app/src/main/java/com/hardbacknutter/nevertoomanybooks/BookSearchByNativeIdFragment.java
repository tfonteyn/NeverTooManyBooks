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
import android.widget.RadioGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

public class BookSearchByNativeIdFragment
        extends BookSearchBaseFragment {

    public static final String TAG = "BookSearchByNativeId";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");

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

        mEntryView.setText(mSearchCoordinator.getNativeIdSearchText());

        mRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
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
                    mEntryView.setInputType(InputType.TYPE_CLASS_NUMBER);
                    mEntryView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.ic_apps, 0, 0, 0);
                    break;

                // 'String' id
                case R.id.site_amazon:
                case R.id.site_open_library:
                    mEntryView.setInputType(InputType.TYPE_CLASS_TEXT
                                            | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    mEntryView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.ic_keyboard, 0, 0, 0);
                    break;

                default:
                    throw new UnexpectedValueException(checkedId);
            }
        });

        mSearchBtn.setOnClickListener(v -> {
            String nativeId = mEntryView.getText().toString().trim();
            //sanity check
            if (nativeId.isEmpty() || mRadioGroup.getCheckedRadioButtonId() == View.NO_ID) {
                UserMessage.show(mEntryView, R.string.warning_requires_site_and_id);
                return;
            }

            mSearchCoordinator.setNativeIdSearchText(nativeId);
            startSearch();
        });
    }

    @Override
    protected boolean onSearch() {
        @IdRes
        int checkedId = mRadioGroup.getCheckedRadioButtonId();

        Site site;
        //NEWTHINGS: add new site specific ID:
        switch (checkedId) {
            // native id is the ASIN
            case R.id.site_amazon:
                site = Site.newSite(SearchSites.AMAZON);
                break;

            case R.id.site_goodreads:
                site = Site.newSite(SearchSites.GOODREADS);
                break;

            case R.id.site_isfdb:
                site = Site.newSite(SearchSites.ISFDB);
                break;
            case R.id.site_library_thing:
                site = Site.newSite(SearchSites.LIBRARY_THING);
                break;
            case R.id.site_open_library:
                site = Site.newSite(SearchSites.OPEN_LIBRARY);
                break;

            case R.id.site_strip_info_be:
                site = Site.newSite(SearchSites.STRIP_INFO_BE);
                break;

            default:
                throw new UnexpectedValueException(checkedId);
        }

        SearchEngine searchEngine = site.getSearchEngine();
        if (searchEngine.isAvailable()) {
            return mSearchCoordinator.searchByNativeId(site);

        } else {
            // If the selected site needs registration, prompt the user.
            //noinspection ConstantConditions
            searchEngine.promptToRegister(getContext(), true, "native_id");
            return false;
        }
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
}
