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
package com.hardbacknutter.nevertoomanybooks;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;

public abstract class BaseFragment
        extends Fragment {

    private View progressFrame;
    private Toolbar toolbar;
    private FloatingActionButton fab;

    @NonNull
    protected View getProgressFrame() {
        if (progressFrame == null) {
            //noinspection ConstantConditions
            progressFrame = Objects.requireNonNull(getActivity().findViewById(R.id.progress_frame),
                                                   "R.id.progress_frame");
        }
        return progressFrame;
    }

    @NonNull
    protected Toolbar getToolbar() {
        if (toolbar == null) {
            //noinspection ConstantConditions
            toolbar = Objects.requireNonNull(getActivity().findViewById(R.id.toolbar),
                                             "R.id.toolbar");
        }
        return toolbar;
    }

    @NonNull
    protected FloatingActionButton getFab() {
        if (fab == null) {
            //noinspection ConstantConditions
            fab = Objects.requireNonNull(getActivity().findViewById(R.id.fab),
                                         "R.id.fab");
        }
        return fab;
    }

    protected void replaceFragment(@NonNull final Fragment fragment,
                                   @NonNull final String tag) {
        final FragmentManager fm = getParentFragmentManager();
        fm.beginTransaction()
          .setReorderingAllowed(true)
          .addToBackStack(tag)
          .replace(R.id.main_fragment, fragment, tag)
          .commit();
    }
}
