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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Definitions and transmogrifying (Hi Calvin) API for preference keys and actual style values.
 *
 * @see BookLevelFieldVisibility
 * @see BookDetailsFieldVisibility
 * @see com.hardbacknutter.nevertoomanybooks.booklist.style.Style.UnderEach
 */
public class StyleDataStore
        extends PreferenceDataStore {

    /** Style display name. */
    public static final String PK_NAME = "style.booklist.name";

    /** Style group preferences. */
    public static final String PK_GROUPS = "style.booklist.groups";
    /**
     * Which type of Author should be considered the primary one. e.g.
     * for comics you might want the artist instead of the writer.
     */
    public static final String PK_GROUPS_AUTHOR_PRIMARY_TYPE =
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
     * How to sort Author names.
     *
     * @see BaseStyle#setSortAuthorByGivenName(boolean)
     */
    public static final String PK_SORT_AUTHOR_NAME_GIVEN_FIRST = "sort.author.name.given_first";

    /** Detail screens: Show the cover images (front/back) for each book. */
    public static final String[] PK_DETAILS_SHOW_COVER = {
            "style.details.show.thumbnail.0",
            "style.details.show.thumbnail.1",
    };

    private static final String VIS_PREFIX = "style.booklist.show.";

    /** Map preference key to {@link DBKey}. */
    private static final Map<String, String> PK_LIST_SHOW_FIELD_TO_DB_KEY = new HashMap<>();

    /** Map preference key to {@link DBKey}. */
    private static final Map<String, String> PK_DETAILS_SHOW_FIELD_TO_DB_KEY = new HashMap<>();

    static {
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "thumbnails", DBKey.COVER[0]);

        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "author", DBKey.FK_AUTHOR);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "series", DBKey.FK_SERIES);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "publisher", DBKey.FK_PUBLISHER);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "bookshelves", DBKey.FK_BOOKSHELF);

        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "original.title", DBKey.TITLE_ORIGINAL_LANG);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "condition", DBKey.BOOK_CONDITION);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "isbn", DBKey.BOOK_ISBN);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "publication.date",
                                         DBKey.BOOK_PUBLICATION__DATE);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "format", DBKey.FORMAT);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "language", DBKey.LANGUAGE);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "location", DBKey.LOCATION);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "rating", DBKey.RATING);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "pages", DBKey.PAGE_COUNT);

        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "signed", DBKey.SIGNED__BOOL);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "edition", DBKey.EDITION__BITMASK);
        PK_LIST_SHOW_FIELD_TO_DB_KEY.put(VIS_PREFIX + "loanee", DBKey.LOANEE_NAME);
    }

    static {
        PK_DETAILS_SHOW_FIELD_TO_DB_KEY.put(PK_DETAILS_SHOW_COVER[0], DBKey.COVER[0]);
        PK_DETAILS_SHOW_FIELD_TO_DB_KEY.put(PK_DETAILS_SHOW_COVER[1], DBKey.COVER[1]);
    }

    @NonNull
    private final WritableStyle style;
    @NonNull
    private final MutableLiveData<Void> onModified;

    private boolean modified;

    /**
     * Constructor.
     *
     * @param style      to use
     * @param onModified the LiveData to update when this store is modified
     */
    public StyleDataStore(@NonNull final WritableStyle style,
                          @NonNull final MutableLiveData<Void> onModified) {
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
     * Flag this data-story as being modified.
     */
    public void setModified() {
        modified = true;
        onModified.setValue(null);
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
            case PK_GROUP_ROW_HEIGHT:
                style.setGroupRowUsesPreferredHeight(value);
                setModified();
                return;

            case PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                style.setShowAuthorByGivenName(value);
                setModified();
                return;

            case PK_SHOW_TITLES_REORDERED:
                style.setShowReorderedTitle(value);
                setModified();
                return;

            case PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                style.setSortAuthorByGivenName(value);
                setModified();
                return;
        }

        final String listDbKey = PK_LIST_SHOW_FIELD_TO_DB_KEY.get(key);
        if (listDbKey != null) {
            style.setFieldVisibility(FieldVisibility.Screen.List,
                                     listDbKey, value);
            setModified();
            return;
        }

        final String detailDbKey = PK_DETAILS_SHOW_FIELD_TO_DB_KEY.get(key);
        if (detailDbKey != null) {
            style.setFieldVisibility(FieldVisibility.Screen.Detail,
                                     detailDbKey, value);
            setModified();
            return;
        }

        final Style.UnderEach underEach = Style.UnderEach.findByPrefKey(key);
        if (underEach != null) {
            style.setShowBooksUnderEachGroup(underEach.getGroupId(), value);
            setModified();
            return;
        }

        throw new IllegalArgumentException(key);
    }

    @Override
    public boolean getBoolean(@NonNull final String key,
                              final boolean defValue) {
        switch (key) {
            case PK_GROUP_ROW_HEIGHT:
                return style.isGroupRowUsesPreferredHeight();

            case PK_SHOW_AUTHOR_NAME_GIVEN_FIRST:
                return style.isShowAuthorByGivenName();

            case PK_SHOW_TITLES_REORDERED:
                return style.isShowReorderedTitle();

            case PK_SORT_AUTHOR_NAME_GIVEN_FIRST:
                return style.isSortAuthorByGivenName();
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
                // The DataStores lives inside a ViewModel, so we can't get a Context
                // and use the type independent #getLabel(Context).
                // But we only allow name editing for a UserStyle anyhow.
                if (style.getType() == StyleType.User) {
                    //noinspection DataFlowIssue
                    ((UserStyle) style).setName(value);
                }
                // else: We can't stop the framework calling us... just ignore
                break;
            }
            case PK_LAYOUT: {
                //noinspection DataFlowIssue
                style.setLayout(Style.Layout.byId(Integer.parseInt(value)));
                break;
            }
            case PK_COVER_CLICK_ACTION: {
                //noinspection DataFlowIssue
                style.setCoverClickAction(Style.CoverClickAction
                                                  .byId(Integer.parseInt(value)));
                break;
            }
            case PK_COVER_LONG_CLICK_ACTION: {
                //noinspection DataFlowIssue
                style.setCoverLongClickAction(Style.CoverLongClickAction
                                                      .byId(Integer.parseInt(value)));
                break;
            }
            default:
                throw new IllegalArgumentException(key);
        }
        setModified();
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key,
                            @Nullable final String defValue) {
        switch (key) {
            case PK_NAME: {
                // See remarks in #putString
                if (style.getType() == StyleType.User) {
                    return ((UserStyle) style).getName();
                } else {
                    // We can't stop the framework calling us... just return bogus
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
            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    public void putStringSet(@NonNull final String key,
                             @Nullable final Set<String> values) {
        switch (key) {
            case PK_LIST_HEADER:
                style.setHeaderFieldVisibility(convert(values, BooklistHeader.BITMASK_ALL));
                break;

            case PK_GROUPS_AUTHOR_PRIMARY_TYPE:
                style.setPrimaryAuthorType(convert(values, Author.TYPE_UNKNOWN));
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
                return convert(style.getHeaderFieldVisibilityValue());

            case PK_GROUPS_AUTHOR_PRIMARY_TYPE:
                return convert(style.getPrimaryAuthorType());

            default:
                throw new IllegalArgumentException(key);
        }
    }
}
