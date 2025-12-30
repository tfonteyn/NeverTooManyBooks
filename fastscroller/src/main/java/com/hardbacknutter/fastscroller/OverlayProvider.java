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

public interface OverlayProvider {

    /**
     * Draw the overlay.
     *
     * @param isDragging  flag
     * @param thumbCenter the offset from the top to the center of the thumb/drag-handle
     */
    void showOverlay(boolean isDragging,
                     int thumbCenter);

    /**
     * Set the padding.
     *
     * @param left   padding
     * @param top    padding
     * @param right  padding
     * @param bottom padding
     */
    void setPadding(int left,
                    int top,
                    int right,
                    int bottom);

    /**
     * Set the interval, i.e. number of rows, which can be skipped when
     * showing the popup text while dragging.
     * <p>
     * Default: {@code 5}
     *
     * @param interval number of rows
     */
    void setInterval(int interval);

    /**
     * The adapter should implement this interface.
     * The OverlayProvider will call the method to get the text to display.
     */
    @FunctionalInterface
    interface PopupTextProvider {

        /**
         * Get the popup text for the given position.
         *
         * @param position to use
         *
         * @return the text
         */
        @Nullable
        CharSequence getPopupText(int position);
    }
}
