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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public final class ReadStatusFragmentFactory {

    public static final int VIEWMODEL_SHOW = 0;
    public static final int VIEWMODEL_EDIT = 1;

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
                            @ViewModelClass final int viewModelClass) {

        final Bundle args = new Bundle(1);
        args.putInt(BKEY_VIEWMODEL, viewModelClass);

        if (style.useReadProgress()) {
            Fragment fragment = fm.findFragmentByTag(ReadProgressFragment.TAG);
            if (fragment == null) {
                fragment = new ReadProgressFragment();
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

    /**
     * To be called from the Read status/progress {@link Fragment#onCreate(Bundle)}.
     *
     * @param args bundle
     *
     * @return the class to use for the {@code ViewModelProvider}.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    static <T extends BookReadStatusViewModel> Class<T> getViewModelClass(
            @NonNull final Bundle args) {
        final int viewModelClass = args.getInt(BKEY_VIEWMODEL);
        switch (viewModelClass) {
            case VIEWMODEL_SHOW:
                return (Class<T>) ShowBookDetailsViewModel.class;
            case VIEWMODEL_EDIT:
                return (Class<T>) EditBookViewModel.class;
            default:
                throw new IllegalArgumentException("viewModelClass=" + viewModelClass);
        }
    }

    @IntDef({VIEWMODEL_SHOW, VIEWMODEL_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    @interface ViewModelClass {
    }
}
