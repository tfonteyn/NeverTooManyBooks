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
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookBaseFragmentModel;

/**
 * The hosting activity for editing a book.
 */
public class EditBookActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav_tabs;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setNavigationItemVisibility(R.id.nav_manage_bookshelves, true);

        replaceFragment(R.id.main_fragment, EditBookFragment.class, EditBookFragment.TAG);
    }

    /**
     * When the user clicks 'back/up', check if we're clean to leave.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public void onBackPressed() {
        // delete any orphaned temporary cover file
        StorageUtils.deleteFile(StorageUtils.getTempCoverFile());

        BookBaseFragmentModel model = new ViewModelProvider(this).get(BookBaseFragmentModel.class);

        if (model.isDirty()) {
            // If the user clicks 'exit', we finish()
            StandardDialogs.unsavedEditsDialog(this, () -> {
                // STILL send an OK and result data!
                // The result data will contain the re-position book id.
                setResult(Activity.RESULT_OK, model.getActivityResultData());
                finish();
            });
            return;
        }

        setResult(Activity.RESULT_OK, model.getActivityResultData());
        super.onBackPressed();
    }
}
