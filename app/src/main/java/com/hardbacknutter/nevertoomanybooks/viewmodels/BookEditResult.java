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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

public class BookEditResult {

    public static final int NOT_SET = 0;
    public static final int CREATED = 1;
    public static final int MODIFIED = 2;
    public static final int DELETED = 3;

    public final long id;
    public final int status;

    private BookEditResult(final long id,
                           final int status) {
        this.id = id;
        this.status = status;
    }

    public static BookEditResult created(final long id) {
        return new BookEditResult(id, CREATED);
    }

    public static BookEditResult modified(final long id) {
        return new BookEditResult(id, MODIFIED);
    }

    public static BookEditResult deleted(final long id) {
        return new BookEditResult(id, DELETED);
    }
}
