/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAdminSearchBinding;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

public class SearchAdminFragment
        extends BaseFragment {

    public static final String TAG = "SearchAdminFragment";

    private TabAdapter tabAdapter;

    private SearchAdminViewModel vm;
    /** View Binding. */
    private FragmentAdminSearchBinding vb;

    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final boolean hasSites = vm.validate();
                    if (hasSites) {
                        // 2022-05-29: we now always persist.
                        //noinspection ConstantConditions
                        vm.persist(getContext());
                        // but to keep changes minimal, we still return the list if it's single.
                        if (vm.getTypes().size() == 1) {
                            final Site.Type type = vm.getTypes().get(0);
                            final Intent resultIntent = new Intent()
                                    .putParcelableArrayListExtra(type.getBundleKey(),
                                                                 vm.getList(type));
                            //noinspection ConstantConditions
                            getActivity().setResult(Activity.RESULT_OK, resultIntent);
                        }
                        //noinspection ConstantConditions
                        getActivity().finish();

                    } else {
                        Snackbar.make(vb.pager, R.string.warning_enable_at_least_1_website,
                                      Snackbar.LENGTH_LONG).show();
                    }
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(SearchAdminViewModel.class);
        vm.init(getArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentAdminSearchBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final List<Site.Type> types = vm.getTypes();
        tabAdapter = new TabAdapter(getActivity(), types);

        final TabLayout tabPanel = getActivity().findViewById(R.id.tab_panel);

        if (types.size() == 1) {
            getToolbar().setSubtitle(types.get(0).getLabelResId());
            tabPanel.setVisibility(View.GONE);
        }

        // We do NOT want any page recycled/reused - hence cache/keep ALL pages.
        vb.pager.setOffscreenPageLimit(tabAdapter.getItemCount());

        vb.pager.setAdapter(tabAdapter);
        new TabLayoutMediator(tabPanel, vb.pager, (tab, position) ->
                tab.setText(getString(tabAdapter.getTabTitle(position))))
                .attach();
    }

    /**
     * Encapsulate all the tabs that will be shown.
     */
    private static class TabAdapter
            extends FragmentStateAdapter {

        @NonNull
        private final List<Site.Type> siteTypeList;

        /**
         * Constructor.
         *
         * @param container hosting activity
         * @param types     the list of tabs (types) to be shown
         */
        TabAdapter(@NonNull final FragmentActivity container,
                   @NonNull final List<Site.Type> types) {
            super(container);
            siteTypeList = types;
        }

        @Override
        public int getItemCount() {
            return siteTypeList.size();
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            return SearchOrderFragment.create(siteTypeList.get(position));
        }

        @StringRes
        int getTabTitle(final int position) {
            return siteTypeList.get(position).getLabelResId();
        }
    }
}
