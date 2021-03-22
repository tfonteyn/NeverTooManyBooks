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

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

/**
 * Notes on using getSupportActionBar to set the activity title:
 * We were trying to use the Toolbar consistently/directly to set a title/subtitle,
 * but found that in some places this worked and in other it failed.
 * Google docs state it should not work *after* setSupportActionBar has been used.
 * Nevertheless... it worked for us most of the time.... but not always.
 * TLDR: ALWAYS using this abstract class and {@link BaseActivity#setTitle(int)} etc....
 */
public abstract class BaseFragment
        extends Fragment {

    public void setTitle(@StringRes final int titleResId,
                         @StringRes final int subtitleResId) {
        final BaseActivity baseActivity = ((BaseActivity) getActivity());
        //noinspection ConstantConditions
        baseActivity.setTitle(titleResId);
        baseActivity.setSubtitle(subtitleResId);
    }

    public void setTitle(@Nullable final CharSequence title,
                         @Nullable final CharSequence subtitle) {
        final BaseActivity baseActivity = ((BaseActivity) getActivity());
        //noinspection ConstantConditions
        baseActivity.setTitle(title);
        baseActivity.setSubtitle(subtitle);
    }

    public void setTitle(@StringRes final int resId) {
        //noinspection ConstantConditions
        getActivity().setTitle(resId);
    }

    public void setTitle(@Nullable final CharSequence title) {
        //noinspection ConstantConditions
        getActivity().setTitle(title);
    }

    public void setSubtitle(@StringRes final int resId) {
        //noinspection ConstantConditions
        ((BaseActivity) getActivity()).setSubtitle(resId);
    }

    public void setSubtitle(@Nullable final CharSequence subtitle) {
        //noinspection ConstantConditions
        ((BaseActivity) getActivity()).setSubtitle(subtitle);
    }
}
