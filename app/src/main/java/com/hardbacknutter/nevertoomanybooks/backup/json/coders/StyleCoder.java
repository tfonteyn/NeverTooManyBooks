/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BaseStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleType;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.WritableStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class StyleCoder
        implements JsonCoder<Style> {

    private static final String TAG = "StyleCoder";

    /** The combined bitmask value for the PK_DETAILS_SHOW* values. */
    private static final String PK_DETAILS_FIELD_VISIBILITY = "style.details.show.fields";
    /** The combined bitmask value for the PK_LIST_SHOW* values. */
    private static final String PK_LIST_FIELD_VISIBILITY = "style.list.show.fields";
    /** The JSON encode string with the orderBy columns for the book level. */
    private static final String PK_LIST_FIELD_ORDER_BY = "style.list.sort.fields";

    /** The sub-tag for the array with the style settings. */
    private static final String STYLE_SETTINGS = "settings";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_SORT = "sort";

    /**
     * Static wrapper convenience method.
     * Encode the {@link Style#getBookLevelFieldsOrderBy()} to a JSON String.
     *
     * @param style to use
     *
     * @return JSON encoded string
     *
     * @throws JSONException upon any parsing error
     */
    @NonNull
    public static String getBookLevelFieldsOrderByAsJsonString(@NonNull final Style style)
            throws JSONException {
        final JSONArray columns = new StyleCoder().encodeBookLevelFieldsOrderBy(style);
        return Objects.requireNonNull(columns.toString(),
                                      "encBookLevelFieldsOrderBy was NULL");
    }

    /**
     * Static wrapper convenience method.
     * Decode a JSON String for use with {@link BaseStyle#setBookLevelFieldsOrderBy(Map)}.
     *
     * @param source JSON encoded string
     *
     * @return map with columns; will be empty if decoding failed (see log for any reason).
     */
    @NonNull
    public static Map<String, Sort> decodeBookLevelFieldsOrderBy(@NonNull final String source) {
        try {
            return new StyleCoder().decodeBookLevelFieldsOrderBy(new JSONArray(source));

        } catch (@NonNull final JSONException e) {
            // Do not crash, this is not critical, but DO log
            LoggerFactory.getLogger().e(TAG, e);
        }
        return Map.of();
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final Style style)
            throws JSONException {
        final JSONObject out = new JSONObject();

        final StyleType type = style.getType();

        out.put(DBKey.STYLE_TYPE, type.getId());
        out.put(DBKey.STYLE_UUID, style.getUuid());

        out.put(DBKey.STYLE_IS_PREFERRED, style.isPreferred());
        out.put(DBKey.STYLE_MENU_POSITION, style.getMenuPosition());

        if (type == StyleType.Builtin) {
            // We're done
            return out;
        }

        if (type == StyleType.User) {
            out.put(DBKey.STYLE_NAME, ((UserStyle) style).getName());
        }

        // The settings will be stored under a new JSON object 'STYLE_SETTINGS'
        final JSONObject settings = new JSONObject();

        encodeGroups(style, settings);

        settings.put(StyleDataStore.PK_LAYOUT,
                     style.getLayout().getId());
        settings.put(StyleDataStore.PK_COVER_CLICK_ACTION,
                     style.getCoverClickAction().getId());
        settings.put(StyleDataStore.PK_COVER_LONG_CLICK_ACTION,
                     style.getCoverLongClickAction().getId());
        settings.put(StyleDataStore.PK_COVER_SCALE,
                     style.getCoverScale().getScale());
        settings.put(StyleDataStore.PK_TEXT_SCALE,
                     style.getTextScale().getScale());

        settings.put(StyleDataStore.PK_GROUP_ROW_HEIGHT,
                     style.isGroupRowUsesPreferredHeight());

        settings.put(StyleDataStore.PK_LIST_HEADER,
                     style.getHeaderFieldVisibilityValue());
        settings.put(PK_LIST_FIELD_ORDER_BY,
                     encodeBookLevelFieldsOrderBy(style));
        settings.put(PK_LIST_FIELD_VISIBILITY,
                     style.getFieldVisibilityValue(FieldVisibility.Screen.List));

        settings.put(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST,
                     style.isSortAuthorByGivenName());

        settings.put(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST,
                     style.isShowAuthorByGivenName());
        settings.put(StyleDataStore.PK_SHOW_TITLES_REORDERED,
                     style.isShowReorderedTitle());

        settings.put(StyleDataStore.PK_USE_READ_PROGRESS,
                     style.useReadProgress());

        settings.put(PK_DETAILS_FIELD_VISIBILITY,
                     style.getFieldVisibilityValue(FieldVisibility.Screen.Detail));

        // Store them
        out.put(STYLE_SETTINGS, settings);

        return out;
    }

    private void encodeGroups(@NonNull final Style style,
                              @NonNull final JSONObject options) {

        options.put(StyleDataStore.PK_EXPANSION_LEVEL, style.getExpansionLevel());

        options.put(StyleDataStore.PK_GROUPS, new JSONArray(
                style.getGroupList()
                     .stream()
                     .map(BooklistGroup::getId)
                     .collect(Collectors.toList())));
        if (style.hasGroup(BooklistGroup.AUTHOR)) {
            options.put(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE, style.getPrimaryAuthorType());
        }
        for (final Style.UnderEach item : Style.UnderEach.values()) {
            if (style.hasGroup(item.getGroupId())) {
                options.put(item.getPrefKey(), style.isShowBooksUnderEachGroup(item.getGroupId()));
            }
        }
    }

    @NonNull
    private JSONArray encodeBookLevelFieldsOrderBy(@NonNull final Style style)
            throws JSONException {
        final JSONArray columns = new JSONArray();
        style.getBookLevelFieldsOrderBy().forEach((columnName, sort) -> {
            final JSONObject column = new JSONObject();
            column.put(COLUMN_NAME, columnName);
            column.put(COLUMN_SORT, sort.name());
            columns.put(column);
        });

        return columns;
    }

    @NonNull
    @Override
    public Style decode(@NonNull final JSONObject data)
            throws JSONException {

        final String uuid = data.getString(DBKey.STYLE_UUID);

        final StyleType type;
        if (data.has(DBKey.STYLE_TYPE)) {
            // Version 5.1 archives store the type; just use it.
            type = StyleType.byId(data.getInt(DBKey.STYLE_TYPE));
        } else {
            // without a STYLE_TYPE, we're reading a version 5.0 or earlier
            // Use the UUID to check if we're reading a builtin Style.
            if (BuiltinStyle.isBuiltin(uuid)) {
                type = StyleType.Builtin;
            } else {
                type = StyleType.User;
            }
        }

        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
        final Style style;
        switch (type) {
            case User: {
                style = UserStyle.createForImport(uuid, stylesHelper.getGlobalStyle());
                if (data.has(DBKey.STYLE_NAME)) {
                    ((UserStyle) style).setName(data.getString(DBKey.STYLE_NAME));
                }
                break;
            }
            case Builtin: {
                style = stylesHelper.getStyle(uuid).orElseGet(
                        // It's a recognized Builtin Style, but it's deprecated.
                        // We return the default builtin style instead.
                        () -> stylesHelper.getStyle(BuiltinStyle.HARD_DEFAULT_UUID).orElseThrow());
                break;
            }
            case Global: {
                style = stylesHelper.getGlobalStyle();
                break;
            }
            default:
                throw new IllegalArgumentException();
        }

        style.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
        style.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));

        if (data.has(STYLE_SETTINGS)) {
            // any element in the source which we don't know, will simply be ignored.
            final JSONObject source = data.getJSONObject(STYLE_SETTINGS);

            if (style.getType() == StyleType.User && source.has(StyleDataStore.PK_GROUPS)) {
                decodeGroups((WritableStyle) style, source,
                             stylesHelper.getGlobalStyle());
            }
            decodeSettings(source, (WritableStyle) style);
        }

        return style;
    }

    private void decodeSettings(@NonNull final JSONObject source,
                                @NonNull final WritableStyle style) {

        if (source.has(StyleDataStore.PK_LAYOUT)) {
            style.setLayout(source.getInt(StyleDataStore.PK_LAYOUT));
        }
        if (source.has(StyleDataStore.PK_COVER_CLICK_ACTION)) {
            style.setCoverClickAction(source.getInt(
                    StyleDataStore.PK_COVER_CLICK_ACTION));
        }
        if (source.has(StyleDataStore.PK_COVER_LONG_CLICK_ACTION)) {
            style.setCoverLongClickAction(source.getInt(
                    StyleDataStore.PK_COVER_LONG_CLICK_ACTION));
        }
        if (source.has(StyleDataStore.PK_COVER_SCALE)) {
            style.setCoverScale(source.getInt(StyleDataStore.PK_COVER_SCALE));
        }
        if (source.has(StyleDataStore.PK_TEXT_SCALE)) {
            style.setTextScale(source.getInt(StyleDataStore.PK_TEXT_SCALE));
        }
        if (source.has(StyleDataStore.PK_GROUP_ROW_HEIGHT)) {
            style.setGroupRowUsesPreferredHeight(
                    source.getBoolean(StyleDataStore.PK_GROUP_ROW_HEIGHT));
        }

        if (source.has(StyleDataStore.PK_LIST_HEADER)) {
            style.setHeaderFieldVisibility(source.getInt(StyleDataStore.PK_LIST_HEADER));
        }
        if (source.has(PK_LIST_FIELD_VISIBILITY)) {
            style.setFieldVisibility(FieldVisibility.Screen.List,
                                     source.getLong(PK_LIST_FIELD_VISIBILITY));
        } else {
            // backwards compatibility
            V2.decodeListVisibility(style, source);
        }
        if (source.has(PK_LIST_FIELD_ORDER_BY)) {
            style.setBookLevelFieldsOrderBy(decodeBookLevelFieldsOrderBy(
                    source.getJSONArray(PK_LIST_FIELD_ORDER_BY)));
        }

        if (source.has(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST)) {
            style.setSortAuthorByGivenName(
                    source.getBoolean(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST));
        }
        if (source.has(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST)) {
            style.setShowAuthorByGivenName(
                    source.getBoolean(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST));
        }
        if (source.has(StyleDataStore.PK_SHOW_TITLES_REORDERED)) {
            style.setShowReorderedTitle(
                    source.getBoolean(StyleDataStore.PK_SHOW_TITLES_REORDERED));
        }
        if (source.has(StyleDataStore.PK_USE_READ_PROGRESS)) {
            style.setUseReadProgress(
                    source.getBoolean(StyleDataStore.PK_USE_READ_PROGRESS));
        }
        if (source.has(PK_DETAILS_FIELD_VISIBILITY)) {
            style.setFieldVisibility(FieldVisibility.Screen.Detail,
                                     source.getLong(PK_DETAILS_FIELD_VISIBILITY));
        } else {
            // backwards compatibility
            V2.decodeDetailVisibility(style, source);
        }
    }

    private void decodeGroups(@NonNull final WritableStyle style,
                              @NonNull final JSONObject source,
                              @NonNull final Style styleDefaults)
            throws JSONException {

        if (source.has(StyleDataStore.PK_EXPANSION_LEVEL)) {
            style.setExpansionLevel(
                    source.getInt(StyleDataStore.PK_EXPANSION_LEVEL));
        }

        final JSONArray groupArray = source.getJSONArray(StyleDataStore.PK_GROUPS);
        final List<Integer> groupIds = IntStream.range(0, groupArray.length())
                                                .mapToObj(groupArray::getInt)
                                                .collect(Collectors.toList());
        style.setGroupIds(groupIds);

        if (source.has(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE)) {
            style.setPrimaryAuthorType(
                    source.getInt(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE));
        } else {
            style.setPrimaryAuthorType(styleDefaults.getPrimaryAuthorType());
        }

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            final int groupId = item.getGroupId();

            if (source.has(item.getPrefKey())) {
                style.setShowBooksUnderEachGroup(groupId,
                                                 source.getBoolean(item.getPrefKey()));
            } else {
                style.setShowBooksUnderEachGroup(groupId,
                                                 styleDefaults.isShowBooksUnderEachGroup(groupId));
            }
        }
    }

    @NonNull
    private Map<String, Sort> decodeBookLevelFieldsOrderBy(@NonNull final JSONArray columns)
            throws JSONException {
        final Map<String, Sort> result = new LinkedHashMap<>();

        for (int i = 0; i < columns.length(); i++) {
            final JSONObject column = columns.getJSONObject(i);
            final String name = column.getString(COLUMN_NAME);
            final String value = column.getString(COLUMN_SORT);
            result.put(name, Sort.valueOf(value));
        }
        return result;
    }

    /**
     * Backwards compatibility with the old V2 version so we can read older backups.
     */
    private static final class V2 {

        private static final String[] DETAILS_COVER = {
                "style.details.show.thumbnail.0",
                "style.details.show.thumbnail.1",
        };
        private static final String LIST_AUTHOR = "style.booklist.show.author";
        private static final String LIST_PUBLISHER = "style.booklist.show.publisher";
        private static final String LIST_PUBLICATION_DATE = "style.booklist.show.publication.date";
        private static final String LIST_FORMAT = "style.booklist.show.format";
        private static final String LIST_LANGUAGE = "style.booklist.show.language";
        private static final String LIST_LOCATION = "style.booklist.show.location";
        private static final String LIST_RATING = "style.booklist.show.rating";
        private static final String LIST_BOOKSHELVES = "style.booklist.show.bookshelves";
        private static final String LIST_ISBN = "style.booklist.show.isbn";
        private static final String LIST_THUMBNAILS = "style.booklist.show.thumbnails";

        private static void decodeListVisibility(@NonNull final WritableStyle style,
                                                 @NonNull final JSONObject source) {

            if (source.has(LIST_THUMBNAILS)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.COVER[0],
                                         source.getBoolean(LIST_THUMBNAILS));
            }
            if (source.has(LIST_AUTHOR)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.FK_AUTHOR,
                                         source.getBoolean(LIST_AUTHOR));
            }
            if (source.has(LIST_PUBLISHER)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.FK_PUBLISHER,
                                         source.getBoolean(LIST_PUBLISHER));
            }
            if (source.has(LIST_PUBLICATION_DATE)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.BOOK_PUBLICATION__DATE,
                                         source.getBoolean(LIST_PUBLICATION_DATE));
            }
            if (source.has(LIST_FORMAT)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.FORMAT,
                                         source.getBoolean(LIST_FORMAT));
            }
            if (source.has(LIST_LANGUAGE)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.LANGUAGE,
                                         source.getBoolean(LIST_LANGUAGE));
            }
            if (source.has(LIST_LOCATION)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.LOCATION,
                                         source.getBoolean(LIST_LOCATION));
            }
            if (source.has(LIST_RATING)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.RATING,
                                         source.getBoolean(LIST_RATING));
            }
            if (source.has(LIST_BOOKSHELVES)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.FK_BOOKSHELF,
                                         source.getBoolean(LIST_BOOKSHELVES));
            }
            if (source.has(LIST_ISBN)) {
                style.setFieldVisibility(FieldVisibility.Screen.List, DBKey.BOOK_ISBN,
                                         source.getBoolean(LIST_ISBN));
            }
            // reminder: this is for backwards compatibility - don't add new fields here!
        }

        private static void decodeDetailVisibility(@NonNull final WritableStyle style,
                                                   @NonNull final JSONObject source) {

            if (source.has(DETAILS_COVER[0])) {
                style.setFieldVisibility(FieldVisibility.Screen.Detail, DBKey.COVER[0],
                                         source.getBoolean(DETAILS_COVER[0]));
            }
            if (source.has(DETAILS_COVER[1])) {
                style.setFieldVisibility(FieldVisibility.Screen.Detail, DBKey.COVER[1],
                                         source.getBoolean(DETAILS_COVER[1]));
            }
            // reminder: this is for backwards compatibility - don't add new fields here!
        }
    }

}
