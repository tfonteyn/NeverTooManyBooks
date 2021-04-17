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

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.filters.Filters;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Note the hardcoded negative ID's.
 * These id/uuid's should NEVER be changed as they will get stored
 * in preferences and serialized. Take care not to add duplicates.
 */
public final class BuiltinStyle
        extends BooklistStyle {

    // NEWTHINGS: ListStyle. Make sure to update the max id when adding a style!
    // and make sure a row is added to the database styles table.
    // next max is -20
    public static final int MAX_ID = -19;


    /** We need a random style with a filter for testing. */
    @VisibleForTesting
    public static final String UUID_UNREAD_AUTHOR_THEN_SERIES
            = "f479e979-c43f-4b0b-9c5b-6942964749df";

    private static final int ID_AUTHOR_THEN_SERIES = -1;

    /** Absolute/initial default. */
    public static final int DEFAULT_ID = ID_AUTHOR_THEN_SERIES;

    private static final String UUID_AUTHOR_THEN_SERIES
            = "6a82c4c0-48f1-4130-8a62-bbf478ffe184";

    /** Absolute/initial default. */
    public static final String DEFAULT_UUID = UUID_AUTHOR_THEN_SERIES;


    private static final int ID_UNREAD_AUTHOR_THEN_SERIES = -2;


    private static final int ID_COMPACT = -3;
    private static final String UUID_COMPACT
            = "5e4c3137-a05f-4c4c-853a-bd1dacb6cd16";

    private static final int ID_TITLE_FIRST_LETTER = -4;
    private static final String UUID_TITLE_FIRST_LETTER
            = "16b4ecdf-edef-4bf2-a682-23f7230446c8";

    private static final int ID_SERIES = -5;
    private static final String UUID_SERIES
            = "ad55ebc3-f79d-4cc2-a27d-f06ff0bf2335";

    private static final int ID_GENRE = -6;
    private static final String UUID_GENRE
            = "edc5c178-60f0-40e7-9674-e08445b6c942";

    private static final int ID_LENDING = -7;
    private static final String UUID_LENDING
            = "e4f1c364-2cbe-467e-a0c1-3ae71bd56fa3";

    private static final int ID_READ_AND_UNREAD = -8;
    private static final String UUID_READ_AND_UNREAD
            = "e3678890-7785-4870-9213-333a68293a49";

    private static final int ID_PUBLICATION_DATA = -9;
    private static final String UUID_PUBLICATION_DATA
            = "182f5d3c-8fd7-4f3a-b5b0-0c93551d1796";

    private static final int ID_DATE_ADDED = -10;
    private static final String UUID_DATE_ADDED
            = "95d7afc0-a70a-4f1f-8d77-aa7ebc60e521";

    private static final int ID_DATE_ACQUIRED = -11;
    private static final String UUID_DATE_ACQUIRED
            = "b3255b1f-5b07-4b3e-9700-96c0f8f35a58";

    private static final int ID_AUTHOR_AND_YEAR = -12;
    private static final String UUID_AUTHOR_AND_YEAR
            = "7c9ad91e-df7c-415a-a205-cdfabff5465d";

    private static final int ID_FORMAT = -13;
    private static final String UUID_FORMAT
            = "bdc43f17-2a95-42ef-b0f8-c750ef920f28";

    private static final int ID_DATE_READ = -14;
    private static final String UUID_DATE_READ
            = "034fe547-879b-4fa0-997a-28d769ba5a84";

    private static final int ID_LOCATION = -15;
    private static final String UUID_LOCATION
            = "e21a90c9-5150-49ee-a204-0cab301fc5a1";

    private static final int ID_LANGUAGE = -16;
    private static final String UUID_LANGUAGE
            = "00379d95-6cb2-40e6-8c3b-f8278f34750a";

    private static final int ID_RATING = -17;
    private static final String UUID_RATING
            = "20a2ebdf-81a7-4eca-a3a9-7275062b907a";

    private static final int ID_BOOKSHELF = -18;
    private static final String UUID_BOOKSHELF
            = "999d383e-6e76-416a-86f9-960c729aa718";

    private static final int ID_DATE_LAST_UPDATE = -19;
    private static final String UUID_DATE_LAST_UPDATE
            = "427a0da5-0779-44b6-89e9-82772e5ad5ef";

    /**
     * Use the NEGATIVE builtin style id to get the UUID for it. Element 0 is not used.
     * NEVER change the order.
     */
    private static final String[] ID_UUID = {
            "",
            UUID_AUTHOR_THEN_SERIES,
            UUID_UNREAD_AUTHOR_THEN_SERIES,
            UUID_COMPACT,
            UUID_TITLE_FIRST_LETTER,
            UUID_SERIES,

            UUID_GENRE,
            UUID_LENDING,
            UUID_READ_AND_UNREAD,
            UUID_PUBLICATION_DATA,
            UUID_DATE_ADDED,

            UUID_DATE_ACQUIRED,
            UUID_AUTHOR_AND_YEAR,
            UUID_FORMAT,
            UUID_DATE_READ,
            UUID_LOCATION,

            UUID_LANGUAGE,
            UUID_RATING,
            UUID_BOOKSHELF,
            UUID_DATE_LAST_UPDATE,
            };

    /**
     * Display name of this style.
     */
    @StringRes
    private final int mLabelId;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param id           a negative int
     * @param uuid         UUID for the builtin style.
     * @param labelId      the resource id for the name
     * @param isPreferred  flag
     * @param menuPosition to set
     * @param groupIds     a list of groups to attach to this style
     */
    private BuiltinStyle(@NonNull final Context context,
                         @IntRange(from = MAX_ID, to = -1) final long id,
                         @NonNull final String uuid,
                         @StringRes final int labelId,
                         final boolean isPreferred,
                         final int menuPosition,
                         @NonNull final int... groupIds) {
        super(context, uuid, false);
        mId = id;

        mLabelId = labelId;

        mIsPreferred = isPreferred;
        mMenuPosition = menuPosition;

        initPrefs(false);

        for (@BooklistGroup.Id final int groupId : groupIds) {
            getGroups().add(BooklistGroup.newInstance(groupId, false, this));
        }
    }

    public static boolean isBuiltin(@NonNull final String uuid) {
        if (!uuid.isEmpty()) {
            // Use the array, not the cache!
            for (final String key : ID_UUID) {
                if (key.equals(uuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Only used during App setup to create the styles table.
     *
     * @param id to lookup
     *
     * @return the uuid
     */
    @NonNull
    public static String getUuidById(final int id) {
        return ID_UUID[id];
    }

    public static BuiltinStyle createFromDatabase(@NonNull final Context context,
                                                  @NonNull final DataHolder rowData) {

        // Dev Note: the way we construct these is not optimal, but it mimics the
        // way we create user-styles. The intention is that this configuration eventually
        // goes into the database.
        final int id = rowData.getInt(DBKey.PK_ID);
        final String uuid = rowData.getString(DBKey.KEY_STYLE_UUID);
        final boolean isPreferred = rowData.getBoolean(DBKey.BOOL_STYLE_IS_PREFERRED);
        final int menuPos = rowData.getInt(DBKey.KEY_STYLE_MENU_POSITION);

        final BuiltinStyle style;
        switch (id) {
            case ID_AUTHOR_THEN_SERIES:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_author_series,
                                         isPreferred, menuPos,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_UNREAD_AUTHOR_THEN_SERIES:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_unread,
                                         isPreferred, menuPos,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                style.getFilters().setFilter(Filters.PK_FILTER_READ, false);
                break;

            case ID_COMPACT:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_compact,
                                         isPreferred, menuPos,
                                         BooklistGroup.AUTHOR);
                style.getTextScale().set(TextScale.TEXT_SCALE_1_SMALL);
                style.getListScreenBookFields()
                     .setShowField(ListScreenBookFields.PK_COVERS, false);
                break;

            case ID_TITLE_FIRST_LETTER:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_first_letter_book_title,
                                         isPreferred, menuPos,
                                         BooklistGroup.BOOK_TITLE_LETTER);
                break;

            case ID_SERIES:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_series,
                                         isPreferred, menuPos,
                                         BooklistGroup.SERIES);
                break;

            case ID_GENRE:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_genre,
                                         isPreferred, menuPos,
                                         BooklistGroup.GENRE,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_LENDING:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_lending,
                                         isPreferred, menuPos,
                                         BooklistGroup.LENDING,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_READ_AND_UNREAD:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_read_and_unread,
                                         isPreferred, menuPos,
                                         BooklistGroup.READ_STATUS,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_PUBLICATION_DATA:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_publication_date,
                                         isPreferred, menuPos,
                                         BooklistGroup.DATE_PUBLISHED_YEAR,
                                         BooklistGroup.DATE_PUBLISHED_MONTH,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_DATE_ADDED:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_added_date,
                                         isPreferred, menuPos,
                                         BooklistGroup.DATE_ADDED_YEAR,
                                         BooklistGroup.DATE_ADDED_MONTH,
                                         BooklistGroup.DATE_ADDED_DAY,
                                         BooklistGroup.AUTHOR);
                break;

            case ID_DATE_ACQUIRED:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_acquired_date,
                                         isPreferred, menuPos,
                                         BooklistGroup.DATE_ACQUIRED_YEAR,
                                         BooklistGroup.DATE_ACQUIRED_MONTH,
                                         BooklistGroup.DATE_ACQUIRED_DAY,
                                         BooklistGroup.AUTHOR);
                break;

            case ID_AUTHOR_AND_YEAR:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_author_year,
                                         isPreferred, menuPos,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.DATE_PUBLISHED_YEAR,
                                         BooklistGroup.SERIES);
                break;

            case ID_FORMAT:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_format,
                                         isPreferred, menuPos,
                                         BooklistGroup.FORMAT);
                break;

            case ID_DATE_READ:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_read_date,
                                         isPreferred, menuPos,
                                         BooklistGroup.DATE_READ_YEAR,
                                         BooklistGroup.DATE_READ_MONTH,
                                         BooklistGroup.AUTHOR);
                style.getFilters().setFilter(Filters.PK_FILTER_READ, true);
                break;

            case ID_LOCATION:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_location,
                                         isPreferred, menuPos,
                                         BooklistGroup.LOCATION,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_LANGUAGE:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_language,
                                         isPreferred, menuPos,
                                         BooklistGroup.LANGUAGE,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_RATING:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_rating,
                                         isPreferred, menuPos,
                                         BooklistGroup.RATING,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_BOOKSHELF:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_bookshelf,
                                         isPreferred, menuPos,
                                         BooklistGroup.BOOKSHELF,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_DATE_LAST_UPDATE:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_update_date,
                                         isPreferred, menuPos,
                                         BooklistGroup.DATE_LAST_UPDATE_YEAR,
                                         BooklistGroup.DATE_LAST_UPDATE_MONTH,
                                         BooklistGroup.DATE_LAST_UPDATE_DAY);
                style.getListScreenBookFields()
                     .setShowField(ListScreenBookFields.PK_AUTHOR, true);
                break;

            // NEWTHINGS: BuiltinStyle: add a new builtin style
            default:
                throw new IllegalStateException("style id=" + id);
        }

        return style;
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final BuiltinStyle that = (BuiltinStyle) o;
        return mLabelId == that.mLabelId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mLabelId);
    }
}
