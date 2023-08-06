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

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
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

    /** The combined bitmask value for the PK_DETAILS_SHOW* values. */
    private static final String PK_DETAILS_FIELD_VISIBILITY = "style.details.show.fields";
    /** The combined bitmask value for the PK_LIST_SHOW* values. */
    private static final String PK_LIST_FIELD_VISIBILITY = "style.list.show.fields";

    /** The sub-tag for the array with the style settings. */
    private static final String STYLE_SETTINGS = "settings";

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

            encodeGroups(userStyle, dest);

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
            dest.put(StyleDataStore.PK_LIST_HEADER, userStyle.getHeaderFieldVisibilityValue());

            // since v3 stored as bitmask and no longer as individual flags
            dest.put(PK_DETAILS_FIELD_VISIBILITY,
                     userStyle.getFieldVisibility(Style.Screen.Detail).getValue());
            // since v3 stored as bitmask and no longer as individual flags
            dest.put(PK_LIST_FIELD_VISIBILITY,
                     userStyle.getFieldVisibility(Style.Screen.List).getValue());

            out.put(STYLE_SETTINGS, dest);
        }
        return out;
    }

    private void encodeGroups(@NonNull final Style style,
                              @NonNull final JSONObject dest) {
        final JSONArray groupArray = new JSONArray(style.getGroupList()
                                                        .stream()
                                                        .map(BooklistGroup::getId)
                                                        .collect(Collectors.toList()));
        dest.put(StyleDataStore.PK_GROUPS, groupArray);

        dest.put(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE,
                 style.getPrimaryAuthorType());

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            dest.put(item.getPrefKey(), style.isShowBooks(item));
        }
    }

    @NonNull
    @Override
    public Style decode(@NonNull final JSONObject data)
            throws JSONException {

        final String uuid = data.getString(DBKey.STYLE_UUID);

        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();

        if (BuiltinStyle.isBuiltin(uuid)) {
            final Optional<Style> oStyle = stylesHelper.getStyle(uuid);
            if (oStyle.isPresent()) {
                final Style style = oStyle.get();
                style.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
                style.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));
                return style;
            } else {
                // It's a recognized Builtin Style, but it's deprecated.
                // We return the default builtin style instead.
                return stylesHelper.getStyle(BuiltinStyle.DEFAULT_UUID).orElseThrow();
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
                    userStyle.setHeaderFieldVisibilityValue(
                            source.getInt(StyleDataStore.PK_LIST_HEADER));
                }

                if (source.has(PK_DETAILS_FIELD_VISIBILITY)) {
                    userStyle.getFieldVisibility(Style.Screen.Detail)
                             .setValue(source.getLong(PK_DETAILS_FIELD_VISIBILITY));
                } else {
                    // backwards compatibility
                    decodeV2DetailVisibility(userStyle, source);
                }

                if (source.has(PK_LIST_FIELD_VISIBILITY)) {
                    userStyle.getFieldVisibility(Style.Screen.List)
                             .setValue(source.getLong(PK_LIST_FIELD_VISIBILITY));
                } else {
                    // backwards compatibility
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
            userStyle.setPrimaryAuthorType(
                    source.getInt(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE));
        }

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            if (source.has(item.getPrefKey())) {
                userStyle.setShowBooks(item, source.getBoolean(item.getPrefKey()));
            }
        }
    }

    private void decodeV2DetailVisibility(@NonNull final Style style,
                                          @NonNull final JSONObject source) {

        if (source.has(V2.DETAILS_COVER[0])) {
            final boolean show = source.getBoolean(V2.DETAILS_COVER[0]);

            style.getFieldVisibility(Style.Screen.Detail).setShowField(DBKey.COVER[0], show);
        }
        if (source.has(V2.DETAILS_COVER[1])) {
            final boolean show = source.getBoolean(V2.DETAILS_COVER[1]);

            style.getFieldVisibility(Style.Screen.Detail).setShowField(DBKey.COVER[1], show);
        }
        // reminder: this is for backwards compatibility - don't add new fields here!
    }

    private void decodeV2ListVisibility(@NonNull final Style style,
                                        @NonNull final JSONObject source) {

        if (source.has(V2.LIST_THUMBNAILS)) {
            final boolean show = source.getBoolean(V2.LIST_THUMBNAILS);

            style.getFieldVisibility(Style.Screen.List).setShowField(DBKey.COVER[0], show);
        }
        if (source.has(V2.LIST_AUTHOR)) {
            final boolean show = source.getBoolean(V2.LIST_AUTHOR);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.FK_AUTHOR, show);
        }
        if (source.has(V2.LIST_PUBLISHER)) {
            final boolean show = source.getBoolean(V2.LIST_PUBLISHER);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.FK_PUBLISHER, show);
        }
        if (source.has(V2.LIST_PUBLICATION_DATE)) {
            final boolean show = source.getBoolean(V2.LIST_PUBLICATION_DATE);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.BOOK_PUBLICATION__DATE, show);
        }
        if (source.has(V2.LIST_FORMAT)) {
            final boolean show = source.getBoolean(V2.LIST_FORMAT);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.FORMAT, show);
        }
        if (source.has(V2.LIST_LANGUAGE)) {
            final boolean show = source.getBoolean(V2.LIST_LANGUAGE);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.LANGUAGE, show);
        }
        if (source.has(V2.LIST_LOCATION)) {
            final boolean show = source.getBoolean(V2.LIST_LOCATION);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.LOCATION, show);
        }
        if (source.has(V2.LIST_RATING)) {
            final boolean show = source.getBoolean(V2.LIST_RATING);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.RATING, show);
        }
        if (source.has(V2.LIST_BOOKSHELVES)) {
            final boolean show = source.getBoolean(V2.LIST_BOOKSHELVES);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.FK_BOOKSHELF, show);
        }
        if (source.has(V2.LIST_ISBN)) {
            final boolean show = source.getBoolean(V2.LIST_ISBN);

            style.getFieldVisibility(Style.Screen.List)
                 .setShowField(DBKey.BOOK_ISBN, show);
        }
        // reminder: this is for backwards compatibility - don't add new fields here!
    }

    /**
     * Preference keys used by the old V2 version. Here for backwards compatibility
     * so we can still read older backups.
     */
    private static final class V2 {

        static final String[] DETAILS_COVER = {
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
    }

}
