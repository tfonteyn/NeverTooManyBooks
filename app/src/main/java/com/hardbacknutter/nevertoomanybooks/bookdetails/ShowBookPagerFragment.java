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

    public static final String TAG = "ShowBookPagerFragment";

    /** View Binding with the ViewPager2. */
    private ViewPager2 mViewPager;

    @SuppressWarnings("FieldCanBeLocal")
    private ShowBookPagerAdapter mPagerAdapter;

    private ShowBookDetailsActivityViewModel mAVm;
    /** Contains ONLY the data relevant to the pager. */
    private ShowBookPagerViewModel mVm;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // always set the *current* book, so BoB can reposition more accurately.
                    final long bookId = mVm.getBookIdAtPosition(mViewPager.getCurrentItem());
                    final Intent resultIntent = EditBookOutput
                            .createResultIntent(bookId, mAVm.isModified());
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();

        //noinspection ConstantConditions
        mAVm = new ViewModelProvider(getActivity()).get(ShowBookDetailsActivityViewModel.class);
        mAVm.init(getActivity(), args);

        mVm = new ViewModelProvider(this).get(ShowBookPagerViewModel.class);
        mVm.init(args);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.fragment_book_details_pager, container, false);
        // pager == view; but keep it future-proof
        mViewPager = view.findViewById(R.id.pager);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mPagerAdapter = new ShowBookPagerAdapter(this);
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setCurrentItem(mVm.getInitialPagerPosition(), false);

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance().display(getContext(), R.string.tip_view_only_help, null);
        }
    }

    private class ShowBookPagerAdapter
            extends FragmentStateAdapter {

        ShowBookPagerAdapter(@NonNull final Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(final int position) {
            return ShowBookDetailsFragment.create(mVm.getBookIdAtPosition(position),
                                                  mAVm.getStyle().getUuid(), false);
        }

        @Override
        public int getItemCount() {
            return mVm.getRowCount();
        }
    }
}
