/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface MultiOnFocusChangeListener<T, V extends View>
        extends View.OnFocusChangeListener {

    @NonNull
    Field<T, V> addOnFocusChangeListener(@NonNull View.OnFocusChangeListener listener);

    void removeOnFocusChangeListener(@NonNull View.OnFocusChangeListener listener);

    @Nullable
    List<View.OnFocusChangeListener> getOnFocusChangeListeners();

    @Override
    default void onFocusChange(@NonNull final View v,
                               final boolean hasFocus) {
        final List<View.OnFocusChangeListener> listeners = getOnFocusChangeListeners();
        if (listeners != null) {
            listeners.forEach(l -> l.onFocusChange(v, hasFocus));
        }
    }
}
