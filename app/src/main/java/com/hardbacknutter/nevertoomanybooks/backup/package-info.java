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

/**
 * The import/export classes are really <strong>too</strong> flexible.
 *
 * <ul>Archives written:
 * <li>csv: books only</li>
 * <li>db: a copy of the internal database</li>
 * <li>json: all data except covers</li>
 * <li>xml: books only</li>
 * <li>zip: all data in json format + covers == full backup</li>
 * </ul>
 * zip can be forced (by changing the version number in the code + recompiling)
 * to contain csv encoded books
 *
 * <ul>Archives read:
 * <li>csv: books only</li>
 * <li>db: Calibre database import only</li>
 * <li>json: all data (except covers)</li>
 * <li>tar: all data in json,xml,csv formats + covers</li>
 * <li>zip: all data in json,xml,csv formats + covers</li>
 * </ul>
 * <p>
 * An entry in an archive will have a Type (books, styles,...) and an Encoding (csv, xml, ...)
 * <p>
 * Not all combinations are implemented (which would be rather pointless) but there are more
 * implementations than really needed.
 *
 */
package com.hardbacknutter.nevertoomanybooks.backup;
