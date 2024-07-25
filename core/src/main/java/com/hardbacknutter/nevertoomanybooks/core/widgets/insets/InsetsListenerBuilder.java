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
@SuppressWarnings("WeakerAccess")
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
     * Convenience constructor to use from an {@code Activity#onCreate}.
     *
     * @param drawerLayout      optional
     * @param coordinatorLayout optional
     * @param toolbar           optional
     * @param fab               optional
     */
    public static void apply(@Nullable final DrawerLayout drawerLayout,
                             @Nullable final CoordinatorLayout coordinatorLayout,
                             @Nullable final MaterialToolbar toolbar,
                             @Nullable final FloatingActionButton fab) {
        if (drawerLayout != null) {
            apply(drawerLayout);
        }
        if (coordinatorLayout != null) {
            apply(coordinatorLayout);
        }
        if (toolbar != null) {
            apply(toolbar);
        }
        if (fab != null) {
            apply(fab);
        }
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final DrawerLayout view) {
        // No action on the view itself, but dispatch incoming insets to all children.
        new InsetsListenerBuilder(view)
                .dispatchToChildren(true)
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     *
     * @see NavigationViewWindowInsetsListener
     */
    public static void apply(@NonNull final NavigationView view) {
        // Custom listener, reacts tot system-bars and display cutouts.
        final OnApplyWindowInsetsListener listener =
                new NavigationViewWindowInsetsListener(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, listener);
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final CoordinatorLayout view) {
        // Draw below system-bars, but not below display cutouts.
        // Shrink when the keyboard comes up.
        // Dispatch incoming insets to all children.
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
    public static void apply(@NonNull final Toolbar view) {
        new InsetsListenerBuilder(view)
                .padding(Side.Left, Side.Top, Side.Right)
                .systemBars()
                .displayCutout()
                .apply();
    }

    /**
     * Apply a predefined listener.
     *
     * @param view to apply to
     */
    public static void apply(@NonNull final FloatingActionButton view) {
        // Don't react to the keyboard; the presumption is that the user is entering data.
        // The FAB is a *start* edit, the *end* edit action is on the Toolbar.
        new InsetsListenerBuilder(view)
                .margins(Side.Right, Side.Bottom)
                .systemBars()
                .displayCutout()
                .apply();
    }

    /**
     * Convenience method for code-readability.
     * This makes it easier to maintain code which needs specific listeners.
     * i.e. search for usage of the {@link #create(View)} method.
     * <p>
     * Effectively disables edge-to-edge for the root view.
     *
     * @param view the fragment root view.
     */
    public static void fragmentRootView(@NonNull final View view) {
        new InsetsListenerBuilder(view)
                .padding(Side.Left, Side.Right, Side.Bottom)
                .systemBars()
                .displayCutout()
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

    /**
     * Request the listener to dispatch the incoming insets to the view children.
     *
     * @param dispatch flag
     *
     * @return {@code this} (for chaining)
     */
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
        // If we HAVE modifiers set, but the mask was NOT set
        // and this is not a dispatcher...
        // then we'll apply the default insets as a fallback.
        if (!modifiers.isEmpty() && insetsTypeMask == 0 && !dispatchToChildren) {
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
