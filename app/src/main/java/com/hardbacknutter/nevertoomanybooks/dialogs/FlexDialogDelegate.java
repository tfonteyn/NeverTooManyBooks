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

import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;

/**
 * The interface between the {@link DialogMode} base classes and
 * the delegates.
 *
 * FIXME: use DefaultLifecycleObserver and add the delegate as an observer to the fragment.
 *
 * @see BaseFFDialogFragment
 * @see BaseBottomSheetDialogFragment
 */
public interface FlexDialogDelegate
        extends FlexToolbar {

    /**
     * {@link DialogMode#BottomSheet} ONLY.
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
     * {@link DialogMode#Dialog} ONLY.
     * <p>
     * Called from {@link Fragment#onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     *
     * @param view to use
     */
    void onCreateView(@NonNull View view);

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
     * Called from {@link Fragment#onViewCreated(View, Bundle)}.
     */
    void onViewCreated();

    /**
     * Called from {@link Fragment#onStart()}.
     */
    default void onStart() {
        // no action
    }

    /**
     * Called from {@link Fragment#onResume()}.
     */
    default void onResume() {
        // no action
    }

    /**
     * Called from {@link Fragment#onPause()}.
     */
    default void onPause() {
        // no action
    }

    /**
     * Called from {@link DialogFragment#onCancel(DialogInterface)}.
     *
     * @param dialog .
     */
    default void onCancel(@NonNull final DialogInterface dialog) {
        // no action
    }
}
