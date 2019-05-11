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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.viewmodels.BookBaseFragmentModel;

/**
 * The hosting activity for editing a book.
 * <p>
 * Note: eventually these 'hosting' activities are meant to go. The idea is to have ONE
 * hosting/main activity, which swaps in fragments as needed.
 */
public class EditBookActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_book;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(EditBookFragment.TAG) == null) {
            Fragment frag = new EditBookFragment();
            frag.setArguments(getIntent().getExtras());
            fm.beginTransaction()
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              .replace(R.id.main_fragment, frag, EditBookFragment.TAG)
              .commit();
        }
    }

    /**
     * When the user clicks 'back/up', check if we're clean to leave.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public void onBackPressed() {
        // delete any leftover temporary thumbnails
        StorageUtils.deleteTempCoverFile();

        BookBaseFragmentModel bookBaseFragmentModel = ViewModelProviders.of(this).get(
                BookBaseFragmentModel.class);

        Intent data = new Intent().putExtra(DBDefinitions.KEY_ID,
                                            bookBaseFragmentModel.getBook().getId());
        //ENHANCE: global changes not detected, so assume they happened.
        setResult(Activity.RESULT_OK, data);

        if (bookBaseFragmentModel.isDirty()) {
            StandardDialogs.showConfirmUnsavedEditsDialog(this, () -> {
                // runs when user clicks 'exit'
                setResult(Activity.RESULT_CANCELED);
                super.onBackPressed();
            });
        } else {
            setResult(Activity.RESULT_CANCELED);
            super.onBackPressed();
        }
    }
}
