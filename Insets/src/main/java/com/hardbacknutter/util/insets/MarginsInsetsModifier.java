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
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;

import java.util.Set;

class MarginsInsetsModifier
        implements InsetsModifier {

    @NonNull
    private final RelativeRect margins;

    MarginsInsetsModifier(@NonNull final View view,
                          @NonNull final Set<Side> sides) {
        final ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        margins = new RelativeRect(lp.getMarginStart(),
                                   lp.topMargin,
                                   lp.getMarginEnd(),
                                   lp.bottomMargin,
                                   sides);
    }

    @Override
    public void apply(@NonNull final View view,
                      @NonNull final Insets insets) {

        final RelativeRect transformed = margins.transform(view, insets);

        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                view.getLayoutParams();
        // lp.setMarginsRelative(..) is '@hide'.... why?? THANKS Google...
        lp.setMargins(transformed.start, transformed.top,
                      transformed.end, transformed.bottom);
        lp.setMarginStart(transformed.start);
        lp.setMarginEnd(transformed.end);

        view.setLayoutParams(lp);
    }
}
