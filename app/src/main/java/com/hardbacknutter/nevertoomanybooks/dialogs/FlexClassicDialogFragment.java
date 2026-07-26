/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.SoftwareKeyboardControllerCompat;
import androidx.fragment.app.DialogFragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ScreenSize;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Provides fullscreen or floating dialog support.
 * <p>
 * Why an action-view in the toolbar?
 * If we want an outline to be drawn AROUND the icon to make it better visible,
 * then we seem forced to use an "actionLayout" with an icon-Button using the outline style.
 * An alternative is to use an icon with outline built-in... but then the actual icon is too small.
 * <p>
 * Reminder: dialogs with a RecyclerView
 * <ol>
 *     <li>add to the RecyclerView:<br>
 *           {@code style="@style/Dialog.Body.RecyclerView"}<br>
 *           {@code app:layout_constrainedHeight="true"}
 *           {@code app:layout_constraintVertical_bias="0.0"}
 *           {@code app:layout_constraintBottom_toTopOf="@id/button_panel_layout"}
 *     </li>
 *     <li>add to the button_panel_layout:<br>
 *           {@code app:layout_constraintBottom_toBottomOf="parent"}
 *     </li>
 * </ol>
 */
public abstract class FlexClassicDialogFragment
        extends DialogFragment {

    /** Must be created/set in {@link #onCreate(Bundle)}. */
    protected FlexDialogDelegate delegate;

    /**
     * Show the dialog fullscreen (default) or as a floating dialog.
     * Decided in {@link #onCreate(Bundle)}
     */
    private boolean fullscreen;

    @CallSuper
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // fullscreen check must be done here as it's needed by both onCreateDialog/onCreateView
        final ScreenSize screenSize = ScreenSize.compute(requireActivity());

        // Use fullscreen mode if the device screen is (too) small.
        fullscreen = !screenSize.isLargeScreen();

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(getClass().getSimpleName(), "onCreate",
                                        "screenSize=" + screenSize,
                                        "fullscreen=" + fullscreen);
        }
    }

    /**
     * Overrule/force this dialog to use floating dialogs or fullscreen mode
     * instead of relying on the screen size.
     * <p>
     * <strong>Must</strong> be called from the child class {@link #onCreate(Bundle)}
     * after it has called {@link FlexClassicDialogFragment#onCreate(Bundle)}.
     *
     * @param enabled flag
     */
    protected void setFullscreen(final boolean enabled) {
        fullscreen = enabled;
    }

    /**
     * Final. Override {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}
     * and/or {@link #onViewCreated(View, Bundle)} instead.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog dialog;
        if (fullscreen) {
            // Reminder: do NOT use "Theme_App_FullScreen"
            dialog = new Dialog(requireContext(), R.style.Theme_App);
        } else {
            dialog = new Dialog(requireContext(), R.style.ThemeOverlay_App_CustomDialog);
        }
        return dialog;
    }

    @Override
    @NonNull
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        // Sanity check
        Objects.requireNonNull(delegate, "delegate not set");

        final View view;
        if (fullscreen) {
            view = delegate.onCreateFullscreen(inflater, container);
        } else {
            view = delegate.onCreateView(inflater, container);
        }

        getLifecycle().addObserver(delegate);
        return view;
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (fullscreen) {
            new InsetsListenerBuilder(view)
                    .padding(Side.Start, Side.End, Side.Top, Side.Bottom)
                    .systemBars()
                    .displayCutout()
                    .apply();
        }
        initDragHandle(view);
        initToolbar(view);
        initButtonBar(view);

        delegate.onViewCreated(fullscreen ? DialogType.Fullscreen : DialogType.Floating);
    }

    private void initDragHandle(@NonNull final View parent) {
        // Ensure the drag handle as used for a BottomSheet is hidden.
        final View dragHandle = parent.findViewById(R.id.drag_handle);
        if (dragHandle != null) {
            dragHandle.setVisibility(View.GONE);
        }
    }

    /**
     * Set up the toolbar: use either the fragment toolbar in fullscreen mode,
     * or else the floating dialog toolbar.
     *
     * @param parent root view
     */
    private void initToolbar(@NonNull final View parent) {
        final Toolbar floatingToolbar = parent.findViewById(R.id.dialog_toolbar);
        if (fullscreen) {
            // Hide the dialog toolbar
            if (floatingToolbar != null) {
                floatingToolbar.setVisibility(View.GONE);
            }
            // and use the fragment toolbar instead
            final Toolbar toolbar = Objects.requireNonNull(parent.findViewById(R.id.toolbar),
                                                           "R.id.toolbar");
            delegate.setToolbar(toolbar);

        } else {
            // Show the dialog toolbar
            if (floatingToolbar != null) {
                floatingToolbar.setVisibility(View.VISIBLE);
                delegate.setToolbar(floatingToolbar);
            }
        }
    }

    /**
     * Set up the (optional) button-bar at the bottom of the dialog:
     * hidden in fullscreen mode and shown in floating dialog mode.
     *
     * @param parent root view
     */
    private void initButtonBar(@NonNull final View parent) {
        final View buttonPanel = parent.findViewById(R.id.button_panel_layout);
        if (fullscreen) {
            // Hide the button bar
            if (buttonPanel != null) {
                buttonPanel.setVisibility(View.GONE);
            }
        } else {
            // Show the button bar
            if (buttonPanel != null) {
                buttonPanel.setVisibility(View.VISIBLE);
                Button button;
                // The negative/cancel button is always hooked up with #onToolbarNavigationClick
                button = buttonPanel.findViewById(R.id.btn_negative);
                if (button != null) {
                    button.setOnClickListener(delegate::onToolbarNavigationClick);
                }
                // The positive and neutral buttons are hooked up with #onToolbarButtonClick
                button = buttonPanel.findViewById(R.id.btn_positive);
                if (button != null) {
                    button.setOnClickListener(delegate::onToolbarButtonClick);
                }
                button = buttonPanel.findViewById(R.id.btn_neutral);
                if (button != null) {
                    button.setOnClickListener(delegate::onToolbarButtonClick);
                }
            }
        }
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        delegate.onCancel(dialog);
        super.onCancel(dialog);
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
     * Hide the keyboard.
     *
     * @param v a View from which we can get the window token.
     */
    private void hideKeyboard(@NonNull final View v) {
        new SoftwareKeyboardControllerCompat(v).hide();
    }
}
