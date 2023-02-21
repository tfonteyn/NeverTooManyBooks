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

import android.text.TextWatcher;

import androidx.annotation.NonNull;

@FunctionalInterface
public interface ExtTextWatcher
        extends TextWatcher {

    @Override
    default void beforeTextChanged(@NonNull final CharSequence s,
                                   final int start,
                                   final int count,
                                   final int after) {
    }

    @Override
    default void onTextChanged(@NonNull final CharSequence s,
                               final int start,
                               final int before,
                               final int count) {
    }
}
