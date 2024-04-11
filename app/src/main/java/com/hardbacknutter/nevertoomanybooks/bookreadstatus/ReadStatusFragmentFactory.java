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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public final class ReadStatusFragmentFactory {

    private static final int VIEWMODEL_SHOW = 0;
    private static final int VIEWMODEL_EDIT = 1;

    private static final String TAG = "ReadStatusFragmentFactory";
    private static final String BKEY_VIEWMODEL = TAG + ":vm";

    private ReadStatusFragmentFactory() {
    }

    /**
     * To be called from the hosting {@link Fragment#onViewCreated(View, Bundle)}.
     *
     * @param fm                      to use
     * @param fragmentContainerViewId where to add the new fragment
     * @param style                   to use
     * @param viewModelClass          the required ViewModel class
     */
    public static void bind(@NonNull final FragmentManager fm,
                            @IdRes final int fragmentContainerViewId,
                            @NonNull final Style style,
                            final Class<? extends ViewModel> viewModelClass) {

        if (style.useReadProgress()) {
            Fragment fragment = fm.findFragmentByTag(ReadProgressFragment.TAG);
            if (fragment == null) {
                fragment = new ReadProgressFragment();
                final Bundle args = new Bundle(1);
                args.putInt(BKEY_VIEWMODEL, getIntArg(viewModelClass));
                fragment.setArguments(args);
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .replace(fragmentContainerViewId, fragment, ReadProgressFragment.TAG)
                  .commit();
            } else {
                ((ReadProgressFragment) fragment).reload();
            }
        } else {
            // Traditional Read/Unread.
            Fragment fragment = fm.findFragmentByTag(ReadStatusFragment.TAG);
            if (fragment == null) {
                fragment = new ReadStatusFragment();
                final Bundle args = new Bundle(1);
                args.putInt(BKEY_VIEWMODEL, getIntArg(viewModelClass));
                fragment.setArguments(args);
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .replace(fragmentContainerViewId, fragment, ReadStatusFragment.TAG)
                  .commit();
            } else {
                ((ReadStatusFragment) fragment).reload();
            }
        }
    }

    @SuppressWarnings("ChainOfInstanceofChecks")
    private static int getIntArg(final Class<? extends ViewModel> viewModelClass) {
        final int vm;
        if (viewModelClass == ShowBookDetailsViewModel.class) {
            vm = VIEWMODEL_SHOW;
        } else if (viewModelClass == EditBookViewModel.class) {
            vm = VIEWMODEL_EDIT;
        } else {
            throw new IllegalArgumentException(viewModelClass.getName());
        }
        return vm;
    }

    /**
     * To be called from the Read status/progress {@link Fragment#onCreate(Bundle)}.
     *
     * @param fragment hosting fragment
     * @param args     bundle
     *
     * @return the ViewModel
     *
     * @throws IllegalArgumentException for illegal values
     */
    @NonNull
    static BookReadStatusViewModel getViewModel(@NonNull final Fragment fragment,
                                                @NonNull final Bundle args) {
        @ViewModelClass
        final int type = args.getInt(BKEY_VIEWMODEL);
        switch (type) {
            case VIEWMODEL_SHOW:
                // https://developer.android.com/guide/fragments/communicate#share_data_between_a_parent_and_child_fragment
                // MUST be in the PARENT Fragment scope
                return new ViewModelProvider(fragment.requireParentFragment())
                        .get(ShowBookDetailsViewModel.class);
            case VIEWMODEL_EDIT:
                // MUST be in the Activity scope
                //noinspection DataFlowIssue
                return new ViewModelProvider(fragment.getActivity())
                        .get(EditBookViewModel.class);
            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    @IntDef({VIEWMODEL_SHOW, VIEWMODEL_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    @interface ViewModelClass {
    }
}
