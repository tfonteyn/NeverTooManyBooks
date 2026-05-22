/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.identifiers;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;

public class IdentifiersAdminFragment
        extends BaseFragment {

    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    getActivity().finish();
                }
            };
    private TabAdapter tabAdapter;
    /** View Binding with the ViewPager2. */
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_edit_identifiers_viewpager,
                                           container, false);
        // pager == view; but keep it future-proof
        viewPager = view.findViewById(R.id.pager);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Effectively disable edge-to-edge for the pager
        // Do NOT include systemGestures as this will cause the content to be squeezed.
        new InsetsListenerBuilder(view)
                .padding(Side.Start, Side.End, Side.Bottom)
                .systemBars()
                .displayCutout()
                .apply();

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_websites);
        toolbar.setSubtitle("");

        tabAdapter = new TabAdapter(getActivity());
        final TabLayout tabPanel = getActivity().findViewById(R.id.tab_panel);

        // We do NOT want any page recycled/reused - hence cache/keep ALL pages.
        viewPager.setOffscreenPageLimit(tabAdapter.getItemCount());
        viewPager.setAdapter(tabAdapter);

        new TabLayoutMediator(tabPanel, viewPager, (tab, position)
                -> tab.setText(getString(tabAdapter.getTabTitle(position)))).attach();
    }

    /**
     * All the tabs that will be shown.
     * <p>
     * Limited amount of Fragments, no need/desire to use ExtFragmentStateAdapter.F
     */
    private static class TabAdapter
            extends FragmentStateAdapter {

        @NonNull
        private final Identifier.EntityType[] values = Identifier.EntityType.values();

        /**
         * Constructor.
         *
         * @param activity the hosting Activity
         */
        TabAdapter(@NonNull final FragmentActivity activity) {
            super(activity);
        }

        @Override
        public int getItemCount() {
            return values.length;
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            return IdentifiersEditorFragment.create(values[position]);
        }

        @StringRes
        int getTabTitle(final int position) {
            return values[position].getLabelResId();
        }
    }
}
