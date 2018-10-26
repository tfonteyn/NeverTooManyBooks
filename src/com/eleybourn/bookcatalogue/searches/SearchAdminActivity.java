package com.eleybourn.bookcatalogue.searches;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.view.View;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;

import java.util.ArrayList;

public class SearchAdminActivity extends BaseActivity {

    private static final int TAB_HOSTS = 0;
    private static final int TAB_SEARCH_ORDER = 1;
    private static final int TAB_SEARCH_COVER_ORDER = 2;
    private TabLayout mTabLayout;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_search;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.search_internet);

        mTabLayout = findViewById(R.id.tab_panel);
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
                ahf.saveState();

                //noinspection ConstantConditions
                holder = (Holder) mTabLayout.getTabAt(TAB_SEARCH_ORDER).getTag();
                //noinspection ConstantConditions
                asf = (AdminSearchOrderFragment) holder.fragment;
                ArrayList<SearchManager.SearchSite> list = asf.getList();
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
                finish();
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                finish();
            }
        });
    }

    private class TabListener implements TabLayout.OnTabSelectedListener {
        @Override
        public void onTabSelected(@NonNull final TabLayout.Tab tab) {
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
