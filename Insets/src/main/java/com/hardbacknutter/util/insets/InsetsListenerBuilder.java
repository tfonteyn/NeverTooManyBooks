/*
 * @Copyright 2018-2026 HardBackNutter
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
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builder for a {@link OnApplyWindowInsetsListener}.
 */
@SuppressWarnings("WeakerAccess")
public final class InsetsListenerBuilder {

    @NonNull
    private final View view;
    private final List<InsetsModifier> modifiers = new ArrayList<>();
    private int insetsTypeMask;
    private boolean dispatchToChildren;

    /**
     * Constructor.
     *
     * @param view to apply to
     */
    public InsetsListenerBuilder(@NonNull final View view) {
        this.view = view;
    }

    /**
     * Convenience constructor to use from an {@code Activity#onCreate}.
     *
     * @param coordinatorLayout optional
     * @param toolbar           optional
     * @param fab               optional
     */
    public static void apply(@Nullable final CoordinatorLayout coordinatorLayout,
                             @Nullable final Toolbar toolbar,
                             @Nullable final FloatingActionButton fab) {
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
    public static void apply(@NonNull final CoordinatorLayout view) {
        // Draw below system-bars and display cutouts.
        // The toolbar will take care of those.
        // Shrink when the keyboard comes up.
        // Dispatch incoming insets to all children.
        new InsetsListenerBuilder(view)
                .margins(Side.Bottom)
                .dispatchToChildren(true)
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
                .padding(Side.Start, Side.Top, Side.End)
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
                .margins(Side.End, Side.Bottom)
                .systemBars()
                .displayCutout()
                .apply();
    }

    /**
     * Convenience method for code-readability.
     * <p>
     * Effectively disables edge-to-edge for the root view.
     *
     * @param view the fragment root view.
     */
    public static void fragmentRootView(@NonNull final View view) {
        new InsetsListenerBuilder(view)
                .padding(Side.Start, Side.End, Side.Bottom)
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
                .padding(Side.Start, Side.End, Side.Bottom)
                .systemBars()
                .displayCutout()
                .ime()
                .apply();
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
     * <p>
     * If modifiers (e.g. {@link #margins(Side...)}) were set,
     * but no insets where added
     * and this is not a simple dispatcher,
     * then we'll apply the default insets {@link WindowInsetsCompat.Type#systemBars()}
     * and {@link WindowInsetsCompat.Type#displayCutout()}.
     */
    public void apply() {
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
