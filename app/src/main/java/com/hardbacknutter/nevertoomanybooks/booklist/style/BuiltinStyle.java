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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        extends BaseStyle {

    private static final int ID_AUTHOR_THEN_SERIES = -1;
    /**
     * Absolute/initial default.
     */
    public static final int DEFAULT_ID = ID_AUTHOR_THEN_SERIES;

    //FIXME: used to be filtered on being "unread"; remove this style
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
     * Use the NEGATIVE builtin style id to get the definition for it.
     * Element 0 is not used.
     * NEVER change the order.
     * NEVER change the UUID values.
     */
    @SuppressWarnings("ZeroLengthArrayAllocation")
    public static final List<Definition> ALL = List.of(
            // not used.
            new Definition(0, "", 0, new int[0]),

            new Definition(ID_AUTHOR_THEN_SERIES,
                           "6a82c4c0-48f1-4130-8a62-bbf478ffe184",
                           R.string.style_builtin_author_series,
                           new int[]{BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_UNREAD_AUTHOR_THEN_SERIES,
                           "f479e979-c43f-4b0b-9c5b-6942964749df",
                           R.string.style_builtin_unread,
                           new int[]{BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_COMPACT,
                           "5e4c3137-a05f-4c4c-853a-bd1dacb6cd16",
                           R.string.style_builtin_compact,
                           new int[]{BooklistGroup.AUTHOR}),
            new Definition(ID_BOOK_TITLE_FIRST_LETTER,
                           "16b4ecdf-edef-4bf2-a682-23f7230446c8",
                           R.string.style_builtin_first_letter_book_title,
                           new int[]{BooklistGroup.BOOK_TITLE_1ST_LETTER}),
            new Definition(ID_SERIES,
                           "ad55ebc3-f79d-4cc2-a27d-f06ff0bf2335",
                           R.string.style_builtin_series,
                           new int[]{BooklistGroup.SERIES}),

            new Definition(ID_GENRE,
                           "edc5c178-60f0-40e7-9674-e08445b6c942",
                           R.string.style_builtin_genre,
                           new int[]{BooklistGroup.GENRE,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_LENDING,
                           "e4f1c364-2cbe-467e-a0c1-3ae71bd56fa3",
                           R.string.style_builtin_lending,
                           new int[]{BooklistGroup.LENDING,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_READ_AND_UNREAD,
                           "e3678890-7785-4870-9213-333a68293a49",
                           R.string.style_builtin_read_and_unread,
                           new int[]{BooklistGroup.READ_STATUS,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_PUBLICATION_DATA,
                           "182f5d3c-8fd7-4f3a-b5b0-0c93551d1796",
                           R.string.style_builtin_publication_date,
                           new int[]{BooklistGroup.DATE_PUBLISHED_YEAR,
                                     BooklistGroup.DATE_PUBLISHED_MONTH,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_DATE_ADDED,
                           "95d7afc0-a70a-4f1f-8d77-aa7ebc60e521",
                           R.string.style_builtin_added_date,
                           new int[]{BooklistGroup.DATE_ADDED_YEAR,
                                     BooklistGroup.DATE_ADDED_MONTH,
                                     BooklistGroup.DATE_ADDED_DAY,
                                     BooklistGroup.AUTHOR}),

            new Definition(ID_DATE_ACQUIRED,
                           "b3255b1f-5b07-4b3e-9700-96c0f8f35a58",
                           R.string.style_builtin_acquired_date,
                           new int[]{BooklistGroup.DATE_ACQUIRED_YEAR,
                                     BooklistGroup.DATE_ACQUIRED_MONTH,
                                     BooklistGroup.DATE_ACQUIRED_DAY,
                                     BooklistGroup.AUTHOR}),
            new Definition(ID_AUTHOR_AND_YEAR,
                           "7c9ad91e-df7c-415a-a205-cdfabff5465d",
                           R.string.style_builtin_author_year,
                           new int[]{BooklistGroup.AUTHOR,
                                     BooklistGroup.DATE_PUBLISHED_YEAR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_FORMAT,
                           "bdc43f17-2a95-42ef-b0f8-c750ef920f28",
                           R.string.style_builtin_format,
                           new int[]{BooklistGroup.FORMAT}),
            new Definition(ID_DATE_READ,
                           "034fe547-879b-4fa0-997a-28d769ba5a84",
                           R.string.style_builtin_read_date,
                           new int[]{BooklistGroup.DATE_READ_YEAR,
                                     BooklistGroup.DATE_READ_MONTH,
                                     BooklistGroup.AUTHOR}),
            new Definition(ID_LOCATION,
                           "e21a90c9-5150-49ee-a204-0cab301fc5a1",
                           R.string.style_builtin_location,
                           new int[]{BooklistGroup.LOCATION,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),

            new Definition(ID_LANGUAGE,
                           "00379d95-6cb2-40e6-8c3b-f8278f34750a",
                           R.string.style_builtin_language,
                           new int[]{BooklistGroup.LANGUAGE,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_RATING,
                           "20a2ebdf-81a7-4eca-a3a9-7275062b907a",
                           R.string.style_builtin_rating,
                           new int[]{BooklistGroup.RATING,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_BOOKSHELF,
                           "999d383e-6e76-416a-86f9-960c729aa718",
                           R.string.style_builtin_bookshelf,
                           new int[]{BooklistGroup.BOOKSHELF,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES}),
            new Definition(ID_DATE_LAST_UPDATE,
                           "427a0da5-0779-44b6-89e9-82772e5ad5ef",
                           R.string.style_builtin_update_date,
                           new int[]{BooklistGroup.DATE_LAST_UPDATE_YEAR,
                                     BooklistGroup.DATE_LAST_UPDATE_MONTH,
                                     BooklistGroup.DATE_LAST_UPDATE_DAY})
                                                      );

    /**
     * Absolute/initial default.
     */
    public static final String DEFAULT_UUID = ALL.get(-ID_AUTHOR_THEN_SERIES).uuid;
    /** We need a random style with a filter for testing. */
    @VisibleForTesting
    public static final String UUID_FOR_TESTING_ONLY = ALL.get(-ID_PUBLICATION_DATA).uuid;

    /**
     * Display name of this style.
     */
    @StringRes
    private final int labelResId;

    /**
     * Constructor.
     *
     * @param definition   to use
     * @param isPreferred  flag
     * @param menuPosition to set
     */
    @VisibleForTesting
    public BuiltinStyle(@NonNull final Definition definition,
                        final boolean isPreferred,
                        final int menuPosition) {
        super(definition.uuid);
        this.id = definition.id;

        this.labelResId = definition.labelResId;

        preferred = isPreferred;
        this.menuPosition = menuPosition;

        setGroupIds(Arrays.stream(definition.groupIds)
                          .boxed()
                          .collect(Collectors.toList()));
    }

    /**
     * Check if the given UUID is a builtin Style.
     *
     * @param uuid to check
     *
     * @return {@code true} if it is
     */
    public static boolean isBuiltin(@NonNull final String uuid) {
        // Use the definitions-list, not the cache!
        return !uuid.isEmpty() && ALL.stream()
                                     .map(definition -> definition.uuid)
                                     .collect(Collectors.toList())
                                     .contains(uuid);
    }

    /**
     * Only used during App setup to create the styles table.
     *
     * @param id the negative id to lookup
     *
     * @return the uuid
     */
    @NonNull
    public static String getUuidById(final int id) {
        return ALL.get(-id).uuid;
    }

    @NonNull
    public static BuiltinStyle createFromDatabase(@NonNull final DataHolder rowData) {

        final int id = rowData.getInt(DBKey.PK_ID);
        if (id >= 0) {
            throw new IllegalStateException("Style id from the db: " + id + " is >= 0");
        }

        final boolean preferred = rowData.getBoolean(DBKey.STYLE_IS_PREFERRED);
        final int menuPosition = rowData.getInt(DBKey.STYLE_MENU_POSITION);

        final BuiltinStyle style = new BuiltinStyle(ALL.get(-id), preferred, menuPosition);

        // NEWTHINGS: BuiltinStyle: add a new builtin style if needed
        if (id == ID_COMPACT) {
            // The predefined "Compact" style: smaller text, no images.
            style.setTextScale(Style.TEXT_SCALE_1_SMALL);
            style.setShowField(Screen.List, FieldVisibility.COVER[0], false);
            style.setShowField(Screen.List, FieldVisibility.COVER[1], false);
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
        return context.getString(labelResId);
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
        return labelResId == that.labelResId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), labelResId);
    }

    public static class Definition {

        public final int id;
        @NonNull
        public final String uuid;
        @NonNull
        public final int[] groupIds;
        @StringRes
        final int labelResId;

        Definition(final int id,
                   @NonNull final String uuid,
                   @StringRes final int labelResId,
                   @NonNull final int[] groupIds) {
            this.id = id;
            this.uuid = uuid;
            this.labelResId = labelResId;
            this.groupIds = groupIds;
        }
    }
}
