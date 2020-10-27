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
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.settings.BarcodePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

/**
 * The hosting activity for searching for a book;
 * including (searching for and) updating fields for a book or set of books.
 */
public class BookSearchActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BookSearchActivity";

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_main_nav);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String tag = getIntent().getStringExtra(BaseActivity.BKEY_FRAGMENT_TAG);
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

            case BookSearchByExternalIdFragment.TAG:
                replaceFragment(containerViewId, BookSearchByExternalIdFragment.class, tag);
                return;

            case UpdateFieldsFragment.TAG:
                replaceFragment(containerViewId, UpdateFieldsFragment.class, tag);
                return;

            default:
                throw new IllegalArgumentException(tag);
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
        if (requestCode == RequestCode.NAV_PANEL_SETTINGS) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // update the search sites list.
                final ArrayList<Site> sites =
                        data.getParcelableArrayListExtra(Site.Type.Data.getBundleKey());
                if (sites != null) {
                    final SearchCoordinator model =
                            new ViewModelProvider(this).get(SearchCoordinator.class);
                    model.setSiteList(sites);
                }

                // Get and reset the scanner if it was changed.
                if (data.getBooleanExtra(BarcodePreferenceFragment.BKEY_SCANNER_MODIFIED, false)) {
                    final ScannerViewModel scannerModel =
                            new ViewModelProvider(this).get(ScannerViewModel.class);
                    scannerModel.resetScanner();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        final ResultDataModel resultData = new ViewModelProvider(this).get(ResultDataModel.class);
        setResult(Activity.RESULT_OK, resultData.getResultIntent());
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        // This is a good time to cleanup the cache.
        // Out of precaution we only trash jpg files
        AppDir.Cache.purge(App.getTaskContext(), true, file -> file.getName().endsWith(".jpg"));
        super.onDestroy();
    }
}
