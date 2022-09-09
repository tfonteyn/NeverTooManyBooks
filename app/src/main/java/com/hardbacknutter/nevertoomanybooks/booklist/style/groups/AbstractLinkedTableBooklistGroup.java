/*
 * @Copyright 2018-2022 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;

/**
 * Support for Foreign Key fields.
 */
public abstract class AbstractLinkedTableBooklistGroup
        extends BooklistGroup {

    /** DomainExpression for displaying the data. Style dependent. */
    @NonNull
    private final DomainExpression displayDomainExpression;

    /** Show a book under each item it is linked to. */
    private boolean underEach;

    AbstractLinkedTableBooklistGroup(@NonNull final Style style,
                                     @Id final int groupId,
                                     final boolean underEach) {
        super(groupId);
        displayDomainExpression = createDisplayDomainExpression(style);

        this.underEach = underEach;
    }

    @NonNull
    protected abstract DomainExpression createDisplayDomainExpression(@NonNull Style style);

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return displayDomainExpression;
    }

    /**
     * Get this preference.
     *
     * @return {@code true} if we want to show a book under each of its linked items.
     */
    public boolean showBooksUnderEach() {
        return underEach;
    }

    public void setShowBooksUnderEach(final boolean value) {
        underEach = value;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (!super.equals(o)) {
            return false;
        }
        final AbstractLinkedTableBooklistGroup that = (AbstractLinkedTableBooklistGroup) o;
        return Objects.equals(displayDomainExpression, that.displayDomainExpression)
               && underEach == that.underEach;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), displayDomainExpression, underEach);
    }

    @Override
    @NonNull
    public String toString() {
        return super.toString()
               + ", displayDomainExpression=" + displayDomainExpression
               + ", underEach=" + underEach;
    }
}
