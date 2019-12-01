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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAdminFragment;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ActivityResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

/**
 * Hosting activity for admin functions.
 */
public class AdminActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String tag = getIntent().getStringExtra(UniqueId.BKEY_FRAGMENT_TAG);
        if (tag == null) {
            tag = ImportExportFragment.TAG;
        }

        replaceFragment(R.id.main_fragment, tag);
    }

    /**
     * Create a fragment based on the given tag.
     *
     * @param containerViewId to receive the fragment
     * @param tag             for the required fragment
     */
    private void replaceFragment(@SuppressWarnings("SameParameterValue")
                                 @IdRes final int containerViewId,
                                 @NonNull final String tag) {
        switch (tag) {
            case ImportExportFragment.TAG:
                replaceFragment(containerViewId, ImportExportFragment.class, tag);
                return;

            case GoodreadsAdminFragment.TAG:
                replaceFragment(containerViewId, GoodreadsAdminFragment.class, tag);
                return;

            default:
                throw new UnexpectedValueException(tag);
        }
    }

    @Override
    public void onBackPressed() {
        ActivityResultDataModel model = new ViewModelProvider(this).get(ResultDataModel.class);
        setResult(Activity.RESULT_OK, model.getActivityResultData());
        super.onBackPressed();
    }
}
