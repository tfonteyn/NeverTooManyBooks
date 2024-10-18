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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Note the hardcoded negative ID's.
 * These id/uuid's should NEVER be changed as they will get stored
 * in preferences and serialized. Take care not to add duplicates.
 * <p>
 * Deprecated styles cannot be removed as we might come across them when importing
 * older backups. They are however filtered/substituted as needed.
 */
public final class BuiltinStyle
        extends BaseStyle {

    /**
     * The style which will get used when there is something wrong with the Style
     * which we were supposed to be using; i.e. this is a FALLBACK.
     * while the {@link GlobalStyle} is a holder for the global DEFAULT values.
     */
    public static final String HARD_DEFAULT_UUID;
    /**
     * Matching id for {@link #HARD_DEFAULT_UUID}.
     */
    public static final int HARD_DEFAULT_ID;

    /**
     * We need a random style for testing. Currently set to -9: ID_PUBLICATION_DATA.
     * See {@link #ALL} remarks.
     */
    @VisibleForTesting
    public static final int ID_FOR_TESTING_ONLY = -9;


    private static final int ID_AUTHOR_THEN_SERIES = -1;
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private static final int ID_DEPRECATED_1 = -2;
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private static final int ID_DEPRECATED_2 = -3;
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
     * It's an <strong>ordered list - NEVER change the order</strong>.
     * It should only ever be exposed as a {@link Collection};
     * i.e. no direct access using an index - the id does NOT match the index as it used to.
     * NEVER change the UUID values.
     */
    @VisibleForTesting
    public static final Collection<Definition> ALL = List.of(
            // 0
            Definition.create(ID_AUTHOR_THEN_SERIES,
                              "6a82c4c0-48f1-4130-8a62-bbf478ffe184",
                              R.string.style_builtin_author_series,
                              List.of(BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),

            // UNREAD_AUTHOR_THEN_SERIES
            Definition.deprecated(ID_DEPRECATED_1, "f479e979-c43f-4b0b-9c5b-6942964749df"),
            // COMPACT
            Definition.deprecated(ID_DEPRECATED_2, "5e4c3137-a05f-4c4c-853a-bd1dacb6cd16"),

            Definition.create(ID_BOOK_TITLE_FIRST_LETTER,
                              "16b4ecdf-edef-4bf2-a682-23f7230446c8",
                              R.string.style_builtin_1st_char_book_title,
                              List.of(BooklistGroup.BOOK_TITLE_1ST_CHAR)),
            Definition.create(ID_SERIES,
                              "ad55ebc3-f79d-4cc2-a27d-f06ff0bf2335",
                              R.string.style_builtin_series,
                              List.of(BooklistGroup.SERIES)),
            // 5
            Definition.create(ID_GENRE,
                              "edc5c178-60f0-40e7-9674-e08445b6c942",
                              R.string.style_builtin_genre,
                              List.of(BooklistGroup.GENRE,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),
            Definition.create(ID_LENDING,
                              "e4f1c364-2cbe-467e-a0c1-3ae71bd56fa3",
                              R.string.style_builtin_lending,
                              List.of(BooklistGroup.LENDING,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),
            Definition.create(ID_READ_AND_UNREAD,
                              "e3678890-7785-4870-9213-333a68293a49",
                              R.string.style_builtin_read_and_unread,
                              List.of(BooklistGroup.READ_STATUS,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),
            Definition.create(ID_PUBLICATION_DATA,
                              "182f5d3c-8fd7-4f3a-b5b0-0c93551d1796",
                              R.string.style_builtin_date_published,
                              List.of(BooklistGroup.DATE_PUBLISHED_YEAR,
                                      BooklistGroup.DATE_PUBLISHED_MONTH,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),
            Definition.create(ID_DATE_ADDED,
                              "95d7afc0-a70a-4f1f-8d77-aa7ebc60e521",
                              R.string.style_builtin_date_added,
                              List.of(BooklistGroup.DATE_ADDED_YEAR,
                                      BooklistGroup.DATE_ADDED_MONTH,
                                      BooklistGroup.DATE_ADDED_DAY,
                                      BooklistGroup.AUTHOR)),
            // 10
            Definition.create(ID_DATE_ACQUIRED,
                              "b3255b1f-5b07-4b3e-9700-96c0f8f35a58",
                              R.string.style_builtin_date_acquired,
                              List.of(BooklistGroup.DATE_ACQUIRED_YEAR,
                                      BooklistGroup.DATE_ACQUIRED_MONTH,
                                      BooklistGroup.DATE_ACQUIRED_DAY,
                                      BooklistGroup.AUTHOR)),
            Definition.create(ID_AUTHOR_AND_YEAR,
                              "7c9ad91e-df7c-415a-a205-cdfabff5465d",
                              R.string.style_builtin_author_year,
                              List.of(BooklistGroup.AUTHOR,
                                      BooklistGroup.DATE_PUBLISHED_YEAR,
                                      BooklistGroup.SERIES)),
            Definition.create(ID_FORMAT,
                              "bdc43f17-2a95-42ef-b0f8-c750ef920f28",
                              R.string.style_builtin_format,
                              List.of(BooklistGroup.FORMAT)),
            Definition.create(ID_DATE_READ,
                              "034fe547-879b-4fa0-997a-28d769ba5a84",
                              R.string.style_builtin_date_read,
                              List.of(BooklistGroup.DATE_READ_YEAR,
                                      BooklistGroup.DATE_READ_MONTH,
                                      BooklistGroup.AUTHOR)),
            Definition.create(ID_LOCATION,
                              "e21a90c9-5150-49ee-a204-0cab301fc5a1",
                              R.string.style_builtin_location,
                              List.of(BooklistGroup.LOCATION,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),
            // 15
            Definition.create(ID_LANGUAGE,
                              "00379d95-6cb2-40e6-8c3b-f8278f34750a",
                              R.string.style_builtin_language,
                              List.of(BooklistGroup.LANGUAGE,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),
            Definition.create(ID_RATING,
                              "20a2ebdf-81a7-4eca-a3a9-7275062b907a",
                              R.string.style_builtin_rating,
                              List.of(BooklistGroup.RATING,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),
            Definition.create(ID_BOOKSHELF,
                              "999d383e-6e76-416a-86f9-960c729aa718",
                              R.string.style_builtin_bookshelf,
                              List.of(BooklistGroup.BOOKSHELF,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES)),
            Definition.create(ID_DATE_LAST_UPDATE,
                              "427a0da5-0779-44b6-89e9-82772e5ad5ef",
                              R.string.style_builtin_date_last_updated,
                              List.of(BooklistGroup.DATE_LAST_UPDATE_YEAR,
                                      BooklistGroup.DATE_LAST_UPDATE_MONTH,
                                      BooklistGroup.DATE_LAST_UPDATE_DAY))
    );

    static {
        HARD_DEFAULT_ID = ID_AUTHOR_THEN_SERIES;

        HARD_DEFAULT_UUID = ALL.stream()
                               .filter(def -> def.getId() == HARD_DEFAULT_ID)
                               .findFirst()
                               .map(Definition::getUuid)
                               .orElseThrow();
    }

    /**
     * Display name of this style.
     */
    @StringRes
    private final int labelResId;

    /**
     * Constructor.
     *
     * @param definition to use
     */
    @VisibleForTesting
    public BuiltinStyle(@NonNull final Definition definition) {
        super(definition.getUuid(), definition.getId());
        this.labelResId = definition.getLabelResId();

        final Style styleDefaults = ServiceLocator.getInstance().getStyles().getGlobalStyle();

        copyBasicSettings(styleDefaults);
        // Groups come from the definition!
        setGroupIds(definition.getGroupIds());
        // But the options once again come from the defaults
        copyGroupOptions(styleDefaults);
    }

    /**
     * Get the number of builtin styles.
     * <p>
     * Deprecated styles are NOT counted.
     *
     * @return count
     */
    public static int size() {
        return (int) ALL.stream().filter(def -> !def.isDeprecated()).count();
    }

    /**
     * Get all the builtin styles.
     * <p>
     * Deprecated styles are NOT included.
     *
     * @return all styles
     */
    @NonNull
    public static Collection<Definition> getAll() {
        return ALL.stream().filter(def -> !def.isDeprecated()).collect(Collectors.toList());
    }

    /**
     * Check if the given UUID is a builtin Style.
     * <p>
     * Deprecated styles ARE checked.
     *
     * @param uuid to check
     *
     * @return {@code true} if it is
     */
    public static boolean isBuiltin(@NonNull final String uuid) {
        return !uuid.isEmpty() && ALL.stream()
                                     .anyMatch(def -> def.getUuid().equalsIgnoreCase(uuid));
    }

    /**
     * Constructor. Load the style data from the database,
     * using the given defaults.
     *
     * @param rowData from the styles table
     *
     * @return the loaded Style, or {@code Optional.empty()} if the style is deprecated.
     */
    @NonNull
    public static Optional<Style> createFromDatabase(@NonNull final DataHolder rowData) {
        final int id = rowData.getInt(DBKey.PK_ID);
        final Definition styleDef = ALL.stream()
                                       .filter(def -> def.getId() == id)
                                       .findFirst()
                                       .orElseThrow(() -> new IllegalStateException(
                                               "Style id from the db: " + id + " is invalid"));
        if (styleDef.isDeprecated()) {
            return Optional.empty();
        }

        final Style style = new BuiltinStyle(styleDef);
        style.setPreferred(rowData.getBoolean(DBKey.STYLE_IS_PREFERRED));
        style.setMenuPosition(rowData.getInt(DBKey.STYLE_MENU_POSITION));

        return Optional.of(style);
    }

    @Override
    @NonNull
    public StyleType getType() {
        return StyleType.Builtin;
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

    public static final class Definition {

        /** The id is always a NEGATIVE value. */
        private final int id;
        @NonNull
        private final String uuid;
        @NonNull
        private final List<Integer> groupIds;
        @StringRes
        private final int labelResId;

        private final boolean deprecated;

        private Definition(final int id,
                           final boolean deprecated,
                           @NonNull final String uuid,
                           @StringRes final int labelResId,
                           @NonNull final List<Integer> groupIds) {
            this.id = id;
            this.uuid = uuid;
            this.labelResId = labelResId;
            this.deprecated = deprecated;
            this.groupIds = groupIds;
        }

        @NonNull
        static Definition create(final int id,
                                 @NonNull final String uuid,
                                 @StringRes final int labelResId,
                                 @NonNull final List<Integer> groupIds) {
            return new Definition(id, false, uuid, labelResId, groupIds);
        }

        @SuppressWarnings("SameParameterValue")
        @NonNull
        static Definition deprecated(final int id,
                                     @NonNull final String uuid) {
            return new Definition(id, true, uuid, R.string.disabled, List.of());
        }

        public int getId() {
            return id;
        }

        @NonNull
        public String getUuid() {
            return uuid;
        }

        @NonNull
        List<Integer> getGroupIds() {
            return groupIds;
        }

        int getLabelResId() {
            return labelResId;
        }

        boolean isDeprecated() {
            return deprecated;
        }
    }
}
