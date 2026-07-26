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
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.SoftwareKeyboardControllerCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.window.layout.WindowMetrics;

import java.util.Objects;
import java.util.function.IntFunction;

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
 *     </li>
 *     <li>add to the button_panel_layout:<br>
 *           {@code app:layout_constraintBottom_toBottomOf="parent"}
 *     </li>
 *     <li>
 *         Special cases<br>
 *         Call {@link #adjustWindowSize(RecyclerView, int, float)}
 *         from {@link #onViewCreated(View, Bundle)}
 *     </li>
 * </ol>
 */
public abstract class FlexClassicDialogFragment
        extends DialogFragment {

    protected static final int MIN_HEIGHT_IN_DP = 200;

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

    /**
     * Fixes the dialog size for smaller screens when the dialog is not already full-screen.
     * <p>
     * <strong>MUST</strong> be called as the last thing from {@link #onViewCreated(View, Bundle)}.
     * <p>
     * FIXME: some day we'll convince the Material library to NOT maximize our dialogs,
     *  and this method will no longer be needed.
     *
     *  @param recyclerView optional RecyclerView to adjust the height of
     *
     * @param dialogMinHeightInDp minimum height of the <strong>window</strong> in dp
     * @param desiredHeightRatio         the ratio to apply to the screen height;
     *                            will be capped at {@code 0.8}
     */
    protected void adjustWindowSize(
            @Nullable final RecyclerView recyclerView,
            @IntRange(from = MIN_HEIGHT_IN_DP) final int dialogMinHeightInDp,
            @FloatRange(from = 0.0, to = 1.0) final float desiredHeightRatio) {

        // Nothing to do.
        if (fullscreen) {
            return;
        }

        // Sanity check
        if (getDialog() == null || getDialog().getWindow() == null) {
            return;
        }

        final FragmentActivity activity = getActivity();
        //noinspection DataFlowIssue
        final ScreenSize screenSize = ScreenSize.compute(activity);

        final int windowWidth;
        if (screenSize.getWidth().isAtLeast(ScreenSize.Value.Medium)) {
            // It's not ideal, but good enough
            windowWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            windowWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        final float heightRatio = computeMaxHeightRatio(desiredHeightRatio, screenSize,
                                                        recyclerView);

        final WindowMetrics metrics = screenSize.getMetrics();

        // Ensure the ratio is not higher than the absolute maximum ratio constant,
        // and apply it to the screen height.
        final float desiredHeightDp = Math.min(1, heightRatio)
                                      * metrics.getHeightDp();

        // Calculate the absolute minimum allowed
        final int minHeightDp = Math.max(dialogMinHeightInDp, MIN_HEIGHT_IN_DP);
        // Ensure the height does not become smaller than the absolute minimum
        final float heightDp = Math.max(minHeightDp, desiredHeightDp);

        // The final height converted into pixels
        final int windowHeight = (int) (metrics.getDensity() * heightDp);

        final Window window = getDialog().getWindow();

        if (BuildConfig.DEBUG /* always */) {
            final IntFunction<String> dbgLp = value -> {
                switch (value) {
                    case ViewGroup.LayoutParams.MATCH_PARENT:
                        return "MATCH_PARENT";
                    case ViewGroup.LayoutParams.WRAP_CONTENT:
                        return "WRAP_CONTENT";
                    default:
                        return String.valueOf(value);
                }
            };

            final WindowManager.LayoutParams lp = window.getAttributes();
            LoggerFactory.getLogger()
                         .d(getClass().getSimpleName(), "adjustWindowSize",
                            "window lp.width=" + dbgLp.apply(lp.width),
                            "window lp.height=" + dbgLp.apply(lp.height),
                            "screen width=" + screenSize.getWidth(),
                            "screen height=" + screenSize.getHeight(),
                            "windowWidth=" + dbgLp.apply(windowWidth),
                            "windowHeight=" + dbgLp.apply(windowHeight)
                         );
        }

        window.setLayout(windowWidth, windowHeight);
    }

    private float computeMaxHeightRatio(final float desiredHeightRatio,
                                        @NonNull final ScreenSize screenSize,
                                        @Nullable final RecyclerView recyclerView) {
        if (recyclerView == null) {
            return desiredHeightRatio;
        }
        final RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null) {
            return desiredHeightRatio;
        }
        final int itemCount = adapter.getItemCount();
        if (itemCount <= 1) {
            return desiredHeightRatio;
        }

        // What we call the "wet finger in the air" method...
        // FIXME: deciding on the final heightRatio should be rational.
        float ratio = desiredHeightRatio;
        if (screenSize.getHeight().isAtLeast(ScreenSize.Value.Expanded)) {
            // Tested on a more or less standard size phone in Portrait.
            // In landscape will work but no amount of tuning will make it fun-to-use.
            if (itemCount == 2) {
                ratio -= 0.35f;
            } else if (itemCount == 3) {
                ratio -= 0.25f;
            } else if (itemCount <= 6) {
                ratio -= 0.15f;
            }
        } else if (screenSize.getHeight().isAtLeast(ScreenSize.Value.Medium)) {
            // Tested om "Small Phone" in Portrait.
            // We consider a "Small Phone" in landscape unusable/unsupported.
            if (itemCount == 2) {
                ratio -= 0.20f;
            } else if (itemCount > 9) {
                // it will get capped at 1.0
                ratio += 0.10f;
            }
        }
        return ratio;
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
