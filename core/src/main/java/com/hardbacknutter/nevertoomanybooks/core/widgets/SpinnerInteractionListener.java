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
package com.hardbacknutter.nevertoomanybooks.core.widgets;

import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A listener/template class for the {@link android.widget.Spinner} widget.
 * <p>
 * <a href="https://stackoverflow.com/questions/27745948/">
 * android-spinner-onitemselected-called-multiple-times-after-screen-rotation#28466764</a>
 * <em>
 * In general, I've found that there are many different events that can trigger the
 * onItemSelected method, and it is difficult to keep track of all of them. Instead,
 * I found it simpler to use an OnTouchListener to only respond to user-initiated changes.
 * </em>
 */
public abstract class SpinnerInteractionListener
        implements AdapterView.OnItemSelectedListener,
                   View.OnTouchListener {

    private boolean userInteraction;

    /**
     * Attach the given Spinner to this listener.
     *
     * @param spinner to attach to
     */
    public void attach(@NonNull final Spinner spinner) {
        spinner.setOnTouchListener(this);
        spinner.setOnItemSelectedListener(this);
    }

    /**
     * Callback with the selected id.
     *
     * @param id selected
     */
    protected abstract void onItemSelected(long id);

    /** internal listener - use {@link #onItemSelected(long)} instead. */
    @Override
    public boolean onTouch(@NonNull final View v,
                           @NonNull final MotionEvent event) {
        userInteraction = true;
        return false;
    }

    /** internal listener - use {@link #onItemSelected(long)} instead. */
    @Override
    public void onItemSelected(@NonNull final AdapterView<?> parent,
                               @Nullable final View view,
                               final int position,
                               final long id) {
        if (userInteraction) {
            userInteraction = false;
            if (view == null) {
                return;
            }
        }

        onItemSelected(id);
    }

    /** internal listener - use {@link #onItemSelected(long)} instead. */
    public void onNothingSelected(@NonNull final AdapterView<?> parent) {
        // Do nothing
    }
}
