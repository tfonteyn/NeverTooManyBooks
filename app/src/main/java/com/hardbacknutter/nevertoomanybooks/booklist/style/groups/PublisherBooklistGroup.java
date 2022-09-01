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
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;

/**
 * Specialized BooklistGroup representing a {@link Publisher} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class PublisherBooklistGroup
        extends AbstractLinkedTableBooklistGroup {

    /** For sorting. */
    private static final Domain DOM_SORTING;

    static {
        DOM_SORTING = new Domain.Builder(DBKey.BL_PUBLISHER_SORT,
                                         SqLiteDataType.Text)
                .build();
    }

    /**
     * Constructor.
     *
     * @param style Style reference.
     */
    PublisherBooklistGroup(@NonNull final Style style) {
        super(style, PUBLISHER, false);
    }

    /**
     * Copy constructor.
     *
     * @param style        Style reference.
     * @param group        to copy from
     */
    PublisherBooklistGroup(@NonNull final Style style,
                           @NonNull final PublisherBooklistGroup group) {
        super(style, group);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey() {
        // We use the foreign ID to create the key domain.
        // We override the display domain in #createDisplayDomainExpression.
        return new GroupKey(R.string.lbl_publisher, "p",
                            new DomainExpression(DOM_FK_PUBLISHER,
                                                 TBL_PUBLISHERS.dot(DBKey.PK_ID)))
                .addGroupDomain(
                        // We do not sort on the key domain but add the OB column instead
                        new DomainExpression(DOM_SORTING,
                                             TBL_PUBLISHERS.dot(DBKey.PUBLISHER_NAME_OB),
                                             DomainExpression.Sort.Asc))
                .addGroupDomain(
                        // Group by id (we want the id available and there is
                        // a chance two Publishers will have the same name)
                        new DomainExpression(DOM_FK_PUBLISHER,
                                             TBL_BOOK_PUBLISHER.dot(DBKey.FK_PUBLISHER)));
    }

    @Override
    @NonNull
    protected DomainExpression createDisplayDomainExpression(@NonNull final Style style) {
        // Not sorted; we sort on the OB domain as defined in #createGroupKey.
        return new DomainExpression(DBDefinitions.DOM_PUBLISHER_NAME,
                                    DBDefinitions.TBL_PUBLISHERS.dot(DBKey.PUBLISHER_NAME));
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(
                StyleDataStore.PSK_STYLE_PUBLISHER);
        if (category != null) {
            final String[] keys = {StyleDataStore.PK_GROUPS_PUBLISHER_SHOW_BOOKS_UNDER_EACH};
            setPreferenceVisibility(category, keys, visible);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PublisherBooklistGroup{"
               + super.toString()
               + "}";
    }
}
