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
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builder for a {@link OnApplyWindowInsetsListener}.
 * <p>
 * The default insets always applied : {@link WindowInsetsCompat.Type#systemBars()}
 * and {@link WindowInsetsCompat.Type#displayCutout()}
 */
public final class InsetsListenerBuilder {

    @NonNull
    private final View view;
    private final List<InsetsModifier> modifiers = new ArrayList<>();
    private int insetsTypeMask;
    private boolean dispatchToChildren;

    private InsetsListenerBuilder(@NonNull final View view) {
        this.view = view;
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final NavigationView view) {
        final OnApplyWindowInsetsListener listener =
                new NavigationViewWindowInsetsListener(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, listener);
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final Toolbar view) {
        new InsetsListenerBuilder(view)
                .padding(Side.Left, Side.Top, Side.Right)
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final FloatingActionButton view) {
        new InsetsListenerBuilder(view)
                .margins(Side.Right, Side.Bottom)
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final RecyclerView view) {
        new InsetsListenerBuilder(view)
                .margins(Side.Left, Side.Right, Side.Bottom)
                .systemBars()
                .displayCutout()
                .ime()
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final CoordinatorLayout view) {
        new InsetsListenerBuilder(view)
                .margins(Side.Bottom)
                .dispatchToChildren(true)
                // no systemBars!
                .displayCutout()
                .ime()
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final DrawerLayout view) {
        new InsetsListenerBuilder(view)
                .dispatchToChildren(true)
                .apply();
    }

    public static void apply(@Nullable final DrawerLayout drawerLayout,
                             @Nullable final CoordinatorLayout coordinatorLayout,
                             @Nullable final MaterialToolbar toolbar,
                             @Nullable final FloatingActionButton fab) {
        if (toolbar != null) {
            apply(toolbar);
        }
        if (coordinatorLayout != null) {
            apply(coordinatorLayout);
        }
        if (drawerLayout != null) {
            apply(drawerLayout);
        }
        if (fab != null) {
            apply(fab);
        }
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
     * Enable {@link WindowInsetsCompat.Type#systemBars()}.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public InsetsListenerBuilder systemBars() {
        insetsTypeMask |= WindowInsetsCompat.Type.systemBars();
        return this;
    }

    /**
     * Enable {@link WindowInsetsCompat.Type#displayCutout()}.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public InsetsListenerBuilder displayCutout() {
        insetsTypeMask |= WindowInsetsCompat.Type.displayCutout();
        return this;
    }

    /**
     * Enable {@link WindowInsetsCompat.Type#systemGestures()}.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public InsetsListenerBuilder systemGestures() {
        insetsTypeMask |= WindowInsetsCompat.Type.systemGestures();
        return this;
    }

    /**
     * Enable {@link WindowInsetsCompat.Type#ime()}.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public InsetsListenerBuilder ime() {
        insetsTypeMask |= WindowInsetsCompat.Type.ime();
        return this;
    }

    @NonNull
    public InsetsListenerBuilder dispatchToChildren(final boolean dispatch) {
        dispatchToChildren = dispatch;
        return this;
    }

    /**
     * Create a padding modifier listener.
     *
     * @param sides list
     *
     * @return {@code this} (for chaining)
     *
     * @throws IllegalStateException if the type was already set
     * @see #margins(Side...)
     */
    @NonNull
    public InsetsListenerBuilder padding(@NonNull final Side... sides) {
        modifiers.add(new PaddingInsetsModifier(view, Set.of(sides)));
        return this;
    }

    /**
     * Create a margins modifier listener.
     *
     * @param sides list
     *
     * @return {@code this} (for chaining)
     *
     * @throws IllegalStateException if the type was already set
     * @see #padding(Side...)
     */
    @NonNull
    public InsetsListenerBuilder margins(@NonNull final Side... sides) {
        modifiers.add(new MarginsInsetsModifier(view, Set.of(sides)));
        return this;
    }

    /**
     * Build and apply the listener.
     */
    public void apply() {
        if (insetsTypeMask == 0) {
            insetsTypeMask = WindowInsetsCompat.Type.systemBars()
                             | WindowInsetsCompat.Type.displayCutout();
        }
        final OnApplyWindowInsetsListener listener =
                new SimpleWindowInsetsListener(insetsTypeMask,
                                               modifiers,
                                               dispatchToChildren);

        ViewCompat.setOnApplyWindowInsetsListener(view, listener);
    }
}
