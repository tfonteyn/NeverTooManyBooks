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
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesFragment;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

/**
 * Hosting activity for generic fragments <strong>without</strong>
 * a DrawerLayout/NavigationView side panel.
 */
public class HostingActivity
        extends BaseActivity {

    private static final String TAG = "HostingActivity";
    public static final String BKEY_FRAGMENT_TAG = TAG + ":fragment";

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String tag = Objects.requireNonNull(
                getIntent().getStringExtra(BKEY_FRAGMENT_TAG), "tag");

        switch (tag) {
            case SearchBookByIsbnFragment.TAG:
                addFirstFragment(R.id.main_fragment, SearchBookByIsbnFragment.class, tag);
                return;

            case SearchBookByTextFragment.TAG:
                addFirstFragment(R.id.main_fragment, SearchBookByTextFragment.class, tag);
                return;

            case SearchBookByExternalIdFragment.TAG:
                addFirstFragment(R.id.main_fragment, SearchBookByExternalIdFragment.class, tag);
                return;


            case SearchFtsFragment.TAG:
                addFirstFragment(R.id.main_fragment, SearchFtsFragment.class, tag);
                return;


            case AuthorWorksFragment.TAG:
                addFirstFragment(R.id.main_fragment, AuthorWorksFragment.class, tag);
                return;

            case SearchBookUpdatesFragment.TAG:
                addFirstFragment(R.id.main_fragment, SearchBookUpdatesFragment.class, tag);
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


            case PreferredStylesFragment.TAG:
                addFirstFragment(R.id.main_fragment, PreferredStylesFragment.class, tag);
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

    @Override
    protected void onDestroy() {
        // This is a good time to cleanup the cache.
        // Out of precaution we only trash jpg files
        AppDir.Cache.purge(App.getTaskContext(), true, file -> file.getName().endsWith(".jpg"));
        super.onDestroy();
    }

}
