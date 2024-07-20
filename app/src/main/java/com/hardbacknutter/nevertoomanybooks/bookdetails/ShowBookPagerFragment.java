/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;

public class ShowBookPagerFragment
        extends BaseFragment {

    /** View Binding with the ViewPager2. */
    private ViewPager2 viewPager;

    private ShowBookDetailsActivityViewModel aVm;

    /** Contains ONLY the data relevant to the pager. */
    private ShowBookPagerViewModel vm;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // always set the *current* book, so BoB can reposition more accurately.
                    final long bookId = vm.getBookIdAtPosition(viewPager.getCurrentItem());
                    final Intent resultIntent = EditBookOutput
                            .createResultIntent(aVm.isModified(), bookId);
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };
    private final ViewPager2.OnPageChangeCallback pageChange =
            new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(final int position) {
                    vm.setPageSelected(position);
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();

        //noinspection DataFlowIssue
        aVm = new ViewModelProvider(getActivity()).get(ShowBookDetailsActivityViewModel.class);
        aVm.init(args);

        vm = new ViewModelProvider(getActivity()).get(ShowBookPagerViewModel.class);
        vm.init(args);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.fragment_book_details_pager, container, false);
        // pager == view; but keep it future-proof
        viewPager = view.findViewById(R.id.pager);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final ShowBookPagerAdapter adapter = new ShowBookPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(vm.getInitialPagerPosition(), false);


        viewPager.registerOnPageChangeCallback(pageChange);

        if (savedInstanceState == null) {
            //noinspection DataFlowIssue
            TipManager.getInstance().display(getContext(), R.string.tip_view_only_help, null);
        }
    }

    @Override
    public void onDestroyView() {
        viewPager.unregisterOnPageChangeCallback(pageChange);
        super.onDestroyView();
    }

    private class ShowBookPagerAdapter
            extends FragmentStateAdapter {

        ShowBookPagerAdapter(@NonNull final Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            return ShowBookDetailsFragment.create(vm.getBookIdAtPosition(position),
                                                  aVm.getStyle().getUuid(), false);
        }

        @Override
        public int getItemCount() {
            return vm.getRowCount();
        }
    }
}
