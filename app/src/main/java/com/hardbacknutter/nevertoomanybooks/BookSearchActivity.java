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
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

/**
 * The hosting activity for searching for a book;
 * including (searching for and) updating fields for a book or set of books.
 */
public class BookSearchActivity
        extends BaseActivity {

    /** log tag. */
    private static final String TAG = "BookSearchActivity";

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String tag = getIntent().getStringExtra(UniqueId.BKEY_FRAGMENT_TAG);
        if (tag == null) {
            tag = BookSearchByIsbnFragment.TAG;
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
            case BookSearchByIsbnFragment.TAG:
                replaceFragment(containerViewId, BookSearchByIsbnFragment.class, tag);
                return;

            case BookSearchByTextFragment.TAG:
                replaceFragment(containerViewId, BookSearchByTextFragment.class, tag);
                return;

            case BookSearchByNativeIdFragment.TAG:
                replaceFragment(containerViewId, BookSearchByNativeIdFragment.class, tag);
                return;

            case UpdateFieldsFragment.TAG:
                replaceFragment(containerViewId, UpdateFieldsFragment.class, tag);
                return;

            default:
                throw new UnexpectedValueException(tag);
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        // Settings initiated from the navigation panel.
        if (requestCode == UniqueId.REQ_NAV_PANEL_SETTINGS) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // update the search sites list.
                SiteList siteList = data.getParcelableExtra(SiteList.Type.Data.getBundleKey());
                if (siteList != null) {
                    SearchCoordinator model =
                            new ViewModelProvider(this).get(SearchCoordinator.class);
                    model.setSiteList(siteList);
                }

                // Reset the scanner if it was changed.
                // Note this creates the scanner model even if it did not exist before.
                // Other then using memory, this is fine.
                // We assume if the user explicitly went to settings to change the scanner
                // they want to use it.
                if (data.getBooleanExtra(UniqueId.BKEY_SHOULD_INIT_SCANNER, false)) {
                    ScannerViewModel model =
                            new ViewModelProvider(this).get(ScannerViewModel.class);
                    model.resetScanner();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        ResultDataModel model = new ViewModelProvider(this).get(ResultDataModel.class);
        setResult(Activity.RESULT_OK, model.getActivityResultData());
        super.onBackPressed();
    }
}
