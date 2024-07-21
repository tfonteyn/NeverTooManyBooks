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
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;
import java.util.Set;

/**
 * Builder for a {@link OnApplyWindowInsetsListener}.
 * <p>
 * The default insets always applied : {@link WindowInsetsCompat.Type#systemBars()}
 * and {@link WindowInsetsCompat.Type#displayCutout()}
 */
@SuppressWarnings("unused")
public final class InsetsListenerBuilder {

    private static final String ERR_TYPE_ALREADY_SET = "Type already set";
    @NonNull
    private final View view;

    private int typeMask = WindowInsetsCompat.Type.systemBars()
                           | WindowInsetsCompat.Type.displayCutout();

    @Nullable
    private Set<Side> sides;

    @Nullable
    private Type type;

    private boolean consume = true;

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
    public static void apply(@NonNull final FloatingActionButton view) {
        create(view)
                .margins()
                .sides(Side.Right, Side.Bottom)
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
     * Enable {@link WindowInsetsCompat.Type#systemGestures()}.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public InsetsListenerBuilder systemGestures() {
        typeMask |= WindowInsetsCompat.Type.systemGestures();
        return this;
    }

    /**
     * Enable {@link WindowInsetsCompat.Type#ime()}.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public InsetsListenerBuilder ime() {
        typeMask |= WindowInsetsCompat.Type.ime();
        return this;
    }

    /**
     * Set whether the listener should consume the insets.
     * The default is {@code true}.
     *
     * @param consume flag
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public InsetsListenerBuilder consume(final boolean consume) {
        this.consume = consume;
        return this;
    }

    /**
     * Set the sides to apply the insets to. If not set, {@link Side#All} is used.
     *
     * @param sides list
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public InsetsListenerBuilder sides(@NonNull final Side... sides) {
        this.sides = Set.of(sides);
        return this;
    }

    /**
     * Create a padding modifier listener.
     *
     * @return {@code this} (for chaining)
     *
     * @throws IllegalStateException if the type was already set
     * @see #margins()
     */
    @NonNull
    public InsetsListenerBuilder padding() {
        if (type != null) {
            throw new IllegalStateException(ERR_TYPE_ALREADY_SET);
        }
        type = Type.Padding;
        return this;
    }

    /**
     * Create a margins modifier listener.
     *
     * @return {@code this} (for chaining)
     *
     * @throws IllegalStateException if the type was already set
     * @see #padding()
     */
    @NonNull
    public InsetsListenerBuilder margins() {
        if (type != null) {
            throw new IllegalStateException(ERR_TYPE_ALREADY_SET);
        }
        type = Type.Margins;
        return this;
    }

    /**
     * Build and apply the listener.
     */
    public void apply() {
        Objects.requireNonNull(type, "Must have a type set");

        final OnApplyWindowInsetsListener listener;
        switch (type) {
            case Padding:
                listener = new PaddingWindowInsetsListener(this);
                break;
            case Margins:
                listener = new MarginWindowInsetsListener(this);
                break;
            default:
                //noinspection CheckStyle
                throw new IllegalArgumentException("type?");
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, listener);
    }

    @NonNull
    View getView() {
        return view;
    }

    int getTypeMask() {
        return typeMask;
    }

    boolean getConsume() {
        return consume;
    }

    @Nullable
    Set<Side> getSides() {
        return sides;
    }

    private enum Type {
        Padding,
        Margins
    }
}
