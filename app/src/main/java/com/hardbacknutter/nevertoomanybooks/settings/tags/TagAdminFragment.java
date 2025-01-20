/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings.tags;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAdminTagsBinding;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;

public class TagAdminFragment
        extends BaseFragment {

    /** View Binding. */
    private FragmentAdminTagsBinding vb;
    /** View Binding. */
    private TabLayout tabPanel;

    private TabAdapter tabAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentAdminTagsBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        InsetsListenerBuilder.apply(toolbar);
        // Effectively disable edge-to-edge for the pager and include system gestures.
        InsetsListenerBuilder.create(view)
                             .padding(Side.Start, Side.End, Side.Bottom)
                             .systemBars()
                             .displayCutout()
                             .systemGestures()
                             .apply();

        toolbar.setTitle(R.string.lbl_tags);
        toolbar.setSubtitle("");

        //noinspection DataFlowIssue
        tabAdapter = new TabAdapter(getActivity());
        tabPanel = getActivity().findViewById(R.id.tab_panel);

        // We do NOT want any page recycled/reused - hence cache/keep ALL pages.
        vb.pager.setOffscreenPageLimit(tabAdapter.getItemCount());

        vb.pager.setAdapter(tabAdapter);
        new TabLayoutMediator(tabPanel, vb.pager, (tab, position) -> {
            tab.setText(getString(tabAdapter.getTabTitle(position)));
        }).attach();

    }

    private static class TabAdapter
            extends FragmentStateAdapter {

        /**
         * Constructor.
         *
         * @param container hosting activity
         */
        TabAdapter(@NonNull final FragmentActivity container) {
            super(container);
        }

        @Override
        public int getItemCount() {
            return 2;
        }


        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            switch (position) {
                case 0:
                    return new TagEditorFragment();
                case 1:
                    return new TagMappingEditorFragment();
            }
            throw new IllegalArgumentException();
        }

        @StringRes
        int getTabTitle(final int position) {
            switch (position) {
                case 0:
                    return R.string.lbl_tags;
                case 1:
                    return R.string.lbl_substitutions;
            }
            throw new IllegalArgumentException();
        }
    }
}
