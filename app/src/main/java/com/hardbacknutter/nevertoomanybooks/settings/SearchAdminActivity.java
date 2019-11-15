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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * USE scenario is (2019-07-05) on a per-page basis only. Hence we 'use' the current displayed list.
 */
public class SearchAdminActivity
        extends BaseActivity {

    private static final String TAG = "SearchAdminActivity";

    private ViewPager2 mViewPager;
    private TabAdapter mTabAdapter;

    private boolean mPersist;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_tabs;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        @SearchAdminModel.Tabs
        int tabToShow = getIntent().getIntExtra(SearchAdminModel.BKEY_TABS_TO_SHOW,
                                                SearchAdminModel.SHOW_ALL_TABS);

        mViewPager = findViewById(R.id.tab_fragment);
        TabLayout tabLayout = findViewById(R.id.tab_panel);

        switch (tabToShow) {
            case SearchAdminModel.TAB_BOOKS: {
                setTitle(R.string.lbl_books);
                mPersist = false;
                tabLayout.setVisibility(View.GONE);
                break;
            }
            case SearchAdminModel.TAB_COVERS: {
                setTitle(R.string.lbl_cover);
                mPersist = false;
                tabLayout.setVisibility(View.GONE);
                break;
            }
            case SearchAdminModel.SHOW_ALL_TABS:
            default: {
                setTitle(R.string.lbl_websites);
                mPersist = true;
                break;
            }
        }

        mTabAdapter = new TabAdapter(this, tabToShow, mPersist, getIntent().getExtras());
        mViewPager.setAdapter(mTabAdapter);
        new TabLayoutMediator(tabLayout, mViewPager, (tab, position) ->
                tab.setText(getString(mTabAdapter.getTabTitle(position))))
                .attach();
    }

    @Override
    public void onBackPressed() {
        // When in 'real' settings mode, we always check the book website list.
        // When in 'use' scenario, we check the list which we're editing.
        boolean noSitesEnabled;
        SearchAdminModel model = new ViewModelProvider(this).get(SearchAdminModel.class);
        if (mPersist) {
            model.persist(this);
            //noinspection ConstantConditions
            noSitesEnabled = SearchSites.getEnabledSites(model.getBooks()) == 0;
        } else {
            Intent data = new Intent()
                    .putExtra(SearchSites.BKEY_SEARCH_SITES_BOOKS, model.getList());
            setResult(Activity.RESULT_OK, data);
            noSitesEnabled = SearchSites.getEnabledSites(model.getList()) == 0;
        }

        if (noSitesEnabled) {
            UserMessage.show(mViewPager, R.string.warning_enable_at_least_1_website);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_RESET:
                switch (mViewPager.getCurrentItem()) {
                    case SearchAdminModel.TAB_BOOKS:
                        SearchSites.resetList(this, SearchSites.ListType.Data);
                        break;

                    case SearchAdminModel.TAB_COVERS:
                        SearchSites.resetList(this, SearchSites.ListType.Covers);
                        break;

                    default:
                        Logger.warnWithStackTrace(this, TAG,
                                                  "item=" + mViewPager.getCurrentItem());
                        break;
                }
                // not ideal, but it will do
                recreate();

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {

        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.btn_reset)
            .setIcon(R.drawable.ic_undo)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Encapsulate all the tabs that can be shown.
     * <p>
     * Limited to showing a single tab, or all tabs.
     */
    private static class TabAdapter
            extends FragmentStateAdapter {

        private final int count;
        private final int mTabsToShow;
        private final boolean mPersist;
        @Nullable
        private final Bundle mArgs;

        TabAdapter(@NonNull final FragmentActivity container,
                   @SearchAdminModel.Tabs final int tabsToShow,
                   final boolean persist,
                   @Nullable final Bundle args) {
            super(container);
            mTabsToShow = tabsToShow;
            mPersist = persist;
            mArgs = args;
            count = Integer.bitCount(mTabsToShow);
        }

        @Override
        public int getItemCount() {
            return count;
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {

            int tab;
            if (count == 1) {
                if ((mTabsToShow & SearchAdminModel.TAB_BOOKS) != 0) {
                    tab = SearchAdminModel.TAB_BOOKS;
                } else if ((mTabsToShow & SearchAdminModel.TAB_COVERS) != 0) {
                    tab = SearchAdminModel.TAB_COVERS;
                } else {
                    throw new IllegalStateException("no active tabs set");
                }
            } else {
                switch (position) {
                    case 0:
                        tab = SearchAdminModel.TAB_BOOKS;
                        break;

                    case 1:
                        tab = SearchAdminModel.TAB_COVERS;
                        break;

                    default:
                        throw new UnexpectedValueException(position);
                }
            }

            Bundle args = new Bundle();
            if (mArgs != null) {
                // add original args first, we'll overwrite if needed.
                args.putAll(mArgs);
            }
            args.putInt(SearchAdminModel.BKEY_TABS_TO_SHOW, tab);
            args.putBoolean(SearchAdminModel.BKEY_PERSIST, mPersist);
            Fragment fragment = new SearchOrderFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @StringRes
        int getTabTitle(final int position) {

            if (count == 1) {
                if ((mTabsToShow & SearchAdminModel.TAB_BOOKS) != 0) {
                    return R.string.lbl_books;
                } else if ((mTabsToShow & SearchAdminModel.TAB_COVERS) != 0) {
                    return R.string.lbl_covers;
                } else {
                    throw new IllegalStateException("no active tabs set");
                }
            }

            switch (position) {
                case 0:
                    return R.string.lbl_books;
                case 1:
                    return R.string.lbl_covers;
                default:
                    throw new UnexpectedValueException(position);
            }

        }
    }
}
