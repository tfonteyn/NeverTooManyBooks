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
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

public class BookSearchByNativeIdFragment
        extends BookSearchBaseFragment {

    /** Log tag. */
    public static final String TAG = "BookSearchByNativeId";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private static final String BKEY_SITE_RES_ID = TAG + ":siteResId";
    private static final String BKEY_NATIVE_ID = TAG + ":nativeId";

    /** User input field. */
    private EditText mNativeIdView;
    private Button mSearchBtn;

    private RadioGroup mRadioGroup;
    /** The currently selected radio button for onPause/onSaveInstanceState. */
    private int mCheckedSiteResId = View.NO_ID;
    /** The current native id text for onPause/onSaveInstanceState. */
    private String mNativeId;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_booksearch_by_native_id, container, false);
        mNativeIdView = view.findViewById(R.id.native_id);
        mSearchBtn = view.findViewById(R.id.btn_search);
        mRadioGroup = view.findViewById(R.id.sites_group);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.fab_add_book_by_native_id);

//       View root = getView();
//
//       int visibleSites = 0;
//        RadioButton singleVisibleBtn = null;
//        Locale locale = LocaleUtils.getUserLocale(getContext());
//        SiteList siteList = SiteList.getList(getContext(), locale, SiteList.Type.Data);
//        for (Site site : siteList.getSites(true)) {
//            int resId = site.getResId();
//            if (resId != View.NO_ID) {
//                RadioButton btn = root.findViewById(resId);
//                if (btn != null) {
//                    btn.setVisibility(site.isEnabled() ? View.VISIBLE : View.GONE);
//                    if (site.isEnabled()) {
//                        visibleSites++;
//                        singleVisibleBtn = btn;
//                    }
//                }
//            }
//        }
//
//        if (visibleSites == 0) {
//            Snackbar.make(mNativeIdView, "Please enable at least one site blah blah...",
//                          Snackbar.LENGTH_LONG).show();
//            getActivity().finish();
//
//        } else if (visibleSites == 1) {
//            singleVisibleBtn.setChecked(true);
//            mNativeIdView.setEnabled(true);
//        }

        Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            int checkedId = args.getInt(BKEY_SITE_RES_ID, View.NO_ID);
            if (checkedId != View.NO_ID) {
                RadioButton btn = mRadioGroup.findViewById(checkedId);
                if (btn.getVisibility() == View.VISIBLE) {
                    btn.setChecked(true);
                    mNativeIdView.setEnabled(true);
                    mNativeIdView.setText(args.getString(BKEY_NATIVE_ID, ""));
                }
            }
        }

        mRadioGroup.setOnCheckedChangeListener(this::onSiteSelect);

        mSearchBtn.setOnClickListener(v -> {
            //sanity check
            if (mNativeIdView.getText().toString().trim().isEmpty()
                || mRadioGroup.getCheckedRadioButtonId() == View.NO_ID) {
                Snackbar.make(mNativeIdView, R.string.warning_requires_site_and_id,
                              Snackbar.LENGTH_LONG).show();
                return;
            }

            startSearch();
        });
    }

    @Override
    protected boolean onSearch() {
        int siteId = SearchSites.getSiteIdFromResId(mRadioGroup.getCheckedRadioButtonId());
        String nativeId = mNativeIdView.getText().toString().trim();
        //noinspection ConstantConditions
        return mSearchCoordinator.searchByNativeId(getContext(),
                                                   Site.createDataSite(siteId), nativeId);
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
        mNativeIdView.setText("");
    }

    @Override
    public void onPause() {
        super.onPause();
        mCheckedSiteResId = mRadioGroup.getCheckedRadioButtonId();
        mNativeId = mNativeIdView.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCheckedSiteResId != View.NO_ID) {
            outState.putInt(BKEY_SITE_RES_ID, mCheckedSiteResId);
            outState.putString(BKEY_NATIVE_ID, mNativeId);
        }
    }

    private void onSiteSelect(@NonNull final RadioGroup group,
                              final int checkedId) {

        Site site = Site.createDataSite(SearchSites.getSiteIdFromResId(checkedId));
        //noinspection ConstantConditions
        SearchEngine searchEngine = site.getSearchEngine(getContext());
        if (!searchEngine.isAvailable(getContext())) {
            // If the selected site needs registration, prompt the user.
            searchEngine.promptToRegister(getContext(), true, "native_id");
            mRadioGroup.clearCheck();
            return;
        }

        //NEWTHINGS: add new site specific ID: split by Long/String value
        switch (checkedId) {
            // 'long' id
            case R.id.site_goodreads:
            case R.id.site_isfdb:
            case R.id.site_library_thing:
            case R.id.site_strip_info_be:
                // if the user switched from a text input, clean the input
                if ((mNativeIdView.getInputType() & InputType.TYPE_CLASS_NUMBER) == 0) {
                    String text = mNativeIdView.getText().toString().trim();
                    if (!DIGITS_PATTERN.matcher(text).matches()) {
                        mNativeIdView.setText("");
                    }
                }
                // display a (sort of) numeric keyboard icon
                mNativeIdView.setInputType(InputType.TYPE_CLASS_NUMBER);
                mNativeIdView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_apps, 0, 0, 0);
                break;

            // 'String' id
            case R.id.site_amazon:
            case R.id.site_open_library:
                mNativeIdView.setInputType(InputType.TYPE_CLASS_TEXT
                                           | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                // display an alphanumeric keyboard icon
                mNativeIdView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_keyboard, 0, 0, 0);
                break;

            default:
                throw new UnexpectedValueException(checkedId);
        }

        mNativeIdView.setEnabled(true);
    }
}
