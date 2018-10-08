package com.eleybourn.bookcatalogue.searches;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.Button;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;

import java.util.ArrayList;

public class SearchAdmin extends BookCatalogueActivity {

    private static final int TAB_HOSTS = 0;
    private static final int TAB_SEARCH_ORDER = 1;
    private static final int TAB_SEARCH_COVER_ORDER = 2;
    private TabLayout mTabLayout;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_search;
    }

    @Override
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

        Button confirmBtn = findViewById(R.id.confirm);
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {

                Holder holder = (Holder) mTabLayout.getTabAt(TAB_HOSTS).getTag();
                AdminHostsFragment ahf = ((AdminHostsFragment) holder.fragment);
                ahf.saveState();

                holder = (Holder) mTabLayout.getTabAt(TAB_SEARCH_ORDER).getTag();
                AdminSearchOrderFragment asf = ((AdminSearchOrderFragment) holder.fragment);
                ArrayList<SearchManager.SearchSite> list = asf.getList();
                if (list != null) {
                    SearchManager.setSearchOrder(list);
                }
                holder = (Holder) mTabLayout.getTabAt(TAB_SEARCH_COVER_ORDER).getTag();
                asf = ((AdminSearchOrderFragment) holder.fragment);
                list = asf.getList();
                if (list != null) {
                    SearchManager.setCoverSearchOrder(list);
                }
                finish();
            }
        });

        Button cancelBtn = findViewById(R.id.cancel);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                finish();
            }
        });
    }

    private void replaceTab(@NonNull final Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, fragment)
                .commit();
    }

    private class TabListener implements TabLayout.OnTabSelectedListener {
        @Override
        public void onTabSelected(final TabLayout.Tab tab) {
            Holder holder = (Holder) tab.getTag();
            replaceTab(holder.fragment);
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
