/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.booklist.style.MapDBKey;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public interface BooklistGroup {

    /**
     * The ID's for the groups.
     * <strong>Never change these values</strong>, they get stored in the db.
     * <p>
     * Also: the code relies on BOOK being == 0
     */
    int BOOK = 0;
    /** {@link AuthorBooklistGroup}. */
    int AUTHOR = 1;
    /** {@link SeriesBooklistGroup}. */
    int SERIES = 2;
    /**
     * 2025-01-17: Formerly the book table column "genre".
     * Now the linked tags table.
     * {@link TagBooklistGroup}
     */
    int TAGS_GENRE = 3;
    /** {@link PublisherBooklistGroup}. */
    int PUBLISHER = 4;
    int READ_STATUS = 5;
    int LENDING = 6;
    int DATE_PUBLISHED_YEAR = 7;
    int DATE_PUBLISHED_MONTH = 8;
    int BOOK_TITLE_1ST_CHAR = 9;
    int DATE_ADDED_YEAR = 10;
    int DATE_ADDED_MONTH = 11;
    int DATE_ADDED_DAY = 12;
    int FORMAT = 13;
    int DATE_READ_YEAR = 14;
    int DATE_READ_MONTH = 15;
    int DATE_READ_DAY = 16;
    int LOCATION = 17;
    int LANGUAGE = 18;
    int DATE_LAST_UPDATE_YEAR = 19;
    int DATE_LAST_UPDATE_MONTH = 20;
    int DATE_LAST_UPDATE_DAY = 21;
    int RATING = 22;
    /** {@link BookshelfBooklistGroup}. */
    int BOOKSHELF = 23;
    int DATE_ACQUIRED_YEAR = 24;
    int DATE_ACQUIRED_MONTH = 25;
    int DATE_ACQUIRED_DAY = 26;
    int DATE_FIRST_PUBLICATION_YEAR = 27;
    int DATE_FIRST_PUBLICATION_MONTH = 28;
    int COLOR = 29;
    @SuppressWarnings("WeakerAccess")
    int SERIES_TITLE_1ST_CHAR = 30;
    int CONDITION = 31;
    int AUTHOR_FAMILY_NAME_1ST_CHAR = 32;
    int PUBLISHER_NAME_1ST_CHAR = 33;
    int IDENTIFIER = 34;
    int ORIGINAL_LANGUAGE = 35;
    /**
     * NEWTHINGS: BooklistGroup
     * The highest valid index of id - ALWAYS to be updated after adding a group.
     */
    @VisibleForTesting
    int GROUP_KEY_MAX = 35;

    /**
     * Factory constructor.
     * <p>
     * Create a new BooklistGroup of the specified id.
     *
     * @param id    of group to create
     * @param style Style reference; only used to retrieve default options for the group
     *
     * @return instance
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    static BooklistGroup newInstance(@Id final int id,
                                     @NonNull final Style style) {

        // The GroupKey is created once and cached in a static Map.
        final GroupKey groupKey = GroupKeyFactory.getKey(id);

        switch (id) {
            case AUTHOR:
                return new AuthorBooklistGroup(groupKey,
                                               style.isShowAuthorByGivenName(),
                                               style.isSortAuthorByGivenName());
            case SERIES:
                return new SeriesBooklistGroup(groupKey);
            case PUBLISHER:
                return new PublisherBooklistGroup(groupKey);
            case BOOKSHELF:
                return new BookshelfBooklistGroup(groupKey);
            case TAGS_GENRE:
                return new TagBooklistGroup(groupKey);
            case IDENTIFIER:
                return new IdentifierBooklistGroup(groupKey);
            default:
                return new BooklistGroupImpl(groupKey);
        }
    }

    /**
     * Factory constructor.
     * <p>
     * Create a list of <strong>ALL defined</strong> groups,
     * <p>
     * Note that these are <strong>not</strong> the {@link Style}'s groups; the style
     * is only used to get default settings for some groups.
     * <p>
     * This <strong>excludes</strong> the Book key itself.
     *
     * @param style Style reference; only used to retrieve default options for the group
     *
     * @return the list
     */
    @SuppressLint("WrongConstant")
    @NonNull
    static List<BooklistGroup> getAllGroups(@NonNull final Style style) {
        return IntStream.rangeClosed(1, BooklistGroup.GROUP_KEY_MAX)
                        .mapToObj(id -> newInstance(id, style))
                        .collect(Collectors.toList());
    }

    /**
     * Get the id for this group.
     *
     * @return id
     */
    @Id
    int getId();

    /**
     * Get the displayable name.
     *
     * @param context Current context
     *
     * @return name
     */
    @NonNull
    String getLabel(@NonNull Context context);

    /**
     * Create the expression for the key column: "/key=value".
     * <p>
     * Implementations <strong>must</strong> replace a {@code null} value with an empty string
     *
     * @return column expression
     */
    @NonNull
    String getNodeKeyExpression();

    /**
     * Get the domain that contains the displayable data.
     * This is used to build the list table.
     *
     * @return domain to display
     */
    @NonNull
    DomainExpression getDisplayDomainExpression();

    /**
     * Get the domains represented by this group.
     * This is used to build the list table.
     *
     * @return list
     */
    @NonNull
    List<DomainExpression> getGroupDomainExpressions();

    /**
     * Get the domains that this group adds to the lowest level (book).
     * This is used to build the list table.
     *
     * @return list
     */
    @NonNull
    List<DomainExpression> getBaseDomainExpressions();

    /**
     * Get the domains for this group <strong>and its outer groups</strong>
     * This is used to build the triggers.
     *
     * @return list
     */
    @NonNull
    List<Domain> getAccumulatedDomains();

    /**
     * Set the accumulated domains represented by this group <strong>and its outer groups</strong>.
     *
     * @param accumulatedDomains list of domains.
     */
    void setAccumulatedDomains(@NonNull List<Domain> accumulatedDomains);

    /**
     * Get the {@link GroupPrefs} structure for this group.
     *
     * @return structure with preference category and keys.
     */
    @Nullable
    GroupPrefs getGroupPrefs();

    // NEWTHINGS: BooklistGroup add IntDef
    @IntDef({
            AUTHOR,
            AUTHOR_FAMILY_NAME_1ST_CHAR,
            BOOK,
            BOOKSHELF,
            BOOK_TITLE_1ST_CHAR,
            COLOR,
            CONDITION,
            DATE_ACQUIRED_DAY,
            DATE_ACQUIRED_MONTH,
            DATE_ACQUIRED_YEAR,
            DATE_ADDED_DAY,
            DATE_ADDED_MONTH,
            DATE_ADDED_YEAR,
            DATE_FIRST_PUBLICATION_MONTH,
            DATE_FIRST_PUBLICATION_YEAR,
            DATE_LAST_UPDATE_DAY,
            DATE_LAST_UPDATE_MONTH,
            DATE_LAST_UPDATE_YEAR,
            DATE_PUBLISHED_MONTH,
            DATE_PUBLISHED_YEAR,
            DATE_READ_DAY,
            DATE_READ_MONTH,
            DATE_READ_YEAR,
            FORMAT,
            IDENTIFIER,
            LANGUAGE,
            LENDING,
            LOCATION,
            ORIGINAL_LANGUAGE,
            PUBLISHER,
            PUBLISHER_NAME_1ST_CHAR,
            RATING,
            READ_STATUS,
            SERIES,
            SERIES_TITLE_1ST_CHAR,
            TAGS_GENRE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface Id {

    }

    /**
     * The equivalent to {@link DBKey}s for the BooklistGroup specific domains.
     *
     * @see MapDBKey
     */
    @SuppressWarnings("WeakerAccess")
    final class BlgDBKey {

        public static final String ACQUIRED_DAY = "blg_acq_d";
        public static final String ACQUIRED_MONTH = "blg_acq_m";
        public static final String ACQUIRED_YEAR = "blg_acq_y";
        public static final String ADDED_DAY = "blg_add_d";
        public static final String ADDED_MONTH = "blg_add_m";
        public static final String ADDED_YEAR = "blg_add_y";
        public static final String FIRST_PUB_MONTH = "blg_1pub_m";
        public static final String FIRST_PUB_YEAR = "blg_1pub_y";
        public static final String LAST_UPD_DAY = "blg_upd_d";
        public static final String LAST_UPD_MONTH = "blg_upd_m";
        public static final String LAST_UPD_YEAR = "blg_upd_y";
        public static final String PUB_MONTH = "blg_pub_m";
        public static final String PUB_YEAR = "blg_pub_y";
        public static final String READ_DAY = "blg_rd_d";
        public static final String READ_MONTH = "blg_rd_m";
        public static final String READ_STATUS = "blg_rd_sts";
        public static final String READ_YEAR = "blg_rd_y";

        public static final String AUTHOR_FAMILY_NAME_1CHAR = "blg_aut_fn_1ch";
        public static final String BOOK_TITLE_1CHAR = "blg_tit_1ch";
        public static final String PUBLISHER_NAME_1CHAR = "blg_pub_1ch";
        public static final String SERIES_TITLE_1CHAR = "blg_ser_tit_1ch";

        /**
         * Specific domains for sorting.
         * <strong>IMPORTANT</strong>: when adding a SORT_* key here,
         * it should also be added to {@link MapDBKey}#DB_KEY_TO_LABEL_RES_ID
         */
        public static final String SORT_AUTHOR = "blg_sort_aut";
        public static final String SORT_BOOKSHELF = "blg_sort_shelf";
        public static final String SORT_IDENTIFIER = "blg_sort_ident";
        public static final String SORT_PUBLISHER = "blg_sort_pub";
        public static final String SORT_SERIES_NUM_FLOAT = "blg_sort_ser_num_f";
        public static final String SORT_SERIES_TITLE = "blg_sort_ser";
        public static final String SORT_TAG = "blg_sort_tags";

        private BlgDBKey() {
        }
    }
}
