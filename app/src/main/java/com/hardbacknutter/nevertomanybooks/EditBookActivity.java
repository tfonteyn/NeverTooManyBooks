/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertomanybooks.viewmodels.BookBaseFragmentModel;

/**
 * The hosting activity for editing a book.
 * <p>
 * <b>Note:</b> eventually these 'hosting' activities are meant to go. The idea is to have ONE
 * hosting/main activity, which swaps in fragments as needed.
 */
public class EditBookActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_book;
    }

    @Override
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

        BookBaseFragmentModel model = new ViewModelProvider(this)
                                                        .get(BookBaseFragmentModel.class);

        Intent data = new Intent().putExtra(DBDefinitions.KEY_PK_ID, model.getBook().getId());
        //ENHANCE: global changes not detected, so assume they happened.
        setResult(Activity.RESULT_OK, data);

        if (model.isDirty()) {
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
