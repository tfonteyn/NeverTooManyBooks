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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;

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
    @NonNull
    private final String requestKey;

    @NonNull
    private final Supplier<DialogFragment> dialogSupplier;
    @NonNull
    private final Supplier<DialogFragment> bottomSheetSupplier;
    @Nullable
    private FragmentManager fragmentManager;

    /**
     * Constructor.
     *
     * @param requestKey          FragmentResultListener request key to use for our response.
     * @param dialogSupplier      a supplier for a new plain DialogFragment
     * @param bottomSheetSupplier a supplier for a new BottomSheetDialogFragment.
     */
    protected DialogLauncher(@NonNull final String requestKey,
                             @NonNull final Supplier<DialogFragment> dialogSupplier,
                             @NonNull final Supplier<DialogFragment> bottomSheetSupplier) {
        this.requestKey = requestKey;
        this.dialogSupplier = dialogSupplier;
        this.bottomSheetSupplier = bottomSheetSupplier;
    }

    /**
     * Register this object for receiving Fragment results.
     *
     * @param fragmentManager typically the {@link Fragment#getChildFragmentManager()}
     *                        or the {@link AppCompatActivity#getSupportFragmentManager()}
     * @param lifecycleOwner  typically the {@link Fragment} or the {@link AppCompatActivity}
     */
    public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        this.fragmentManager = fragmentManager;
        // DO NOT MOVE THIS TO THE CONSTRUCTOR!
        // the FragmentManager will use 'this' immediately!
        this.fragmentManager.setFragmentResultListener(requestKey, lifecycleOwner, this);
    }

    /**
     * Create the dialog, setup the arguments adding the requestKey and show it.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param args    to pass
     */
    protected void showDialog(@NonNull final Context context,
                              @NonNull final Bundle args) {
        Objects.requireNonNull(fragmentManager, "fragmentManager");

        final DialogMode mode = DialogMode.getMode(context);
        final DialogFragment dialogFragment;
        switch (mode) {
            case Dialog: {
                dialogFragment = dialogSupplier.get();
                break;
            }
            case BottomSheet: {
                dialogFragment = bottomSheetSupplier.get();
                break;
            }
            default:
                throw new IllegalArgumentException("requestKey=" + requestKey + ", type=" + mode);
        }

        args.putString(BKEY_REQUEST_KEY, requestKey);
        dialogFragment.setArguments(args);
        // using the requestKey as the fragment tag.
        dialogFragment.show(fragmentManager, requestKey);
    }
}
