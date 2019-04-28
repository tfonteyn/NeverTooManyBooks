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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.entities.BookModel;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * The hosting activity for editing a book.
 *
 * Note: eventually these 'hosting' activities are meant to go. The idea is to have ONE
 * hosting/main activity, which swaps in fragments as needed.
 */
public class EditBookActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav_tabbar;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getSupportFragmentManager().findFragmentByTag(EditBookFragment.TAG)) {
            Fragment frag = new EditBookFragment();
            frag.setArguments(getIntent().getExtras());
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.main_fragment, frag, EditBookFragment.TAG)
                    .commit();
        }
    }

    /**
     * When the user clicks 'back/up', check if we're clean to leave.
     * <p>
     * <p>{@inheritDoc}
     */
    @Override
    public void onBackPressed() {
        // delete any leftover temporary thumbnails
        StorageUtils.deleteTempCoverFile();

        BookModel bookModel = ViewModelProviders.of(this).get(BookModel.class);
        finishIfClean(bookModel.isDirty());

        super.onBackPressed();
    }
}
