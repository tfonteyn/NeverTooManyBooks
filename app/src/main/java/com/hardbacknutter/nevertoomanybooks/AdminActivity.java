/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAdminFragment;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

/**
 * Hosting activity for admin functions.
 */
public class AdminActivity
        extends BaseActivity {

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String tag = Objects.requireNonNull(
                getIntent().getStringExtra(BaseActivity.BKEY_FRAGMENT_TAG), "tag");
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
            case EditBookshelvesFragment.TAG:
                replaceFragment(containerViewId, EditBookshelvesFragment.class, tag);
                return;

            case ImportFragment.TAG:
                replaceFragment(containerViewId, ImportFragment.class, tag);
                return;

            case ExportFragment.TAG:
                replaceFragment(containerViewId, ExportFragment.class, tag);
                return;

            case GoodreadsAdminFragment.TAG:
                replaceFragment(containerViewId, GoodreadsAdminFragment.class, tag);
                return;

            default:
                throw new IllegalArgumentException(tag);
        }
    }

    @Override
    public void onBackPressed() {
        final ResultDataModel resultData = new ViewModelProvider(this).get(ResultDataModel.class);
        setResult(Activity.RESULT_OK, resultData.getResultIntent());
        super.onBackPressed();
    }
}
