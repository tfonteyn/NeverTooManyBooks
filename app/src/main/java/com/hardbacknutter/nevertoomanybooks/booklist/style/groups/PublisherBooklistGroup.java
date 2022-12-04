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
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Sort;
import com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

/**
 * Specialized BooklistGroup representing a {@link Publisher} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class PublisherBooklistGroup
        extends BooklistGroup
        implements UnderEachGroup {

    /** DomainExpression for displaying the data. */
    @NonNull
    private final DomainExpression displayDomainExpression;
    /** Show a book under each item it is linked to. */
    private boolean underEach;

    /**
     * Constructor.
     */
    PublisherBooklistGroup() {
        super(PUBLISHER);
        // Not sorted; we sort on the OB domain as defined in #createGroupKey.
        displayDomainExpression = new DomainExpression(DBDefinitions.DOM_PUBLISHER_NAME,
                                                       DBDefinitions.TBL_PUBLISHERS,
                                                       Sort.Unsorted);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey() {
        // We use the foreign ID to create the key-domain.
        // It is NOT used to display the data; instead we use #displayDomainExpression.
        // Neither the key-domain nor the display-domain is sorted;
        // instead we add the OB column, sorted, as a group domain.
        return new GroupKey(R.string.lbl_publisher, "p",
                            new DomainExpression(DBDefinitions.DOM_FK_PUBLISHER,
                                                 DBDefinitions.TBL_PUBLISHERS.dot(DBKey.PK_ID),
                                                 Sort.Unsorted))
                .addGroupDomain(
                        new DomainExpression(
                                new Domain.Builder(BlgKey.SORT_PUBLISHER, SqLiteDataType.Text)
                                        .build(),
                                DBDefinitions.TBL_PUBLISHERS.dot(DBKey.PUBLISHER_NAME_OB),
                                Sort.Asc))
                .addGroupDomain(
                        new DomainExpression(DBDefinitions.DOM_FK_PUBLISHER,
                                             DBDefinitions.TBL_BOOK_PUBLISHER));
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

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category =
                screen.findPreference(StyleDataStore.PSK_STYLE_PUBLISHER);
        if (category != null) {
            final String[] keys = {StyleDataStore.PK_GROUPS_PUBLISHER_SHOW_BOOKS_UNDER_EACH};
            setPreferenceVisibility(category, keys, visible);
        }
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
