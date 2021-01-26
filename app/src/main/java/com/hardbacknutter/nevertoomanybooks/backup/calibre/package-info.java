/*
 * @Copyright 2018-2021 HardBackNutter
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
 * Installing the Calibre extension.
 * <p>
 * With the calibre code root located at: CROOT=/usr/lib/calibre/
 * <p>
 * Copy the 'nevertoomanybooks.py' to $CROOT/calibre/srv/
 * <p>
 * Edit  $CROOT/calibre/srv/ajax.py, and look for the line
 * {@code from calibre.srv.errors import HTTPNotFound, BookNotFound}
 * modify it:
 * {@code from calibre.srv.errors import HTTPNotFound, BookNotFound, HTTPBadRequest}
 * <p>
 * At the end of the file, add from ajax_additions.py
 *
 * <p>
 * Recompile (either with 'python2' or 'python3' depending on platform)
 * python2 -m py_compile ajax.py
 * <p>
 * Restart Calibre server
 */
package com.hardbacknutter.nevertoomanybooks.backup.calibre;
