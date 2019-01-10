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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;

public class SearchAdminActivity
        extends BaseActivity {

    /**
     * Optional: set to one of the {@link AdminSearchOrderFragment} tabs
     * if we should *only* show that tab, and NOT save the new setting.
     */
    public static final String REQUEST_BKEY_TAB = "tab";

    public static final String RESULT_SEARCH_SITES = "resultSearchSites";
    public static final int TAB_SEARCH_ORDER = 1;
    public static final int TAB_SEARCH_COVER_ORDER = 2;
    private static final int TAB_ALL = -1;
    private static final int TAB_HOSTS = 0;
    private TabLayout mTabLayout;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_search;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.search_internet);

        int requestedTab;
        if (savedInstanceState != null) {
            requestedTab = savedInstanceState.getInt(REQUEST_BKEY_TAB, TAB_ALL);
        } else {
            requestedTab = getIntent().getIntExtra(REQUEST_BKEY_TAB, TAB_ALL);
        }

        mTabLayout = findViewById(R.id.tab_panel);

        switch (requestedTab) {
            case TAB_SEARCH_ORDER:
                mTabLayout.setVisibility(View.GONE);
                initSingleTab(R.string.tab_lbl_search_site_order, SearchSites.getSites());
                break;

            case TAB_SEARCH_COVER_ORDER:
                mTabLayout.setVisibility(View.GONE);
                initSingleTab(R.string.tab_lbl_search_site_cover_order,
                              SearchSites.getSitesForCoverSearches());
                break;

            default:
                initAllTabs();
                break;
        }

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
        Tracker.exitOnCreate(this);
    }

    private void initSingleTab(@StringRes final int titleId,
                               @NonNull final ArrayList<SearchSites.Site> list) {
        setTitle(titleId);

        Bundle args = new Bundle();
        args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES, list);

        final AdminSearchOrderFragment frag = new AdminSearchOrderFragment();
        frag.setArguments(args);

        Button confirmBtn = findViewById(R.id.confirm);
        confirmBtn.setText(R.string.btn_use);
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                int sites = SearchSites.Site.SEARCH_ALL;
                ArrayList<SearchSites.Site> list = frag.getList();
                if (list != null) {
                    for (SearchSites.Site site : list) {
                        sites = (site.enabled ? sites | site.id : sites & ~site.id);
                    }
                }
                Intent data = new Intent();
                data.putExtra(RESULT_SEARCH_SITES, sites);
                // no changes committed, we got data to use temporarily
                setResult(Activity.RESULT_OK, data); /* 4266b81b-137b-4647-aa1c-8ec0fc8726e6 */
                finish();
            }
        });


        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment, frag)
                .commit();
    }

    /**
     * The 'small' setup is for all tabs to show.
     */
    private void initAllTabs() {
        setTitle(R.string.search_internet);

        mTabLayout.addOnTabSelectedListener(new TabListener());
        FragmentHolder fragmentHolder = new FragmentHolder();
        fragmentHolder.fragment = new AdminHostsFragment();
        fragmentHolder.tag = AdminHostsFragment.TAG;
        TabLayout.Tab tab = mTabLayout.newTab().setText(R.string.tab_lbl_search_sites).setTag(
                fragmentHolder);
        mTabLayout.addTab(tab); //TAB_HOSTS

        Bundle args = new Bundle();
        fragmentHolder = new FragmentHolder();
        fragmentHolder.fragment = new AdminSearchOrderFragment();
        fragmentHolder.tag = AdminSearchOrderFragment.TAG + TAB_SEARCH_ORDER;
        args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES, SearchSites.getSites());
        fragmentHolder.fragment.setArguments(args);
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_search_site_order).setTag(
                fragmentHolder);
        mTabLayout.addTab(tab); //TAB_SEARCH_ORDER
        tab.select();

        args = new Bundle();
        fragmentHolder = new FragmentHolder();
        fragmentHolder.fragment = new AdminSearchOrderFragment();
        fragmentHolder.tag = AdminSearchOrderFragment.TAG + TAB_SEARCH_COVER_ORDER;
        args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES,
                                    SearchSites.getSitesForCoverSearches());
        fragmentHolder.fragment.setArguments(args);
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_search_site_cover_order).setTag(
                fragmentHolder);
        mTabLayout.addTab(tab); //TAB_SEARCH_COVER_ORDER

        findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {

                //ENHANCE: compare this approach to what is used in EditBookFragment & children. Decide later...

                AdminHostsFragment ahf;
                AdminSearchOrderFragment asf;

                //noinspection ConstantConditions
                FragmentHolder fragmentHolder = (FragmentHolder) mTabLayout.getTabAt(
                        TAB_HOSTS).getTag();
                //noinspection ConstantConditions
                ahf = (AdminHostsFragment) fragmentHolder.fragment;
                ahf.saveSettings();

                ArrayList<SearchSites.Site> list;

                //noinspection ConstantConditions
                fragmentHolder = (FragmentHolder) mTabLayout.getTabAt(TAB_SEARCH_ORDER).getTag();
                //noinspection ConstantConditions
                asf = (AdminSearchOrderFragment) fragmentHolder.fragment;
                list = asf.getList();
                if (list != null) {
                    SearchSites.setSearchOrder(list);
                }

                //noinspection ConstantConditions
                fragmentHolder = (FragmentHolder) mTabLayout.getTabAt(
                        TAB_SEARCH_COVER_ORDER).getTag();
                //noinspection ConstantConditions
                asf = (AdminSearchOrderFragment) fragmentHolder.fragment;
                list = asf.getList();
                if (list != null) {
                    SearchSites.setCoverSearchOrder(list);
                }
                // no data to return
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
    }

    private static class FragmentHolder {

        Fragment fragment;
        String tag;
    }

    private class TabListener
            implements TabLayout.OnTabSelectedListener {

        @Override
        public void onTabSelected(@NonNull final TabLayout.Tab tab) {
            FragmentHolder fragmentHolder = (FragmentHolder) tab.getTag();
            //noinspection ConstantConditions
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_fragment, fragmentHolder.fragment, fragmentHolder.tag)
                    .commit();
        }

        @Override
        public void onTabUnselected(final TabLayout.Tab tab) {
        }

        @Override
        public void onTabReselected(final TabLayout.Tab tab) {
        }
    }
}
