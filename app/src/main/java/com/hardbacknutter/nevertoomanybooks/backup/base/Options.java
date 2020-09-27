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

import java.util.StringJoiner;

/**
 * Options as to what should be imported/exported.
 * Not all implementations will support all options.
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

    /**
     * All entity types which can be written/read.
     * This does not include INFO nor IS_SYNC.
     */
    public static final int ENTITIES = BOOKS | COVERS | PREFS | STYLES;

    /**
     * Do a sync.
     * <ul>
     *     <li>
     *          Export:
     *          <ul>
     *              <li>0: all books</li>
     *              <li>1: books added/updated since last backup</li>
     *          </ul>
     *     </li>
     *     <li>
     *         Import:
     *         <ul>
     *             <li>0: all books</li>
     *             <li>1: only new books and books with more recent update_date fields</li>
     *         </ul>
     *     </li>
     * </ul>
     */
    public static final int IS_SYNC = 1 << 16;


    /** DEBUG. */
    public static String toString(final int options) {
        final StringJoiner sj = new StringJoiner(",", "Options{", "}");
        if ((options & INFO) != 0) {
            sj.add("INFO");
        }
        if ((options & BOOKS) != 0) {
            sj.add("INFO");
        }
        if ((options & COVERS) != 0) {
            sj.add("COVERS");
        }
        if ((options & PREFS) != 0) {
            sj.add("PREFS");
        }
        if ((options & STYLES) != 0) {
            sj.add("STYLES");
        }
        if ((options & IS_SYNC) != 0) {
            sj.add("IS_SYNC");
        }
        return sj.toString();
    }
}
