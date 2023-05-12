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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.textfield.TextInputLayout;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

/**
 * Provides fullscreen or floating dialog support.
 * <p>
 * Why an action-view in the toolbar?
 * If we want an outline to be drawn AROUND the icon to make it better visible,
 * then we seem forced to use an "actionLayout" with an icon-Button using the outline style.
 * An alternative is to use an icon with outline builtin... but then the actual icon is too small.
 * <p>
 * Reminder: dialogs with a RecyclerView
 * <ol>
 *     <li>add to the root layout of the _content.xml:<br>
 *         {@code android:minHeight="@dimen/floating_dialog_recycler_view_min_height"}
 *     </li>
 *     <li>add to the RecyclerView:<br>
 *           {@code style="@style/Dialog.Body.RecyclerView"}
 *     </li>
 *     <li>add to the RecyclerView:<br>
 *         {@code app:layout_constraintBottom_toTopOf="@id/button_panel_layout"}
 *     </li>
 *     <li>
 *         Call {@link #adjustWindowSize()} from {@link #onViewCreated(View, Bundle)}
 *     </li>
 * </ol>
 * <p>
 * Special cases see {@link #adjustWindowSize} methods.
 */
public abstract class FFBaseDialogFragment
        extends DialogFragment {

    private static final String TAG = "FFBaseDialogFragment";

    private final int fullscreenLayoutId;
    private final int contentLayoutId;
    private final boolean forceFullscreen;
    @NonNull
    private final Set<WindowSizeClass> useFullscreenWidth = EnumSet.noneOf(WindowSizeClass.class);
    @NonNull
    private final Set<WindowSizeClass> useFullscreenHeight = EnumSet.noneOf(WindowSizeClass.class);

    /** The <strong>Dialog</strong> Toolbar. Not to be confused with the Activity's Toolbar! */
    @Nullable
    private Toolbar dialogToolbar;
    /** Show the dialog fullscreen (default) or as a floating dialog. */
    private boolean fullscreen;

    /**
     * Constructor.
     *
     * @param fullscreenLayoutId  the layout resource id which offers a full screen
     *                            dialog-fragment with a CoordinatorLayout/AppBarLayout
     *                            at the root.
     * @param contentLayoutId     the layout resource if which can be used to view the same
     *                            dialog-fragment as a floating dialog; i.e. without
     *                            the CoordinatorLayout/AppBarLayout.
     *                            Set this to {@code 0} to <strong>force</strong> fullscreen usage
     *                            on all screen sizes.
     * @param useFullscreenWidth  set of WindowSizeClass when to use fullscreen.
     *                            Ignored when contentLayoutId == 0
     * @param useFullscreenHeight set of WindowSizeClass when to use fullscreen.
     *                            Ignored when contentLayoutId == 0
     */
    protected FFBaseDialogFragment(@LayoutRes final int fullscreenLayoutId,
                                   @LayoutRes final int contentLayoutId,
                                   @NonNull final Set<WindowSizeClass> useFullscreenWidth,
                                   @NonNull final Set<WindowSizeClass> useFullscreenHeight) {
        this.fullscreenLayoutId = fullscreenLayoutId;
        this.contentLayoutId = contentLayoutId;
        forceFullscreen = contentLayoutId == 0;
        this.useFullscreenWidth.addAll(useFullscreenWidth);
        this.useFullscreenHeight.addAll(useFullscreenHeight);
    }

    protected FFBaseDialogFragment(@LayoutRes final int fullscreenLayoutId,
                                   @LayoutRes final int contentLayoutId) {
        this(fullscreenLayoutId, contentLayoutId,
             EnumSet.of(WindowSizeClass.Compact),
             EnumSet.of(WindowSizeClass.Compact));
    }

    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        // fullscreen check must be done here as it's needed by both onCreateDialog/onCreateView
        final FragmentActivity activity = requireActivity();
        final WindowSizeClass width = WindowSizeClass.getWidth(activity);
        final WindowSizeClass height = WindowSizeClass.getHeight(activity);
        fullscreen = forceFullscreen
                     ||
                     useFullscreenWidth.contains(width) && useFullscreenHeight.contains(height);

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, "onAttach",
                                        getClass().getSimpleName(),
                                        "forceFullscreen=" + forceFullscreen,
                                        "width=" + width,
                                        "height=" + height,
                                        "fullscreen=" + fullscreen);
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
            dialog = new Dialog(requireContext(), R.style.Theme_App_FullScreen);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else {
            dialog = new Dialog(requireContext(), R.style.ThemeOverlay_App_CustomDialog);
        }
        return dialog;
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

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final View buttonPanel = view.findViewById(R.id.button_panel_layout);
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
     * FIXME: Workaround for dialogs which need to be extra-wide.
     *  Maybe try to use minWidth instead?
     * <strong>MUST</strong> be called as the last thing from {@link #onViewCreated(View, Bundle)}.
     * <strong>ONLY ONE</strong> {@link #adjustWindowSize} method should be called.
     *
     * @param widthDimenResId the width to use as an 'R.dimen.value'
     */
    protected void adjustWindowSize(@DimenRes final int widthDimenResId) {
        // Sanity check
        if (getDialog() != null && getDialog().getWindow() != null) {
            final Window window = getDialog().getWindow();
            final WindowManager.LayoutParams attributes = window.getAttributes();
            window.setLayout(getResources().getDimensionPixelSize(widthDimenResId),
                             attributes.height);
        }
    }

    /**
     * FIXME: Workaround for dialogs with a RecyclerView.
     * <strong>MUST</strong> be called as the last thing from {@link #onViewCreated(View, Bundle)}.
     * <strong>ONLY ONE</strong> {@link #adjustWindowSize} method should be called.
     * <p>
     * Dev note: RecyclerView in a dialog is cursed... and should NOT be used.
     */
    protected void adjustWindowSize() {
        // If we are NOT already in fullscreen mode:
        // - phones: make the dialog match the entire screen size; i.e. force fullscreen
        //   but keep the dialog UI.
        // - tablets: make the dialog wrap the content width, but match the screen size
        if (!fullscreen) {
            // Sanity check
            if (getDialog() != null && getDialog().getWindow() != null) {
                //noinspection ConstantConditions
                final WindowSizeClass height = WindowSizeClass.getHeight(getContext());
                if (height == WindowSizeClass.Compact || height == WindowSizeClass.Medium) {

                    if (BuildConfig.DEBUG /* always */) {
                        LoggerFactory.getLogger().d(TAG, "workaroundRecyclerViewContent");
                    }

                    final WindowSizeClass width = WindowSizeClass.getWidth(getContext());
                    final int lpWidth;
                    if (width == WindowSizeClass.Compact || width == WindowSizeClass.Medium) {
                        // Maximize the dialog width; i.e. make the Dialog match the full screen.
                        lpWidth = ViewGroup.LayoutParams.MATCH_PARENT;
                    } else {
                        // WindowSizeClass.Expanded: keep the dialog width reasonable
                        lpWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
                    }
                    // Either way, the height is always maximized.
                    getDialog().getWindow().setLayout(lpWidth, ViewGroup.LayoutParams.MATCH_PARENT);
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
