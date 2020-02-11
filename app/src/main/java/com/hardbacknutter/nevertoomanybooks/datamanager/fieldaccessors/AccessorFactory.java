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
package com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.chip.ChipGroup;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;

public final class AccessorFactory {

    private static final String TAG = "AccessorFactory";

    /**
     * Automatically determine a suitable FieldDataAccessor based on the View type.
     *
     * @param view to examine
     *
     * @return FieldDataAccessor
     */
    @NonNull
    public static <T> FieldDataAccessor<T> createAccessor(@NonNull final View view) {

        FieldDataAccessor accessor;

        // Reminder: the order is important due to the class inheritance.
        // e.g. a CompoundButton IS a Button IS a TextView... etc.
        if (view instanceof ChipGroup) {
            accessor = new BitmaskChipGroupAccessor();

        } else if (view instanceof CompoundButton) {
            accessor = new CompoundButtonAccessor();

        } else if (view instanceof EditText) {
            accessor = new EditTextAccessor<>();

        } else if (view instanceof Button) {
            accessor = new TextViewAccessor<>();

        } else if (view instanceof TextView) {
            accessor = new TextViewAccessor<>();

        } else if (view instanceof RatingBar) {
            accessor = new RatingBarAccessor();

        } else if (view instanceof Spinner) {
            accessor = new SpinnerAccessor();

        } else {
            // field has no layout, store in a dummy String.
            accessor = new StringDataAccessor();
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "Using StringDataAccessor");
            }
        }

        //noinspection unchecked
        return (FieldDataAccessor<T>) accessor;
    }
}
