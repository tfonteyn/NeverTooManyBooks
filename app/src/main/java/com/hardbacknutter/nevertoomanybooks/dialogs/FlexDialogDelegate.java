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

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import com.google.android.material.appbar.MaterialToolbar;

import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;

/**
 * The interface between the {@link DialogMode} base classes and the delegates.
 * <p>
 * It should be enabled in the {@code onCreateView} method of the below base classes with
 * {@code getLifecycle().addObserver(delegate);}
 *
 * @see FlexClassicDialogFragment#onCreateView(LayoutInflater, ViewGroup, Bundle)
 * @see FlexBottomSheetDialogFragment#onCreateView(LayoutInflater, ViewGroup, Bundle)
 */
public interface FlexDialogDelegate
        extends FlexToolbar, DefaultLifecycleObserver {

    /** Private. Used to handle differences in height on 360dp-height devices. */
    int LIMITD_HEIGHT_PX = 800;

    /**
     * {@link DialogType#BottomSheet} and {@link DialogType#Floating}.
     * <p>
     * Called from {@link Fragment#onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     *
     * @param inflater  LayoutInflater
     * @param container parent view
     *
     * @return the view
     */
    @NonNull
    View onCreateView(@NonNull LayoutInflater inflater,
                      @Nullable ViewGroup container);

    /**
     * {@link DialogType#Fullscreen}.
     * <p>
     * Called from {@link Fragment#onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     *
     * @param inflater  LayoutInflater
     * @param container parent view
     *
     * @return the view
     */
    @NonNull
    View onCreateFullscreen(@NonNull LayoutInflater inflater,
                            @Nullable ViewGroup container);

    /**
     * HACK.... a "Small Phone" with 360dp (720px) is smaller
     * than a larger phone with 360dp (1080px).
     * For bottom-sheets it can be REALLY difficult...
     *
     * @param activity to check
     *
     * @return flag
     */
    default boolean isVeryLimitedHeight(@NonNull final Activity activity) {
        final WindowMetrics metrics = WindowMetricsCalculator
                .getOrCreate().computeCurrentWindowMetrics(activity);
        final Rect bounds = metrics.getBounds();
        // not enough samples... we just have to guess for now; 720 < x < 1080
        return bounds.height() < LIMITD_HEIGHT_PX;
    }

    /**
     * Only used when {@link #isVeryLimitedHeight(Activity)} returned {@code true}.
     *
     * @param dialogContent hosting layout
     * @param dialogToolbar to adjust
     */
    default void letToolbarOverlapDragHandle(@NonNull final ConstraintLayout dialogContent,
                                             @NonNull final MaterialToolbar dialogToolbar) {
        final ConstraintSet set = new ConstraintSet();
        set.clone(dialogContent);
        // remove layout_constraintTop_toBottomOf "@id/drag_handle"
        set.clear(dialogToolbar.getId(), ConstraintSet.TOP);
        // set layout_constraintTop_toTopOf to "parent"
        set.connect(dialogToolbar.getId(), ConstraintSet.TOP,
                    dialogContent.getId(), ConstraintSet.TOP);

        set.applyTo(dialogContent);
    }

    /**
     * Delegates can force floating dialogs to go into fullscreen/dialog mode overriding
     * the screen-size based decision logic.
     * <p>
     * This method is only applicable when the delegates run in dialog mode.
     * Not applicable to BottomSheet mode.
     * <p>
     * The default implementation does <strong>NOT</strong> override.
     *
     * @return {@code null} leave it to the screen-size based decision logic.
     *         {@code false} override, forcing floating dialogs
     *         {@code true} override, forcing fullscreen
     */
    @Nullable
    default Boolean isForceFullscreen() {
        return null;
    }

    /**
     * Get the previously set toolbar.
     *
     * @return the toolbar
     *
     * @throws NullPointerException if there was no toolbar set
     */
    @NonNull
    Toolbar getToolbar();

    /**
     * Called from {@link Fragment#onViewCreated(View, Bundle)}.
     * <p>
     * There are 4 potential types of toolbar:
     * <ol>
     *     <li>None at all; pass in a {@code null}</li>
     *     <li>Fullscreen/top toolbar with all the menus as configured in XML</li>
     *     <li>BottomSheet toolbar with all the menus as configured in XML</li>
     *     <li>Dialog toolbar where the 'positive', 'negative' and 'neutral'
     *         menu options <strong>should</strong> be removed
     *         in favour of using the bottom button panel for those.</li>
     * </ol>
     *
     * @param toolbar optional Toolbar to show
     */
    void setToolbar(@Nullable Toolbar toolbar);

    /**
     * Called from {@link Fragment#onViewCreated(View, Bundle)}.
     *
     * @param dialogType the type
     */
    void onViewCreated(@NonNull DialogType dialogType);

    /**
     * Called from {@link DialogFragment#onCancel(DialogInterface)}.
     *
     * @param dialog .
     */
    default void onCancel(@NonNull final DialogInterface dialog) {
        // no action
    }
}
