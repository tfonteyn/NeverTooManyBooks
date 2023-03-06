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
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceScreen;

import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
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
    public static final String PK_GROUPS_AUTHOR_PRIMARY_TYPE =
            "style.booklist.group.authors.primary.type";
    public static final String PK_GROUPS_AUTHOR_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.authors.show.all";
    public static final String PK_GROUPS_BOOKSHELF_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.bookshelf.show.all";
    public static final String PK_GROUPS_PUBLISHER_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.publisher.show.all";
    public static final String PK_GROUPS_SERIES_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.series.show.all";


    /** The default expansion level for the groups. */
    public static final String PK_EXPANSION_LEVEL = "style.booklist.levels.default";

    /** Relative scaling factor for text on the list screen. */
    public static final String PK_TEXT_SCALE = "style.booklist.scale.font";
    /** Relative scaling factor for covers on the list screen. */
    public static final String PK_COVER_SCALE = "style.booklist.scale.thumbnails";

    /** What fields the user wants to see in the list header. */
    public static final String PK_LIST_HEADER = "style.booklist.header";

    public static final String PK_GROUP_ROW_HEIGHT = "style.booklist.group.height";


    /** Style overrides global setting with the same key. */
    public static final String PK_SHOW_AUTHOR_NAME_GIVEN_FIRST = "show.author.name.given_first";
    public static final String PK_SORT_AUTHOR_NAME_GIVEN_FIRST = "sort.author.name.given_first";


    /** The combined bitmask value for the PK_LIST_SHOW* values. */
    public static final String PK_LIST_FIELD_VISIBILITY = "style.list.show.fields";
    /** The combined bitmask value for the PK_DETAILS_SHOW* values. */
    public static final String PK_DETAILS_FIELD_VISIBILITY = "style.details.show.fields";

    /** Show the cover image (front only) for each book on the list screen. */
    public static final String PK_LIST_SHOW_COVERS = "style.booklist.show.thumbnails";
    /** Show/hide these fields for each book on the list screen. */
    public static final String PK_LIST_SHOW_AUTHOR = "style.booklist.show.author";
    public static final String PK_LIST_SHOW_PUBLISHER = "style.booklist.show.publisher";
    public static final String PK_LIST_SHOW_SERIES = "style.booklist.show.series";
    public static final String PK_LIST_SHOW_PUB_DATE = "style.booklist.show.publication.date";
    public static final String PK_LIST_SHOW_FORMAT = "style.booklist.show.format";
    public static final String PK_LIST_SHOW_LANGUAGE = "style.booklist.show.language";
    public static final String PK_LIST_SHOW_LOCATION = "style.booklist.show.location";
    public static final String PK_LIST_SHOW_CONDITION = "style.booklist.show.condition";
    public static final String PK_LIST_SHOW_RATING = "style.booklist.show.rating";
    public static final String PK_LIST_SHOW_BOOKSHELVES = "style.booklist.show.bookshelves";
    public static final String PK_LIST_SHOW_ISBN = "style.booklist.show.isbn";

    /** Show the cover images (front/back) for each book on the details screen. */
    public static final String[] PK_DETAILS_SHOW_COVER = {
            "style.details.show.thumbnail.0",
            "style.details.show.thumbnail.1",
            };


    /** See {@link BooklistGroup#setPreferencesVisible(PreferenceScreen, boolean)}. */
    public static final String PSK_STYLE_AUTHOR = "psk_style_author";
    public static final String PSK_STYLE_SERIES = "psk_style_series";
    public static final String PSK_STYLE_PUBLISHER = "psk_style_publisher";
    public static final String PSK_STYLE_BOOKSHELF = "psk_style_bookshelf";


    @NonNull
    private final UserStyle style;
    @NonNull
    private final MutableLiveData<Void> onModified;

    private boolean modified;

    public StyleDataStore(@NonNull final UserStyle style,
                          @NonNull final MutableLiveData<Void> onModified) {
        this.style = style;
        this.onModified = onModified;
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

    public void setModified() {
        modified = true;
        onModified.setValue(null);
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
        setModified();
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
                style.setShowField(Style.Screen.List, DBKey.COVER[0], value);
                break;
            case PK_LIST_SHOW_AUTHOR:
                style.setShowField(Style.Screen.List, DBKey.FK_AUTHOR, value);
                break;
            case PK_LIST_SHOW_PUBLISHER:
                style.setShowField(Style.Screen.List, DBKey.FK_PUBLISHER, value);
                break;
            case PK_LIST_SHOW_SERIES:
                style.setShowField(Style.Screen.List, DBKey.FK_SERIES, value);
                break;
            case PK_LIST_SHOW_PUB_DATE:
                style.setShowField(Style.Screen.List, DBKey.BOOK_PUBLICATION__DATE, value);
                break;
            case PK_LIST_SHOW_FORMAT:
                style.setShowField(Style.Screen.List, DBKey.FORMAT, value);
                break;
            case PK_LIST_SHOW_LANGUAGE:
                style.setShowField(Style.Screen.List, DBKey.LANGUAGE, value);
                break;
            case PK_LIST_SHOW_LOCATION:
                style.setShowField(Style.Screen.List, DBKey.LOCATION, value);
                break;
            case PK_LIST_SHOW_CONDITION:
                style.setShowField(Style.Screen.List, DBKey.BOOK_CONDITION, value);
                break;
            case PK_LIST_SHOW_RATING:
                style.setShowField(Style.Screen.List, DBKey.RATING, value);
                break;
            case PK_LIST_SHOW_BOOKSHELVES:
                style.setShowField(Style.Screen.List, DBKey.FK_BOOKSHELF, value);
                break;
            case PK_LIST_SHOW_ISBN:
                style.setShowField(Style.Screen.List, DBKey.BOOK_ISBN, value);
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

            case PK_GROUPS_AUTHOR_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooks(Style.UnderEach.Author, value);
                break;

            case PK_GROUPS_SERIES_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooks(Style.UnderEach.Series, value);
                break;

            case PK_GROUPS_PUBLISHER_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooks(Style.UnderEach.Publisher, value);
                break;

            case PK_GROUPS_BOOKSHELF_SHOW_BOOKS_UNDER_EACH:
                style.setShowBooks(Style.UnderEach.Bookshelf, value);
                break;

            default:
                if (key.equals(PK_DETAILS_SHOW_COVER[0])) {
                    style.setShowField(Style.Screen.Detail, DBKey.COVER[0], value);
                } else if (key.equals(PK_DETAILS_SHOW_COVER[1])) {
                    style.setShowField(Style.Screen.Detail, DBKey.COVER[1], value);
                } else {
                    throw new IllegalArgumentException(key);
                }
                break;
        }
        setModified();
    }

    @Override
    public boolean getBoolean(@NonNull final String key,
                              final boolean defValue) {
        switch (key) {
            case PK_LIST_SHOW_COVERS:
                return style.isShowField(Style.Screen.List, DBKey.COVER[0]);

            case PK_LIST_SHOW_AUTHOR:
                return style.isShowField(Style.Screen.List, DBKey.FK_AUTHOR);

            case PK_LIST_SHOW_PUBLISHER:
                return style.isShowField(Style.Screen.List, DBKey.FK_PUBLISHER);

            case PK_LIST_SHOW_SERIES:
                return style.isShowField(Style.Screen.List, DBKey.FK_SERIES);

            case PK_LIST_SHOW_PUB_DATE:
                return style.isShowField(Style.Screen.List, DBKey.BOOK_PUBLICATION__DATE);

            case PK_LIST_SHOW_FORMAT:
                return style.isShowField(Style.Screen.List, DBKey.FORMAT);

            case PK_LIST_SHOW_LANGUAGE:
                return style.isShowField(Style.Screen.List, DBKey.LANGUAGE);

            case PK_LIST_SHOW_LOCATION:
                return style.isShowField(Style.Screen.List, DBKey.LOCATION);

            case PK_LIST_SHOW_CONDITION:
                return style.isShowField(Style.Screen.List, DBKey.BOOK_CONDITION);

            case PK_LIST_SHOW_RATING:
                return style.isShowField(Style.Screen.List, DBKey.RATING);

            case PK_LIST_SHOW_BOOKSHELVES:
                return style.isShowField(Style.Screen.List, DBKey.FK_BOOKSHELF);

            case PK_LIST_SHOW_ISBN:
                return style.isShowField(Style.Screen.List, DBKey.BOOK_ISBN);

            case PK_GROUP_ROW_HEIGHT:
                return style.isGroupRowUsesPreferredHeight();

            case PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                return style.isShowAuthorByGivenName();

            case PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                return style.isSortAuthorByGivenName();

            case PK_GROUPS_AUTHOR_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooks(Style.UnderEach.Author);

            case PK_GROUPS_SERIES_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooks(Style.UnderEach.Series);

            case PK_GROUPS_PUBLISHER_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooks(Style.UnderEach.Publisher);

            case PK_GROUPS_BOOKSHELF_SHOW_BOOKS_UNDER_EACH:
                return style.isShowBooks(Style.UnderEach.Bookshelf);

            default:
                if (key.equals(PK_DETAILS_SHOW_COVER[0])) {
                    return style.isShowField(Style.Screen.Detail, DBKey.COVER[0]);

                } else if (key.equals(PK_DETAILS_SHOW_COVER[1])) {
                    return style.isShowField(Style.Screen.Detail, DBKey.COVER[1]);
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
        setModified();
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

            case PK_GROUPS_AUTHOR_PRIMARY_TYPE:
                style.setPrimaryAuthorTypes(convert(values, Author.TYPE_UNKNOWN));
                break;

            default:
                throw new IllegalArgumentException(key);
        }
        setModified();
    }

    @Nullable
    @Override
    public Set<String> getStringSet(@NonNull final String key,
                                    @Nullable final Set<String> defValues) {
        switch (key) {
            case PK_LIST_HEADER:
                return convert(style.getHeaderFieldVisibility());

            case PK_GROUPS_AUTHOR_PRIMARY_TYPE:
                return convert(style.getPrimaryAuthorType());

            default:
                throw new IllegalArgumentException(key);
        }
    }
}
