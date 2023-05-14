/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.function.Supplier;

public abstract class EditLauncher
        implements FragmentResultListener {

    protected static final String TAG = "EditLauncher";

    public static final String BKEY_REQUEST_KEY = TAG + ":rk";
    public static final String BKEY_ITEM = TAG + ":item";

    static final String ORIGINAL = TAG + ":o";
    static final String MODIFIED = TAG + ":m";

    /** FragmentResultListener request key to use for our response. */
    protected final String requestKey;
    @NonNull
    final Supplier<DialogFragment> dialogFragmentSupplier;
    @Nullable
    protected FragmentManager fragmentManager;

    EditLauncher(@NonNull final String requestKey,
                 @NonNull final Supplier<DialogFragment> dialogSupplier) {
        this.requestKey = requestKey;
        this.dialogFragmentSupplier = dialogSupplier;
    }

    public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        this.fragmentManager = fragmentManager;
        this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
    }
}
