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

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public interface WritableStyle
        extends Style {

    void setExpansionLevel(@IntRange(from = 1) int value);

    void setLayout(@NonNull Style.Layout layout);

    void setCoverClickAction(@NonNull Style.CoverClickAction coverClickAction);

    void setShowAuthorByGivenName(boolean value);

    void setSortAuthorByGivenName(boolean value);

    void setTextScale(@Style.TextScale int scale);

    void setCoverScale(@Style.CoverScale int coverScale);

    void setHeaderFieldVisibilityValue(@BooklistHeader.Option int bitmask);

    void setBookLevelFieldsOrderBy(@NonNull Map<String, Sort> map);

    void setGroupRowUsesPreferredHeight(boolean value);

    void setGroupList(@Nullable List<BooklistGroup> list);

    void setGroupIds(@NonNull List<Integer> groupIds);

    void setPrimaryAuthorType(@Author.Type int type);

    void setShowBooksUnderEachGroup(@BooklistGroup.Id int groupId,
                                    boolean value);
}
