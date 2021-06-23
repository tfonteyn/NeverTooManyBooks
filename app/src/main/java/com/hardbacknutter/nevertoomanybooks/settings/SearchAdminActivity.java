/*
 * @Copyright 2018-2021 HardBackNutter
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

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityAdminSearchBinding;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

public class SearchAdminActivity
        extends BaseActivity {

    private TabAdapter mTabAdapter;

    private SearchAdminViewModel mVm;

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

        mVm = new ViewModelProvider(this).get(SearchAdminViewModel.class);
        mVm.init(getIntent().getExtras());

        final List<Site.Type> types = mVm.getTypes();
        mTabAdapter = new TabAdapter(this, types);

        if (types.size() == 1) {
            setSubtitle(types.get(0).getLabelId());
            mVb.tabPanel.setVisibility(View.GONE);
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
        final boolean hasSites = mVm.validate();
        if (hasSites) {
            if (mVm.getTypes().size() == 1) {
                // single-list is NOT persisted, just returned for temporary usage.
                final Site.Type type = mVm.getTypes().get(0);
                final Intent resultIntent = new Intent()
                        .putExtra(type.getBundleKey(), mVm.getList(type));
                setResult(Activity.RESULT_OK, resultIntent);

            } else {
                mVm.persist();
            }
            super.onBackPressed();

        } else {
            Snackbar.make(mVb.pager, R.string.warning_enable_at_least_1_website,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Encapsulate all the tabs that will be shown.
     */
    private static class TabAdapter
            extends FragmentStateAdapter {

        @NonNull
        private final List<Site.Type> mTypes;

        TabAdapter(@NonNull final FragmentActivity container,
                   @NonNull final List<Site.Type> types) {
            super(container);
            mTypes = types;
        }

        @Override
        public int getItemCount() {
            return mTypes.size();
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            final Fragment fragment = new SearchOrderFragment();
            final Bundle args = new Bundle(1);
            args.putParcelable(SearchOrderFragment.BKEY_TYPE, mTypes.get(position));
            fragment.setArguments(args);
            return fragment;
        }


        @StringRes
        int getTabTitle(final int position) {
            return mTypes.get(position).getLabelId();
        }
    }
}
