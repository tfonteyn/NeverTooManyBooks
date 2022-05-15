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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BookDetailsFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistBookFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Styles;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BookshelfBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.PublisherBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.SeriesBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleDataStore;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class ListStyleCoder
        implements JsonCoder<ListStyle> {

    /** The sub-tag for the array with the style settings. */
    private static final String STYLE_SETTINGS = "settings";

    @NonNull
    private final Context context;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public ListStyleCoder(@NonNull final Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final ListStyle style)
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

            dest.put(StyleDataStore.PK_STYLE_GROUPS, new JSONArray(
                    userStyle.getGroupList()
                             .stream()
                             .map(BooklistGroup::getId)
                             .collect(Collectors.toList())));
            dest.put(AuthorBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH,
                     userStyle.isShowBooksUnderEachAuthor());
            dest.put(AuthorBooklistGroup.PK_PRIMARY_TYPE,
                     userStyle.getPrimaryAuthorType());
            dest.put(SeriesBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH,
                     userStyle.isShowBooksUnderEachSeries());
            dest.put(PublisherBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH,
                     userStyle.isShowBooksUnderEachPublisher());
            dest.put(BookshelfBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH,
                     userStyle.isShowBooksUnderEachBookshelf());


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

            dest.put(StyleDataStore.PK_LIST_HEADER,
                     userStyle.getShowHeaderFields());
            dest.put(BookDetailsFieldVisibility.PK_VISIBILITY,
                     userStyle.getBookDetailsFieldVisibility().getValue());
            dest.put(BooklistBookFieldVisibility.PK_VISIBILITY,
                     userStyle.getBooklistBookFieldVisibility().getValue());

            out.put(STYLE_SETTINGS, dest);
        }
        return out;
    }

    @NonNull
    @Override
    public ListStyle decode(@NonNull final JSONObject data)
            throws JSONException {

        final String uuid = data.getString(DBKey.STYLE_UUID);

        final Styles styles = ServiceLocator.getInstance().getStyles();

        if (BuiltinStyle.isBuiltin(uuid)) {
            // It's a builtin style
            final ListStyle style = styles.getStyle(context, uuid);
            //noinspection ConstantConditions
            style.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
            style.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));
            return style;

        } else {
            final UserStyle userStyle = UserStyle.createFromImport(uuid);
            userStyle.setName(data.getString(DBKey.STYLE_NAME));
            userStyle.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
            userStyle.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));

            if (data.has(STYLE_SETTINGS)) {
                // any element in the source which we don't know, will simply be ignored.
                final JSONObject source = data.getJSONObject(STYLE_SETTINGS);

                if (source.has(StyleDataStore.PK_STYLE_GROUPS)) {
                    decodeGroups(userStyle, source);
                }

                if (source.has(StyleDataStore.PK_EXPANSION_LEVEL)) {
                    userStyle.setExpansionLevel(
                            source.getInt(StyleDataStore.PK_EXPANSION_LEVEL));
                }
                if (source.has(StyleDataStore.PK_GROUP_ROW_HEIGHT)) {
                    userStyle.setUseGroupRowPreferredHeight(
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
                    userStyle.setShowHeaderFields(
                            source.getInt(StyleDataStore.PK_LIST_HEADER));
                }

                decodeV2ListAndDetailVisibility(userStyle, source);

                if (source.has(BookDetailsFieldVisibility.PK_VISIBILITY)) {
                    userStyle.getBookDetailsFieldVisibility()
                             .setValue(source.getInt(BookDetailsFieldVisibility.PK_VISIBILITY));
                }
                if (source.has(BooklistBookFieldVisibility.PK_VISIBILITY)) {
                    userStyle.getBooklistBookFieldVisibility()
                             .setValue(source.getInt(BooklistBookFieldVisibility.PK_VISIBILITY));
                }
            }

            return userStyle;
        }
    }

    private void decodeGroups(final UserStyle userStyle,
                              final JSONObject source)
            throws JSONException {
        final JSONArray groupArray = source.getJSONArray(StyleDataStore.PK_STYLE_GROUPS);
        final List<Integer> groupIds = IntStream.range(0, groupArray.length())
                                                .mapToObj(groupArray::getInt)
                                                .collect(Collectors.toList());
        userStyle.setGroupIds(groupIds);

        if (source.has(AuthorBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH)) {
            userStyle.setShowBooksUnderEachAuthor(
                    source.getBoolean(AuthorBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH));
        }
        if (source.has(AuthorBooklistGroup.PK_PRIMARY_TYPE)) {
            userStyle.setPrimaryAuthorTypes(
                    source.getInt(AuthorBooklistGroup.PK_PRIMARY_TYPE));
        }
        if (source.has(SeriesBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH)) {
            userStyle.setShowBooksUnderEachSeries(
                    source.getBoolean(SeriesBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH));
        }
        if (source.has(BookshelfBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH)) {
            userStyle.setShowBooksUnderEachBookshelf(
                    source.getBoolean(BookshelfBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH));
        }
        if (source.has(PublisherBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH)) {
            userStyle.setShowBooksUnderEachPublisher(
                    source.getBoolean(PublisherBooklistGroup.PK_SHOW_BOOKS_UNDER_EACH));
        }
    }

    private void decodeV2ListAndDetailVisibility(@NonNull final BooklistStyle userStyle,
                                                 @NonNull final JSONObject source) {

        final BookDetailsFieldVisibility detailScreen =
                userStyle.getBookDetailsFieldVisibility();

        if (source.has(StyleDataStore.PK_STYLE_BOOK_DETAILS_COVER[0])) {
            detailScreen.setShowCover(0, source.getBoolean(
                    StyleDataStore.PK_STYLE_BOOK_DETAILS_COVER[0]));
        }
        if (source.has(StyleDataStore.PK_STYLE_BOOK_DETAILS_COVER[1])) {
            detailScreen.setShowCover(1, source.getBoolean(
                    StyleDataStore.PK_STYLE_BOOK_DETAILS_COVER[1]));
        }


        final BooklistBookFieldVisibility listScreen =
                userStyle.getBooklistBookFieldVisibility();

        if (source.has(StyleDataStore.PK_LIST_SHOW_COVERS)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_COVER_0,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_COVERS));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_AUTHOR)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_AUTHOR,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_AUTHOR));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_PUBLISHER)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_PUBLISHER,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_PUBLISHER));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_PUB_DATE)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_PUB_DATE,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_PUB_DATE));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_FORMAT)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_FORMAT,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_FORMAT));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_LOCATION)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_LOCATION,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_LOCATION));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_RATING)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_RATING,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_RATING));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_BOOKSHELVES)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_BOOKSHELVES,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_BOOKSHELVES));
        }
        if (source.has(StyleDataStore.PK_LIST_SHOW_ISBN)) {
            listScreen.setShowField(BooklistBookFieldVisibility.SHOW_ISBN,
                                    source.getBoolean(StyleDataStore.PK_LIST_SHOW_ISBN));
        }
    }

}
