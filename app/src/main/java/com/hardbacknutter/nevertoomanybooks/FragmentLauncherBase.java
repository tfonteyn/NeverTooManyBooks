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

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

/**
 * This class and the implementations TRY to mimic a ActivityResultContract.
 */
public abstract class FragmentLauncherBase
        implements FragmentResultListener {

    protected final String mRequestKey;
    protected FragmentManager mFragmentManager;

    public FragmentLauncherBase(@NonNull final String requestKey) {
        mRequestKey = requestKey;
    }

    public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        mFragmentManager = fragmentManager;
        mFragmentManager.setFragmentResultListener(mRequestKey, lifecycleOwner, this);
    }
}
