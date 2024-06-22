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

package com.hardbacknutter.nevertoomanybooks.widgets;

import android.widget.EditText;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputLayout;

import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;

public final class TilUtil {
    private TilUtil() {
    }

    /**
     * Add the needed listeners to automatically remove any error text from
     * a {@link TextInputLayout} when the user changes the content.
     *
     * @param editText inner text edit view
     * @param til      outer layout view
     */
    public static void autoRemoveError(@NonNull final EditText editText,
                                       @NonNull final TextInputLayout til) {
        editText.addTextChangedListener((ExtTextWatcher) s -> til.setError(null));
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                til.setError(null);
            }
        });
    }
}
