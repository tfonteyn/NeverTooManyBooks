package com.eleybourn.bookcatalogue.searches;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.Button;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;

import java.util.ArrayList;

public class SearchAdminActivity extends BaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_SEARCH_SITES;

    /**
     * Optional: set to one of the {@link AdminSearchOrderFragment} tabs
     * if we should *only* show that tab, and NOT save the new setting.
     */
    public static final String REQUEST_BKEY_TAB = "tab";

    public static final String RESULT_SEARCH_SITES = "resultSearchSites";

    private static final int TAB_ALL = -1;
    private static final int TAB_HOSTS = 0;
    public static final int TAB_SEARCH_ORDER = 1;
    public static final int TAB_SEARCH_COVER_ORDER = 2;

    private TabLayout mTabLayout;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_search;
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                initSingleTab(R.string.search_site_order, SearchManager.getSiteSearchOrder());
                break;

            case TAB_SEARCH_COVER_ORDER:
                mTabLayout.setVisibility(View.GONE);
                initSingleTab(R.string.search_site_cover_order, SearchManager.getSiteCoverSearchOrder());
                break;

            default:
                initAllTabs();
                break;
        }

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
    }

    private void initSingleTab(final @StringRes int titleId, final @NonNull ArrayList<SearchManager.SearchSite> list) {
        setTitle(titleId);

        Bundle args = new Bundle();
        args.putSerializable(SearchManager.BKEY_SEARCH_SITES, list);

        final AdminSearchOrderFragment frag = new AdminSearchOrderFragment();
        frag.setArguments(args);

        Button confirmBtn = findViewById(R.id.confirm);
        confirmBtn.setText(R.string.use);
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                int sites = SearchManager.SEARCH_ALL;
                ArrayList<SearchManager.SearchSite> list = frag.getList();
                if (list != null) {
                    for (SearchManager.SearchSite site : list) {
                        sites = (site.enabled ? sites | site.id : sites & ~site.id);
                    }
                }
                Intent data = new Intent();
                data.putExtra(RESULT_SEARCH_SITES, sites);
                setResult(Activity.RESULT_OK, data); /* 4266b81b-137b-4647-aa1c-8ec0fc8726e6 */
                finish();
            }
        });


        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, frag)
                .commit();
    }

    /**
     * The 'normal' setup is for all tabs to show.
     */
    private void initAllTabs() {
        setTitle(R.string.search_internet);

        mTabLayout.addOnTabSelectedListener(new TabListener());
        Holder holder = new Holder();
        holder.fragment = new AdminHostsFragment();
        TabLayout.Tab tab = mTabLayout.newTab().setText(R.string.search_sites).setTag(holder);
        mTabLayout.addTab(tab); //TAB_HOSTS

        Bundle args = new Bundle();
        holder = new Holder();
        holder.fragment = new AdminSearchOrderFragment();
        args.putSerializable(SearchManager.BKEY_SEARCH_SITES, SearchManager.getSiteSearchOrder());
        holder.fragment.setArguments(args);
        tab = mTabLayout.newTab().setText(R.string.search_site_order).setTag(holder);
        mTabLayout.addTab(tab); //TAB_SEARCH_ORDER
        tab.select();

        args = new Bundle();
        holder = new Holder();
        holder.fragment = new AdminSearchOrderFragment();
        args.putSerializable(SearchManager.BKEY_SEARCH_SITES, SearchManager.getSiteCoverSearchOrder());
        holder.fragment.setArguments(args);
        tab = mTabLayout.newTab().setText(R.string.search_site_cover_order).setTag(holder);
        mTabLayout.addTab(tab); //TAB_SEARCH_COVER_ORDER

        findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {

                AdminHostsFragment ahf;
                AdminSearchOrderFragment asf;

                //noinspection ConstantConditions
                Holder holder = (Holder) mTabLayout.getTabAt(TAB_HOSTS).getTag();
                //noinspection ConstantConditions
                ahf = (AdminHostsFragment) holder.fragment;
                ahf.saveSettings();

                ArrayList<SearchManager.SearchSite> list;

                //noinspection ConstantConditions
                holder = (Holder) mTabLayout.getTabAt(TAB_SEARCH_ORDER).getTag();
                //noinspection ConstantConditions
                asf = (AdminSearchOrderFragment) holder.fragment;
                list = asf.getList();
                if (list != null) {
                    SearchManager.setSearchOrder(list);
                }

                //noinspection ConstantConditions
                holder = (Holder) mTabLayout.getTabAt(TAB_SEARCH_COVER_ORDER).getTag();
                //noinspection ConstantConditions
                asf = (AdminSearchOrderFragment) holder.fragment;
                list = asf.getList();
                if (list != null) {
                    SearchManager.setCoverSearchOrder(list);
                }
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
    }

    private class TabListener implements TabLayout.OnTabSelectedListener {
        @Override
        public void onTabSelected(final @NonNull TabLayout.Tab tab) {
            Holder holder = (Holder) tab.getTag();
            //noinspection ConstantConditions
            Fragment frag = holder.fragment;
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment, frag)
                    .commit();
        }

        @Override
        public void onTabUnselected(final TabLayout.Tab tab) {
        }

        @Override
        public void onTabReselected(final TabLayout.Tab tab) {
        }
    }

    private class Holder {
        Fragment fragment;
    }
}
