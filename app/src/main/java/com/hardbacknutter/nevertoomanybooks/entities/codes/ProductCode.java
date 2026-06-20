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

package com.hardbacknutter.nevertoomanybooks.entities.codes;

import androidx.annotation.NonNull;

public interface ProductCode {

    /**
     * Get the {@link ProductCodeType}.
     *
     * @return ProductCodeType
     */
    @NonNull
    ProductCodeType getType();

    /**
     * Check if we have a valid code.
     *
     * @return validity
     */
    boolean isValid();

    /**
     * Check if the code <strong>is</strong> an {@link ProductCodeType#Isbn10}
     * or an {@link ProductCodeType#Isbn13}.
     *
     * @return flag
     */
    boolean isIsbn();

    /**
     * Check if the code <strong>is</strong> an {@link ProductCodeType#Isbn10},
     * or an {@link ProductCodeType#Isbn13} which can be converted
     * to an {@link ProductCodeType#Isbn10}.
     *
     * @return {@code true} if compatible; {@code false} if not compatible or not a valid ISBN
     */
    boolean isIsbn10Compat();

    /**
     * Get the code as a normalised text string.
     * Normalization format depends on the {@link ProductCodeType}.
     *
     * @return string
     */
    @NonNull
    String asText();

    /**
     * Get the code as a text string converted to the given type.
     * Normalization format depends on the {@link ProductCodeType}.
     *
     * @param toType to convert to
     *
     * @return string
     *
     * @throws NumberFormatException if a conversion is not possible.
     */
    @NonNull
    String asText(@NonNull ProductCodeType toType)
            throws NumberFormatException;
}
