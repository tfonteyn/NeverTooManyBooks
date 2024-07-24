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

package com.hardbacknutter.nevertoomanybooks.core.widgets.insets;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

class SimpleWindowInsetsListener
        implements OnApplyWindowInsetsListener {

    private final int insetsTypeMask;
    @NonNull
    private final List<InsetsModifier> insetsModifiers;
    private final boolean dispatchToChildren;

    SimpleWindowInsetsListener(final int insetsTypeMask,
                               @NonNull final List<InsetsModifier> insetsModifiers,
                               final boolean dispatchToChildren) {
        this.insetsTypeMask = insetsTypeMask;
        this.insetsModifiers = insetsModifiers;
        this.dispatchToChildren = dispatchToChildren;
    }

    @Override
    @NonNull
    public WindowInsetsCompat onApplyWindowInsets(@NonNull final View v,
                                                  @NonNull final WindowInsetsCompat wic) {

        if (insetsTypeMask != 0 && !insetsModifiers.isEmpty()) {
            final Insets insets = wic.getInsets(insetsTypeMask);
            insetsModifiers.forEach(m -> m.apply(v, insets));
        }

        if (dispatchToChildren && v instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) v;
            for (int c = 0; c < viewGroup.getChildCount(); c++) {
                final View child = viewGroup.getChildAt(c);
                ViewCompat.dispatchApplyWindowInsets(child, wic);
            }
        }

        return WindowInsetsCompat.CONSUMED;
    }
}
