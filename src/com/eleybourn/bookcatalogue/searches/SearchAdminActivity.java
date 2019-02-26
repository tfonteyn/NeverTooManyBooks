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
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.google.android.material.tabs.TabLayout;

public class SearchAdminActivity
        extends BaseActivity {

    /**
     * Optional: set to one of the {@link AdminSearchOrderFragment} tabs,
     * if we should *only* show that tab, and NOT save the new setting.
     */
    public static final String REQUEST_BKEY_TAB = "tab";

    public static final String RESULT_SEARCH_SITES = "resultSearchSites";
    public static final int TAB_ORDER = 1;
    public static final int TAB_COVER_ORDER = 2;
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
        super.onCreate(savedInstanceState);
        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();

        int requestedTab = args == null ? TAB_ALL : args.getInt(REQUEST_BKEY_TAB, TAB_ALL);

        mTabLayout = findViewById(R.id.tab_panel);

        switch (requestedTab) {
            case TAB_ORDER:
                mTabLayout.setVisibility(View.GONE);
                initSingleTab(R.string.tab_lbl_search_site_order,
                              SearchSites.getSites());
                break;

            case TAB_COVER_ORDER:
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
    }

    private void initSingleTab(@StringRes final int titleId,
                               @NonNull final ArrayList<SearchSites.Site> list) {
        setTitle(titleId);

        if (getSupportFragmentManager().findFragmentByTag(AdminSearchOrderFragment.TAG) == null) {
            Fragment frag = new AdminSearchOrderFragment();
            Bundle args = new Bundle();
            args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES, list);
            frag.setArguments(args);
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .add(R.id.main_fragment, frag, AdminSearchOrderFragment.TAG)
                    .commit();
        }

        Button confirmBtn = findViewById(R.id.confirm);
        // indicate to user this is not a 'save'
        confirmBtn.setText(R.string.btn_use);
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                int sites = SearchSites.Site.SEARCH_ALL;
                for (SearchSites.Site site : list) {
                    sites = site.isEnabled() ? sites | site.id
                                             : sites & ~site.id;
                }
                Intent data = new Intent();
                data.putExtra(RESULT_SEARCH_SITES, sites);
                // no changes committed, we got data to use temporarily
                setResult(Activity.RESULT_OK, data);
                finish();
            }
        });
    }

    /**
     * The 'small' setup is for all tabs to show.
     */
    private void initAllTabs() {
        setTitle(R.string.search_internet);

        FragmentHolder holder;
        Bundle args;
        TabLayout.Tab tab;

        //TAB_HOSTS
        mTabLayout.addOnTabSelectedListener(new TabListener());
        holder = new FragmentHolder(AdminHostsFragment.TAG);
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_search_sites).setTag(holder);
        mTabLayout.addTab(tab);

        //TAB_ORDER
        holder = new FragmentHolder(AdminSearchOrderFragment.TAG + TAB_ORDER);
        args = new Bundle();
        args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES,
                                    SearchSites.getSites());
        holder.fragment.setArguments(args);
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_search_site_order).setTag(holder);
        mTabLayout.addTab(tab);
        tab.select();

        //TAB_COVER_ORDER
        holder = new FragmentHolder(AdminSearchOrderFragment.TAG + TAB_COVER_ORDER);
        args = new Bundle();
        args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES,
                                    SearchSites.getSitesForCoverSearches());
        holder.fragment.setArguments(args);
        tab = mTabLayout.newTab().setText(R.string.tab_lbl_search_site_cover_order).setTag(holder);
        mTabLayout.addTab(tab);

        findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {

                //ENHANCE: compare this approach to what is used in EditBookFragment & children.
                // Decide later...

                FragmentHolder fragmentHolder;

                //noinspection ConstantConditions
                fragmentHolder = (FragmentHolder) mTabLayout.getTabAt(TAB_HOSTS).getTag();
                //noinspection ConstantConditions
                ((AdminHostsFragment) fragmentHolder.fragment).saveSettings();

                ArrayList<SearchSites.Site> list;

                //noinspection ConstantConditions
                fragmentHolder = (FragmentHolder) mTabLayout.getTabAt(TAB_ORDER).getTag();
                //noinspection ConstantConditions
                list = ((AdminSearchOrderFragment) fragmentHolder.fragment).getList();
                if (list != null) {
                    SearchSites.setSearchOrder(list);
                }

                //noinspection ConstantConditions
                fragmentHolder = (FragmentHolder) mTabLayout.getTabAt(TAB_COVER_ORDER).getTag();
                //noinspection ConstantConditions
                list = ((AdminSearchOrderFragment) fragmentHolder.fragment).getList();
                if (list != null) {
                    SearchSites.setCoverSearchOrder(list);
                }

                // no data to return
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
    }

    private class FragmentHolder {

        @NonNull
        final String tag;
        Fragment fragment;

        FragmentHolder(@NonNull final String tag) {
            this.tag = tag;
            fragment = getSupportFragmentManager().findFragmentByTag(tag);
            if (fragment == null) {
                if (AdminHostsFragment.TAG.equals(tag)) {
                    fragment = new AdminHostsFragment();

                } else if (tag.equals(AdminSearchOrderFragment.TAG + TAB_ORDER)
                        || tag.equals(AdminSearchOrderFragment.TAG + TAB_COVER_ORDER)) {
                    fragment = new AdminSearchOrderFragment();
                }
            }
        }
    }

    private class TabListener
            implements TabLayout.OnTabSelectedListener {

        @Override
        public void onTabSelected(@NonNull final TabLayout.Tab tab) {
            FragmentHolder fragmentHolder = (FragmentHolder) tab.getTag();
            //noinspection ConstantConditions
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    // use replace, as this is a tab bar
                    .replace(R.id.main_fragment, fragmentHolder.fragment, fragmentHolder.tag)
                    .commit();
        }

        @Override
        public void onTabUnselected(@NonNull final TabLayout.Tab tab) {
        }

        @Override
        public void onTabReselected(@NonNull final TabLayout.Tab tab) {
        }
    }
}
