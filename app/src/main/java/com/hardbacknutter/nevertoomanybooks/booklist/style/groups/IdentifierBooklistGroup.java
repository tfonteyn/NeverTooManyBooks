/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

/**
 * Specialized {@link BooklistGroup} representing an {@link Identifier} group.
 * <p>
 * 'under each' preference is hardcoded to {@code true}
 * There is no 'position' column for Identifier
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 */
class IdentifierBooklistGroup
        extends BooklistGroupImpl
        implements UnderEachGroup {

    /** DomainExpression for displaying the data. */
    @NonNull
    private final DomainExpression displayDomainExpression;

    /**
     * Constructor.
     *
     * @param groupKey of group to create
     */
    IdentifierBooklistGroup(@NonNull final GroupKey groupKey) {
        super(groupKey);
        // Not sorted; we sort on the name domain as defined in GroupKeyFactory#create
        // This is "replacing" the foreign-key domain; it's NOT duplicating the
        // group/sort domain from the GroupKey
        displayDomainExpression = new DomainExpression(DBDefinitions.DOM_IDENTIFIER_KEY,
                                                       DBDefinitions.TBL_IDENTIFIERS,
                                                       Sort.Unsorted);
    }

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return displayDomainExpression;
    }

    @Override
    public boolean isShowBooksUnderEach() {
        return true;
    }

    @Override
    public void setShowBooksUnderEach(final boolean value) {
        // ignore, always true
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final IdentifierBooklistGroup that = (IdentifierBooklistGroup) o;
        return displayDomainExpression.equals(that.displayDomainExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), displayDomainExpression);
    }

    @Override
    @NonNull
    public String toString() {
        return "IdentifierBooklistGroup{"
               + super.toString()
               + ", displayDomainExpression=" + displayDomainExpression
               + ", underEach=true"
               + '}';
    }
}
