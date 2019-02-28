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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;

/**
 * This class will search the internet for book details based on either
 * a manually provided ISBN, or a scanned ISBN.
 * Alternatively, it will search based on Author/Title.
 * <p>
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 * <p>
 * ASIN stands for Amazon Standard Identification Number.
 * Every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN-10 number, but for all other products a new ASIN
 * is created when the item is uploaded to their catalogue.
 */
public class BookSearchActivity
        extends BaseActivityWithTasks {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        //noinspection ConstantConditions
        String tag = extras.getString(UniqueId.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG);
        if (null == getSupportFragmentManager().findFragmentByTag(tag)) {
            Fragment frag = createFragment(tag);
            frag.setArguments(getIntent().getExtras());
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .add(R.id.main_fragment, frag, tag)
                    .commit();
        }

    }

    /**
     * @param tag for the required fragment
     *
     * @return a new fragment instance from the tag.
     */
    private Fragment createFragment(@NonNull final String tag) {
        if (BookSearchByIsbnFragment.TAG.equals(tag)) {
            return new BookSearchByIsbnFragment();
        } else if (BookSearchByTextFragment.TAG.equals(tag)) {
            return new BookSearchByTextFragment();
        } else {
            throw new IllegalArgumentException("tag=" + tag);
        }
    }
}
