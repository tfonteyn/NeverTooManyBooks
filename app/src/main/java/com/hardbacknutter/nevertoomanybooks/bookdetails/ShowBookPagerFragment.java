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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.vp2.ExtFragmentStateAdapter;

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
                    final int currentItem = viewPager != null ? viewPager.getCurrentItem() : 0;
                    //noinspection DataFlowIssue
                    new EditBookOutput(aVm.isModified(), vm.getBookIdAtPosition(currentItem), 0)
                            .finishActivityAndSend(getActivity());
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ShowBookPagerInput args =
                ShowBookPagerInput.fromBundle(requireArguments());

        //noinspection DataFlowIssue
        aVm = new ViewModelProvider(getActivity()).get(ShowBookDetailsActivityViewModel.class);
        aVm.init(args.getBookshelf());

        vm = new ViewModelProvider(getActivity()).get(ShowBookPagerViewModel.class);
        if (!vm.init(args)) {
            // the nav-table was not there, we must have been frozen/killed ...
            // ABORT, back to BoB
            backPressedCallback.handleOnBackPressed();
        }
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
        // We're NOT applying any insets but do that on the individual fragments.

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final ShowBookPagerAdapter adapter = new ShowBookPagerAdapter(this);
        viewPager.setAdapter(adapter);
        // allow 3 books to be preloaded, b-1, b, b+1
        viewPager.setOffscreenPageLimit(1);

        // Don't animate/softscroll, always jump.
        viewPager.setCurrentItem(vm.getInitialPagerPosition(), false);

        if (savedInstanceState == null) {
            //noinspection DataFlowIssue
            TipManager.getInstance().show(getContext(), Tip.BOOK_DETAILS);
        }
    }

    private class ShowBookPagerAdapter
            extends ExtFragmentStateAdapter {

        ShowBookPagerAdapter(@NonNull final Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            return ShowBookDetailsFragment.create(vm.getBookIdAtPosition(position),
                                                  aVm.getBookshelf(),
                                                  false);
        }

        @Override
        public int getItemCount() {
            return vm.getRowCount();
        }
    }
}
