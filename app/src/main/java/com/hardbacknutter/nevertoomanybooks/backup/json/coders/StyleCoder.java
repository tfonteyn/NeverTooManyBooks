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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BaseStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class StyleCoder
        implements JsonCoder<Style> {

    /** The sub-tag for the array with the style settings. */
    private static final String STYLE_SETTINGS = "settings";

    @NonNull
    private final Context context;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public StyleCoder(@NonNull final Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final Style style)
            throws JSONException {
        final JSONObject out = new JSONObject();

        out.put(DBKey.STYLE_UUID, style.getUuid());
        out.put(DBKey.STYLE_IS_PREFERRED, style.isPreferred());
        out.put(DBKey.STYLE_MENU_POSITION, style.getMenuPosition());

        if (style.isUserDefined()) {
            final UserStyle userStyle = (UserStyle) style;

            out.put(DBKey.STYLE_NAME, userStyle.getName());

            // The set 'dest' will go under a new JSON object 'STYLE_SETTINGS'
            final JSONObject dest = new JSONObject();

            dest.put(StyleDataStore.PK_GROUPS, new JSONArray(
                    userStyle.getGroupList()
                             .stream()
                             .map(BooklistGroup::getId)
                             .collect(Collectors.toList())));
            dest.put(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE,
                     userStyle.getPrimaryAuthorType());

            for (final Style.UnderEach item : Style.UnderEach.values()) {
                dest.put(item.getPrefKey(), userStyle.isShowBooks(item));
            }
            dest.put(StyleDataStore.PK_EXPANSION_LEVEL,
                     userStyle.getExpansionLevel());
            dest.put(StyleDataStore.PK_GROUP_ROW_HEIGHT,
                     userStyle.isGroupRowUsesPreferredHeight());
            dest.put(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST,
                     userStyle.isSortAuthorByGivenName());
            dest.put(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST,
                     userStyle.isShowAuthorByGivenName());

            dest.put(StyleDataStore.PK_TEXT_SCALE, userStyle.getTextScale());
            dest.put(StyleDataStore.PK_COVER_SCALE, userStyle.getCoverScale());
            dest.put(StyleDataStore.PK_LIST_HEADER, userStyle.getHeaderFieldVisibility());

            // since v3 stored as bitmask and no longer as individual flags
            dest.put(StyleDataStore.PK_DETAILS_FIELD_VISIBILITY,
                     userStyle.getFieldVisibility(Style.Screen.Detail));
            // since v3 stored as bitmask and no longer as individual flags
            dest.put(StyleDataStore.PK_LIST_FIELD_VISIBILITY,
                     userStyle.getFieldVisibility(Style.Screen.List));

            out.put(STYLE_SETTINGS, dest);
        }
        return out;
    }

    @NonNull
    @Override
    public Style decode(@NonNull final JSONObject data)
            throws JSONException {

        final String uuid = data.getString(DBKey.STYLE_UUID);

        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();

        if (BuiltinStyle.isBuiltin(uuid)) {
            final Optional<Style> oStyle = stylesHelper.getStyle(context, uuid);
            if (oStyle.isPresent()) {
                final Style style = oStyle.get();
                style.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
                style.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));
                return style;
            } else {
                // It's a recognized Builtin Style, but it's deprecated.
                // We return the default builtin style instead.
                //noinspection OptionalGetWithoutIsPresent
                return stylesHelper.getStyle(context, BuiltinStyle.DEFAULT_UUID).get();
            }

        } else {
            final UserStyle userStyle = UserStyle.createFromImport(uuid);
            userStyle.setName(data.getString(DBKey.STYLE_NAME));
            userStyle.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
            userStyle.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));

            if (data.has(STYLE_SETTINGS)) {
                // any element in the source which we don't know, will simply be ignored.
                final JSONObject source = data.getJSONObject(STYLE_SETTINGS);

                if (source.has(StyleDataStore.PK_GROUPS)) {
                    decodeGroups(userStyle, source);
                }

                if (source.has(StyleDataStore.PK_EXPANSION_LEVEL)) {
                    userStyle.setExpansionLevel(
                            source.getInt(StyleDataStore.PK_EXPANSION_LEVEL));
                }
                if (source.has(StyleDataStore.PK_GROUP_ROW_HEIGHT)) {
                    userStyle.setGroupRowUsesPreferredHeight(
                            source.getBoolean(StyleDataStore.PK_GROUP_ROW_HEIGHT));
                }
                if (source.has(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST)) {
                    userStyle.setSortAuthorByGivenName(
                            source.getBoolean(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST));
                }
                if (source.has(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST)) {
                    userStyle.setShowAuthorByGivenName(
                            source.getBoolean(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST));
                }
                if (source.has(StyleDataStore.PK_TEXT_SCALE)) {
                    userStyle.setTextScale(source.getInt(StyleDataStore.PK_TEXT_SCALE));
                }
                if (source.has(StyleDataStore.PK_COVER_SCALE)) {
                    userStyle.setCoverScale(source.getInt(StyleDataStore.PK_COVER_SCALE));
                }
                if (source.has(StyleDataStore.PK_LIST_HEADER)) {
                    userStyle.setHeaderFieldVisibility(
                            source.getInt(StyleDataStore.PK_LIST_HEADER));
                }


                if (source.has(StyleDataStore.PK_DETAILS_FIELD_VISIBILITY)) {
                    userStyle.setFieldVisibility(Style.Screen.Detail, source.getLong(
                            StyleDataStore.PK_DETAILS_FIELD_VISIBILITY));
                } else {
                    decodeV2DetailVisibility(userStyle, source);
                }
                if (source.has(StyleDataStore.PK_LIST_FIELD_VISIBILITY)) {
                    userStyle.setFieldVisibility(Style.Screen.List, source.getLong(
                            StyleDataStore.PK_LIST_FIELD_VISIBILITY));
                } else {
                    decodeV2ListVisibility(userStyle, source);
                }
            }

            return userStyle;
        }
    }

    private void decodeGroups(@NonNull final UserStyle userStyle,
                              @NonNull final JSONObject source)
            throws JSONException {
        final JSONArray groupArray = source.getJSONArray(StyleDataStore.PK_GROUPS);
        final List<Integer> groupIds = IntStream.range(0, groupArray.length())
                                                .mapToObj(groupArray::getInt)
                                                .collect(Collectors.toList());
        userStyle.setGroupIds(groupIds);

        if (source.has(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE)) {
            userStyle.setPrimaryAuthorTypes(
                    source.getInt(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE));
        }

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            if (source.has(item.getPrefKey())) {
                userStyle.setShowBooks(item, source.getBoolean(item.getPrefKey()));
            }
        }
    }

    private void decodeV2DetailVisibility(@NonNull final BaseStyle style,
                                          @NonNull final JSONObject source) {

        if (source.has(StyleDataStore.PK_DETAILS_SHOW_COVER[0])) {
            style.setShowField(Style.Screen.Detail, DBKey.COVER[0], source.getBoolean(
                    StyleDataStore.PK_DETAILS_SHOW_COVER[0]));
        }
        if (source.has(StyleDataStore.PK_DETAILS_SHOW_COVER[1])) {
            style.setShowField(Style.Screen.Detail, DBKey.COVER[1], source.getBoolean(
                    StyleDataStore.PK_DETAILS_SHOW_COVER[1]));
        }
    }

    private void decodeV2ListVisibility(@NonNull final BaseStyle style,
                                        @NonNull final JSONObject source) {

        if (source.has(StyleDataStore.PK_LIST_SHOW_COVERS)) {
            style.setShowField(Style.Screen.List, DBKey.COVER[0],
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_COVERS));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_AUTHOR)) {
            style.setShowField(Style.Screen.List, DBKey.FK_AUTHOR,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_AUTHOR));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_PUBLISHER)) {
            style.setShowField(Style.Screen.List, DBKey.FK_PUBLISHER,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_PUBLISHER));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_PUB_DATE)) {
            style.setShowField(Style.Screen.List, DBKey.BOOK_PUBLICATION__DATE,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_PUB_DATE));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_FORMAT)) {
            style.setShowField(Style.Screen.List, DBKey.FORMAT,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_FORMAT));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_LANGUAGE)) {
            style.setShowField(Style.Screen.List, DBKey.LANGUAGE,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_LANGUAGE));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_LOCATION)) {
            style.setShowField(Style.Screen.List, DBKey.LOCATION,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_LOCATION));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_RATING)) {
            style.setShowField(Style.Screen.List, DBKey.RATING,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_RATING));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_BOOKSHELVES)) {
            style.setShowField(Style.Screen.List, DBKey.FK_BOOKSHELF,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_BOOKSHELVES));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_ISBN)) {
            style.setShowField(Style.Screen.List, DBKey.BOOK_ISBN,
                               source.getBoolean(StyleDataStore.PK_LIST_SHOW_ISBN));
        }
    }

}
