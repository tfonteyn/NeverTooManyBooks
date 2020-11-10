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

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityAdminSearchBinding;
import com.hardbacknutter.nevertoomanybooks.searches.Site;

public class SearchAdminActivity
        extends BaseActivity {

    private TabAdapter mTabAdapter;

    private SearchAdminViewModel mModel;

    /** View Binding. */
    private ActivityAdminSearchBinding mVb;

    @Override
    protected void onSetContentView() {
        mVb = ActivityAdminSearchBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(SearchAdminViewModel.class);
        mModel.init(getIntent().getExtras());

        if (mModel.isSingleListMode()) {
            final Site.Type type = mModel.getType();
            //noinspection ConstantConditions
            getSupportActionBar().setSubtitle(type.getLabelId());
            mVb.tabPanel.setVisibility(View.GONE);

            mTabAdapter = new TabAdapter(this, type);
        } else {
            mTabAdapter = new TabAdapter(this, null);
        }

        //FIXME: workaround for what seems to be a bug with FragmentStateAdapter#createFragment
        // and its re-use strategy.
        mVb.pager.setOffscreenPageLimit(mTabAdapter.getItemCount());

        mVb.pager.setAdapter(mTabAdapter);
        new TabLayoutMediator(mVb.tabPanel, mVb.pager, (tab, position) ->
                tab.setText(getString(mTabAdapter.getTabTitle(position))))
                .attach();
    }

    @Override
    public void onBackPressed() {
        final boolean hasSites = mModel.validate();
        if (hasSites) {
            if (mModel.isSingleListMode()) {
                // single-list is NOT persisted, just returned for temporary usage.
                final Intent resultData = new Intent()
                        .putExtra(mModel.getType().getBundleKey(), mModel.getList());
                setResult(Activity.RESULT_OK, resultData);

            } else {
                mModel.persist(this);
            }
            super.onBackPressed();

        } else {
            Snackbar.make(mVb.pager, R.string.warning_enable_at_least_1_website,
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

        /**
         * If in single-list mode, the type of that list,
         * or {@code null} in all-lists mode.
         */
        @Nullable
        private final Site.Type mSingleListType;

        TabAdapter(@NonNull final FragmentActivity container,
                   @Nullable final Site.Type singleListType) {
            super(container);
            mSingleListType = singleListType;
        }

        @Override
        public int getItemCount() {
            if (mSingleListType == null) {
                return Site.Type.values().length;
            } else {
                return 1;
            }
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            final Fragment fragment = new SearchOrderFragment();
            final Bundle args = new Bundle(1);
            args.putParcelable(SearchOrderFragment.BKEY_TYPE, toType(position));
            fragment.setArguments(args);
            return fragment;
        }


        @StringRes
        int getTabTitle(final int position) {
            return toType(position).getLabelId();
        }

        @NonNull
        private Site.Type toType(final int position) {
            // showing a single tab ?
            if (mSingleListType != null) {
                return mSingleListType;
            } else {
                return Site.Type.values()[position];
            }
        }
    }
}
