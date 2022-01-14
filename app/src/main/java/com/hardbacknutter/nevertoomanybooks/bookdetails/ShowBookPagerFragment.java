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
import android.content.Context;
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

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBookDetailsPagerBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;

public class ShowBookPagerFragment
        extends BaseFragment {

    public static final String TAG = "ShowBookPagerFragment";

    /** View Binding with the ViewPager2. */
    private FragmentBookDetailsPagerBinding mVb;

    @SuppressWarnings("FieldCanBeLocal")
    private ShowBookPagerAdapter mPagerAdapter;
    private ShowBookPagerViewModel mVm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final long bookId = mVm.getBookIdAtPosition(mVb.pager.getCurrentItem());
                    final Intent resultIntent = new Intent();
                    resultIntent.putExtra(DBKey.FK_BOOK, bookId);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    /**
     * @param context Current context
     * @param bookId  initial book to show
     *
     * @return Intent
     */
    public static Intent createIntent(@NonNull final Context context,
                                      final long bookId) {
        return new Intent(context, FragmentHostActivity.class)
                .putExtra(FragmentHostActivity.BKEY_ACTIVITY,
                          R.layout.activity_book_details)
                .putExtra(FragmentHostActivity.BKEY_FRAGMENT_CLASS,
                          ShowBookPagerFragment.class.getName())
                .putExtra(DBKey.FK_BOOK, bookId);
    }

    public static Intent createIntent(@NonNull final Context context,
                                      final long bookId,
                                      @Nullable final String navTableName,
                                      final long listTableRowId,
                                      @Nullable final String styleUuid) {
        return createIntent(context, bookId)
                // the current list table, so the user can swipe
                // to the next/previous book
                .putExtra(ShowBookPagerViewModel.BKEY_NAV_TABLE_NAME, navTableName)
                // The row id in the list table of the given book.
                // Keep in mind a book can occur multiple times,
                // so we need to pass the specific one.
                .putExtra(ShowBookPagerViewModel.BKEY_LIST_TABLE_ROW_ID, listTableRowId)
                // some style elements are applicable for the details screen
                .putExtra(ListStyle.BKEY_STYLE_UUID, styleUuid);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(ShowBookPagerViewModel.class);
        mVm.init(requireArguments());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        mVb = FragmentBookDetailsPagerBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mPagerAdapter = new ShowBookPagerAdapter(this);
        mVb.pager.setAdapter(mPagerAdapter);
        mVb.pager.setCurrentItem(mVm.getInitialPagerPosition(), false);

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
            final ShowBookDetailsFragment fragment = new ShowBookDetailsFragment();
            final Bundle args = new Bundle();
            args.putLong(DBKey.FK_BOOK, mVm.getBookIdAtPosition(position));
            args.putString(ListStyle.BKEY_STYLE_UUID, mVm.getStyleUuid());
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return mVm.getRowCount();
        }
    }
}
