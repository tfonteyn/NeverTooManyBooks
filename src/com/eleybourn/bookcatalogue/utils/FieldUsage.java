/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.utils;

/**
 * read {@link FieldUsages}
 */
public class FieldUsage {
    public final String fieldName;
    public final int stringId;
    public FieldUsages.Usages usage;
    public boolean selected;
    public final boolean canAppend;

    public FieldUsage(String name, int id, FieldUsages.Usages usage, boolean canAppend) {
        this.fieldName = name;
        this.stringId = id;
        this.usage = usage;
        this.selected = true;
        this.canAppend = canAppend;
    }
}
