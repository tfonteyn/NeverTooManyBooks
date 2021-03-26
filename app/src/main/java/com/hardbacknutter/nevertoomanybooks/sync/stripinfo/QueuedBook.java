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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

public class QueuedBook {

    public final long externalId;

    public final boolean needsFullImport;

    /**
     * Ignored/irrelevant if {@link #needsFullImport} is true.
     * Dev Note: this field is not strictly needed, as for now it will in effect always be true.
     * However, this might make it easier to improve performance.
     */
    public final boolean needsBackCover;

    /**
     * Constructor. Should only be used from
     * {@link com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao}.
     * Other code should use the static factory methods.
     *
     * @param externalId      web site book id
     * @param needsFullImport flag
     * @param needsBackCover  flag
     */
    public QueuedBook(final long externalId,
                      final boolean needsFullImport,
                      final boolean needsBackCover) {
        this.externalId = externalId;
        this.needsFullImport = needsFullImport;
        this.needsBackCover = needsBackCover;
    }
}
