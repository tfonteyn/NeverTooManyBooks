/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.view.View;
import android.widget.AdapterView;

import androidx.annotation.NonNull;

/**
 * A listener/template class for the {@link android.widget.Spinner} widget.
 *
 * <a href="https://stackoverflow.com/questions/27745948/android-spinner-onitemselected-called-multiple-times-after-screen-rotation#28466764">
 * stackoverflow</a>
 * <em>
 * In general, I've found that there are many different events that can trigger the
 * onItemSelected method, and it is difficult to keep track of all of them. Instead,
 * I found it simpler to use an OnTouchListener to only respond to user-initiated changes.
 * </em>>
 * <pre>
 * {@code
 *
 *     SpinnerInteractionListener mListener =
 *         new SpinnerInteractionListener() {
 *
 *             private boolean userInteraction = false;
 *
 *             @SuppressLint("ClickableViewAccessibility")
 *             @Override
 *             public boolean onTouch(@NonNull final View v,
 *                                    @NonNull final MotionEvent event) {
 *                 userInteraction = true;
 *                 return false;
 *             }
 *
 *             @Override
 *             public void onItemSelected(@NonNull final AdapterView<?> parent,
 *                                        @Nullable final View view,
 *                                        final int position,
 *                                        final long id) {
 *                 if (userInteraction) {
 *                     userInteraction = false;
 *                     // Your selection handling code here
 *                 }
 *             }
 *         };
 *
 *     // Add the listener to the spinner as both
 *     // an OnItemSelectedListener + an OnTouchListener:
 *     mSpinnerView.setOnTouchListener(mListener);
 *     mSpinnerView.setOnItemSelectedListener(mListener);
 * }
 * </pre>
 */
public interface SpinnerInteractionListener
        extends
        AdapterView.OnItemSelectedListener,
        View.OnTouchListener {

    /**
     * Handy default.
     * {@inheritDoc}
     */
    default void onNothingSelected(@NonNull final AdapterView<?> parent) {
        // Do nothing
    }
}
