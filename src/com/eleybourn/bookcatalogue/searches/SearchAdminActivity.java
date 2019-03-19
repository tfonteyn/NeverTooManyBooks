package com.eleybourn.bookcatalogue.searches;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.google.android.material.tabs.TabLayout;

public class SearchAdminActivity
        extends BaseActivity {

    /**
     * Optional: set to one of the {@link AdminSearchOrderFragment} tabs,
     * if we should *only* show that tab, and NOT save the new setting (i.e. the "use" scenario).
     */
    public static final String REQUEST_BKEY_TAB = "tab";
    /** Bundle key with the resulting site usage for the "use" scenario. */
    public static final String RESULT_SEARCH_SITES = "resultSearchSites";

    public static final int TAB_ORDER = 0;
    public static final int TAB_COVER_ORDER = 1;
    private static final int TAB_HOSTS = 2;
    private static final int SHOW_ALL_TABS = -1;

    private ViewPagerAdapter mAdapter;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_search;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        int requestedTab = args == null ? SHOW_ALL_TABS
                                        : args.getInt(REQUEST_BKEY_TAB, SHOW_ALL_TABS);

        ViewPager viewPager = findViewById(R.id.tab_fragment);
        TabLayout tabLayout = findViewById(R.id.tab_panel);
        mAdapter = new ViewPagerAdapter(getSupportFragmentManager());

        switch (requestedTab) {
            case TAB_ORDER:
                tabLayout.setVisibility(View.GONE);
                initSingleTab(AdminSearchOrderFragment.TAG + TAB_ORDER,
                              R.string.tab_lbl_search_site_order,
                              SearchSites.getSites());
                break;

            case TAB_COVER_ORDER:
                tabLayout.setVisibility(View.GONE);
                initSingleTab(AdminSearchOrderFragment.TAG + TAB_COVER_ORDER,
                              R.string.tab_lbl_search_site_cover_order,
                              SearchSites.getSitesForCoverSearches());
                break;

            default:
                initAllTabs();
                break;
        }

        // fire up the adapter.
        viewPager.setAdapter(mAdapter);
        tabLayout.setupWithViewPager(viewPager);
    }

    private void initSingleTab(@NonNull final String tag,
                               @StringRes final int titleId,
                               @NonNull final ArrayList<SearchSites.Site> list) {
        setTitle(titleId);
        mAdapter.add(new FragmentHolder(getSupportFragmentManager(),
                                        tag, getString(titleId)));

        Button confirmBtn = findViewById(R.id.confirm);
        // indicate to the user this is the 'use' scenario (instead of 'save')
        confirmBtn.setText(R.string.btn_use);
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                int sites = SearchSites.Site.SEARCH_ALL;
                for (SearchSites.Site site : list) {
                    sites = site.isEnabled() ? sites | site.id
                                             : sites & ~site.id;
                }
                Intent data = new Intent()
                        .putExtra(RESULT_SEARCH_SITES, sites);
                // don't commit any changes, we got data to use temporarily
                setResult(Activity.RESULT_OK, data);
                finish();
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                // cancel without checking 'dirty'... this is 'use' only.
                finish();
            }
        });
    }

    /**
     * The 'small' setup is for all tabs to show.
     */
    private void initAllTabs() {
        setTitle(R.string.search_internet);

        FragmentManager fm = getSupportFragmentManager();
        // add them in order! i.e. in the order the TAB_* constants are defined.
        mAdapter.add(new FragmentHolder(
                fm, AdminSearchOrderFragment.TAG + TAB_ORDER,
                getString(R.string.tab_lbl_search_site_order)));

        mAdapter.add(new FragmentHolder(
                fm, AdminSearchOrderFragment.TAG + TAB_COVER_ORDER,
                getString(R.string.tab_lbl_search_site_cover_order)));

        mAdapter.add(new FragmentHolder(
                fm, AdminHostsFragment.TAG, getString(R.string.tab_lbl_search_sites)));


        findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                if (isDirty()) {
                    //ENHANCE: compare this approach to what is used in EditBookFragment & children.
                    // Decide later...

                    ((AdminHostsFragment) mAdapter.getItem(TAB_HOSTS)).saveSettings();

                    ArrayList<SearchSites.Site> list;
                    list = ((AdminSearchOrderFragment)
                            mAdapter.getItem(TAB_ORDER)).getList();
                    if (list != null) {
                        SearchSites.setSearchOrder(list);
                    }

                    list = ((AdminSearchOrderFragment)
                            mAdapter.getItem(TAB_COVER_ORDER)).getList();
                    if (list != null) {
                        SearchSites.setCoverSearchOrder(list);
                    }
                }
                // no data to return
                setResult(Activity.RESULT_OK);
                finish();
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                finishIfClean(isDirty());
            }
        });
    }

    protected boolean isDirty() {
        return ((AdminSearchOrderFragment) mAdapter.getItem(TAB_ORDER)).isDirty()
                || ((AdminSearchOrderFragment) mAdapter.getItem(TAB_COVER_ORDER)).isDirty()
                || ((AdminHostsFragment) mAdapter.getItem(TAB_HOSTS)).isDirty();
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
            super(fm);
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

        public void add(@NonNull final FragmentHolder fragmentHolder) {
            mFragmentList.add(fragmentHolder);
        }

        @Override
        @NonNull
        public CharSequence getPageTitle(final int position) {
            return mFragmentList.get(position).title;
        }
    }

    private static class FragmentHolder {

        @NonNull
        final String tag;
        @NonNull
        String title;
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
            this.tag = tag;
            this.title = title;
            //noinspection ConstantConditions
            fragment = fm.findFragmentByTag(tag);
            if (fragment == null) {
                if (AdminHostsFragment.TAG.equals(tag)) {
                    fragment = new AdminHostsFragment();

                } else if (tag.equals(AdminSearchOrderFragment.TAG + TAB_ORDER)) {
                    fragment = new AdminSearchOrderFragment();
                    Bundle args = new Bundle();
                    args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES,
                                                SearchSites.getSites());
                    fragment.setArguments(args);

                } else if (tag.equals(AdminSearchOrderFragment.TAG + TAB_COVER_ORDER)) {
                    fragment = new AdminSearchOrderFragment();
                    Bundle args = new Bundle();
                    args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES,
                                                SearchSites.getSitesForCoverSearches());
                    fragment.setArguments(args);
                }
            }
        }
    }
}
