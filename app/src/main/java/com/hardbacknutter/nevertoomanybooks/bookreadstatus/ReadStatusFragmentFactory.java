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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public final class ReadStatusFragmentFactory {

    private ReadStatusFragmentFactory() {
    }

    /**
     * To be called from the hosting {@link Fragment#onViewCreated(View, Bundle)}.
     *
     * @param fm                      The FragmentManager this fragment will be added to.
     * @param fragmentContainerViewId where to add the new fragment
     * @param style                   to use
     * @param embedded                flag, whether we're running in tablet-landscape (embedded)
     */
    public static void createShow(@NonNull final FragmentManager fm,
                                  @IdRes final int fragmentContainerViewId,
                                  @NonNull final Style style,
                                  final boolean embedded) {
        create(fm, fragmentContainerViewId, style, Mode.Show, embedded);
    }

    /**
     * To be called from the hosting {@link Fragment#onViewCreated(View, Bundle)}.
     *
     * @param fm                      The FragmentManager this fragment will be added to.
     * @param fragmentContainerViewId where to add the new fragment
     * @param style                   to use
     */
    public static void createEditor(@NonNull final FragmentManager fm,
                                    @IdRes final int fragmentContainerViewId,
                                    @NonNull final Style style) {
        create(fm, fragmentContainerViewId, style, Mode.Edit, false);
    }

    /**
     * Constructor.
     *
     * @param fm                      The FragmentManager this fragment will be added to.
     * @param fragmentContainerViewId where to add the new fragment
     * @param style                   to use
     * @param mode                    the required ViewModel mode
     * @param embedded                flag, whether we're running in tablet-landscape (embedded).
     *                                Only applicable to {@link Mode#Show}.
     */
    private static void create(@NonNull final FragmentManager fm,
                               @IdRes final int fragmentContainerViewId,
                               @NonNull final Style style,
                               @NonNull final Mode mode,
                               final boolean embedded) {

        if (style.useReadProgress()) {
            if (fm.findFragmentByTag(ReadProgressFragment.TAG) == null) {
                create(fm, fragmentContainerViewId,
                       ReadProgressFragment.TAG, new ReadProgressFragment(),
                       mode, embedded);
            }
        } else {
            // Traditional Read/Unread.
            if (fm.findFragmentByTag(ReadStatusFragment.TAG) == null) {
                create(fm, fragmentContainerViewId,
                       ReadStatusFragment.TAG, new ReadStatusFragment(),
                       mode, embedded);
            }
        }
    }

    private static void create(@NonNull final FragmentManager fm,
                               final int fragmentContainerViewId,
                               @NonNull final String tag,
                               @NonNull final Fragment fragment,
                               @NonNull final Mode mode,
                               final boolean embedded) {
        final ReadStatusInput input = new ReadStatusInput(mode, embedded);
        fragment.setArguments(input.toBundle());
        fm.beginTransaction()
          .setReorderingAllowed(true)
          .replace(fragmentContainerViewId, fragment, tag)
          .commit();
    }

    /**
     * To be called from the Read status/progress {@link Fragment#onCreate(Bundle)}.
     *
     * @param fragment hosting fragment
     * @param args     all arguments
     *
     * @return the ViewModel
     *
     * @throws IllegalArgumentException for illegal values
     */
    @NonNull
    static BookReadStatusViewModel getViewModel(@NonNull final Fragment fragment,
                                                @NonNull final ReadStatusInput args) {

        final Mode mode = args.getMode();
        switch (mode) {
            case Show: {
                // See class docs for ShowBookDetailsFragment
                if (args.isEmbedded()) {
                    return new ViewModelProvider(fragment.requireActivity())
                            .get(ShowBookDetailsViewModel.class);
                } else {
                    return new ViewModelProvider(fragment.requireParentFragment())
                            .get(ShowBookDetailsViewModel.class);
                }
            }
            case Edit: {
                // MUST be in the Activity scope
                // The editor fragments all exchange data via the Activity.
                //noinspection DataFlowIssue
                return new ViewModelProvider(fragment.getActivity())
                        .get(EditBookViewModel.class);
            }
            default:
                throw new IllegalArgumentException(mode.toString());
        }
    }

    /**
     * The Mode for the ViewModel.
     * <p>
     * Reminder: don't try to stick the vm class in the enum instances.
     * ViewModelProvider#get needs a concrete class
     */
    public enum Mode
            implements Parcelable {
        Show,
        Edit;

        /** {@link Parcelable}. */
        public static final Creator<Mode> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Mode createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public Mode[] newArray(final int size) {
                return new Mode[size];
            }
        };

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
