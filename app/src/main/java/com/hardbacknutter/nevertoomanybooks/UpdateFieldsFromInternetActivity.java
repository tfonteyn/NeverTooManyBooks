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

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivityWithTasks;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.UpdateFieldsModel;

/**
 * Searches the internet for book details based on ISBN or Author/Title.
 * Automatically updates fields of the book for which it finds new/extra data,
 */
public class UpdateFieldsFromInternetActivity
        extends BaseActivityWithTasks {

    private static final String TAG = "UpdateFields";

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleUtils.insanityCheck(this);

        replaceFragment(R.id.main_fragment, UpdateFieldsFragment.class, UpdateFieldsFragment.TAG);
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        if (requestCode == UniqueId.REQ_NAV_PANEL_SETTINGS) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                ArrayList<Site> sites = data.getParcelableArrayListExtra(
                        SearchSites.BKEY_SEARCH_SITES_BOOKS);
                if (sites != null) {
                    UpdateFieldsModel model =
                            new ViewModelProvider(this).get(UpdateFieldsModel.class);
                    model.setSearchSites(sites);
                }
            }
        }
        // passthrough
        super.onActivityResult(requestCode, resultCode, data);
    }
}
