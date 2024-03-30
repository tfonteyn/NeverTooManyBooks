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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Objects;
import java.util.function.Supplier;

//URGENT: clean up this mess of subclasses
public abstract class DialogLauncher
        implements FragmentResultListener {

    private static final String TAG = "DialogLauncher";
    /**
     * The bundle key to pass the {@link #requestKey} around.
     * Keep in mind this value is irrelevant to the Android OS.
     * Only the actual {@link #requestKey} is relevant to Android.
     */
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /**
     * FragmentResultListener request key to use for our response.
     * Doubles up as the fragment TAG
     */
    private final String requestKey;

    @NonNull
    private final Supplier<DialogFragment> dialogFragmentSupplier;
    @Nullable
    private FragmentManager fragmentManager;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param dialogSupplier a supplier for a new DialogFragment
     */
    protected DialogLauncher(@NonNull final String requestKey,
                             @NonNull final Supplier<DialogFragment> dialogSupplier) {
        this.requestKey = requestKey;
        this.dialogFragmentSupplier = dialogSupplier;
    }

    public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        this.fragmentManager = fragmentManager;
        this.fragmentManager.setFragmentResultListener(requestKey, lifecycleOwner, this);
    }

    /**
     * Create the dialog, setup the arguments adding the requestKey and show it.
     *
     * @param args to pass
     */
    protected void createDialog(@NonNull final Bundle args) {
        Objects.requireNonNull(fragmentManager, "fragmentManager");

        args.putString(BKEY_REQUEST_KEY, requestKey);

        final DialogFragment dialogFragment = dialogFragmentSupplier.get();
        dialogFragment.setArguments(args);
        // using the requestKey as the fragment tag.
        dialogFragment.show(fragmentManager, requestKey);
    }
}
