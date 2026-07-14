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

import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

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

    /**
     * Get as a formatted string.
     *
     * @param engineId for the required format
     *
     * @return formatted product code text
     */
    @NonNull
    default String getFormatted(@NonNull final EngineId engineId) {
        //noinspection DataFlowIssue
        if (engineId.getConfig().prefersIsbn10() && this.isIsbn10Compat()) {
            return this.asText(ProductCodeType.Isbn10);
        } else {
            return this.asText();
        }
    }

    /**
     * Most (not all) sites want the ISSN formatted as "XXXX-XXXX".
     * <p>
     * The caller <strong>must</strong> have checked this is a valid
     * {@link ProductCodeType#Issn8} or compatible.
     *
     * @param engineId for the required format
     *
     * @return formatted product code text
     *
     * @throws SearchException if the product code was not an Issn8 or compatible
     */
    @NonNull
    default String getDashFormattedIssn8(@NonNull final EngineId engineId)
            throws SearchException {
        final String codeStr;
        try {
            codeStr = this.asText(ProductCodeType.Issn8);
            if (codeStr.length() == 8) {
                return codeStr.substring(0, 4) + "-" + codeStr.substring(4);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
        // We should never get here... flw
        throw new SearchException(engineId, "Failed to convert to Issn8: " + this, null);
    }
}
