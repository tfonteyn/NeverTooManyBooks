/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

/**
 * Options as to what should be imported/exported.
 * Not all implementations will support all options.
 * These are the common lower 16 bits of the 'options'.
 * <p>
 * The bit numbers are not stored.
 */
public final class Options {

    public static final int NOTHING = 0;
    public static final int INFO = 1;

    public static final int BOOKS = 1 << 1;
    public static final int COVERS = 1 << 2;

    public static final int PREFS = 1 << 8;
    public static final int STYLES = 1 << 9;

    /** All entity types which can be written/read. This does not include INFO. */
    public static final int ENTITIES = BOOKS | COVERS | PREFS | STYLES;
}
