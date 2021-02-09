/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.os.Bundle;

import androidx.annotation.Nullable;

/**
 * Hosting activity for showing a book <strong>with</strong>
 * a DrawerLayout/NavigationView side panel.
 */
public class ShowBookActivity
        extends BaseActivity {

    public static final String TAG = "ShowBookActivity";

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_book_details);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addFirstFragment(R.id.main_fragment, ShowBookFragment.class,
                         ShowBookFragment.TAG);
    }

}
