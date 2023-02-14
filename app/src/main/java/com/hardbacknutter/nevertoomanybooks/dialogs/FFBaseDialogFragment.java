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
import android.widget.EditText;

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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.covers.CoverBrowserDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;

/**
 * Provides fullscreen or floating dialog support.
 * <p>
 * Special cases:
 * <p>
 * {@link #setFloatingDialogWidth(int)}: force the width to a 'dimen' setting to maximize it.
 * e.g. {@link CoverBrowserDialogFragment}
 * <p>
 * {@link #setFloatingDialogHeight(int)}: force the height to a 'dimen' to work
 * around the RecyclerView collapsing or growing to large.
 * e.g. {@link StylePickerDialogFragment}
 * <p>
 * Why an action-view in the toolbar?
 * If we want an outline to be drawn AROUND the icon to make it better visible,
 * then we seem forced to use an "actionLayout" with an icon-Button using the outline style.
 * An alternative is to use an icon with outline builtin... but that makes the actual icon to small.
 */
public abstract class FFBaseDialogFragment
        extends DialogFragment {

    private final int fullscreenLayoutId;
    private final int contentLayoutId;

    /** The <strong>Dialog</strong> Toolbar. Not to be confused with the Activity's Toolbar! */
    @Nullable
    private Toolbar dialogToolbar;
    /** Show the dialog fullscreen (default) or as a floating dialog. */
    private boolean fullscreen;
    private boolean forceFullscreen;

    /** FLOATING DIALOG mode only. Default set in {@link #onAttach(Context)}. */
    @DimenRes
    private int widthDimenResId;
    /** FLOATING DIALOG mode only. Default set in {@link #onAttach(Context)}. */
    @DimenRes
    private int heightDimenResId;

    /**
     * Constructor.
     */
    protected FFBaseDialogFragment(@LayoutRes final int fullscreenLayoutId,
                                   @LayoutRes final int contentLayoutId) {
        this.fullscreenLayoutId = fullscreenLayoutId;
        this.contentLayoutId = contentLayoutId;
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
     * <p>
     * Normally never needed unless a particular dialog needs to be extra-wide.
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
     * <p>
     * This is (almost?) always needed when then content contains a RecyclerView;
     * and (almost?) never needed when not.
     *
     * @param dimenResId the height to use as an 'R.dimen.value'
     */
    protected void setFloatingDialogHeight(@SuppressWarnings("SameParameterValue")
                                           @DimenRes final int dimenResId) {
        heightDimenResId = dimenResId;
    }

    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        // Must be here as needed by both onCreateDialog/onCreateView
        //noinspection ConstantConditions
        fullscreen = WindowSizeClass.getWidth(getActivity()) == WindowSizeClass.COMPACT
                     || forceFullscreen;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        if (fullscreen) {
            return inflater.inflate(fullscreenLayoutId, container, false);
        } else {
            return inflater.inflate(contentLayoutId, container, false);
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
        final Dialog dialog;
        if (fullscreen) {
            dialog = new Dialog(getContext(), R.style.Theme_App_FullScreen);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else {
            dialog = new Dialog(getContext(), com.google.android.material
                    .R.style.ThemeOverlay_Material3_Dialog_Alert);
        }
        return dialog;
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final View buttonPanel = view.findViewById(R.id.buttonPanel);
        if (buttonPanel != null) {
            buttonPanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        }

        final Toolbar floatingToolbar = view.findViewById(R.id.dialog_toolbar);
        if (floatingToolbar != null) {
            floatingToolbar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        }

        if (fullscreen) {
            dialogToolbar = Objects.requireNonNull(view.findViewById(R.id.toolbar), "R.id.toolbar");
        } else {
            dialogToolbar = floatingToolbar;

            //view.setBackgroundResource(R.drawable.bg_floating_dialog);

            if (buttonPanel != null) {
                Button button;
                // The cancel button is always hooked up with #onToolbarNavigationClick
                button = buttonPanel.findViewById(R.id.btn_negative);
                if (button != null) {
                    button.setOnClickListener(this::onToolbarNavigationClick);
                }
                button = buttonPanel.findViewById(R.id.btn_positive);
                if (button != null) {
                    button.setOnClickListener(this::onToolbarButtonClick);
                }
                button = buttonPanel.findViewById(R.id.btn_neutral);
                if (button != null) {
                    button.setOnClickListener(this::onToolbarButtonClick);
                }
            }

            final Resources res = getResources();

            if (widthDimenResId != 0) {
                view.getLayoutParams().width = res.getDimensionPixelSize(widthDimenResId);
            }

            if (heightDimenResId != 0) {
                view.getLayoutParams().height = res.getDimensionPixelSize(heightDimenResId);
            }
        }

        if (dialogToolbar != null) {
            dialogToolbar.setNavigationOnClickListener(this::onToolbarNavigationClick);
            // Simple menu items; i.e. non-action view.
            dialogToolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);

            // Hookup any/all buttons in the action-view to use #onToolbarButtonClick
            final MenuItem menuItem = dialogToolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
            if (menuItem != null) {
                final View actionView = menuItem.getActionView();

                if (actionView instanceof Button) {
                    actionView.setOnClickListener(this::onToolbarButtonClick);

                } else if (actionView instanceof ViewGroup) {
                    final ViewGroup av = (ViewGroup) actionView;
                    for (int c = 0; c < av.getChildCount(); c++) {
                        final View child = av.getChildAt(c);
                        if (child instanceof Button) {
                            child.setOnClickListener(this::onToolbarButtonClick);
                        }
                    }
                }
            }
        }
    }

    /**
     * Set the title of the toolbar.
     *
     * @param resId Resource ID of a string to set as the title
     */
    public void setTitle(@StringRes final int resId) {
        if (dialogToolbar != null) {
            dialogToolbar.setTitle(resId);
        }
    }

    public void setTitle(@Nullable final CharSequence title) {
        if (dialogToolbar != null) {
            dialogToolbar.setTitle(title);
        }
    }

    public void setSubtitle(@Nullable final CharSequence subtitle) {
        if (dialogToolbar != null) {
            dialogToolbar.setSubtitle(subtitle);
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
     * Called when the user clicks a button on the toolbar or the bottom button-bar.
     * The default action ignores the selection.
     *
     * @param button the toolbar action-view-button or button-bar button
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        return false;
    }

    /**
     * Called when the user selects a menu item from the toolbar menu.
     * The default action ignores the selection.
     *
     * @param menuItem {@link MenuItem} that was clicked
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    protected boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
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
     * Add the needed listeners to automatically remove any error text from
     * a {@link TextInputLayout} when the user changes the content.
     *
     * @param editText inner text edit view
     * @param til      outer layout view
     */
    protected void autoRemoveError(@NonNull final EditText editText,
                                   @NonNull final TextInputLayout til) {
        editText.addTextChangedListener((ExtTextWatcher) s -> til.setError(null));
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                til.setError(null);
            }
        });
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
