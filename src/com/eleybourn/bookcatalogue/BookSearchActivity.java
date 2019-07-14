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
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.Objects;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;

/**
 * Searches the internet for book details based on:
 * - manually provided or scanned ISBN.
 * - Author/Title.
 * <p>
 * <b>Note:</b> eventually these 'hosting' activities are meant to go. The idea is to have ONE
 * hosting/main activity, which swaps in fragments as needed.
 */
public class BookSearchActivity
        extends BaseActivityWithTasks {

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getIntent().getExtras();
        Objects.requireNonNull(args);
        String tag = args.getString(UniqueId.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG);

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            Fragment frag = createFragment(tag);
            frag.setArguments(args);
            fm.beginTransaction()
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              .replace(R.id.main_fragment, frag, tag)
              .commit();
        }
    }

    /**
     * Create a fragment based on tag name.
     *
     * @param tag for the required fragment
     *
     * @return a new fragment instance.
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
