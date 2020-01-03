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

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookBaseFragmentModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

/**
 * The hosting activity for editing a book.
 */
public class EditBookActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "EditBookActivity";

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
//                // update the search sites list.
//                SiteList siteList = data.getParcelableExtra(SiteList.Type.Data.getBundleKey());
//                if (siteList != null) {
//                    SearchCoordinator model =
//                            new ViewModelProvider(this).get(SearchCoordinator.class);
//                    model.setSiteList(siteList);
//                }

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
        BookBaseFragmentModel model = new ViewModelProvider(this).get(BookBaseFragmentModel.class);

        int backStackEntryCount = getSupportFragmentManager().getBackStackEntryCount();

        // When the user clicks 'back/up', and we're not at some sub fragment,
        // check if we're clean to leave.
        if (backStackEntryCount == 0 && model.isDirty()) {
            // If the user clicks 'exit', we finish()
            StandardDialogs.unsavedEditsDialog(this, () -> {
                // STILL send an OK and result data!
                // The result data will contain the re-position book id.
                setResult(Activity.RESULT_OK, model.getActivityResultData());
                // we're really leaving, clean up
                CoverHandler.deleteOrphanedCoverFiles(this);
                finish();
            });
            return;
        }

        setResult(Activity.RESULT_OK, model.getActivityResultData());
        if (backStackEntryCount == 0) {
            // we're really leaving, clean up
            CoverHandler.deleteOrphanedCoverFiles(this);
        }
        super.onBackPressed();
    }
}
