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

import java.util.Arrays;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
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

    private static final int ID_AUTHOR_THEN_SERIES = -1;
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private static final int ID_UNREAD_AUTHOR_THEN_SERIES = -2;
    private static final int ID_COMPACT = -3;
    private static final int ID_BOOK_TITLE_FIRST_LETTER = -4;
    private static final int ID_SERIES = -5;
    private static final int ID_GENRE = -6;
    private static final int ID_LENDING = -7;
    private static final int ID_READ_AND_UNREAD = -8;
    private static final int ID_PUBLICATION_DATA = -9;
    private static final int ID_DATE_ADDED = -10;
    private static final int ID_DATE_ACQUIRED = -11;
    private static final int ID_AUTHOR_AND_YEAR = -12;
    private static final int ID_FORMAT = -13;
    private static final int ID_DATE_READ = -14;
    private static final int ID_LOCATION = -15;
    private static final int ID_LANGUAGE = -16;
    private static final int ID_RATING = -17;
    private static final int ID_BOOKSHELF = -18;
    private static final int ID_DATE_LAST_UPDATE = -19;


    /**
     * Absolute/initial default.
     */
    public static final int DEFAULT_ID = ID_AUTHOR_THEN_SERIES;

    /**
     * Use the NEGATIVE builtin style id to get the UUID for it. Element 0 is not used.
     * NEVER change the order; NEVER change the UUID values.
     */
    private static final String[] ID_UUID = {
            "",
            //  1: ID_AUTHOR_THEN_SERIES
            "6a82c4c0-48f1-4130-8a62-bbf478ffe184",
            //Deprecated  2: ID_UNREAD_AUTHOR_THEN_SERIES
            "f479e979-c43f-4b0b-9c5b-6942964749df",
            //  3: ID_COMPACT
            "5e4c3137-a05f-4c4c-853a-bd1dacb6cd16",
            //  4: ID_BOOK_TITLE_FIRST_LETTER
            "16b4ecdf-edef-4bf2-a682-23f7230446c8",
            //  5: ID_SERIES
            "ad55ebc3-f79d-4cc2-a27d-f06ff0bf2335",

            //  6: ID_GENRE
            "edc5c178-60f0-40e7-9674-e08445b6c942",
            //  7: ID_LENDING
            "e4f1c364-2cbe-467e-a0c1-3ae71bd56fa3",
            //  8: ID_READ_AND_UNREAD
            "e3678890-7785-4870-9213-333a68293a49",
            //  9: ID_PUBLICATION_DATA
            "182f5d3c-8fd7-4f3a-b5b0-0c93551d1796",
            // 10: ID_DATE_ADDED
            "95d7afc0-a70a-4f1f-8d77-aa7ebc60e521",

            // 11: ID_DATE_ACQUIRED
            "b3255b1f-5b07-4b3e-9700-96c0f8f35a58",
            // 12: ID_AUTHOR_AND_YEAR
            "7c9ad91e-df7c-415a-a205-cdfabff5465d",
            // 13: ID_FORMAT
            "bdc43f17-2a95-42ef-b0f8-c750ef920f28",
            // 14: ID_DATE_READ
            "034fe547-879b-4fa0-997a-28d769ba5a84",
            // 15: ID_LOCATION
            "e21a90c9-5150-49ee-a204-0cab301fc5a1",

            // 16: ID_LANGUAGE
            "00379d95-6cb2-40e6-8c3b-f8278f34750a",
            // 17: ID_RATING
            "20a2ebdf-81a7-4eca-a3a9-7275062b907a",
            // 18: ID_BOOKSHELF
            "999d383e-6e76-416a-86f9-960c729aa718",
            // 19: ID_DATE_LAST_UPDATE
            "427a0da5-0779-44b6-89e9-82772e5ad5ef",
            };
    /**
     * Absolute/initial default.
     */
    public static final String DEFAULT_UUID = ID_UUID[-ID_AUTHOR_THEN_SERIES];

    /** We need a random style with a filter for testing. */
    @VisibleForTesting
    public static final String UUID_FOR_TESTING_ONLY = ID_UUID[-ID_PUBLICATION_DATA];

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

        Arrays.stream(groupIds)
              .forEach(groupId -> mGroups.add(BooklistGroup.newInstance(groupId, false, this)));
    }


    /**
     * Check if the given UUID is a builtin Style.
     *
     * @param uuid to check
     *
     * @return {@code true} if it is
     */
    public static boolean isBuiltin(@NonNull final String uuid) {
        // Use the array, not the cache!
        return !uuid.isEmpty() && Arrays.asList(ID_UUID).contains(uuid);
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

    @NonNull
    public static BuiltinStyle createFromDatabase(@NonNull final Context context,
                                                  @NonNull final DataHolder rowData) {

        // Dev Note: the way we construct these is not optimal, but it mimics the
        // way we create user-styles. The intention is that eventually all configuration
        // goes into the database.
        final int id = rowData.getInt(DBKey.PK_ID);
        final String uuid = rowData.getString(DBKey.KEY_STYLE_UUID);
        final boolean isPreferred = rowData.getBoolean(DBKey.BOOL_STYLE_IS_PREFERRED);
        final int menuPosition = rowData.getInt(DBKey.KEY_STYLE_MENU_POSITION);

        return create(context, id, uuid, isPreferred, menuPosition);
    }

    @VisibleForTesting
    @NonNull
    public static BuiltinStyle create(@NonNull final Context context,
                                      final int id,
                                      @NonNull final String uuid,
                                      final boolean isPreferred,
                                      final int menuPosition) {
        final BuiltinStyle style;
        switch (id) {
            case ID_AUTHOR_THEN_SERIES:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_author_series,
                                         isPreferred, menuPosition,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_UNREAD_AUTHOR_THEN_SERIES:
                //FIXME: used to be filtered on being "unread"; remove this style
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_unread,
                                         isPreferred, menuPosition,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_COMPACT:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_compact,
                                         isPreferred, menuPosition,
                                         BooklistGroup.AUTHOR);
                style.getTextScale().setScale(TextScale.TEXT_SCALE_1_SMALL);
                style.getListScreenBookFields()
                     .setShowField(ListScreenBookFields.PK_COVERS, false);
                break;

            case ID_BOOK_TITLE_FIRST_LETTER:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_first_letter_book_title,
                                         isPreferred, menuPosition,
                                         BooklistGroup.BOOK_TITLE_1ST_LETTER);
                break;

            case ID_SERIES:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_series,
                                         isPreferred, menuPosition,
                                         BooklistGroup.SERIES);
                break;

            case ID_GENRE:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_genre,
                                         isPreferred, menuPosition,
                                         BooklistGroup.GENRE,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_LENDING:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_lending,
                                         isPreferred, menuPosition,
                                         BooklistGroup.LENDING,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_READ_AND_UNREAD:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_read_and_unread,
                                         isPreferred, menuPosition,
                                         BooklistGroup.READ_STATUS,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_PUBLICATION_DATA:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_publication_date,
                                         isPreferred, menuPosition,
                                         BooklistGroup.DATE_PUBLISHED_YEAR,
                                         BooklistGroup.DATE_PUBLISHED_MONTH,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_DATE_ADDED:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_added_date,
                                         isPreferred, menuPosition,
                                         BooklistGroup.DATE_ADDED_YEAR,
                                         BooklistGroup.DATE_ADDED_MONTH,
                                         BooklistGroup.DATE_ADDED_DAY,
                                         BooklistGroup.AUTHOR);
                break;

            case ID_DATE_ACQUIRED:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_acquired_date,
                                         isPreferred, menuPosition,
                                         BooklistGroup.DATE_ACQUIRED_YEAR,
                                         BooklistGroup.DATE_ACQUIRED_MONTH,
                                         BooklistGroup.DATE_ACQUIRED_DAY,
                                         BooklistGroup.AUTHOR);
                break;

            case ID_AUTHOR_AND_YEAR:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_author_year,
                                         isPreferred, menuPosition,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.DATE_PUBLISHED_YEAR,
                                         BooklistGroup.SERIES);
                break;

            case ID_FORMAT:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_format,
                                         isPreferred, menuPosition,
                                         BooklistGroup.FORMAT);
                break;

            case ID_DATE_READ:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_read_date,
                                         isPreferred, menuPosition,
                                         BooklistGroup.DATE_READ_YEAR,
                                         BooklistGroup.DATE_READ_MONTH,
                                         BooklistGroup.AUTHOR);
                break;

            case ID_LOCATION:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_location,
                                         isPreferred, menuPosition,
                                         BooklistGroup.LOCATION,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_LANGUAGE:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_language,
                                         isPreferred, menuPosition,
                                         BooklistGroup.LANGUAGE,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_RATING:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_rating,
                                         isPreferred, menuPosition,
                                         BooklistGroup.RATING,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_BOOKSHELF:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_bookshelf,
                                         isPreferred, menuPosition,
                                         BooklistGroup.BOOKSHELF,
                                         BooklistGroup.AUTHOR,
                                         BooklistGroup.SERIES);
                break;

            case ID_DATE_LAST_UPDATE:
                style = new BuiltinStyle(context, id, uuid,
                                         R.string.style_builtin_update_date,
                                         isPreferred, menuPosition,
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
    public boolean isUserDefined() {
        return false;
    }

    @Override
    @NonNull
    public String getTypeDescription(@NonNull final Context context) {
        return context.getString(R.string.style_is_builtin);
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
