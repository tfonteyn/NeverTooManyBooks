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

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;

public abstract class BaseFragment
        extends Fragment {

    private View mProgressFrame;
    private Toolbar mToolbar;
    private FloatingActionButton mFab;

    @NonNull
    protected View getProgressFrame() {
        if (mProgressFrame == null) {
            //noinspection ConstantConditions
            mProgressFrame = Objects.requireNonNull(getActivity().findViewById(R.id.progress_frame),
                                                    "R.id.progress_frame");
        }
        return mProgressFrame;
    }

    @NonNull
    protected Toolbar getToolbar() {
        if (mToolbar == null) {
            //noinspection ConstantConditions
            mToolbar = Objects.requireNonNull(getActivity().findViewById(R.id.toolbar),
                                              "R.id.toolbar");
        }
        return mToolbar;
    }

    @NonNull
    protected FloatingActionButton getFab() {
        if (mFab == null) {
            //noinspection ConstantConditions
            mFab = Objects.requireNonNull(getActivity().findViewById(R.id.fab),
                                          "R.id.fab");
        }
        return mFab;
    }


}
