/*
 * @Copyright 2018-2022 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.covers.CoverBrowserDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

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
 * Why an action-view in the toolbar?
 * If we want an outline to be drawn AROUND the icon, then we seem forced to use an "actionLayout"
 * with an icon-Button using the outline style.
 * <p>
 * Only alternative is to use an icon with outline builtin... which makes the actual icon to small.
 */
public abstract class FFBaseDialogFragment
        extends DialogFragment {

    private static final int USE_DEFAULT = -1;
    /** The <strong>Dialog</strong> Toolbar. Not to be confused with the Activity's Toolbar! */
    private Toolbar dialogToolbar;
    /** Show the dialog fullscreen (default) or as a floating dialog. */
    private boolean fullscreen;
    private boolean forceFullscreen;

    /** FLOATING DIALOG mode only. Default set in {@link #onAttach(Context)}. */
    @DimenRes
    private int widthDimenResId = USE_DEFAULT;
    /** FLOATING DIALOG mode only. Default set in {@link #onAttach(Context)}. */
    @DimenRes
    private int heightDimenResId = USE_DEFAULT;
    /** FLOATING DIALOG mode only. Default set in {@link #onAttach(Context)}. */
    @DimenRes
    private int marginBottomDimenResId = USE_DEFAULT;

    /**
     * Constructor.
     *
     * @param contentLayoutId to use
     */
    protected FFBaseDialogFragment(@LayoutRes final int contentLayoutId) {
        super(contentLayoutId);
    }

    /**
     * If required, this <strong>MUST</strong> be called from the constructor.
     */
    protected void setForceFullscreen() {
        forceFullscreen = true;
    }

    /**
     * FLOATING DIALOG mode only. Has no effect in fullscreen mode.
     * If required, this <strong>MUST</strong> be called from the constructor.
     * <p>
     * Default: {@code R.dimen.floating_dialogs_min_width}
     *
     * @param dimenResId the width to use as an 'R.dimen.value'
     */
    protected void setFloatingDialogWidth(@SuppressWarnings("SameParameterValue")
                                          @DimenRes final int dimenResId) {
        widthDimenResId = dimenResId;
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
        heightDimenResId = dimenResId;
    }

    /**
     * FLOATING DIALOG mode only. Has no effect in fullscreen mode.
     * If required, this <strong>MUST</strong> be called from the constructor.
     * <p>
     * Default: the resolved {@code R.attr.actionBarSize}
     *
     * @param dimenResId the bottom margin to use as an 'R.dimen.value'
     */
    protected void setFloatingDialogMarginBottom(@SuppressWarnings("SameParameterValue")
                                                 @DimenRes final int dimenResId) {
        marginBottomDimenResId = dimenResId;
    }

    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        //noinspection ConstantConditions
        fullscreen = WindowSizeClass.getWidth(getActivity()) == WindowSizeClass.COMPACT
                     || forceFullscreen;

        if (fullscreen) {
            setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);

        } else {
            if (widthDimenResId == USE_DEFAULT) {
                widthDimenResId = R.dimen.floating_dialogs_width;
            }
            if (heightDimenResId == USE_DEFAULT) {
                heightDimenResId = 0;
            }
            if (marginBottomDimenResId == USE_DEFAULT) {
                marginBottomDimenResId = AttrUtils.getResId(context, R.attr.actionBarSize);
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

        dialogToolbar = Objects.requireNonNull(view.findViewById(R.id.toolbar), "R.id.toolbar");
        dialogToolbar.setNavigationOnClickListener(this::onToolbarNavigationClick);
        dialogToolbar.setOnMenuItemClickListener(item -> onToolbarMenuItemClick(item, null));

        final View buttonPanel = view.findViewById(R.id.buttonPanel);

        if (fullscreen) {
            // Always hide the button bar if there is one
            if (buttonPanel != null) {
                buttonPanel.setVisibility(View.GONE);
            }

            // Hookup any/all buttons in the action-view.
            final MenuItem menuItem = dialogToolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
            if (menuItem != null) {
                final View actionView = menuItem.getActionView();

                if (actionView instanceof Button) {
                    actionView.setOnClickListener(v -> onToolbarMenuItemClick(
                            menuItem, (Button) v));

                } else if (actionView instanceof ViewGroup) {
                    final ViewGroup av = (ViewGroup) actionView;
                    for (int c = 0; c < av.getChildCount(); c++) {
                        final View child = av.getChildAt(c);
                        if (child instanceof Button) {
                            child.setOnClickListener(v -> onToolbarMenuItemClick(
                                    menuItem, (Button) v));
                        }
                    }
                }
            }

        } else {
            if (buttonPanel != null) {
                // Always show the button bar
                buttonPanel.setVisibility(View.VISIBLE);

                // Hookup the button-bar 'cancel' and 'ok' buttons.
                final MenuItem menuItem =
                        dialogToolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
                if (menuItem != null) {
                    // Always hide the menu item
                    menuItem.setVisible(false);

                    // The ok-button is a simple button on the button panel.
                    // The ok-button is ignored, and the toolbar button is passed to
                    // the onToolbarMenuItemClick method.
                    final Button okButton = buttonPanel.findViewById(R.id.btn_ok);
                    if (okButton != null) {
                        okButton.setVisibility(View.VISIBLE);

                        final View actionView = menuItem.getActionView();
                        if (actionView instanceof Button) {
                            if (actionView.getId() == R.id.btn_action) {
                                hookupButton(okButton, menuItem, (Button) actionView);
                            }
                        } else if (actionView instanceof ViewGroup) {
                            final ViewGroup av = (ViewGroup) actionView;
                            for (int c = 0; c < av.getChildCount(); c++) {
                                final View child = av.getChildAt(c);
                                if (child instanceof Button) {
                                    if (child.getId() == R.id.btn_action) {
                                        hookupButton(okButton, menuItem, (Button) child);

                                    } else if (child.getId() == R.id.btn_action_2) {
                                        final Button nBtn =
                                                buttonPanel.findViewById(R.id.btn_neutral);
                                        hookupButton(nBtn, menuItem, (Button) child);

                                    } else {
                                        throw new IllegalStateException("Max 2 buttons");
                                    }
                                }
                            }
                        }
                    }
                }

                // Toolbar navigation icon is mapped to the cancel button. Both are visible.
                final Button cancelBtn = buttonPanel.findViewById(R.id.btn_cancel);
                if (cancelBtn != null) {
                    // If the nav button has a description, use it for the 'cancel' button.
                    final CharSequence text = dialogToolbar.getNavigationContentDescription();
                    if (text != null) {
                        cancelBtn.setText(text);
                    }
                    cancelBtn.setOnClickListener(this::onToolbarNavigationClick);
                }
            }

            final Resources res = getResources();

            if (widthDimenResId != 0) {
                view.getLayoutParams().width = res.getDimensionPixelSize(widthDimenResId);
            }

            if (heightDimenResId != 0) {
                view.getLayoutParams().height = res.getDimensionPixelSize(heightDimenResId);
            }

            if (marginBottomDimenResId != 0) {
                final int marginBottom = res.getDimensionPixelSize(marginBottomDimenResId);
                final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                        view.findViewById(R.id.body_frame).getLayoutParams();
                lp.setMargins(0, 0, 0, marginBottom);
            }
        }
    }

    /**
     * Use the ContentDescription of the actionView as the text for the button.
     * Set the button click listener to match the menuItem click.
     *
     * @param button       on the dialog's bottom button bar to hookup
     * @param menuItem     to pass to the listener
     * @param actionButton from the toolbar; to use the content-description from,
     *                     and to pass to the listener
     */
    private void hookupButton(@NonNull final Button button,
                              @NonNull final MenuItem menuItem,
                              @NonNull final Button actionButton) {
        // We copy the title/listener from the toolbar button
        final CharSequence text = actionButton.getContentDescription();
        if (text == null) {
            throw new IllegalStateException("Missing ContentDescription");
        }

        button.setVisibility(View.VISIBLE);
        button.setText(text);
        button.setOnClickListener(v -> onToolbarMenuItemClick(menuItem, actionButton));
    }

    /**
     * Set the title of the toolbar.
     *
     * @param resId Resource ID of a string to set as the title
     */
    public void setTitle(@StringRes final int resId) {
        dialogToolbar.setTitle(resId);
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
     * @param item   {@link MenuItem} that was clicked
     * @param button the actual button if the menu-item has an action-view
     *               otherwise {@code null}
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item,
                                             @Nullable final Button button) {
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
        til.postDelayed(() -> til.setError(null), BaseActivity.DELAY_LONG_MS);
    }

    /**
     * Hide the keyboard.
     *
     * @param view a View from which we can get the window token.
     */
    protected void hideKeyboard(@NonNull final View view) {
        final InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
