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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;

/**
 * USE scenario is (2019-07-05) on a per-page basis only. Hence we 'use' the current displayed list.
 * SAVE: always saves all tabs displayed.
 * RESET: only resets the currently displayed tab.
 */
public class SearchAdminActivity
        extends BaseActivity {

    /**
     * Optional: set to one of the {@link SearchOrderFragment} tabs,
     * if we should *only* show that tab, and NOT save the new setting (i.e. the "use" scenario).
     */
    public static final String REQUEST_BKEY_TAB = "tab";
    /** Bundle key with the resulting site usage for the "use" scenario. */
    public static final String RESULT_SEARCH_SITES = "resultSearchSites";

    public static final int TAB_ORDER = 0;
    public static final int TAB_COVER_ORDER = 1;
    private static final int SHOW_ALL_TABS = -1;

    private ViewPager mViewPager;
    private ViewPagerAdapter mAdapter;

    private boolean mIsDirty;

    private boolean mUseScenario;

    public void setDirty(final boolean dirty) {
        mIsDirty = dirty;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_tabs;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int requestedTab = getIntent().getIntExtra(REQUEST_BKEY_TAB, SHOW_ALL_TABS);

        mViewPager = findViewById(R.id.tab_fragment);
        TabLayout tabLayout = findViewById(R.id.tab_panel);
        mAdapter = new ViewPagerAdapter(getSupportFragmentManager());

        FragmentManager fm = getSupportFragmentManager();
        switch (requestedTab) {
            case TAB_ORDER: {
                setTitle(R.string.lbl_books);
                mUseScenario = true;
                tabLayout.setVisibility(View.GONE);
                mAdapter.add(new FragmentHolder(fm, SearchOrderFragment.TAG + TAB_ORDER,
                                                getString(R.string.lbl_books)));
                break;
            }
            case TAB_COVER_ORDER: {
                setTitle(R.string.lbl_cover);
                mUseScenario = true;
                tabLayout.setVisibility(View.GONE);
                mAdapter.add(new FragmentHolder(fm, SearchOrderFragment.TAG + TAB_COVER_ORDER,
                                                getString(R.string.lbl_cover)));
                break;
            }
            default: {
                // show both
                setTitle(R.string.menu_add_book_by_internet_search);
                mUseScenario = false;
                // add them in order! i.e. in the order the TAB_* constants are defined.
                mAdapter.add(new FragmentHolder(fm, SearchOrderFragment.TAG + TAB_ORDER,
                                                getString(R.string.lbl_books)));

                mAdapter.add(new FragmentHolder(fm, SearchOrderFragment.TAG + TAB_COVER_ORDER,
                                                getString(R.string.lbl_covers)));
                break;
            }
        }

        // fire up the adapter.
        mViewPager.setAdapter(mAdapter);
        tabLayout.setupWithViewPager(mViewPager);
    }

    @Override
    public void onBackPressed() {
        if (mUseScenario) {
            doUse();
        } else {
            doSave();
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_RESET:
                switch (mViewPager.getCurrentItem()) {
                    case TAB_ORDER:
                        SearchSites.resetSearchOrder(this);
                        ((SearchOrderFragment) mAdapter.getItem(TAB_ORDER))
                                .setList(SearchSites.getSites());
                        break;

                    case TAB_COVER_ORDER:
                        SearchSites.resetCoverSearchOrder(this);
                        ((SearchOrderFragment) mAdapter.getItem(TAB_COVER_ORDER))
                                .setList(SearchSites.getSitesForCoverSearches());
                        break;

                    default:
                        Logger.warnWithStackTrace(this, this,
                                                  "item=" + mViewPager.getCurrentItem());
                        break;
                }
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
     * Prepares and sets the activity result.
     */
    private void doUse() {

        int sites = SearchSites.SEARCH_ALL;
        ArrayList<Site> list = ((SearchOrderFragment)
                                        mAdapter.getItem(mViewPager.getCurrentItem())).getList();
        //noinspection ConstantConditions
        for (Site site : list) {
            if (site.isEnabled()) {
                // add the site
                sites = sites | site.id;
            } else {
                // remove the site
                sites = sites & ~site.id;
            }
        }
        Intent data = new Intent().putExtra(RESULT_SEARCH_SITES, sites);
        // don't commit any changes, we got data to use temporarily
        setResult(Activity.RESULT_OK, data);
    }

    /**
     * Saves the settings & sets the activity result.
     */
    private void doSave() {

        if (mIsDirty) {
            ArrayList<Site> list;
            //ENHANCE: compare this approach to what is used in EditBookFragment & children.
            // Decide later...
            list = ((SearchOrderFragment) mAdapter.getItem(TAB_ORDER)).getList();
            if (list != null) {
                SearchSites.setSearchOrder(this, list);
            }

            list = ((SearchOrderFragment) mAdapter.getItem(TAB_COVER_ORDER)).getList();
            if (list != null) {
                SearchSites.setCoverSearchOrder(this, list);
            }
        }

        // no data to return
        setResult(Activity.RESULT_OK);
    }

    private static class ViewPagerAdapter
            extends FragmentPagerAdapter {

        private final List<FragmentHolder> mFragmentList = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param fm FragmentManager
         */
        ViewPagerAdapter(@NonNull final FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        @NonNull
        public Fragment getItem(final int position) {
            return mFragmentList.get(position).fragment;
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        @Override
        @NonNull
        public CharSequence getPageTitle(final int position) {
            return mFragmentList.get(position).title;
        }

        void add(@NonNull final FragmentHolder fragmentHolder) {
            mFragmentList.add(fragmentHolder);
        }
    }

    private static class FragmentHolder {

        @NonNull
        final String title;
        @NonNull
        Fragment fragment;

        /**
         * Constructor.
         *
         * @param fm FragmentManager
         */
        FragmentHolder(@NonNull final FragmentManager fm,
                       @NonNull final String tag,
                       @NonNull final String title) {
            this.title = title;
            //noinspection ConstantConditions
            fragment = fm.findFragmentByTag(tag);
            if (fragment == null) {

                ArrayList<Site> list;
                if ((SearchOrderFragment.TAG + TAB_ORDER).equals(tag)) {
                    list = SearchSites.getSites();
                } else /* if (t.equals(SearchOrderFragment.TAG + TAB_COVER_ORDER)) */ {
                    list = SearchSites.getSitesForCoverSearches();
                }

                Bundle args = new Bundle();
                args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES, list);
                fragment = new SearchOrderFragment();
                fragment.setArguments(args);
            }
        }
    }
}
