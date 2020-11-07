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

import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ExportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportFragment;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAdminFragment;

/**
 * Hosting activity for generic fragments <strong>without</strong>
 * a DrawerLayout/NavigationView side panel.
 */
public class HostingActivity
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

        switch (tag) {
            case AuthorWorksFragment.TAG:
                addFirstFragment(R.id.main_fragment, AuthorWorksFragment.class, tag);
                return;

            case FTSSearchFragment.TAG:
                addFirstFragment(R.id.main_fragment, FTSSearchFragment.class, tag);
                return;

            case EditBookshelvesFragment.TAG:
                addFirstFragment(R.id.main_fragment, EditBookshelvesFragment.class, tag);
                return;

            case ImportFragment.TAG:
                addFirstFragment(R.id.main_fragment, ImportFragment.class, tag);
                return;

            case ExportFragment.TAG:
                addFirstFragment(R.id.main_fragment, ExportFragment.class, tag);
                return;

            case GoodreadsAdminFragment.TAG:
                addFirstFragment(R.id.main_fragment, GoodreadsAdminFragment.class, tag);
                return;

            case AboutFragment.TAG:
                addFirstFragment(R.id.main_fragment, AboutFragment.class, tag);
                return;

            default:
                throw new IllegalArgumentException(tag);
        }
    }
}
