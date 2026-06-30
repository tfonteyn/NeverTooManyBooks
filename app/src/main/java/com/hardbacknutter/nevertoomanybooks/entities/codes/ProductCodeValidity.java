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

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

/**
 * Describes how we check for valid codes. This is a user-setting.
 */
public enum ProductCodeValidity {
    /** No checks are done, the code is used as-is. */
    NoChecks(0),
    /** Any type as long as it's NOT {@link ProductCodeType#Invalid}. */
    ValidCodes(1),
    /**
     * Must be either {@link ProductCodeType#Isbn10} or {@link ProductCodeType#Isbn13}
     * (or auto-converted) to be considered valid.
     */
    Isbn(2);

    /**
     * {@link ProductCode} Validity level.
     * Type: int
     *
     * @see ProductCodeValidity
     */
    public static final String PK_EDIT_BOOK_PRODUCT_CODE_CHECKS = "edit.book.isbn.checks";

    private final int id;

    ProductCodeValidity(final int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /**
     * Lookup by id.
     *
     * @param id to lookup
     *
     * @return Validity level
     */
    @NonNull
    public static ProductCodeValidity byId(final int id) {
        return Arrays.stream(values())
                     .filter(v -> v.id == id)
                     .findFirst()
                     .orElse(Isbn);
    }

    /**
     * Get the users preferred level of validity checking.
     *
     * @return level
     */
    @NonNull
    public static ProductCodeValidity getPreferredLevel() {
        return byId(
                // def==-1 to make byId return the enum default.
                ServiceLocator.getInstance().getSharedPreferences()
                              .getIntFromString(PK_EDIT_BOOK_PRODUCT_CODE_CHECKS, -1));
    }

    /**
     * Set the users preferred level of validity checking.
     *
     * @param validity level
     */
    public static void setPreferredLevel(@NonNull final ProductCodeValidity validity) {
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putString(PK_EDIT_BOOK_PRODUCT_CODE_CHECKS, String.valueOf(validity.id))
                      .apply();
    }
}
