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

import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;

public class InsetsModifier {

    private static final String TAG = "InsetsModifier";
    @NonNull
    private final Map<Type, Insets> typeInsets;
    @NonNull
    private final Map<Type, Set<Side>> typeSides;

    public InsetsModifier(@NonNull final Map<Type, Insets> typeInsets,
                          @NonNull final Map<Type, Set<Side>> typeSides) {
        this.typeInsets = typeInsets;
        this.typeSides = typeSides;
    }

    void apply(@NonNull final View view,
               @NonNull final Insets insets) {
        typeSides.forEach((type, sides) -> {
            final Insets base = Objects.requireNonNull(typeInsets.get(type));
            final int left = base.left + (sides.contains(Side.Left) ? insets.left : 0);
            final int top = base.top + (sides.contains(Side.Top) ? insets.top : 0);
            final int right = base.right + (sides.contains(Side.Right) ? insets.right : 0);
            final int bottom = base.bottom + (sides.contains(Side.Bottom) ? insets.bottom : 0);

            switch (type) {
                case Padding: {
                    view.setPadding(left, top, right, bottom);
                    break;
                }
                case Margins: {
                    final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                            view.getLayoutParams();
                    lp.setMargins(left, top, right, bottom);
                    view.setLayoutParams(lp);
                    break;
                }
            }

            if (BuildConfig.DEBUG /* always */) {
                final int id = view.getId();
                String resourceEntryName;
                try {
                    resourceEntryName = view.getResources().getResourceEntryName(id);
                } catch (@NonNull final Resources.NotFoundException ignore) {
                    resourceEntryName = String.valueOf(id);
                }
                Log.d(TAG, "type=" + type
                           + "; sides=" + sides
                           + "; resName=" + resourceEntryName
                           + "; viewClass=" + view.getClass().getName()
                           + "; insets=" + insets);
            }
        });
    }

    public enum Type {
        Padding,
        Margins
    }

    public static class IMBuilder {

        @NonNull
        private final View view;
        private final Map<Type, Insets> typeInsets = new EnumMap<>(Type.class);
        private final Map<Type, Set<Side>> typeSides = new EnumMap<>(Type.class);

        public IMBuilder(@NonNull final View view) {
            this.view = view;
        }

        @NonNull
        public IMBuilder margins(@NonNull final Side... sides) {
            final ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            typeInsets.put(Type.Margins, Insets.of(lp.leftMargin,
                                                   lp.topMargin,
                                                   lp.rightMargin,
                                                   lp.bottomMargin));
            typeSides.put(Type.Margins, Set.of(sides));
            return this;
        }

        @NonNull
        public IMBuilder padding(@NonNull final Side... sides) {
            typeInsets.put(Type.Padding, Insets.of(view.getPaddingLeft(),
                                                   view.getPaddingTop(),
                                                   view.getPaddingRight(),
                                                   view.getPaddingBottom()));
            typeSides.put(Type.Padding, Set.of(sides));
            return this;
        }

        @NonNull
        public InsetsModifier create() {
            return new InsetsModifier(typeInsets, typeSides);
        }
    }
}
