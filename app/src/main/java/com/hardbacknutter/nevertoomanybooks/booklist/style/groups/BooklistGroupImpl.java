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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.adapter.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.utils.UniqueMap;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_ADDED__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LAST_UPDATED__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * Class representing a single level in the booklist hierarchy.
 * <p>
 * There is a one-to-one mapping with a {@link GroupKey},
 * the latter providing a lightweight (static final) object without user preferences.
 * The BooklistGroup encapsulates the {@link GroupKey}, adding user/temp stuff.
 * <p>
 * How to add a new Group:
 * <ol>
 *      <li>add it to {@link GroupKey} and update {@link BooklistGroup#GROUP_KEY_MAX}</li>
 *      <li>add it to the {@link Id}</li>
 *      <li>if necessary add new domain to {@link DBDefinitions}</li>
 *      <li>add to the switch() in {@link #initGroupKey}. Create key/sort domains as needed</li>
 *      <li>Optionally modify {@link BooklistAdapter#onCreateViewHolder};
 *          If it is just a string field it can use a {@link BooklistAdapter}.GenericStringHolder
 *          otherwise add a new holder</li>
 * </ol>
 */
class BooklistGroupImpl
        implements BooklistGroup {

    private static final String GLOB_YYYY =
            " GLOB '[0-9][0-9][0-9][0-9]*'";
    private static final String GLOB_YYYY_MM =
            " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]*'";
    private static final String GLOB_YYYY_M =
            " GLOB '[0-9][0-9][0-9][0-9]-[0-9]*'";
    private static final String GLOB_YYYY_MM_DD =
            " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*'";
    private static final String GLOB_YYYY_M_DD =
            " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9][0-9]*'";
    private static final String GLOB_YYYY_MM_D =
            " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9]*'";
    private static final String GLOB_YYY_M_D =
            " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9]*'";
    /**
     * Base domains (BD_*) for Date groups.
     * Date based groups have to sort on the full date for cases
     * where we don't have all separate year,month,day fields.
     */
    private static final DomainExpression BD_DATE_PUBLISHED =
            new DomainExpression(DOM_BOOK_DATE_PUBLISHED, Sort.Desc);
    private static final DomainExpression BD_DATE_FIRST_PUBLICATION =
            new DomainExpression(DOM_DATE_FIRST_PUBLICATION, Sort.Desc);
    private static final DomainExpression BD_BOOK_IS_READ =
            new DomainExpression(DOM_BOOK_READ, Sort.Desc);
    private static final DomainExpression BD_DATE_READ_END =
            new DomainExpression(DOM_BOOK_DATE_READ_END, Sort.Desc);
    private static final DomainExpression BD_DATE_ADDED =
            new DomainExpression(DOM_ADDED__UTC, Sort.Desc);
    private static final DomainExpression BD_DATE_LAST_UPDATED =
            new DomainExpression(DOM_LAST_UPDATED__UTC, Sort.Desc);
    private static final DomainExpression BD_DATE_ACQUIRED =
            new DomainExpression(DOM_BOOK_DATE_ACQUIRED, Sort.Desc);

    private static final String _AND_ = " AND ";
    private static final String CASE = "CASE";
    private static final String _WHEN_ = " WHEN ";
    private static final String _THEN_ = " THEN ";
    private static final String SUBSTR = "SUBSTR(";
    private static final String _ELSE_ = " ELSE ";
    private static final String _END = " END";

    /** Cache for the static GroupKey instances. */
    private static final Map<Integer, GroupKey> GROUP_KEYS = new UniqueMap<>();

    /** The underlying group key object. */
    @NonNull
    private final GroupKey groupKey;
    /**
     * The domains represented by this group.
     * Set at <strong>runtime</strong> by the BooklistBuilder
     * based on current group <strong>and its outer groups</strong>
     */
    @Nullable
    private List<Domain> accumulatedDomains;

    /**
     * Constructor.
     *
     * @param id of group to create
     */
    BooklistGroupImpl(@BooklistGroup.Id final int id) {
        groupKey = initGroupKey(id);
    }

    /**
     * If the field has a time part, convert it to local time.
     * This deals with legacy 'date-only' dates.
     * The logic being that IF they had a time part it would be UTC.
     * Without a time part, we assume the zone is local (or irrelevant).
     *
     * @param fieldSpec fully qualified field name
     *
     * @return expression
     */
    @NonNull
    private static String localDateTimeExpression(@NonNull final String fieldSpec) {
        return CASE
               + _WHEN_ + fieldSpec + " GLOB '*-*-* *' "
               + _THEN_ + "datetime(" + fieldSpec + ", 'localtime')"
               + _ELSE_ + fieldSpec
               + _END;
    }

    /**
     * General remark on the use of GLOB instead of 'strftime(format, date)':
     * strftime() only works on full date(time) strings. i.e. 'YYYY-MM-DD*'
     * for all other formats, it will fail to extract the fields.
     * <p>
     * Create a GLOB expression to get the 'year' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers. We don't care about anything else.
     * <p>
     * See <a href="https://www.sqlitetutorial.net/sqlite-glob/">sqlite-glob</a>
     *
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     * @param fieldSpec fully qualified field name
     *
     * @return expression
     */
    @NonNull
    private static String year(final boolean toLocal,
                               @NonNull final String... fieldSpec) {

        final StringBuilder sb = new StringBuilder(CASE);

        for (int i = 0; i < fieldSpec.length; i++) {
            if (toLocal) {
                fieldSpec[i] = localDateTimeExpression(fieldSpec[i]);
            }
            sb.append(_WHEN_).append(fieldSpec[i]).append(GLOB_YYYY)
              .append(_THEN_).append(SUBSTR).append(fieldSpec[i]).append(",1,4)");
        }
        sb.append(" ELSE ''").append(_END);
        return sb.toString();
    }

    /**
     * Create a GLOB expression to get the 'month' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit.
     * We don't care about anything else.
     *
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     * @param fieldSpec fully qualified field name
     *
     * @return expression
     */
    @NonNull
    private static String month(final boolean toLocal,
                                @NonNull final String... fieldSpec) {
        final StringBuilder sb = new StringBuilder(CASE);

        for (int i = 0; i < fieldSpec.length; i++) {
            if (toLocal) {
                fieldSpec[i] = localDateTimeExpression(fieldSpec[i]);
            }
            sb.append(_WHEN_).append(fieldSpec[i]).append(GLOB_YYYY_MM)
              .append(_THEN_).append(SUBSTR).append(fieldSpec[i]).append(",6,2)")
              .append(_WHEN_).append(fieldSpec[i]).append(GLOB_YYYY_M)
              .append(_THEN_).append(SUBSTR).append(fieldSpec[i]).append(",6,1)");
        }
        sb.append(" ELSE ''").append(_END);
        return sb.toString();
    }

    /**
     * Create a GLOB expression to get the 'day' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit,
     * and then by '-' and 1 or two digits.
     * We don't care about anything else.
     *
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     * @param fieldSpec fully qualified field name
     *
     * @return expression
     */
    @NonNull
    private static String day(@SuppressWarnings("SameParameterValue") final boolean toLocal,
                              @NonNull final String... fieldSpec) {
        final StringBuilder sb = new StringBuilder(CASE);

        for (int i = 0; i < fieldSpec.length; i++) {
            if (toLocal) {
                fieldSpec[i] = localDateTimeExpression(fieldSpec[i]);
            }
            // Look for 4 leading numbers followed by 2 or 1 digit then another 2 or 1 digit.
            // YYYY-MM-DD or YYYY-M-DD or YYYY-MM-D or YYYY-M-D
            sb.append(_WHEN_).append(fieldSpec[i]).append(GLOB_YYYY_MM_DD)
              .append(_THEN_).append(SUBSTR).append(fieldSpec[i]).append(",9,2)")
              .append(_WHEN_).append(fieldSpec[i]).append(GLOB_YYYY_M_DD)
              .append(_THEN_).append(SUBSTR).append(fieldSpec[i]).append(",8,2)")
              .append(_WHEN_).append(fieldSpec[i]).append(GLOB_YYYY_MM_D)
              .append(_THEN_).append(SUBSTR).append(fieldSpec[i]).append(",9,1)")
              .append(_WHEN_).append(fieldSpec[i]).append(GLOB_YYY_M_D)
              .append(_THEN_).append(SUBSTR).append(fieldSpec[i]).append(",8,1)");
        }
        sb.append(" ELSE ''").append(_END);
        return sb.toString();
    }

    /**
     * Create/get a GroupKey. We create the keys only once and keep them in a static cache map.
     * This must be called <strong>after</strong> construction, i.e. from {@link BooklistGroup#newInstance}.
     *
     * @param id of group to create
     *
     * @return the GroupKey
     */
    @NonNull
    private GroupKey initGroupKey(@BooklistGroup.Id final int id) {
        GroupKey key = GROUP_KEYS.get(id);
        if (key == null) {
            key = createGroupKey(id);
            GROUP_KEYS.put(id, key);
        }
        return key;
    }

    /**
     * GroupKey factory constructor. Called <strong>ONCE</strong> for each group
     * during the lifetime of the app.
     * <p>
     * Specialized classes must override this method to provide their key.
     * (which is why this method is not static...)
     *
     * @param id of group to create
     *
     * @return new GroupKey instance
     *
     * @throws IllegalArgumentException for an unknown group -> bug
     * @see AuthorBooklistGroup
     * @see BookshelfBooklistGroup
     * @see PublisherBooklistGroup
     * @see SeriesBooklistGroup
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    protected GroupKey createGroupKey(@BooklistGroup.Id final int id) {
        // NEWTHINGS: BooklistGroup
        switch (id) {
            // Data without a linked table uses the display name as the key domain.
            case COLOR: {
                return new GroupKey(id, R.string.lbl_color, "col",
                                    new DomainExpression(DOM_BOOK_COLOR, TBL_BOOKS, Sort.Asc));
            }
            case FORMAT: {
                return new GroupKey(id, R.string.lbl_format, "fmt",
                                    new DomainExpression(DOM_BOOK_FORMAT, TBL_BOOKS, Sort.Asc));
            }
            case GENRE: {
                return new GroupKey(id, R.string.lbl_genre, "g",
                                    new DomainExpression(DOM_BOOK_GENRE, TBL_BOOKS, Sort.Asc));
            }
            case LANGUAGE: {
                // Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_language, "lng",
                                    new DomainExpression(DOM_BOOK_LANGUAGE, TBL_BOOKS, Sort.Asc));
            }
            case LOCATION: {
                return new GroupKey(id, R.string.lbl_location, "loc",
                                    new DomainExpression(DOM_BOOK_LOCATION, TBL_BOOKS, Sort.Asc));
            }
            case CONDITION: {
                return new GroupKey(id, R.string.lbl_condition, "bk_cnd",
                                    new DomainExpression(DOM_BOOK_CONDITION, TBL_BOOKS, Sort.Desc));
            }
            case RATING: {
                // Formatting is done after fetching; sort with highest rated first.
                // The data is cast to an integer as a precaution/paranoia,
                // but also to avoid having to post-process every row in code.
                return new GroupKey(id, R.string.lbl_rating, "rt", new DomainExpression(
                        DOM_BOOK_RATING,
                        "CAST(" + TBL_BOOKS.dot(DBKey.RATING) + " AS INTEGER)",
                        Sort.Desc));
            }
            case LENDING: {
                // This will be a LEFT OUTER JOIN, so coerce missing rows to ''
                return new GroupKey(id, R.string.lbl_lend_out, "l", new DomainExpression(
                        DOM_LOANEE,
                        "COALESCE(" + TBL_BOOK_LOANEE.dot(DBKey.LOANEE_NAME) + ",'')",
                        Sort.Asc));
            }

            // the others here below are custom key domains
            case READ_STATUS: {
                // Formatting is done after fetching.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.READ_STATUS, SqLiteDataType.Text)
                                .notNull()
                                .build(),
                        // Ww could also test for books where the start/end dates
                        // are set and the flag is NOT set...
                        // But that situation should never occur anyhow... flw
                        CASE
                        + _WHEN_ + ReadStatus.W_READ + _THEN_ + ReadStatus.Read.getId()
                        + _WHEN_ + ReadStatus.W_READING + _THEN_ + ReadStatus.Reading.getId()
                        + _ELSE_ + ReadStatus.Unread.getId()
                        + _END,
                        Sort.Asc);
                return new GroupKey(id, R.string.lbl_group_read_and_unread, "r",
                                    keyDomainExpression);
            }

            case AUTHOR_FAMILY_NAME_1ST_CHAR: {
                // Uses the OrderBy column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.AUTHOR_FAMILY_NAME_1CHAR, SqLiteDataType.Text)
                                .notNull()
                                .build(),
                        "UPPER(SUBSTR(" + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME_OB) + ",1,1))",
                        Sort.Asc);
                return new GroupKey(id, R.string.lbl_group_1st_char_author_family_name, "af1",
                                    keyDomainExpression);
            }
            case SERIES_TITLE_1ST_CHAR: {
                // Uses the OrderBy column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.SERIES_TITLE_1CHAR, SqLiteDataType.Text)
                                .notNull()
                                .build(),
                        "UPPER(SUBSTR(" + TBL_SERIES.dot(DBKey.SERIES_TITLE_OB) + ",1,1))",
                        Sort.Asc);
                return new GroupKey(id, R.string.lbl_group_1st_char_series_title, "st1",
                                    keyDomainExpression);
            }
            case PUBLISHER_NAME_1ST_CHAR: {
                // Uses the OB column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.PUBLISHER_NAME_1CHAR, SqLiteDataType.Text)
                                .notNull()
                                .build(),
                        "UPPER(SUBSTR(" + TBL_BOOKS.dot(DBKey.PUBLISHER_NAME_OB) + ",1,1))",
                        Sort.Asc);
                return new GroupKey(id, R.string.lbl_group_1st_char_publisher_name, "p1",
                                    keyDomainExpression);
            }
            case BOOK_TITLE_1ST_CHAR: {
                // Uses the OB column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.BOOK_TITLE_1CHAR, SqLiteDataType.Text)
                                .notNull()
                                .build(),
                        "UPPER(SUBSTR(" + TBL_BOOKS.dot(DBKey.TITLE_OB) + ",1,1))",
                        Sort.Asc);
                return new GroupKey(id, R.string.lbl_group_1st_char_book_title, "bt1",
                                    keyDomainExpression);
            }

            case DATE_PUBLISHED_YEAR: {
                // UTC. Formatting is done after fetching.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.PUB_YEAR, SqLiteDataType.Integer).build(),
                        year(false, TBL_BOOKS.dot(DBKey.BOOK_PUBLICATION__DATE)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_published_year, "yrp",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_PUBLISHED);
            }
            case DATE_PUBLISHED_MONTH: {
                // UTC. Formatting is done after fetching.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.PUB_MONTH, SqLiteDataType.Integer).build(),
                        month(false, TBL_BOOKS.dot(DBKey.BOOK_PUBLICATION__DATE)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_published_month, "mp",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_PUBLISHED);
            }


            case DATE_FIRST_PUBLICATION_YEAR: {
                // UTC. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.FIRST_PUB_YEAR, SqLiteDataType.Integer).build(),
                        year(false, TBL_BOOKS.dot(DBKey.FIRST_PUBLICATION__DATE)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_first_publication_year, "yfp",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_FIRST_PUBLICATION);
            }
            case DATE_FIRST_PUBLICATION_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.FIRST_PUB_MONTH,
                                           SqLiteDataType.Integer).build(),
                        month(false, TBL_BOOKS.dot(DBKey.FIRST_PUBLICATION__DATE)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_first_publication_month, "mfp",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_FIRST_PUBLICATION);
            }


            case DATE_ACQUIRED_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.ACQUIRED_YEAR, SqLiteDataType.Integer).build(),
                        year(true,
                             TBL_BOOKS.dot(DBKey.DATE_ACQUIRED),
                             TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_acquired_year, "yac",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_ACQUIRED);
            }
            case DATE_ACQUIRED_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.ACQUIRED_MONTH, SqLiteDataType.Integer).build(),
                        month(true,
                              TBL_BOOKS.dot(DBKey.DATE_ACQUIRED),
                              TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_acquired_month, "mac",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_ACQUIRED);
            }
            case DATE_ACQUIRED_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.ACQUIRED_DAY, SqLiteDataType.Integer).build(),
                        day(true,
                            TBL_BOOKS.dot(DBKey.DATE_ACQUIRED),
                            TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_acquired_day, "dac",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_ACQUIRED);
            }


            case DATE_ADDED_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.ADDED_YEAR, SqLiteDataType.Integer).build(),
                        year(true, TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_added_year, "ya", keyDomainExpression)
                        .addBaseDomain(BD_DATE_ADDED);
            }
            case DATE_ADDED_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.ADDED_DAY, SqLiteDataType.Integer).build(),
                        month(true, TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_added_month, "ma", keyDomainExpression)
                        .addBaseDomain(BD_DATE_ADDED);
            }
            case DATE_ADDED_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.ADDED_MONTH, SqLiteDataType.Integer).build(),
                        day(true, TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_added_day, "da", keyDomainExpression)
                        .addBaseDomain(BD_DATE_ADDED);
            }


            case DATE_LAST_UPDATE_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.LAST_UPD_YEAR, SqLiteDataType.Integer).build(),
                        year(true, TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_last_updated_year, "yu",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_LAST_UPDATED);
            }
            case DATE_LAST_UPDATE_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.LAST_UPD_MONTH, SqLiteDataType.Integer).build(),
                        month(true, TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_last_updated_month, "mu",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_LAST_UPDATED);
            }
            case DATE_LAST_UPDATE_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.LAST_UPD_DAY, SqLiteDataType.Integer).build(),
                        day(true, TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_last_updated_day, "du",
                                    keyDomainExpression)
                        .addBaseDomain(BD_DATE_LAST_UPDATED);
            }


            case DATE_READ_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.READ_YEAR, SqLiteDataType.Integer).build(),
                        year(true, TBL_BOOKS.dot(DBKey.READ_END__DATE)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_read_year, "yr", keyDomainExpression)
                        .addBaseDomain(BD_DATE_READ_END)
                        .addGroupDomain(BD_BOOK_IS_READ);
            }
            case DATE_READ_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.READ_MONTH, SqLiteDataType.Integer).build(),
                        month(true, TBL_BOOKS.dot(DBKey.READ_END__DATE)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_read_month, "mr", keyDomainExpression)
                        .addBaseDomain(BD_DATE_READ_END)
                        .addGroupDomain(BD_BOOK_IS_READ);
            }
            case DATE_READ_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomainExpression = new DomainExpression(
                        new Domain.Builder(BlgDBKey.READ_DAY, SqLiteDataType.Integer).build(),
                        day(true, TBL_BOOKS.dot(DBKey.READ_END__DATE)),
                        Sort.Desc);
                return new GroupKey(id, R.string.lbl_date_read_day, "dr", keyDomainExpression)
                        .addBaseDomain(BD_DATE_READ_END)
                        .addGroupDomain(BD_BOOK_IS_READ);
            }

            // The key domain for a book is not used but we define one
            // to prevents any potential null issues.
            case BOOK: {
                final DomainExpression keyDomainExpression =
                        new DomainExpression(DOM_TITLE, TBL_BOOKS, Sort.Unsorted);
                return new GroupKey(id, R.string.lbl_book, "b", keyDomainExpression);
            }
            default:
                throw new IllegalArgumentException(String.valueOf(id));
        }
    }

    /**
     * Get the {@link GroupKey} id.
     *
     * @return id
     */
    @Override
    @BooklistGroup.Id
    public int getId() {
        return groupKey.getId();
    }

    @VisibleForTesting
    @NonNull
    GroupKey getGroupKey() {
        return groupKey;
    }

    /**
     * Get the {@link GroupKey} displayable name.
     *
     * @param context Current context
     *
     * @return name
     */
    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return groupKey.getLabel(context);
    }

    /**
     * Create the expression for the key column: "/key=value".
     * A {@code null} value is reformatted as an empty string
     *
     * @return column expression
     */
    @Override
    @NonNull
    public String getNodeKeyExpression() {
        return groupKey.getNodeKeyExpression();
    }

    /**
     * Get the domain that contains the displayable data.
     * This is used to build the list table.
     * <p>
     * By default, this is the key domain.
     * <p>
     * Override as needed.
     *
     * @return domain to display
     */
    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return groupKey.getKeyDomainExpression();
    }

    /**
     * Get the domains represented by this group.
     * This is used to build the list table.
     * <p>
     * Override as needed.
     *
     * @return list
     */
    @Override
    @NonNull
    public List<DomainExpression> getGroupDomainExpressions() {
        return groupKey.getGroupDomainExpressions();
    }

    /**
     * Get the domains that this group adds to the lowest level (book).
     * This is used to build the list table.
     * <p>
     * Override as needed.
     *
     * @return list
     */
    @Override
    @NonNull
    public List<DomainExpression> getBaseDomainExpressions() {
        return groupKey.getBaseDomainExpressions();
    }

    /**
     * Get the domains for this group <strong>and its outer groups</strong>
     * This is used to build the triggers.
     *
     * @return list
     */
    @Override
    @NonNull
    public List<Domain> getAccumulatedDomains() {
        return Objects.requireNonNull(accumulatedDomains);
    }

    /**
     * Set the accumulated domains represented by this group <strong>and its outer groups</strong>.
     *
     * @param accumulatedDomains list of domains.
     */
    @Override
    public void setAccumulatedDomains(@NonNull final List<Domain> accumulatedDomains) {
        this.accumulatedDomains = accumulatedDomains;
    }

    /**
     * Preference UI support.
     * <p>
     * This method can be called multiple times.
     * Visibility of individual preferences should always be updated.
     *
     * @param screen  which hosts the prefs
     * @param visible whether to make the preferences visible
     */
    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {
        // no properties by default
    }

    /**
     * Set the visibility of the list of the passed preferences.
     * When one preference is visible, make the category visible.
     *
     * @param category to set
     * @param keys     to set visibility on
     * @param visible  to set
     */
    void setPreferenceVisibility(@NonNull final PreferenceCategory category,
                                 @NonNull final String[] keys,
                                 final boolean visible) {

        for (final String key : keys) {
            final Preference preference = category.findPreference(key);
            if (preference != null) {
                preference.setVisible(visible);
            }
        }

        int i = 0;
        while (i < category.getPreferenceCount()) {
            if (category.getPreference(i).isVisible()) {
                category.setVisible(true);
                return;
            }
            i++;
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BooklistGroupImpl that = (BooklistGroupImpl) o;
        return Objects.equals(groupKey, that.groupKey)
               && Objects.equals(accumulatedDomains, that.accumulatedDomains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupKey, accumulatedDomains);
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistGroup{"
               + "groupKey=" + groupKey
               + ", accumulatedDomains=" + accumulatedDomains
               + '}';
    }

}

