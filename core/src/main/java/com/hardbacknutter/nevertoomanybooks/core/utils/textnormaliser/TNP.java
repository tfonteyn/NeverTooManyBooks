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

package com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser;

import java.util.regex.Pattern;

final class TNP {

    /** KEEP alpha/digit. KEEP SINGLE spaces. */
    static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^\\p{Alpha}\\d ]");

    /** KEEP alpha/digit. REMOVE ALL white-space */
    static final Pattern ORDERBY_PATTERN = Pattern.compile("[^\\p{Alpha}\\d]");

    /** Replace ALL white-space characters with a single space. */
    static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** KEEP alpha/digit. KEEP white-space and '-' */
    static final Pattern FTS_PATTERN = Pattern.compile("[^\\p{Alpha}\\d\\s-]");

    static final String SINGLE_SPACE = " ";
    static final String REMOVE = "";

    private TNP() {
    }
}
