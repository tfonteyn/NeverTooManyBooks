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
import android.util.Log;
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

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.covers.CoverBrowserDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Provides fullscreen or floating dialog support.
 * <p>
 * Special cases:
 * <p>
 * {@link CoverBrowserDialogFragment}
 * - force the width to a 'dimen' setting to maximize it.
 * <p>
 * {@link StylePickerDialogFragment}
 * - force the height to a 'dimen' to work around the RecyclerView collapsing or growing to large.
 * - prevent the compensation of the 'actionBarSize'.
 * <p>
 * 2020-10-10: experiments done to reverse fullscreen/floating coding produced MORE problems...
 * In onViewCreated set MATCH_PARENT when running fullscreen, and do nothing when floating.
 * Set the width of the CoordinatorLayout to 360dp
 * -> the action view is set to invisible, and the AppBarLayout gets shorter,
 * -> leaving a white space on the right
 * Set the width of the CoordinatorLayout to 360dp AND the AppBarLayout to 360dp
 * -> a small white space is seen on the right of the AppBarLayout
 * -> layout inspector shows that AppBarLayout=360dp.. BUT CoordinatorLayout==367dp  ???
 * <p>
 * As
 */
public abstract class BaseDialogFragment
        extends DialogFragment {

    /** Log tag. */
    private static final String TAG = "BaseDialogFragment";

    // SnackBar:
    //  private static final int SHORT_DURATION_MS = 1500;
    //  private static final int LONG_DURATION_MS = 2750;

    @Nullable
    private View mButtonPanel;
    private Toolbar mToolbar;
    @Nullable
    private Button mOkButton;

    /** Show the dialog fullscreen (default) or as a floating dialog. */
    private boolean mFullscreen;
    private boolean mForceFullscreen;

    //URGENT: clean this up
    private static final int DIMEN_NOT_SET = -1;

    /** FLOATING DIALOG mode only. */
    @DimenRes
    private int mWidthDrId = R.dimen.floating_dialogs_width;
    /** FLOATING DIALOG mode only. Default is 'do not set'. */
    @DimenRes
    private int mDHeightDrId = DIMEN_NOT_SET;
    /** FLOATING DIALOG mode only. Default set in {@link #onAttach(Context)}. */
    @DimenRes
    private int mMarginBottomDrId = DIMEN_NOT_SET;

    /**
     * Constructor.
     *
     * @param contentLayoutId to use
     */
    protected BaseDialogFragment(@LayoutRes final int contentLayoutId) {
        super(contentLayoutId);
    }

    /**
     * If required, this <strong>MUST</strong> be called from the constructor.
     */
    protected void setForceFullscreen() {
        mForceFullscreen = true;
    }

    /**
     * FLOATING DIALOG mode only. Has no effect in fullscreen mode.
     * If required, this <strong>MUST</strong> be called from the constructor.
     * <p>
     * Default: R.dimen.floating_dialogs_min_width
     *
     * @param dimenResId the width to use as an 'R.dimen.value'
     */
    protected void setFloatingDialogWidth(@SuppressWarnings("SameParameterValue")
                                          @DimenRes final int dimenResId) {
        mWidthDrId = dimenResId;
    }

    /**
     * FLOATING DIALOG mode only. Has no effect in fullscreen mode.
     * If required, this <strong>MUST</strong> be called from the constructor.
     * <p>
     * Default: as configured in the layout
     *
     * @param dimenResId the height to use as an 'R.dimen.value'
     */
    protected void setFloatingDialogHeight(@SuppressWarnings("SameParameterValue")
                                           @DimenRes final int dimenResId) {
        mDHeightDrId = dimenResId;
    }

    /**
     * FLOATING DIALOG mode only. Has no effect in fullscreen mode.
     * If required, this <strong>MUST</strong> be called from the constructor.
     * <p>
     * Default: the resolved R.attr.actionBarSize
     *
     * @param dimenResId the bottom margin to use as an 'R.dimen.value'
     */
    protected void setFloatingDialogMarginBottom(@SuppressWarnings("SameParameterValue")
                                                 @DimenRes final int dimenResId) {
        mMarginBottomDrId = dimenResId;
    }

    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mFullscreen = !getResources().getBoolean(R.bool.floating_dialogs_enabled)
                      || mForceFullscreen;
        if (mFullscreen) {
            setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);
        } else {
            // resolve the attribute to the actual dimen res id
            // If not set in the constructor, use the default.
            if (mMarginBottomDrId == DIMEN_NOT_SET) {
                mMarginBottomDrId = AttrUtils.getResId(context, R.attr.actionBarSize);
            }
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
        mToolbar = Objects.requireNonNull(view.findViewById(R.id.toolbar), "R.id.toolbar");

        // dialogs that are set as full-screen ONLY will not have a button bar.
        mButtonPanel = view.findViewById(R.id.buttonPanel);

        hookupButtons();

        if (!mFullscreen) {
            final Resources res = getResources();

            if (mWidthDrId != DIMEN_NOT_SET) {
                final float screenWidthDp = res.getConfiguration().screenWidthDp;
                final int dimenWidth = res.getDimensionPixelSize(mWidthDrId);
                // sanity check
                final int width = (int) Math.min(screenWidthDp, dimenWidth);
                view.getLayoutParams().width = width;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "onViewCreated|width=" + width);
                }
            }

            if (mDHeightDrId != DIMEN_NOT_SET) {
                final float screenHeightDp = res.getConfiguration().screenHeightDp;
                final int dimenHeight = res.getDimensionPixelSize(mDHeightDrId);
                // sanity check
                final int height = (int) Math.min(screenHeightDp, dimenHeight);
                view.getLayoutParams().height = height;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "onViewCreated|height=" + height);
                }
            }

            if (mMarginBottomDrId != DIMEN_NOT_SET && mMarginBottomDrId != 0) {
                final int marginBottom = res.getDimensionPixelSize(mMarginBottomDrId);
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                        view.findViewById(R.id.body_frame).getLayoutParams();
                lp.setMargins(0, 0, 0, marginBottom);

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "onViewCreated|marginBottom=" + marginBottom);
                }
            }
        }
    }

    /**
     * In fullscreen mode, hookup the navigation icon to the 'cancel' action,
     * and the toolbar action view confirmation button to the 'ok' action.
     * <p>
     * In floating mode, hookup the navigation icon to the 'cancel' action,
     * remove the toolbar action view, and display the button-bar instead.
     * Then hookup the button-bar 'cancel' and 'ok' buttons.
     */
    private void hookupButtons() {

        mToolbar.setNavigationOnClickListener(this::onToolbarNavigationClick);
        mToolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);

        // Show or hide the button panel depending on the dialog being fullscreen or not.
        if (mFullscreen) {
            if (mButtonPanel != null) {
                mButtonPanel.setVisibility(View.GONE);
            }

            // Hook-up the toolbar 'confirm' button with the menu title
            // and the default menu listener.
            final MenuItem menuItem = mToolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
            if (menuItem != null) {
                // the ok-button is a button inside the action view of the toolbar menu item
                mOkButton = menuItem.getActionView().findViewById(R.id.btn_confirm);
                if (mOkButton != null) {
                    mOkButton.setText(menuItem.getTitle());
                    mOkButton.setOnClickListener(v -> onToolbarMenuItemClick(menuItem));
                }
            }

        } else {
            if (mButtonPanel != null) {
                mButtonPanel.setVisibility(View.VISIBLE);
                // Toolbar confirmation menu item is mapped to the ok button.
                // The menu item is hidden.
                final MenuItem menuItem = mToolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
                if (menuItem != null) {
                    menuItem.setVisible(false);
                    // the ok-button is a simple button on the button panel.
                    mOkButton = mButtonPanel.findViewById(R.id.btn_ok);
                    if (mOkButton != null) {
                        mOkButton.setVisibility(View.VISIBLE);
                        mOkButton.setText(menuItem.getTitle());
                        mOkButton.setOnClickListener(v -> onToolbarMenuItemClick(menuItem));
                    }
                }

                // Toolbar navigation icon is mapped to the cancel button. Both are visible.
                final Button cancelBtn = mButtonPanel.findViewById(R.id.btn_cancel);
                if (cancelBtn != null) {
                    // If the toolbar nav button has a description,
                    // use it for the 'cancel' button text.
                    final CharSequence cancelText = mToolbar.getNavigationContentDescription();
                    if (cancelText != null) {
                        cancelBtn.setText(cancelText);
                    }
                    cancelBtn.setOnClickListener(this::onToolbarNavigationClick);
                }
            }
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

    @NonNull
    public Button requireConfirmButton() {
        return Objects.requireNonNull(mOkButton, "mConfirmButton");
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

    /**
     * Show an error on the passed text field. Automatically remove it after a delay.
     *
     * @param til     the text field
     * @param errorId to show
     */
    protected void showError(@NonNull final TextInputLayout til,
                             @SuppressWarnings("SameParameterValue") @StringRes final int errorId) {
        showError(til, getString(errorId));
    }

    /**
     * Show an error on the passed text field. Automatically remove it after a delay.
     *
     * @param til   the text field
     * @param error to show
     */
    protected void showError(@NonNull final TextInputLayout til,
                             @NonNull final CharSequence error) {
        til.setError(error);
        til.postDelayed(() -> til.setError(null), BaseActivity.ERROR_DELAY_MS);
    }

    /**
     * Show an error message, and finish the Activity after a delay.
     * <p>
     * Not sure if this approach is a really good solution, but it will have to do for now.
     *
     * @param body     (optional) view to make invisible
     * @param stringId message id to show
     */
    protected void finishActivityWithErrorMessage(@Nullable final View body,
                                                  @StringRes final int stringId) {
        if (body != null) {
            body.setVisibility(View.INVISIBLE);
        }
        //noinspection ConstantConditions
        Snackbar.make(getView(), stringId, Snackbar.LENGTH_LONG).show();
        //noinspection ConstantConditions
        getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
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
