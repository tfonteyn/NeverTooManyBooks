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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivityWithTasks;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookSearchBaseModel;

/**
 * Searches the internet for book details based on:
 * - manually provided or scanned ISBN.
 * - Author/Title.
 */
public class BookSearchActivity
        extends BaseActivityWithTasks {

    private String mTag;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTag = getIntent().getStringExtra(UniqueId.BKEY_FRAGMENT_TAG);
        if (mTag == null) {
            mTag = BookSearchByIsbnFragment.TAG;
        }

        replaceFragment(R.id.main_fragment, mTag);
    }

    /**
     * Create a fragment based on the given tag.
     *
     * @param tag for the required fragment
     */
    public void replaceFragment(@IdRes final int containerViewId,
                                @NonNull final String tag) {
        switch (tag) {
            case BookSearchByIsbnFragment.TAG:
                replaceFragment(containerViewId, BookSearchByIsbnFragment.class,
                                BookSearchByIsbnFragment.TAG);
                return;

            case BookSearchByTextFragment.TAG:
                replaceFragment(containerViewId, BookSearchByTextFragment.class,
                                BookSearchByTextFragment.TAG);
                return;

            case UpdateFieldsFromInternetFragment.TAG:
                replaceFragment(containerViewId, UpdateFieldsFromInternetFragment.class,
                                UpdateFieldsFromInternetFragment.TAG);
                return;

            default:
                throw new UnexpectedValueException(tag);
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {

        if (requestCode == UniqueId.REQ_NAV_PANEL_SETTINGS) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                int searchSites = data.getIntExtra(UniqueId.BKEY_SEARCH_SITES, 0);
                if (searchSites != 0) {
                    BookSearchBaseModel model =
                            new ViewModelProvider(this).get(BookSearchBaseModel.class);
                    model.setSearchSites(searchSites);
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {

//        if (!UpdateFieldsFromInternetFragment.TAG.equals(mTag)) {
        BookSearchBaseModel model = new ViewModelProvider(this).get(BookSearchBaseModel.class);
        Intent lastBookData = model.getLastBookData();
        if (lastBookData != null) {
            setResult(Activity.RESULT_OK, lastBookData);
        }
//        }
        super.onBackPressed();
    }
}
