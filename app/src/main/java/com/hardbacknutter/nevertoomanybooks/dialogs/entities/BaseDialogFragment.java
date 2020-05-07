/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Provides fullscreen or floating dialog support.
 * Does <strong>not</strong> support AlertDialog style title bar and buttons.
 * <p>
 * Usage: from the no-args constructor, call the provided constructor with the layout id to use.
 * Implement {@link #onViewCreated(View, Bundle)} to get the root view.
 */
public abstract class BaseDialogFragment
        extends DialogFragment {

    // SnackBar:
    //  private static final int SHORT_DURATION_MS = 1500;
    //  private static final int LONG_DURATION_MS = 2750;


    private final boolean mForceFullscreen;
    @LayoutRes
    private final int mLayoutId;
    /** Show the dialog fullscreen (default) or as a floating dialog. */
    private boolean mFullscreen;
    @Nullable
    private View mDialogView;

    /**
     * Constructor.
     *
     * @param layoutId to use
     */
    protected BaseDialogFragment(@LayoutRes final int layoutId) {
        mLayoutId = layoutId;
        mForceFullscreen = false;
    }

    /**
     * Constructor.
     *
     * @param layoutId to use
     */
    protected BaseDialogFragment(@LayoutRes final int layoutId,
                                 final boolean forceFullscreen) {
        mLayoutId = layoutId;
        mForceFullscreen = forceFullscreen;
    }

    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mFullscreen = !getResources().getBoolean(R.bool.isLargeScreen) || mForceFullscreen;
        if (mFullscreen) {
            setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);
        }
    }

    /**
     * Final. Override {@link #onViewCreated(View, Bundle)} instead.
     * <p>
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public final View onCreateView(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container,
                                   @Nullable final Bundle savedInstanceState) {
        if (mFullscreen) {
            mDialogView = inflater.inflate(mLayoutId, container, false);
            return mDialogView;
        } else {
            return null;
        }
    }

    /**
     * Final. Override {@link #onViewCreated(View, Bundle)} instead.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public final Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog dialog;
        if (mFullscreen) {
            dialog = super.onCreateDialog(savedInstanceState);
        } else {
            mDialogView = getLayoutInflater().inflate(mLayoutId, null);
            onViewCreated(mDialogView, savedInstanceState);
            //noinspection ConstantConditions
            dialog = new MaterialAlertDialogBuilder(getContext())
                    .setView(mDialogView)
                    .create();
        }

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    @CallSuper
    public void onDismiss(@NonNull final DialogInterface dialog) {
        // Depending on how we close the dialog, the onscreen keyboard sometimes stays up.
        if (mDialogView != null) {
            // dismiss it manually
            hideKeyboard(mDialogView);
        }
        super.onDismiss(dialog);
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @SuppressWarnings("SameParameterValue") final int errorId) {
        showError(til, getString(errorId));
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @NonNull final CharSequence error) {
        til.setError(error);
        new Handler().postDelayed(() -> til.setError(null), App.ERROR_DELAY_MS);
    }

    /**
     * Hide the keyboard.
     *
     * @param view a View from which we can get the window token.
     */
    @SuppressWarnings("WeakerAccess")
    public void hideKeyboard(@NonNull final View view) {
        final InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
