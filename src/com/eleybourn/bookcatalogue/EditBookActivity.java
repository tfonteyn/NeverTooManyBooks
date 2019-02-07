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
import androidx.fragment.app.FragmentTransaction;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * The hosting activity for editing a book.
 */
public class EditBookActivity
        extends BaseActivity {

    /** universal flag used to indicate something was changed and not saved (yet). */
    private boolean mIsDirty;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        EditBookFragment frag = new EditBookFragment();
        frag.setArguments(extras);

        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.main_fragment, frag, EditBookFragment.TAG)
                .commit();
    }

    /**
     * @return <tt>true</tt> if the data in this activity was changed and should be saved.
     */
    public boolean isDirty() {
        return mIsDirty;
    }

    /**
     * @param isDirty set to <tt>true</tt> if the data in this activity was changed.
     */
    public void setDirty(final boolean isDirty) {
        mIsDirty = isDirty;
    }

    /**
     * Check if edits need saving.
     * If they don't, simply finish the activity,
     * otherwise ask the user.
     */
    public void finishIfClean() {
        if (mIsDirty) {
            StandardDialogs.showConfirmUnsavedEditsDialog(
                    this,
                    /* only runs if user clicks 'exit' */
                    new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
        } else {
            finish();
        }
    }

    /**
     * When the user clicks 'back/up', check if we're clean to leave.
     */
    @Override
    @CallSuper
    public void onBackPressed() {
        // delete any leftover temporary thumbnails
        StorageUtils.deleteTempCoverFile();

        finishIfClean();
    }
}
