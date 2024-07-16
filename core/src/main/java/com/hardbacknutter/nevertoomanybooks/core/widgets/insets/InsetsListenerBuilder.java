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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;
import java.util.Set;

public final class InsetsListenerBuilder {

    @NonNull
    private final View view;

    private int typeMask = WindowInsetsCompat.Type.systemBars()
                           | WindowInsetsCompat.Type.displayCutout();

    @Nullable
    private
    Set<Side> sides;

    @Nullable
    private Type type;

    private InsetsListenerBuilder(@NonNull final View view) {
        this.view = view;
    }

    /**
     * Constructor.
     *
     * @param view to apply to
     *
     * @return builder
     */
    @NonNull
    public static InsetsListenerBuilder create(@NonNull final View view) {
        return new InsetsListenerBuilder(view);
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final Toolbar view) {
        create(view)
                .padding()
                .sides(Side.Left, Side.Top, Side.Right)
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final NestedScrollView view) {
        create(view)
                .padding()
                .sides(Side.Left, Side.Right, Side.Bottom)
                .ime()
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final RecyclerView view) {
        create(view)
                .margins()
                .sides(Side.Left, Side.Right, Side.Bottom)
                .ime()
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final FloatingActionButton view) {
        create(view)
                .margins()
                .sides(Side.Right, Side.Bottom)
                .apply();
    }

    @NonNull
    public InsetsListenerBuilder ime() {
        typeMask |= WindowInsetsCompat.Type.ime();
        return this;
    }

    @NonNull
    public InsetsListenerBuilder sides(@NonNull final Side... sides) {
        this.sides = Set.of(sides);
        return this;
    }

    @NonNull
    public InsetsListenerBuilder padding() {
        type = Type.Padding;
        return this;
    }

    @NonNull
    public InsetsListenerBuilder margins() {
        type = Type.Margins;
        return this;
    }

    @NonNull
    View getView() {
        return view;
    }

    int getTypeMask() {
        return typeMask;
    }

    @Nullable
    Set<Side> getSides() {
        return sides;
    }

    /**
     * Build and apply a the listener.
     */
    public void apply() {
        Objects.requireNonNull(type, "Must have a type set");
        if (sides == null) {
            sides = Set.of(Side.All);
        }
        final OnApplyWindowInsetsListener listener;
        switch (type) {
            case Padding:
                listener = new PaddingWindowInsetsListener(this);
                break;
            case Margins:
                listener = new MarginWindowInsetsListener(this);
                break;
            default:
                throw new IllegalArgumentException("type?");
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, listener);
    }

    private enum Type {
        Padding,
        Margins
    }
}
