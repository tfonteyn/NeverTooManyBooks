/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.preference.PreferenceDataStore;

import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BookshelfBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.PublisherBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.SeriesBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Definitions and transmogrifying (Hi Calvin) API for preference keys and actual style values.
 * <p>
 * The PK_STYLE strings are still used during import/export for backwards compatibility.
 */
public class StyleDataStore
        extends PreferenceDataStore {

    /** Style display name. */
    public static final String PK_NAME = "style.booklist.name";
    /** Style group preferences. */
    public static final String PK_GROUPS = "style.booklist.groups";
    /** The default expansion level for the groups. */
    public static final String PK_EXPANSION_LEVEL = "style.booklist.levels.default";

    /** Relative scaling factor for text on the list screen. */
    public static final String PK_TEXT_SCALE = "style.booklist.scale.font";
    /** Relative scaling factor for covers on the list screen. */
    public static final String PK_COVER_SCALE = "style.booklist.scale.thumbnails";

    /** What fields the user wants to see in the list header. */
    public static final String PK_LIST_HEADER = "style.booklist.header";

    public static final String PK_SORT_AUTHOR_NAME_GIVEN_FIRST = "sort.author.name.given_first";
    public static final String PK_SHOW_AUTHOR_NAME_GIVEN_FIRST = "show.author.name.given_first";

    public static final String PK_GROUP_ROW_HEIGHT = "style.booklist.group.height";

    /** Show the cover image (front only) for each book on the list screen. */
    public static final String PK_LIST_SHOW_COVERS = "style.booklist.show.thumbnails";
    /** Show author for each book on the list screen. */
    public static final String PK_LIST_SHOW_AUTHOR = "style.booklist.show.author";
    /** Show publisher for each book on the list screen. */
    public static final String PK_LIST_SHOW_PUBLISHER = "style.booklist.show.publisher";
    /** Show publication date for each book on the list screen. */
    public static final String PK_LIST_SHOW_PUB_DATE = "style.booklist.show.publication.date";
    /** Show format for each book on the list screen. */
    public static final String PK_LIST_SHOW_FORMAT = "style.booklist.show.format";
    /** Show location for each book on the list screen. */
    public static final String PK_LIST_SHOW_LOCATION = "style.booklist.show.location";
    /** Show rating for each book on the list screen. */
    public static final String PK_LIST_SHOW_RATING = "style.booklist.show.rating";
    /** Show list of bookshelves for each book on the list screen. */
    public static final String PK_LIST_SHOW_BOOKSHELVES = "style.booklist.show.bookshelves";
    /** Show ISBN for each book on the list screen. */
    public static final String PK_LIST_SHOW_ISBN = "style.booklist.show.isbn";

    /** Show the cover images (front/back) for each book on the details screen. */
    public static final String[] PK_DETAILS_SHOW_COVER = {
            "style.details.show.thumbnail.0",
            "style.details.show.thumbnail.1",
            };

    @NonNull
    private final UserStyle style;

    private boolean modified;

    public StyleDataStore(@NonNull final UserStyle style) {
        this.style = style;
    }

    /**
     * Parse and combine the values in the set into a bitmask.
     *
     * @param stringSet to parse
     * @param defValue  to use upon any error
     *
     * @return bitmask, or the defValue if converting failed
     */
    @IntRange(from = 0, to = 0xFFFF)
    public static int convert(@Nullable final Set<String> stringSet,
                              final int defValue) {
        if (stringSet == null || stringSet.isEmpty()) {
            return defValue;
        }

        try {
            int bitmask = 0;
            for (final String s : stringSet) {
                bitmask |= Integer.parseInt(s);
            }
            return bitmask;

        } catch (@NonNull final NumberFormatException ignore) {
            // we should never have an invalid setting in the prefs... flw
            return defValue;
        }
    }

    @NonNull
    private static Set<String> convert(final int value) {
        final Set<String> stringSet = new HashSet<>();
        int tmp = value;
        int bit = 1;
        while (tmp != 0) {
            if ((tmp & 1) == 1) {
                stringSet.add(String.valueOf(bit));
            }
            bit *= 2;
            // unsigned shift
            tmp = tmp >>> 1;
        }
        return stringSet;
    }

    public boolean isModified() {
        return modified;
    }

    @Override
    public void putInt(@NonNull final String key,
                       final int value) {
        switch (key) {
            case PK_EXPANSION_LEVEL:
                style.setExpansionLevel(value);
                break;

            case PK_COVER_SCALE:
                style.setCoverScale(value);
                break;

            case PK_TEXT_SCALE:
                style.setTextScale(value);
                break;

            default:
                throw new IllegalArgumentException(key);
        }
        modified = true;
    }

    @Override
    public int getInt(@NonNull final String key,
                      final int defValue) {
        switch (key) {
            case PK_EXPANSION_LEVEL:
                return style.getExpansionLevel();

            case PK_COVER_SCALE:
                return style.getCoverScale();

            case PK_TEXT_SCALE:
                return style.getTextScale();

            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    public void putBoolean(@NonNull final String key,
                           final boolean value) {
        switch (key) {
            case PK_LIST_SHOW_COVERS:
                style.setShowField(Style.Screen.List, DBKey.COVER_IS_USED[0], value);
                break;
            case PK_LIST_SHOW_AUTHOR:
                style.setShowField(Style.Screen.List, DBKey.FK_AUTHOR, value);
                break;
            case PK_LIST_SHOW_PUBLISHER:
                style.setShowField(Style.Screen.List, DBKey.FK_PUBLISHER, value);
                break;
            case PK_LIST_SHOW_PUB_DATE:
                style.setShowField(Style.Screen.List, DBKey.DATE_BOOK_PUBLICATION, value);
                break;
            case PK_LIST_SHOW_FORMAT:
                style.setShowField(Style.Screen.List, DBKey.BOOK_FORMAT, value);
                break;
            case PK_LIST_SHOW_LOCATION:
                style.setShowField(Style.Screen.List, DBKey.LOCATION, value);
                break;
            case PK_LIST_SHOW_RATING:
                style.setShowField(Style.Screen.List, DBKey.RATING, value);
                break;
            case PK_LIST_SHOW_BOOKSHELVES:
                style.setShowField(Style.Screen.List, DBKey.FK_BOOKSHELF, value);
                break;
            case PK_LIST_SHOW_ISBN:
                style.setShowField(Style.Screen.List, DBKey.KEY_ISBN, value);
                break;

            case PK_GROUP_ROW_HEIGHT:
                style.setGroupRowUsesPreferredHeight(value);
                break;

            case PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                style.setShowAuthorByGivenName(value);
                break;

            case PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                style.setSortAuthorByGivenName(value);
                break;

            case AuthorBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooksUnderEachAuthor(value);
                break;

            case SeriesBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooksUnderEachSeries(value);
                break;

            case PublisherBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooksUnderEachPublisher(value);
                break;

            case BookshelfBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooksUnderEachBookshelf(value);
                break;

            default:
                if (key.equals(PK_DETAILS_SHOW_COVER[0])) {
                    style.setShowField(Style.Screen.Detail, DBKey.COVER_IS_USED[0], value);
                } else if (key.equals(PK_DETAILS_SHOW_COVER[1])) {
                    style.setShowField(Style.Screen.Detail, DBKey.COVER_IS_USED[1], value);
                } else {
                    throw new IllegalArgumentException(key);
                }
                break;
        }
        modified = true;
    }

    @Override
    public boolean getBoolean(@NonNull final String key,
                              final boolean defValue) {
        switch (key) {
            case PK_LIST_SHOW_COVERS:
                return style.isShowField(Style.Screen.List, DBKey.COVER_IS_USED[0]);

            case PK_LIST_SHOW_AUTHOR:
                return style.isShowField(Style.Screen.List, DBKey.FK_AUTHOR);

            case PK_LIST_SHOW_PUBLISHER:
                return style.isShowField(Style.Screen.List, DBKey.FK_PUBLISHER);

            case PK_LIST_SHOW_PUB_DATE:
                return style.isShowField(Style.Screen.List, DBKey.DATE_BOOK_PUBLICATION);

            case PK_LIST_SHOW_FORMAT:
                return style.isShowField(Style.Screen.List, DBKey.BOOK_FORMAT);

            case PK_LIST_SHOW_LOCATION:
                return style.isShowField(Style.Screen.List, DBKey.LOCATION);

            case PK_LIST_SHOW_RATING:
                return style.isShowField(Style.Screen.List, DBKey.RATING);

            case PK_LIST_SHOW_BOOKSHELVES:
                return style.isShowField(Style.Screen.List, DBKey.FK_BOOKSHELF);

            case PK_LIST_SHOW_ISBN:
                return style.isShowField(Style.Screen.List, DBKey.KEY_ISBN);

            case PK_GROUP_ROW_HEIGHT:
                return style.isGroupRowUsesPreferredHeight();

            case PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                return style.isShowAuthorByGivenName();

            case PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                return style.isSortAuthorByGivenName();

            case AuthorBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooksUnderEachAuthor();

            case SeriesBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooksUnderEachSeries();

            case PublisherBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooksUnderEachPublisher();

            case BookshelfBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooksUnderEachBookshelf();

            default:
                if (key.equals(PK_DETAILS_SHOW_COVER[0])) {
                    return style.isShowField(Style.Screen.Detail, DBKey.COVER_IS_USED[0]);

                } else if (key.equals(PK_DETAILS_SHOW_COVER[1])) {
                    return style.isShowField(Style.Screen.Detail, DBKey.COVER_IS_USED[1]);
                }
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    public void putString(@NonNull final String key,
                          @Nullable final String value) {
        if (PK_NAME.equals(key)) {
            //noinspection ConstantConditions
            style.setName(value);
        } else {
            throw new IllegalArgumentException(key);
        }
        modified = true;
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key,
                            @Nullable final String defValue) {
        if (PK_NAME.equals(key)) {
            return style.getName();
        }
        throw new IllegalArgumentException(key);
    }

    @Override
    public void putStringSet(@NonNull final String key,
                             @Nullable final Set<String> values) {
        switch (key) {
            case PK_LIST_HEADER:
                style.setHeaderFieldVisibility(convert(values, BooklistHeader.BITMASK_ALL));
                break;

            case AuthorBooklistGroup.PK_PRIMARY_TYPE:
                style.setPrimaryAuthorTypes(convert(values, Author.TYPE_UNKNOWN));
                break;

            default:
                throw new IllegalArgumentException(key);
        }
        modified = true;
    }

    @Nullable
    @Override
    public Set<String> getStringSet(@NonNull final String key,
                                    @Nullable final Set<String> defValues) {
        switch (key) {
            case PK_LIST_HEADER:
                return convert(style.getHeaderFieldVisibility());

            case AuthorBooklistGroup.PK_PRIMARY_TYPE:
                return convert(style.getPrimaryAuthorType());

            default:
                throw new IllegalArgumentException(key);
        }
    }
}
