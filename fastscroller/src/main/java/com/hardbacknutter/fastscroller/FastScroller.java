/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.fastscroller;

import androidx.annotation.Nullable;

public interface FastScroller {

    /**
     * Set the provider to use.
     *
     * @param overlayProvider to use, {@code null} for none.
     */
    void setOverlayProvider(@Nullable OverlayProvider overlayProvider);

    /**
     * Set a listener to receive feedback.
     *
     * @param listener to use
     */
    void setOnFastScrollStateChangeListener(@Nullable OnFastScrollStateChangeListener listener);

    /**
     * Allow updating the margin, typically from inside
     * an {@code Insets} listener set on the {@code RecyclrView}.
     *
     * @param margin to use
     */
    void setMargin(int margin);
}
