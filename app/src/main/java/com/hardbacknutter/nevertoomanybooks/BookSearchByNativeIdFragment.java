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

import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
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
    private final SearchCoordinator.OnSearchFinishedListener mOnSearchFinishedListener =
            (wasCancelled, bookData) -> {
                try {
                    if (!wasCancelled && !bookData.isEmpty()) {
                        Intent intent = new Intent(getContext(), EditBookActivity.class)
                                .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                        startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);

                        clearPreviousSearchCriteria();

                    } else {
                        //noinspection ConstantConditions
                        UserMessage.show(getActivity(),
                                         R.string.warning_no_matching_book_found);
                    }
                } finally {
                    mBookSearchBaseModel.setSearchCoordinator(0);
                    // Tell our listener they can close the progress dialog.
                    mTaskManager.sendHeaderUpdate(null);
                }
            };

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

        mEntryView.setText(mBookSearchBaseModel.getNativeIdSearchText());

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
                    break;

                // 'String' id
                case R.id.site_amazon:
                case R.id.site_open_library:
                    mEntryView.setInputType(InputType.TYPE_CLASS_TEXT
                                            | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    break;

                default:
                    throw new UnexpectedValueException(checkedId);
            }
        });

        mSearchBtn.setOnClickListener(v -> {
            String nativeId = mEntryView.getText().toString().trim();
            //sanity check
            if (nativeId.isEmpty() || mRadioGroup.getCheckedRadioButtonId() == View.NO_ID) {
                //noinspection ConstantConditions
                UserMessage.show(getActivity(), R.string.warning_requires_id_and_site);
                return;
            }

            mBookSearchBaseModel.setNativeIdSearchText(nativeId);
            startSearch();
        });
    }

    @Override
    protected void startSearch(@NonNull final SearchCoordinator searchCoordinator) {

        String idStr = mBookSearchBaseModel.getNativeIdSearchText();
        if (idStr.isEmpty()) {
            return;
        }

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

        searchCoordinator.setFetchThumbnail(true);
        searchCoordinator.searchByNativeId(site, idStr);
    }

    @Override
    SearchCoordinator.OnSearchFinishedListener getOnSearchFinishedListener() {
        return mOnSearchFinishedListener;
    }

    @Override
    void clearPreviousSearchCriteria() {
        super.clearPreviousSearchCriteria();
        mEntryView.setText("");
    }
}
