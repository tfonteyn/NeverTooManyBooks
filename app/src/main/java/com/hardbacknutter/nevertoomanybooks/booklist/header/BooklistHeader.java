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
package com.hardbacknutter.nevertoomanybooks.booklist.header;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public class BooklistHeader {

    /**
     * The amount of details to show in the header.
     * <strong>Never change these values</strong>, they get stored in the db.
     * <p>
     * not in use:
     * 1 << 1
     */
    public static final int NONE = 0;
    public static final int SHOW_BOOK_COUNT = 1;
    public static final int SHOW_SEARCH_CRITERIA = 1 << 2;
    public static final int SHOW_STYLE_NAME = 1 << 3;
    public static final int SHOW_FILTERS = 1 << 4;
    /** The bitmask for to apply to the settings values. This is also the default. */
    public static final int BITMASK_ALL = SHOW_BOOK_COUNT
                                          | SHOW_SEARCH_CRITERIA
                                          | SHOW_STYLE_NAME
                                          | SHOW_FILTERS;

    @Nullable
    private String styleName;
    @Nullable
    private String bookCount;
    @Nullable
    private String filterText;
    @Nullable
    private String searchText;

    private final boolean hasFilters;
    private boolean hasSearchCriteria;

    public BooklistHeader(@NonNull final Context context,
                          @NonNull final Style style,
                          final int totalBooks,
                          final int distinctBooks,
                          @NonNull final List<PFilter<?>> filters,
                          @Nullable final SearchCriteria searchCriteria) {

        if (style.isShowHeaderField(SHOW_STYLE_NAME)) {
            styleName = style.getLabel(context);
        }

        if (style.isShowHeaderField(SHOW_BOOK_COUNT)) {
            if (distinctBooks == totalBooks) {
                // Using a plural, so this covers zero books as well
                bookCount = context.getResources()
                                   .getQuantityString(R.plurals.n_books,
                                                      distinctBooks, totalBooks);
            } else {
                bookCount = context.getString(R.string.info_displaying_n_books_in_m_entries,
                                              distinctBooks, totalBooks);
            }
        }

        if (searchCriteria != null) {
            final List<String> list = searchCriteria.getDisplayText();
            hasSearchCriteria = !list.isEmpty();
            if (hasSearchCriteria && style.isShowHeaderField(SHOW_SEARCH_CRITERIA)) {
                searchText = String.join(", ", list);
            }
        }

        final String tmpFilterText = filters
                .stream()
                .filter(Filter::isActive)
                .map(filter -> filter.getValueText(context))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        hasFilters = !tmpFilterText.isEmpty();
        if (hasFilters && style.isShowHeaderField(SHOW_FILTERS)) {
            filterText = tmpFilterText;
        }
    }

    @Nullable
    String getStyleName() {
        return styleName;
    }

    @Nullable
    String getBookCount() {
        return bookCount;
    }

    public boolean hasFilters() {
        return hasFilters;
    }

    public boolean hasSearchCriteria() {
        return hasSearchCriteria;
    }

    @Nullable
    String getFilterText() {
        return filterText;
    }

    @Nullable
    String getSearchText() {
        return searchText;
    }

    @IntDef(flag = true, value = {
            NONE,
            SHOW_BOOK_COUNT,
            SHOW_SEARCH_CRITERIA,
            SHOW_STYLE_NAME,
            SHOW_FILTERS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Option {

    }
}
