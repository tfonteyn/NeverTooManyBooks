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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceDataStore;

import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Definitions and transmogrifying (Hi Calvin) API for preference keys and actual style values.
 *
 * @see BooklistFieldVisibility
 * @see BookDetailsFieldVisibility
 * @see com.hardbacknutter.nevertoomanybooks.booklist.style.Style.UnderEach
 */
public class StyleDataStore
        extends PreferenceDataStore {

    /** Style display name. */
    public static final String PK_NAME = "style.booklist.name";

    /** Style group preferences. */
    public static final String PK_GROUPS = "style.booklist.groups";
    public static final String PK_GROUPS_AUTHOR_PRIMARY_TYPE =
            "style.booklist.group.authors.primary.type";


    /** The default expansion level for the groups. */
    public static final String PK_EXPANSION_LEVEL = "style.booklist.levels.default";

    /** Relative scaling factor for text on the list screen. */
    public static final String PK_TEXT_SCALE = "style.booklist.scale.font";
    /** Relative scaling factor for covers on the list screen. */
    public static final String PK_COVER_SCALE = "style.booklist.scale.thumbnails";

    /** What fields the user wants to see in the list header. */
    public static final String PK_LIST_HEADER = "style.booklist.header";

    public static final String PK_GROUP_ROW_HEIGHT = "style.booklist.group.height";


    /**
     * Style overrides global setting with the same key.
     *
     * @see BaseStyle#setShowAuthorByGivenName(boolean)
     */
    public static final String PK_SHOW_AUTHOR_NAME_GIVEN_FIRST = "show.author.name.given_first";

    /**
     * Style overrides global setting with the same key.
     *
     * @see BaseStyle#setSortAuthorByGivenName(boolean)
     */
    public static final String PK_SORT_AUTHOR_NAME_GIVEN_FIRST = "sort.author.name.given_first";


    @NonNull
    private final Context appContext;
    @NonNull
    private final UserStyle style;
    @NonNull
    private final MutableLiveData<Void> onModified;

    private boolean modified;

    /**
     * Constructor.
     *
     * @param context    Current context
     * @param style      to use
     * @param onModified the LiveData to update when this store is modified
     */
    public StyleDataStore(@NonNull final Context context,
                          @NonNull final UserStyle style,
                          @NonNull final MutableLiveData<Void> onModified) {
        // we're cache this context for preferences access. So use the AppContext!
        this.appContext = context.getApplicationContext();
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

        if (PK_GROUP_ROW_HEIGHT.equals(key)) {
            style.setGroupRowUsesPreferredHeight(value);

        } else if (PK_SHOW_AUTHOR_NAME_GIVEN_FIRST.equals(key)) {
            style.setShowAuthorByGivenName(value);

        } else if (PK_SORT_AUTHOR_NAME_GIVEN_FIRST.equals(key)) {
            style.setSortAuthorByGivenName(value);

        } else if (BooklistFieldVisibility.containsKey(key)) {
            style.setShowField(Style.Screen.List,
                               BooklistFieldVisibility.getDBKeyForPrefKey(key), value);

        } else if (BookDetailsFieldVisibility.containsKey(key)) {
            style.setShowField(Style.Screen.Detail,
                               BookDetailsFieldVisibility.getDBKeyForPrefKey(key), value);

        } else {
            final Style.UnderEach underEach = Style.UnderEach.findByPrefKey(key);
            if (underEach != null) {
                style.setShowBooks(underEach, value);
            } else {
                throw new IllegalArgumentException(key);
            }
        }
        setModified();
    }

    @Override
    public boolean getBoolean(@NonNull final String key,
                              final boolean defValue) {
        if (PK_GROUP_ROW_HEIGHT.equals(key)) {
            return style.isGroupRowUsesPreferredHeight();

        } else if (PK_SHOW_AUTHOR_NAME_GIVEN_FIRST.equals(key)) {
            return style.isShowAuthorByGivenName();

        } else if (PK_SORT_AUTHOR_NAME_GIVEN_FIRST.equals(key)) {
            return style.isSortAuthorByGivenName();

        } else if (BooklistFieldVisibility.containsKey(key)) {
            return style.isShowField(appContext, Style.Screen.List,
                                     BooklistFieldVisibility.getDBKeyForPrefKey(key));

        } else if (BookDetailsFieldVisibility.containsKey(key)) {
            return style.isShowField(appContext, Style.Screen.Detail,
                                     BookDetailsFieldVisibility.getDBKeyForPrefKey(key));
        } else {
            final Style.UnderEach underEach = Style.UnderEach.findByPrefKey(key);
            if (underEach != null) {
                return style.isShowBooks(underEach);
            }
        }

        throw new IllegalArgumentException(key);
    }

    @Override
    public void putString(@NonNull final String key,
                          @Nullable final String value) {
        if (PK_NAME.equals(key)) {
            //noinspection DataFlowIssue
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
