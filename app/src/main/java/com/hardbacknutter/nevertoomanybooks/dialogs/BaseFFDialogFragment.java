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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.SoftwareKeyboardControllerCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.window.layout.WindowMetricsCalculator;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;
import com.hardbacknutter.util.logger.LoggerFactory;

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
 *           {@code style="@style/Dialog.Body.RecyclerView"}<br>
 *           {@code app:layout_constraintBottom_toTopOf="@id/button_panel_layout"}
 *     </li>
 *     <li>
 *         Special cases<br>
 *         Call {@link #adjustWindowSize(RecyclerView, float)}
 *         from {@link #onViewCreated(View, Bundle)}
 *     </li>
 * </ol>
 */
public abstract class BaseFFDialogFragment
        extends DialogFragment {

    private final int fullscreenLayoutId;
    private final int contentLayoutId;
    @NonNull
    private final Set<WindowSizeClass> useFullscreenWidth = EnumSet.of(WindowSizeClass.Compact);
    @NonNull
    private final Set<WindowSizeClass> useFullscreenHeight = EnumSet.of(WindowSizeClass.Compact);

    /** Must be created/set in {@link #onCreate(Bundle)}. */
    protected FlexDialogDelegate delegate;

    /**
     * Show the dialog fullscreen (default) or as a floating dialog.
     * Decided in {@link #onAttach(Context)}
     */
    private boolean fullscreen;

    /**
     * Constructor.
     *
     * @param fullscreenLayoutId the layout resource id which offers a full screen
     *                           dialog-fragment with a CoordinatorLayout/AppBarLayout
     *                           at the root.
     * @param contentLayoutId    the layout resource if which can be used to view the same
     *                           dialog-fragment as a floating dialog; i.e. without
     *                           the CoordinatorLayout/AppBarLayout.
     *                           Set this to {@code 0} to <strong>force</strong> fullscreen usage
     *                           on all screen sizes.
     */
    protected BaseFFDialogFragment(@LayoutRes final int fullscreenLayoutId,
                                   @LayoutRes final int contentLayoutId) {
        this.fullscreenLayoutId = fullscreenLayoutId;
        this.contentLayoutId = contentLayoutId;
    }

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
     * @param useFullscreenWidth  set of {@link WindowSizeClass} when to use fullscreen.
     *                            Ignored when {@code contentLayoutId == 0}
     * @param useFullscreenHeight set of {@link WindowSizeClass} when to use fullscreen.
     *                            Ignored when {@code contentLayoutId == 0}
     */
    protected BaseFFDialogFragment(@LayoutRes final int fullscreenLayoutId,
                                   @LayoutRes final int contentLayoutId,
                                   @NonNull final Set<WindowSizeClass> useFullscreenWidth,
                                   @NonNull final Set<WindowSizeClass> useFullscreenHeight) {
        this(fullscreenLayoutId, contentLayoutId);
        this.useFullscreenWidth.addAll(useFullscreenWidth);
        this.useFullscreenHeight.addAll(useFullscreenHeight);
    }

    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        // fullscreen check must be done here as it's needed by both onCreateDialog/onCreateView
        final FragmentActivity activity = requireActivity();
        final WindowSizeClass width = WindowSizeClass.getWidth(activity);
        final WindowSizeClass height = WindowSizeClass.getHeight(activity);
        // Use fullscreen mode if there is no content layout set,
        // or if the device screen does not match our size requirements as set in the constructor.
        fullscreen = this.contentLayoutId == 0
                     ||
                     useFullscreenWidth.contains(width) && useFullscreenHeight.contains(height);

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(getClass().getSimpleName(), "onAttach",
                                        "forceFullscreen=" + (this.contentLayoutId == 0),
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

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        // Sanity check
        Objects.requireNonNull(delegate, "delegate not set");

        final View view;
        if (fullscreen) {
            view = inflater.inflate(fullscreenLayoutId, container, false);
        } else {
            view = inflater.inflate(contentLayoutId, container, false);
        }
        delegate.onCreateView(view);
        return view;
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Ensure the drag handle is hidden.
        final View dragHandle = view.findViewById(R.id.drag_handle);
        if (dragHandle != null) {
            dragHandle.setVisibility(View.GONE);
        }

        final Toolbar floatingToolbar = view.findViewById(R.id.dialog_toolbar);
        final View buttonPanel = view.findViewById(R.id.button_panel_layout);

        if (fullscreen) {
            // Hide the dialog toolbar
            if (floatingToolbar != null) {
                floatingToolbar.setVisibility(View.GONE);
            }
            delegate.setToolbar(Objects.requireNonNull(view.findViewById(R.id.toolbar),
                                                       "R.id.toolbar"));

            // Hide the button bar at the bottom of the dialog
            if (buttonPanel != null) {
                buttonPanel.setVisibility(View.GONE);
            }
        } else {
            // Show the dialog toolbar
            if (floatingToolbar != null) {
                floatingToolbar.setVisibility(View.VISIBLE);
            }
            // can be null, that's ok
            delegate.setToolbar(floatingToolbar);

            // Show the button bar at the bottom of the dialog
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

        delegate.onViewCreated();
    }

    /**
     * Fixes the dialog size for smaller screens when the dialog is not already full-screen.
     * <p>
     * <strong>MUST</strong> be called as the last thing from {@link #onViewCreated(View, Bundle)}.
     * <p>
     * URGENT: RecyclerView in a dialog is cursed... we need to redo this whole floating
     *  dialog code
     *
     * @param recyclerView optional RecyclerView to adjust the height of
     * @param heightRatio  the ratio to apply to the screen height;
     *                     The resulting value is used to set the RecyclerView absolute height.
     */
    protected void adjustWindowSize(@Nullable final RecyclerView recyclerView,
                                    final float heightRatio) {
        if (fullscreen) {
            return;
        }

        // Sanity check
        if (getDialog() == null || getDialog().getWindow() == null) {
            return;
        }

        final FragmentActivity activity = getActivity();
        //noinspection DataFlowIssue
        final WindowSizeClass width = WindowSizeClass.getWidth(activity);
        final WindowSizeClass height = WindowSizeClass.getHeight(activity);

        int lpWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        int lpHeight = ViewGroup.LayoutParams.MATCH_PARENT;

        if (width == WindowSizeClass.Expanded) {
            lpWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        // GitHub #17 with v4.4.1
        // Pixel 6a:
        // lp.width=WRAP_CONTENT|lp.height=WRAP_CONTENT|
        // width=Compact|height=Expanded|
        // lpWidth=MATCH_PARENT|lpHeight=WRAP_CONTENT|.
        //==> the filter/style dialogs are squashed vertically
        //
        // 10" tablet
        // lp.width=WRAP_CONTENT|lp.height=WRAP_CONTENT|
        // width=Compact|height=Expanded|
        // lpWidth=MATCH_PARENT|lpHeight=WRAP_CONTENT|.
        //==> the filter/style dialogs show ok!

        // So we can't rely on Android being consistent (surprise...)
        // 2023-06-09: patch 4.4.2: adjust the recyclerView manually
        if (height == WindowSizeClass.Expanded) {
            lpHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (recyclerView != null) {
                final ViewGroup.LayoutParams rvLp = recyclerView.getLayoutParams();
                // Sanity check
                if (rvLp != null) {
                    final int heightPx = WindowMetricsCalculator
                            .getOrCreate()
                            .computeCurrentWindowMetrics(activity)
                            .getBounds()
                            .height();
                    // We're setting the height relative to the screen height
                    // with a ratio as per Dialog needs.
                    rvLp.height = (int) (heightPx * heightRatio);
                    recyclerView.setLayoutParams(rvLp);
                }
            }
        }

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
                            "lp.width=" + dbgLp.apply(lp.width),
                            "lp.height=" + dbgLp.apply(lp.height),
                            "width=" + width,
                            "height=" + height,
                            "lpWidth=" + dbgLp.apply(lpWidth),
                            "lpHeight=" + dbgLp.apply(lpHeight));
        }

        window.setLayout(lpWidth, lpHeight);
    }

    @Override
    public void onStart() {
        super.onStart();
        delegate.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        delegate.onResume();
    }

    @Override
    public void onPause() {
        delegate.onPause();
        super.onPause();
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
