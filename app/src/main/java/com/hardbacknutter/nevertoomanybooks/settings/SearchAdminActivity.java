/*
 * @Copyright 2020 HardBackNutter
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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;

/**
 * USE scenario is (2019-07-05) on a per-page basis only. Hence we 'use' the current displayed list.
 */
public class SearchAdminActivity
        extends BaseActivity {

    private ViewPager2 mViewPager;
    private TabAdapter mTabAdapter;

    private SearchAdminModel mModel;

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_admin_search);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(SearchAdminModel.class);
        mModel.init(getIntent().getExtras());

        mViewPager = findViewById(R.id.pager);
        final TabLayout tabBarLayout = findViewById(R.id.tab_panel);

        if (mModel.getType() != null) {
            //noinspection ConstantConditions
            getSupportActionBar().setSubtitle(mModel.getType().getLabelId());
            tabBarLayout.setVisibility(View.GONE);
        }

        mTabAdapter = new TabAdapter(this, mModel.getType());

        //FIXME: workaround for what seems to be a bug with FragmentStateAdapter#createFragment
        // and its re-use strategy.
        mViewPager.setOffscreenPageLimit(mTabAdapter.getItemCount());

        mViewPager.setAdapter(mTabAdapter);
        new TabLayoutMediator(tabBarLayout, mViewPager, (tab, position) ->
                tab.setText(getString(mTabAdapter.getTabTitle(position))))
                .attach();
    }

    @Override
    public void onBackPressed() {
        final boolean hasSites;
        if (mModel.getType() == null) {
            hasSites = mModel.persist(this);

        } else {
            final SiteList siteList = mModel.getList(this, mModel.getType());
            final Intent resultData = new Intent()
                    .putExtra(mModel.getType().getBundleKey(), siteList);
            setResult(Activity.RESULT_OK, resultData);
            hasSites = !siteList.getEnabledSites().isEmpty();
        }

        if (hasSites) {
            super.onBackPressed();
        } else {
            Snackbar.make(mViewPager, R.string.warning_enable_at_least_1_website,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Encapsulate all the tabs that can be shown.
     * <p>
     * Limited to showing a single tab, or all tabs.
     */
    private static class TabAdapter
            extends FragmentStateAdapter {

        @Nullable
        private final SiteList.Type mType;

        TabAdapter(@NonNull final FragmentActivity container,
                   @Nullable final SiteList.Type type) {
            super(container);
            mType = type;
        }

        @Override
        public int getItemCount() {
            if (mType == null) {
                return SiteList.Type.values().length;
            } else {
                return 1;
            }
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            final Fragment fragment = new SearchOrderFragment();
            final Bundle args = new Bundle(1);
            args.putParcelable(SearchAdminModel.BKEY_LIST_TYPE, toType(position));
            fragment.setArguments(args);
            return fragment;
        }


        @StringRes
        int getTabTitle(final int position) {
            return toType(position).getLabelId();
        }

        @NonNull
        private SiteList.Type toType(final int position) {
            // showing a single tab ?
            if (mType != null) {
                return mType;
            } else {
                return SiteList.Type.values()[position];
            }
        }
    }
}
