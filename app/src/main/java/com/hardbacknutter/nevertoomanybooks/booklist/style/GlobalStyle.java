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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Settings which are configurable on "preferences.xml".
 * These are used where a Style is not needed/present
 * and as defaults for styles in general. User defined styles can override these settings.
 * <p>
 * Note this is NOT a {@link Style} as it's really only a set of preferences.
 */
@SuppressWarnings("WeakerAccess")
public final class GlobalStyle {


    /**
     * IMPORTANT: this is the ALMOST the same set as used by BookLevelFieldVisibility
     * and should be kept in sync.
     * but note the differences:
     * <ul>
     *     <li>TITLE added: we ALWAYS display it.</li>
     *     <li>ISBN & LANGUAGE removed: we already have it added during BooklistBuilder setup.</li>
     * </ul>
     * Also note this is an <strong>ORDERED LIST!</strong>
     */
    static final Map<String, Sort> BOOK_LEVEL_FIELDS_DEFAULTS = new LinkedHashMap<>();
    @NonNull
    private final SharedPreferences preferences;

    static {
        // The default is sorting by book title only
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.TITLE, Sort.Asc);

        // The field order here is assuming the user will need to sort more likely
        // on the fields listed at the top.
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.TITLE_ORIGINAL_LANG, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_AUTHOR, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_SERIES, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_PUBLISHER, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.BOOK_PUBLICATION__DATE, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FORMAT, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.LOCATION, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.RATING, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.PAGE_COUNT, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.BOOK_CONDITION, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.SIGNED__BOOL, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.EDITION__BITMASK, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.LOANEE_NAME, Sort.Unsorted);
    }

    /**
     * Constructor.
     *
     * @param appContext The <strong>application</strong> context
     */
    public GlobalStyle(@NonNull final Context appContext) {
        preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    /**
     * List "Summary details in header".
     *
     * @return bitmask value
     */
    public int getBooklistHeaderValue() {
        return BooklistHeader.BITMASK_ALL;
    }

    /**
     * List "Expansion Level".
     *
     * @return level
     */
    public int getExpansionLevel() {
        return 1;
    }

    /**
     * List "Sorting".
     *
     * @return sorting info
     */
    @NonNull
    public Map<String, Sort> getBookLevelFieldsOrderBy() {
        return new LinkedHashMap<>(BOOK_LEVEL_FIELDS_DEFAULTS);
    }

    /**
     * List layout "Layout".
     *
     * @return Layout
     */
    @NonNull
    public Style.Layout getLayout() {
        return Style.Layout.List;
    }

    /**
     * List layout "Tapping on a cover image".
     *
     * @return CoverClickAction
     */
    @NonNull
    public Style.CoverClickAction getCoverClickAction() {
        return Style.CoverClickAction.Zoom;
    }

    /**
     * List layout "Cover size in lists".
     *
     * @return scaling factor
     */
    @Style.CoverScale
    public int getCoverScale() {
        return Style.DEFAULT_COVER_SCALE;
    }

    /**
     * List layout "Font size".
     *
     * @return scaling factor
     */
    @Style.TextScale
    public int getTextScale() {
        return Style.DEFAULT_TEXT_SCALE;
    }

    /**
     * List layout "Line spacing".
     *
     * @return {@code true} for "?attr/listPreferredItemHeightSmall"
     *         or {@code false} for {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}
     */
    public boolean isGroupRowUsesPreferredHeight() {
        return true;
    }

    /**
     * List layout "Book details shown in the list" (includes "Show covers").
     * <p>
     * Book details page "Book details shown on the book details page"
     * i.e. "Show Cover", "Show Back cover"
     * <p>
     * Global FieldVisibility
     * i.e. the user settings which fields they want to use / never see
     *
     * @param screen to get the setting for
     *
     * @return FieldVisibility
     *
     * @throws IllegalArgumentException when there is a bug with the enums
     */
    @NonNull
    public FieldVisibility getFieldVisibility(@NonNull final Style.Screen screen) {
        switch (screen) {
            case List:
                return new BookLevelFieldVisibility();
            case Detail:
                return new BookDetailsFieldVisibility();
            case Global:
                return ServiceLocator.getInstance().getGlobalFieldVisibility();
        }
        throw new IllegalArgumentException();
    }

    /**
     * Author "Formatting".
     * <p>
     * Whether the user prefers the Author names displayed by Given names, or by Family name first.
     *
     * @return {@code true} if the Given name should be displayed before the Family name
     */
    public boolean isShowAuthorByGivenName() {
        return preferences.getBoolean(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST, false);
    }

    /**
     * Author "Sorting".
     * <p>
     * Whether the user prefers the Author names sorted by Given names, or by Family name first.
     *
     * @return {@code true} if the Given name should be sorted before then the Family name
     */
    public boolean isSortAuthorByGivenName() {
        return preferences.getBoolean(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST, false);
    }

    /**
     * Author, Series, Publisher, Bookshelf "Show books with multiple ...".
     *
     * @param item to get the preference for
     *
     * @return {@code true} to show Books under each [item], or {@code false} to only
     *         show Books under the primary [item].
     */
    public boolean getShowBooksUnderEach(@NonNull final Style.UnderEach item) {
        return preferences.getBoolean(item.getPrefKey(), false);
    }
}
