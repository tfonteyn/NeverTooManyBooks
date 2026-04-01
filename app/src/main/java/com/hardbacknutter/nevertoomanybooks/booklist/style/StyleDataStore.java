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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.citations.CitationType;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.prefslib.SettingsDataStore;

/**
 * Definitions and transmogrifying (Hi Calvin) API for preference keys and actual style values.
 * <p>
 * NEWTHINGS: BookLevelField: Keys must be kept in sync with
 *  {@link StyleDataStore} preference keys
 *  com.hardbacknutter.nevertoomanybooks.booklist.style.BaseStyle BOOK_LEVEL_FIELDS_DEFAULTS
 *  "res/xml/preferences_style_book_details.xml"
 * <p>
 * NEWTHINGS: style option: add a PK, add it to the get/set, keep in sync with
 *  {@link com.hardbacknutter.nevertoomanybooks.settings.styles.StyleBaseFragment}
 *
 * @see BookLevelFieldVisibility
 * @see BookDetailsFieldVisibility
 * @see com.hardbacknutter.nevertoomanybooks.booklist.style.Style.UnderEach
 */
@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public class StyleDataStore
        implements SettingsDataStore {

    /** Style display name. */
    public static final String PK_NAME = "style.booklist.name";

    /** Style group preferences. */
    public static final String PK_GROUPS = "style.booklist.groups";
    /**
     * Which role of Author should be considered the primary one. e.g.
     * for comics you might want the artist instead of the writer.
     */
    public static final String PK_GROUPS_AUTHOR_PRIMARY_ROLE =
            "style.booklist.group.authors.primary.type";


    /** List(0) or Grid(1) layout for BoB. */
    public static final String PK_LAYOUT = "style.booklist.layout";

    /** Action to take when the user taps a cover image in the BoB list-mode/grid-mode. */
    public static final String PK_COVER_CLICK_ACTION = "style.booklist.cover.click";
    /** Action to take when the user long-click a cover image in the BoB list-grid-mode. */
    public static final String PK_COVER_LONG_CLICK_ACTION = "style.booklist.cover.click.long";

    /** The default expansion level for the groups. */
    public static final String PK_EXPANSION_LEVEL = "style.booklist.levels.default";

    /** Relative scaling factor for text on the list screen. */
    public static final String PK_TEXT_SCALE = "style.booklist.scale.font";

    /** Relative scaling factor for covers on the list screen. */
    public static final String PK_COVER_SCALE = "style.booklist.scale.thumbnails";

    /** What fields the user wants to see in the list header. */
    public static final String PK_LIST_HEADER = "style.booklist.header";

    /** Fat-finger adjustment. */
    public static final String PK_GROUP_ROW_HEIGHT = "style.booklist.group.height";

    /** Group level summation of the number of books. */
    public static final String PK_SHOW_GROUP_BOOK_COUNT = "style.booklist.show.group.book.count";

    /**
     * How to show Author names.
     *
     * @see BaseStyle#setShowAuthorByGivenName(boolean)
     */
    public static final String PK_SHOW_AUTHOR_NAME_GIVEN_FIRST = "show.author.name.given_first";

    /**
     * How to show Book/Series/TOC titles.
     *
     * @see BaseStyle#setShowReorderedTitle(boolean)
     */
    public static final String PK_SHOW_TITLES_REORDERED = "show.title.reordered";

    /**
     * How to track reading-progress.
     * <p>
     * {@code true} to use the extended tracking; {@code false} to use traditional Read/Unread.
     *
     * @see BaseStyle#setUseReadProgress(boolean)
     */
    public static final String PK_USE_READ_PROGRESS = "style.read.status.extended";

    /**
     * The format used to generate citations, i.e. as used by the "Share" option.
     *
     * @see BaseStyle#setCitationType(CitationType)
     */
    public static final String PK_CITATION_TYPE = "style.citation.type";

    /**
     * How to sort Author names.
     *
     * @see BaseStyle#setSortAuthorByGivenName(boolean)
     */
    public static final String PK_SORT_AUTHOR_NAME_GIVEN_FIRST = "sort.author.name.given_first";

    public static final String PSK_STYLE_AUTHOR = "psk_style_author";
    public static final String PSK_STYLE_SERIES = "psk_style_series";
    public static final String PSK_STYLE_PUBLISHER = "psk_style_publisher";
    public static final String PSK_STYLE_BOOKSHELF = "psk_style_bookshelf";
    public static final String PSK_LIST_BOOK_LEVEL_FIELDS = "psk_style_book_level_fields";
    public static final String PSK_LIST_BOOK_LEVEL_SORTING = "psk_style_book_level_sorting";

    /** Detail screens: Show the images for each book. */
    public static final String[] PK_DETAILS_SHOW_COVER = new String[DBKey.NR_OF_BOOK_COVERS];
    public static final String PK_LIST_BOOK_SHOW_COVER_0 = "style.booklist.show.thumbnails";

    /** Visibility prefix. */
    public static final String VIS_PREFIX = "style.booklist.show.";

    /** Map preference key to {@link DBKey}. */
    private static final Map<String, String> PK_LIST_SHOW_FIELD_TO_DB_KEY = new HashMap<>();
    /** Map preference key to {@link DBKey}. */
    private static final Map<String, String> PK_DETAILS_SHOW_FIELD_TO_DB_KEY = new HashMap<>();

    /*
     * NEWTHINGS: BookLevelField: add mapping
     * NEWTHINGS: style option: add mapping
     */
    static {
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "thumbnails", DBKey.COVER[0]);

        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "author", DBKey.FK_AUTHOR);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "series", DBKey.FK_SERIES);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "publisher", DBKey.FK_PUBLISHER);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "bookshelves", DBKey.FK_BOOKSHELF);

        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "original.title",
                                         DBKey.TRANSLATION_ORIGINAL_TITLE);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "original.language",
                                         DBKey.TRANSLATION_ORIGINAL_LANGUAGE);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "condition", DBKey.CONDITION_BOOK);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "isbn", DBKey.ISBN);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "publication.date",
                                         DBKey.PUBLICATION_DATE);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "first.publication.date",
                                         DBKey.FIRST_PUBLICATION_DATE);

        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "format", DBKey.FORMAT);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "language", DBKey.LANGUAGE);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "location", DBKey.LOCATION);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "rating", DBKey.RATING);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "pages", DBKey.PAGES);

        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "signed", DBKey.SIGNED__BOOL);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "edition", DBKey.EDITION);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "loanee", DBKey.LOANEE_NAME);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "reading.progress", DBKey.READ_PROGRESS);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "read", DBKey.READ__BOOL);

        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "date.added", DBKey.DATE_ADDED__UTC);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "date.updated", DBKey.DATE_LAST_UPDATED__UTC);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "date.acquired", DBKey.DATE_ACQUIRED);
    }

    static {
        for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
            PK_DETAILS_SHOW_COVER[cIdx] = "style.details.show.thumbnail." + cIdx;
            PK_DETAILS_SHOW_FIELD_TO_DB_KEY.put(PK_DETAILS_SHOW_COVER[cIdx], DBKey.COVER[cIdx]);
        }
    }

    /**
     * The style we're editing.
     * <p>
     * A {@link UserStyle} or {@link GlobalStyle},
     * but <strong>never</strong> a {@link BuiltinStyle}
     */
    @NonNull
    private final WritableStyle style;
    @NonNull
    private final MutableLiveData<String> onModified;

    private boolean modified;

    /**
     * Constructor.
     *
     * @param style      to use
     * @param onModified the LiveData to update when this store is modified
     */
    public StyleDataStore(@NonNull final WritableStyle style,
                          @NonNull final MutableLiveData<String> onModified) {
        this.style = style;
        this.onModified = onModified;
    }

    /**
     * Parse and combine the stringified integer-bit values in the set into a bitmask.
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
            return stringSet.stream()
                            .mapToInt(Integer::parseInt)
                            .reduce(0, (a, b) -> a | b);

        } catch (@NonNull final NumberFormatException ignore) {
            // we should never have an invalid setting in the prefs... flw
            return defValue;
        }
    }

    /**
     * Split the bitmask into a {@code Set} with stringified integer-bit values.
     *
     * @param value the bitmask to convert
     *
     * @return the set
     */
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

    /**
     * Flag this data-store as being modified.
     *
     * @param key which was updated
     */
    public void setModified(@NonNull final String key) {
        modified = true;
        onModified.setValue(key);
    }

    /**
     * Check if this data-store has modified data.
     *
     * @return flag
     */
    public boolean isModified() {
        return modified;
    }

    @Override
    public void putBoolean(@NonNull final String key,
                           @Nullable final Boolean value) {
        // Sanity check, should never happen... flw
        if (value == null) {
            return;
        }

        switch (key) {
            case PK_GROUP_ROW_HEIGHT:
                style.setGroupRowUsesPreferredHeight(value);
                setModified(key);
                return;

            case PK_SHOW_GROUP_BOOK_COUNT:
                style.setShowGroupBookCount(value);
                setModified(key);
                return;

            case PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                style.setShowAuthorByGivenName(value);
                setModified(key);
                return;

            case PK_SHOW_TITLES_REORDERED:
                style.setShowReorderedTitle(value);
                setModified(key);
                return;

            case PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                style.setSortAuthorByGivenName(value);
                setModified(key);
                return;

            case PK_USE_READ_PROGRESS:
                style.setUseReadProgress(value);
                setModified(key);
                return;
        }

        final String listDbKey = PK_LIST_SHOW_FIELD_TO_DB_KEY.get(key);
        if (listDbKey != null) {
            style.setFieldVisibility(FieldVisibility.Screen.List,
                                     listDbKey, value);
            setModified(key);
            return;
        }

        final String detailDbKey = PK_DETAILS_SHOW_FIELD_TO_DB_KEY.get(key);
        if (detailDbKey != null) {
            style.setFieldVisibility(FieldVisibility.Screen.Detail,
                                     detailDbKey, value);
            setModified(key);
            return;
        }

        final Style.UnderEach underEach = Style.UnderEach.findByPrefKey(key);
        if (underEach != null) {
            style.setShowBooksUnderEachGroup(underEach.getGroupId(), value);
            setModified(key);
            return;
        }

        throw new IllegalArgumentException(key);
    }

    @Override
    public boolean getBoolean(@NonNull final String key,
                              @Nullable final Boolean defValue) {
        switch (key) {
            case PK_GROUP_ROW_HEIGHT:
                return style.isGroupRowUsesPreferredHeight();

            case PK_SHOW_GROUP_BOOK_COUNT:
                return style.isShowGroupBookCount();

            case PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                return style.isShowAuthorByGivenName();

            case PK_SHOW_TITLES_REORDERED:
                return style.isShowReorderedTitle();

            case PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                return style.isSortAuthorByGivenName();

            case PK_USE_READ_PROGRESS:
                return style.useReadProgress();
        }

        final String listDbKey = PK_LIST_SHOW_FIELD_TO_DB_KEY.get(key);
        if (listDbKey != null) {
            return style.isShowField(FieldVisibility.Screen.List, listDbKey);
        }

        final String detailDbKey = PK_DETAILS_SHOW_FIELD_TO_DB_KEY.get(key);
        if (detailDbKey != null) {
            return style.isShowField(FieldVisibility.Screen.Detail, detailDbKey);
        }

        final Style.UnderEach underEach = Style.UnderEach.findByPrefKey(key);
        if (underEach != null) {
            return style.isShowBooksUnderEachGroup(underEach.getGroupId());
        }

        throw new IllegalArgumentException(key);
    }

    @Override
    public void putString(@NonNull final String key,
                          @Nullable final String value) {
        switch (key) {
            case PK_NAME: {
                // We only allow name editing for a UserStyle.
                // We should never get here if it's not a UserStyle... but paranoia...
                if (style.getType() == Style.Type.User) {
                    //noinspection DataFlowIssue
                    ((UserStyle) style).setName(value);
                }
                break;
            }
            case PK_LAYOUT: {
                style.setLayout(value != null
                                ? ScreenLayout.byId(Integer.parseInt(value))
                                : ScreenLayout.List);
                break;
            }
            case PK_COVER_CLICK_ACTION: {
                style.setCoverClickAction(
                        value != null
                        ? Style.CoverClickAction.byId(Integer.parseInt(value))
                        : Style.CoverClickAction.OpenBookDetails);
                break;
            }
            case PK_COVER_LONG_CLICK_ACTION: {
                style.setCoverLongClickAction(
                        value != null
                        ? Style.CoverLongClickAction.byId(Integer.parseInt(value))
                        : Style.CoverLongClickAction.PopupMenu);
                break;
            }
            case PK_CITATION_TYPE: {
                style.setCitationType(
                        value != null
                        ? CitationType.byId(Integer.parseInt(value))
                        : CitationType.Default);
                break;
            }
            default:
                throw new IllegalArgumentException(key);
        }
        setModified(key);
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key,
                            @Nullable final String defValue) {
        switch (key) {
            case PK_NAME: {
                // We only allow name editing for a UserStyle.
                if (style.getType() == Style.Type.User) {
                    return ((UserStyle) style).getName();
                } else {
                    // We should never get here if it's not a UserStyle... but paranoia...
                    // Just return bogus
                    return "";
                }
            }
            case PK_LAYOUT: {
                return String.valueOf(style.getLayout().getId());
            }
            case PK_COVER_CLICK_ACTION: {
                return String.valueOf(style.getCoverClickAction().getId());
            }
            case PK_COVER_LONG_CLICK_ACTION: {
                return String.valueOf(style.getCoverLongClickAction().getId());
            }
            case PK_CITATION_TYPE: {
                return String.valueOf(style.getCitationType().getId());
            }
            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    public void putStringSet(@NonNull final String key,
                             @Nullable final Set<String> values) {
        switch (key) {
            case PK_LIST_HEADER:
                if (values == null || values.isEmpty()) {
                    style.setHeaderFieldVisibility(BooklistHeader.NONE);
                } else {
                    style.setHeaderFieldVisibility(convert(values, BooklistHeader.BITMASK_ALL));
                }
                break;

            case PK_GROUPS_AUTHOR_PRIMARY_ROLE:
                style.setPrimaryAuthorRole(convert(values, AuthorRole.UNKNOWN));
                break;

            default:
                throw new IllegalArgumentException(key);
        }
        setModified(key);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(@NonNull final String key,
                                    @Nullable final Set<String> defValues) {
        switch (key) {
            case PK_LIST_HEADER:
                return convert(style.getHeaderFieldVisibilityValue());

            case PK_GROUPS_AUTHOR_PRIMARY_ROLE:
                return convert(style.getPrimaryAuthorRole());

            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    public void putFloat(@NonNull final String key,
                         @Nullable final Float value) {
        // Sanity check, should never happen... flw
        if (value == null) {
            return;
        }

        switch (key) {
            case PK_EXPANSION_LEVEL:
                style.setExpansionLevel(value.intValue());
                break;

            case PK_COVER_SCALE:
                style.setCoverScale(CoverScale.byId(value.intValue()));
                break;

            case PK_TEXT_SCALE:
                style.setTextScale(TextScale.byId(value.intValue()));
                break;

            default:
                throw new IllegalArgumentException(key);
        }
        setModified(key);
    }

    @Override
    public float getFloat(@NonNull final String key,
                          @Nullable final Float defValue) {
        switch (key) {
            case PK_EXPANSION_LEVEL:
                return style.getExpansionLevel();

            case PK_COVER_SCALE:
                return style.getCoverScale().getId();

            case PK_TEXT_SCALE:
                return style.getTextScale().getId();

            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    public void putLong(@NonNull final String key,
                        @Nullable final Long value) {
        throw new IllegalArgumentException(key);
    }

    @Override
    public long getLong(@NonNull final String key,
                        @Nullable final Long defValue) {
        throw new IllegalArgumentException(key);
    }

    @Override
    public void putInt(@NonNull final String key,
                       @Nullable final Integer value) {

    }

    @Override
    public int getInt(@NonNull final String key,
                      @Nullable final Integer defValue) {
        throw new IllegalArgumentException(key);
    }
}
