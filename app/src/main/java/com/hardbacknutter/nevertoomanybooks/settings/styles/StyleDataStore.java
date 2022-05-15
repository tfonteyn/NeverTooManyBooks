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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistBookFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BookshelfBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.PublisherBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.SeriesBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * It's not possible to use PreferenceDataStore for individual preferences:
 * FIXME: https://issuetracker.google.com/issues/232206237
 *
 * The PK_STYLE strings are still used during import/export for backwards compatibility.
 */
public class StyleDataStore
        extends PreferenceDataStore {

    /** Style display name. */
    public static final String PK_STYLE_NAME = "style.booklist.name";
    /** The default expansion level for the groups. */
    public static final String PK_EXPANSION_LEVEL = "style.booklist.levels.default";

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
    /** Style group preferences. */
    public static final String PK_STYLE_GROUPS = "style.booklist.groups";

    /** Show the cover images (front/back) for each book on the details screen. */
    public static final String[] PK_STYLE_BOOK_DETAILS_COVER = {
            "style.details.show.thumbnail.0",
            "style.details.show.thumbnail.1",
            };

    /** Relative scaling factor for text on the list screen. */
    public static final String PK_TEXT_SCALE = "style.booklist.scale.font";
    /** Relative scaling factor for covers on the list screen. */
    public static final String PK_COVER_SCALE = "style.booklist.scale.thumbnails";

    @NonNull
    private final UserStyle style;

    private boolean modified;

    StyleDataStore(@NonNull final UserStyle style) {
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
                super.putInt(key, value);
                break;
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
                return super.getInt(key, defValue);
        }
    }

    @Override
    public void putBoolean(final String key,
                           final boolean value) {
        switch (key) {
            case PK_LIST_SHOW_COVERS:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_COVER_0, value);
                break;
            case PK_LIST_SHOW_AUTHOR:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_AUTHOR, value);
                break;
            case PK_LIST_SHOW_PUBLISHER:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_PUBLISHER, value);
                break;
            case PK_LIST_SHOW_PUB_DATE:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_PUB_DATE, value);
                break;
            case PK_LIST_SHOW_FORMAT:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_FORMAT, value);
                break;
            case PK_LIST_SHOW_LOCATION:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_LOCATION, value);
                break;
            case PK_LIST_SHOW_RATING:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_RATING, value);
                break;
            case PK_LIST_SHOW_BOOKSHELVES:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_BOOKSHELVES, value);
                break;
            case PK_LIST_SHOW_ISBN:
                style.getBooklistBookFieldVisibility()
                     .setShowField(BooklistBookFieldVisibility.SHOW_ISBN, value);
                break;

            case PK_GROUP_ROW_HEIGHT:
                style.setUseGroupRowPreferredHeight(value);
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
                if (key.equals(PK_STYLE_BOOK_DETAILS_COVER[0])) {
                    style.getBookDetailsFieldVisibility().setShowCover(0, value);
                } else if (key.equals(PK_STYLE_BOOK_DETAILS_COVER[1])) {
                    style.getBookDetailsFieldVisibility().setShowCover(1, value);
                } else {
                    super.putBoolean(key, value);
                }
                break;
        }
        modified = true;
    }

    @Override
    public boolean getBoolean(final String key,
                              final boolean defValue) {
        switch (key) {
            case PK_LIST_SHOW_COVERS:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_COVER_0);

            case PK_LIST_SHOW_AUTHOR:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_AUTHOR);

            case PK_LIST_SHOW_PUBLISHER:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_PUBLISHER);

            case PK_LIST_SHOW_PUB_DATE:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_PUB_DATE);

            case PK_LIST_SHOW_FORMAT:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_FORMAT);

            case PK_LIST_SHOW_LOCATION:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_LOCATION);

            case PK_LIST_SHOW_RATING:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_RATING);

            case PK_LIST_SHOW_BOOKSHELVES:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_BOOKSHELVES);

            case PK_LIST_SHOW_ISBN:
                return style.getBooklistBookFieldVisibility().isShowField(
                        BooklistBookFieldVisibility.SHOW_ISBN);

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
                if (key.equals(PK_STYLE_BOOK_DETAILS_COVER[0])) {
                    return style.getBookDetailsFieldVisibility().isShowCover(0);

                } else if (key.equals(PK_STYLE_BOOK_DETAILS_COVER[1])) {
                    return style.getBookDetailsFieldVisibility().isShowCover(1);
                }
                return super.getBoolean(key, defValue);
        }
    }

    @Override
    public void putString(@NonNull final String key,
                          @Nullable final String value) {
        if (PK_STYLE_NAME.equals(key)) {
            //noinspection ConstantConditions
            style.setName(value);
        } else {
            super.putString(key, value);
        }
        modified = true;
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key,
                            @Nullable final String defValue) {
        if (PK_STYLE_NAME.equals(key)) {
            return style.getName();
        }
        return super.getString(key, defValue);
    }

    @Override
    public void putStringSet(@NonNull final String key,
                             @Nullable final Set<String> values) {
        switch (key) {
            case PK_LIST_HEADER:
                style.setShowHeaderFields(convert(values, BooklistHeader.BITMASK_ALL));
                break;

            case AuthorBooklistGroup.PK_PRIMARY_TYPE:
                style.setPrimaryAuthorTypes(convert(values, Author.TYPE_UNKNOWN));
                break;

            default:
                super.putStringSet(key, values);
                break;
        }
        modified = true;
    }

    @Nullable
    @Override
    public Set<String> getStringSet(@NonNull final String key,
                                    @Nullable final Set<String> defValues) {
        switch (key) {
            case PK_LIST_HEADER:
                return convert(style.getShowHeaderFields());

            case AuthorBooklistGroup.PK_PRIMARY_TYPE:
                return convert(style.getPrimaryAuthorType());

            default:
                return super.getStringSet(key, defValues);
        }
    }
}
