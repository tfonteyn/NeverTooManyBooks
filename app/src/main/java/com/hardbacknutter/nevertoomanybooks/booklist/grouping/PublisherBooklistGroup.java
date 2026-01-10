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
package com.hardbacknutter.nevertoomanybooks.booklist.grouping;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

/**
 * Specialized BooklistGroup representing a {@link Publisher} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customised display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
class PublisherBooklistGroup
        extends BooklistGroupImpl
        implements UnderEachGroup {

    private static final GroupPrefs GROUP_PREFS =
            new GroupPrefs("psk_style_publisher",
                           Style.UnderEach.Publisher.getPrefKey());

    /** DomainExpression for displaying the data. */
    @NonNull
    private final DomainExpression displayDomainExpression;
    /** Show a book under each item it is linked to. */
    private boolean underEach;

    /**
     * Constructor.
     *
     * @param groupKey of group to create
     */
    PublisherBooklistGroup(@NonNull final GroupKey groupKey) {
        super(groupKey);
        // Not sorted; we sort on the OB domain as defined in GroupKeyFactory#create
        displayDomainExpression = new DomainExpression(DBDefinitions.DOM_PUBLISHER_NAME,
                                                       DBDefinitions.TBL_PUBLISHERS,
                                                       Sort.Unsorted);
    }

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return displayDomainExpression;
    }

    @Override
    public boolean isShowBooksUnderEach() {
        return underEach;
    }

    @Override
    public void setShowBooksUnderEach(final boolean value) {
        underEach = value;
    }

    @NonNull
    @Override
    public GroupPrefs getGroupPrefs() {
        return GROUP_PREFS;
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
        final PublisherBooklistGroup that = (PublisherBooklistGroup) o;
        return underEach == that.underEach
               && displayDomainExpression.equals(that.displayDomainExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), underEach, displayDomainExpression);
    }

    @Override
    @NonNull
    public String toString() {
        return "PublisherBooklistGroup{"
               + super.toString()
               + ", displayDomainExpression=" + displayDomainExpression
               + ", underEach=" + underEach
               + "}";
    }
}
