/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.booklist.style;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public interface WritableStyle
        extends Style {

    void setExpansionLevel(@IntRange(from = 1) int value);

    void setLayout(@NonNull Style.Layout layout);

    void setCoverClickAction(@NonNull Style.CoverClickAction coverClickAction);

    /**
     * Set the preference whether to <strong>show</strong> the Author full name
     * with their given-name first, or their family name first.
     * i.e.
     *
     * @param value {@code true} for "given family", or {@code false} for "family, given"
     */
    void setShowAuthorByGivenName(boolean value);

    void setShowReorderedPublisherName(boolean value);

    void setShowReorderedTitle(boolean value);

    /**
     * Set the preference whether to <strong>sort</strong> the Author full name
     * with their given-name first, or their family name first.
     * i.e.
     *
     * @param value {@code true} for "given family", or {@code false} for "family, given"
     */
    void setSortAuthorByGivenName(boolean value);

    void setTextScale(@Style.TextScale int scale);

    void setCoverScale(@Style.CoverScale int coverScale);

    /**
     * Set the bitmap value with the list header fields to show.
     *
     * @param bitmask to set
     */
    void setHeaderFieldVisibility(@BooklistHeader.Option int bitmask);

    /**
     * Set the list of fields on which we'll sort the lowest level (books) in the BoB.
     *
     * @param map the fields and how to sort them
     */
    void setBookLevelFieldsOrderBy(@NonNull Map<String, Sort> map);

    /**
     * Set the visibility bitmask the given {@link FieldVisibility.Screen}.
     *
     * @param screen  containing the key
     * @param bitmask to set
     */
    void setFieldVisibility(@NonNull FieldVisibility.Screen screen,
                            long bitmask);

    /**
     * Set the visibility for the given {@link DBKey}.
     *
     * @param screen containing the key
     * @param dbKey  to set
     * @param show   flag
     */
    void setFieldVisibility(@NonNull FieldVisibility.Screen screen,
                            @NonNull String dbKey,
                            boolean show);

    /**
     * Set the height to use for grouping rows.
     *
     * @param value {@code true} for "?attr/listPreferredItemHeightSmall"
     *              or {@code false} for {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}
     */
    void setGroupRowUsesPreferredHeight(boolean value);

    /**
     * Set the list of groups.
     *
     * @param list to set
     *
     * @see #setGroupIds(List)
     */
    void setGroupList(@Nullable List<BooklistGroup> list);

    /**
     * Using the given group-ids, create and set the group list.
     *
     * @param groupIds to create groups for
     *
     * @see #setGroupList(List)
     */
    void setGroupIds(@NonNull List<Integer> groupIds);

    /**
     * Set the primary-author-type from the {@link AuthorBooklistGroup}
     * (if this Style has the group).
     *
     * @param type the Author type
     */
    void setPrimaryAuthorType(@Author.Type int type);

    /**
     * Set the show-book-under-each for the given wrapped group
     * (if this Style has the group).
     *
     * @param groupId the {@link BooklistGroup} id
     * @param value   to set
     */
    void setShowBooksUnderEachGroup(@BooklistGroup.Id int groupId,
                                    boolean value);
}
