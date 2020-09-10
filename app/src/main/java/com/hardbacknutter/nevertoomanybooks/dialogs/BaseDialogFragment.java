/*
 * @Copyright 2020 HardBackNutter
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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.DimenRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Provides fullscreen or floating dialog support.
 * Does <strong>not</strong> support AlertDialog style title bar and buttons.
 */
public abstract class BaseDialogFragment
        extends DialogFragment {

    // SnackBar:
    //  private static final int SHORT_DURATION_MS = 1500;
    //  private static final int LONG_DURATION_MS = 2750;

    private boolean mForceFullscreen;
    /** Show the dialog fullscreen (default) or as a floating dialog. */
    private boolean mFullscreen;

    private int mFloatingDialogWidth;

    /**
     * Constructor.
     */
    public BaseDialogFragment() {
    }

    /**
     * Constructor.
     *
     * @param contentLayoutId to use
     */
    protected BaseDialogFragment(@LayoutRes final int contentLayoutId) {
        super(contentLayoutId);
    }

    /**
     * If required, this <strong>MUST</strong> be called <strong>BEFORE</strong>
     * {@link #onAttach(Context)}.
     */
    protected void setForceFullscreen() {
        mForceFullscreen = true;
    }

    /**
     * If required, this <strong>MUST</strong> be called <strong>BEFORE</strong>
     * {@link #onViewCreated(View, Bundle)}.
     *
     * @param floatingDialogWidth the width to use
     */
    protected void setFloatingDialogWidth(@DimenRes final int floatingDialogWidth) {
//        if ((floatingDialogWidth == ViewGroup.LayoutParams.MATCH_PARENT)
//            || (floatingDialogWidth == ViewGroup.LayoutParams.WRAP_CONTENT)) {
//            mFloatingDialogWidth = floatingDialogWidth;
//
//        } else {
        final Resources res = getResources();
        final float density = res.getDisplayMetrics().density;
        final float widthDp = res.getConfiguration().screenWidthDp;
        final float minWidthDp = res.getDimension(floatingDialogWidth);
        mFloatingDialogWidth = (int) (density * Math.min(widthDp, minWidthDp));
//        }
    }

    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mFullscreen = !getResources().getBoolean(R.bool.floating_dialogs_enabled)
                      || mForceFullscreen;
        if (mFullscreen) {
            setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);
        }
    }

    /**
     * Final. Override {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}
     * and/or {@link #onViewCreated(View, Bundle)} instead.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        //noinspection ConstantConditions
        final Dialog dialog = new Dialog(getContext(), getTheme());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        @NonNull
        final Toolbar toolbar = Objects.requireNonNull(view.findViewById(R.id.toolbar));
        toolbar.setNavigationOnClickListener(this::onToolbarNavigationClick);
        toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);
        MenuHandler.prepareMenuSelectButton(toolbar.getMenu(), this::onToolbarMenuItemClick);

        @Nullable
        final View buttonPanel = view.findViewById(R.id.buttonPanel);
        if (buttonPanel != null) {
            buttonPanel.setVisibility(mFullscreen ? View.GONE : View.VISIBLE);
        }

        if (!mFullscreen) {
            // if the parent class has not set the width, use the default.
            if (mFloatingDialogWidth == 0) {
                setFloatingDialogWidth(R.dimen.floating_dialogs_min_width);
            }

            // Always add actionBarSize (56dp) to the frame to compensate for the toolbar.
            // When there is no buttonbar, we'll also add dialogPreferredPadding (see below)
            //noinspection ConstantConditions
            int bottomMargin = AttrUtils.getDimen(getContext(), R.attr.actionBarSize);

            if (buttonPanel != null) {
                // Toolbar navigation is mapped to the cancel button. Both are visible.
                final Button cancelBtn = buttonPanel.findViewById(R.id.btn_cancel);
                cancelBtn.setOnClickListener(this::onToolbarNavigationClick);
                // If the toolbar nav button has a description, use it for the 'cancel' button text.
                final CharSequence cancelText = toolbar.getNavigationContentDescription();
                if (cancelText != null) {
                    cancelBtn.setText(cancelText);
                }

                // Toolbar confirmation menu item is mapped to the ok button.
                // The menu item is removed.
                final Button okBtn = buttonPanel.findViewById(R.id.btn_ok);
                final MenuItem actionConfirm = toolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
                if (actionConfirm != null) {
                    actionConfirm.setVisible(false);
                    okBtn.setVisibility(View.VISIBLE);
                    // always update the 'ok' button text.
                    okBtn.setText(actionConfirm.getTitle());
                    okBtn.setOnClickListener(v -> onToolbarMenuItemClick(actionConfirm));
                } else {
                    okBtn.setVisibility(View.GONE);
                }
            } else {
                // If there is no button bar, add a padding (24dp) to the dialog bottom.
                bottomMargin += AttrUtils.getDimen(getContext(), R.attr.dialogPreferredPadding);
            }

            // adjust the overall width as calculated before
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            lp.width = mFloatingDialogWidth;
            // and adjust the bottom margin of the body_frame
            final View bodyFrame = view.findViewById(R.id.body_frame);
            lp = (ViewGroup.MarginLayoutParams) bodyFrame.getLayoutParams();
            lp.setMargins(0, 0, 0, bottomMargin);
        }
    }

    /**
     * Called when the user clicks the Navigation icon from the toolbar menu.
     * The default action simply dismisses the dialog.
     *
     * @param v view
     */
    protected void onToolbarNavigationClick(@NonNull final View v) {
        dismiss();
    }

    /**
     * Called when the user selects a menu item from the toolbar menu.
     * The default action ignores the selection.
     *
     * @param item {@link MenuItem} that was clicked
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    @SuppressWarnings("UnusedReturnValue")
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        return false;
    }

    @Override
    @CallSuper
    public void onDismiss(@NonNull final DialogInterface dialog) {
        // Depending on how we close the dialog, the onscreen keyboard sometimes stays up.
        final View view = getView();
        if (view != null) {
            // dismiss it manually
            hideKeyboard(view);
        }
        super.onDismiss(dialog);
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @SuppressWarnings("SameParameterValue") @StringRes final int errorId) {
        showError(til, getString(errorId));
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @NonNull final CharSequence error) {
        til.setError(error);
        til.postDelayed(() -> til.setError(null), BaseActivity.ERROR_DELAY_MS);
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
