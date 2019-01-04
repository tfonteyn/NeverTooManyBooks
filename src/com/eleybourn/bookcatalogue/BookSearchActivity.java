/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.debug.Tracker;

import java.util.Objects;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * This class will search the internet for book details based on either
 * a manually provided ISBN, or a scanned ISBN.
 * Alternatively, it will search based on Author/Title.
 *
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 *
 * ASIN stands for Amazon Standard Identification Number.
 * Every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN-10 number, but for all other products a new ASIN
 * is created when the item is uploaded to their catalogue.
 */
public class BookSearchActivity
    extends BaseActivityWithTasks {

    public static final String REQUEST_BKEY_BY = "by";
    public static final String BY_ISBN = "isbn";
    public static final String BY_TEXT = "text";
    public static final String BY_SCAN = "scan";

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        Objects.requireNonNull(extras);
        String searchBy = extras.getString(REQUEST_BKEY_BY, BY_ISBN);

        Fragment frag;
        switch (searchBy) {
            case BY_SCAN:
            case BY_ISBN:
                frag = new BookSearchByIsbnFragment();
                break;
            case BY_TEXT:
                frag = new BookSearchByTextFragment();
                break;
            default:
                throw new IllegalStateException();
        }
        frag.setArguments(extras);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.main_fragment, frag)
            .commit();

        Tracker.exitOnCreate(this);
    }
}
