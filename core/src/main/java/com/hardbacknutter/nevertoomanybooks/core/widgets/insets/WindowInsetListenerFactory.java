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

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Standard / commonly used WindowInsetListeners.
 */
public final class WindowInsetListenerFactory {

    private WindowInsetListenerFactory() {
    }

    public static void init(@NonNull final Toolbar view) {
        ViewCompat.setOnApplyWindowInsetsListener(
                view, new PaddingWindowInsetsListener(
                        view, true, true, true, false));
    }

    public static void init(@NonNull final RecyclerView view) {
        ViewCompat.setOnApplyWindowInsetsListener(
                view, new MarginWindowInsetListener(
                        view, true, false, true, true));
    }

    public static void init(@NonNull final FloatingActionButton view) {
        ViewCompat.setOnApplyWindowInsetsListener(
                view, new MarginWindowInsetListener(
                        view, false, false, true, true));
    }
}
