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

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;

import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;

/**
 * The interface between the {@link DialogMode} base classes and the delegates.
 * <p>
 * It should be enabled in the {@code onCreateView} method of the below base classes with
 * {@code getLifecycle().addObserver(delegate);}
 *
 * @see BaseFFDialogFragment#onCreateView(LayoutInflater, ViewGroup, Bundle)
 * @see BaseBottomSheetDialogFragment#onCreateView(LayoutInflater, ViewGroup, Bundle)
 */
public interface FlexDialogDelegate
        extends FlexToolbar, DefaultLifecycleObserver {

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
     * Called from {@link Fragment#onViewCreated(View, Bundle)}.
     * <p>
     * There are 4 potential types of toolbar:
     * <ul>
     *     <li>None at all; pass in a {@code null}</li>
     *     <li>Fullscreen/top toolbar with all the menus as configured in xml</li>
     *     <li>BottomSheet toolbar with all the menus as configured in xml</li>
     *     <li>Dialog toolbar where the 'positive', 'negative' and 'neutral'
     *         menu options <strong>should</strong> be removed
     *         in favour of using the bottom button panel for those.</li>
     * </ul>
     *
     * @param toolbar optional Toolbar to show
     */
    void setToolbar(@Nullable Toolbar toolbar);

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
