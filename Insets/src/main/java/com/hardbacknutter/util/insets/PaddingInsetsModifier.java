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

package com.hardbacknutter.util.insets;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;

import java.util.Set;

class PaddingInsetsModifier
        implements InsetsModifier {

    @NonNull
    private final RelativeRect paddings;

    PaddingInsetsModifier(@NonNull final View view,
                          @NonNull final Set<Side> sides) {
        this.paddings = new RelativeRect(view.getPaddingStart(),
                                         view.getPaddingTop(),
                                         view.getPaddingEnd(),
                                         view.getPaddingBottom(),
                                         sides);
    }

    @Override
    public void apply(@NonNull final View view,
                      @NonNull final Insets insets) {

        final RelativeRect transformed = paddings.transform(view, insets);
        view.setPaddingRelative(transformed.start, transformed.top,
                                transformed.end, transformed.bottom);
    }
}
