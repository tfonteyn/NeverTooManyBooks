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

package com.hardbacknutter.nevertoomanybooks.booklist.grouping;

import androidx.annotation.NonNull;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.utils.UniqueMap;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_REAL_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SERIES_NUMBER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_ADDED__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_TAG;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LAST_UPDATED__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TRANSLATION_ORIGINAL_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TAG;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PSEUDONYM_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAGS;

public final class GroupKeyFactory {
    /** Cache for the static GroupKey instances. */
    private static final Map<Integer, GroupKey> GROUP_KEYS = new UniqueMap<>();

    private static final String SUBSTR = "SUBSTR(";
    private static final String CASE = "CASE";
    private static final String _WHEN_ = " WHEN ";
    private static final String _THEN_ = " THEN ";
    private static final String _ELSE_ = " ELSE ";
    private static final String _END = " END";

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
            new DomainExpression(DOM_DATE_ADDED__UTC, Sort.Desc);
    private static final DomainExpression BD_DATE_LAST_UPDATED =
            new DomainExpression(DOM_LAST_UPDATED__UTC, Sort.Desc);
    private static final DomainExpression BD_DATE_ACQUIRED =
            new DomainExpression(DOM_BOOK_DATE_ACQUIRED, Sort.Desc);

    private GroupKeyFactory() {
    }

    /**
     * Create/get a GroupKey. We create the keys only once and keep them in a static cache map.
     *
     * @param id of group to create
     *
     * @return the GroupKey
     *
     * @throws IllegalArgumentException for an unknown group id
     */
    @NonNull
    static GroupKey getKey(@BooklistGroup.Id final int id)
            throws IllegalArgumentException {
        GroupKey key = GROUP_KEYS.get(id);
        if (key == null) {
            key = create(id);
            GROUP_KEYS.put(id, key);
        }
        return key;
    }

    /**
     * Get the name, i.e. the {@link DBKey} for the given {@link BooklistGroup} id.
     * Convenience method to hide the internals.
     *
     * @param id BooklistGroup
     *
     * @return DBKey
     *
     * @throws IllegalArgumentException for an unknown group id
     */
    public static String getKeyDomainName(@BooklistGroup.Id final int id)
            throws IllegalArgumentException {
        return getKey(id).getKeyDomainExpression().getDomain().getName();
    }

    /**
     * GroupKey factory constructor. Called <strong>ONCE</strong> for each group
     * during the lifetime of the app.
     *
     * @param id of group to create
     *
     * @return new GroupKey instance
     *
     * @throws IllegalArgumentException for an unknown group id
     */
    private static GroupKey create(@BooklistGroup.Id final int id)
            throws IllegalArgumentException {
        // NEWTHINGS: BooklistGroup
        switch (id) {
            case BooklistGroup.AUTHOR: {
                // We use the foreign ID to create the key-domain.
                // It is NOT used to display the data; instead we use
                // AuthorBooklistGroup#displayDomainExpression.
                // We do NOT sort by the display-domain; instead we use
                // AuthorBooklistGroup#sortingDomainExpression
                return new GroupKey(id, R.string.lbl_author, "a",
                                    new DomainExpression(DOM_FK_AUTHOR,
                                                         TBL_AUTHORS.dot(DBKey.PK_ID),
                                                         Sort.Unsorted))
                        .addGroupDomain(
                                new DomainExpression(DOM_FK_AUTHOR,
                                                     TBL_BOOK_AUTHOR,
                                                     Sort.Unsorted))

                        .addGroupDomain(
                                new DomainExpression(DOM_AUTHOR_IS_COMPLETE,
                                                     TBL_AUTHORS,
                                                     Sort.Unsorted))
                        .addGroupDomain(
                                new DomainExpression(DOM_AUTHOR_REAL_AUTHOR,
                                                     TBL_PSEUDONYM_AUTHOR,
                                                     Sort.Unsorted));
            }

            case BooklistGroup.SERIES: {
                // We use the foreign ID to create the key-domain.
                // It is NOT used to display the data; instead we use
                // SeriesBooklistGroup#displayDomainExpression.
                return new GroupKey(id, R.string.lbl_series, "s",
                                    new DomainExpression(DOM_FK_SERIES,
                                                         TBL_SERIES.dot(DBKey.PK_ID),
                                                         Sort.Unsorted))
                        .addGroupDomain(
                                // Sort by TITLE_OB
                                new DomainExpression(
                                        new Domain.Builder(
                                                BooklistGroup.BlgDBKey.SORT_SERIES_TITLE,
                                                SqLiteDataType.Text)
                                                .build(),
                                        TBL_SERIES.dot(DBKey.SERIES.TITLE_OB),
                                        Sort.Asc))
                        .addGroupDomain(
                                new DomainExpression(DOM_FK_SERIES,
                                                     TBL_BOOK_SERIES,
                                                     Sort.Unsorted))

                        .addGroupDomain(
                                new DomainExpression(DOM_SERIES_IS_COMPLETE,
                                                     TBL_SERIES,
                                                     Sort.Unsorted))
                        .addBaseDomain(
                                // The series number in the base data in sorted order.
                                // This field is NOT displayed.
                                // Casting it as a float allows for the possibility of 3.1,
                                // or even 3.1|Omnibus 3-10" as a series number.
                                new DomainExpression(
                                        new Domain.Builder(
                                                BooklistGroup.BlgDBKey.SORT_SERIES_NUM_FLOAT,
                                                SqLiteDataType.Real)
                                                .build(),
                                        "CAST("
                                        + TBL_BOOK_SERIES.dot(DBKey.SERIES.BOOK_SERIES_NUMBER)
                                        + " AS REAL)",
                                        Sort.Asc))
                        .addBaseDomain(
                                // The series number in the base data in sorted order.
                                // This field is displayed.
                                // Covers non-numeric data (where the above float would fail)
                                new DomainExpression(
                                        DOM_BOOK_SERIES_NUMBER,
                                        TBL_BOOK_SERIES.dot(DBKey.SERIES.BOOK_SERIES_NUMBER),
                                        Sort.Asc));
            }

            case BooklistGroup.PUBLISHER: {
                // We use the foreign ID to create the key-domain.
                // It is NOT used to display the data; instead we use
                // PublisherBooklistGroup#displayDomainExpression.
                return new GroupKey(id, R.string.lbl_publisher, "p",
                                    new DomainExpression(DOM_FK_PUBLISHER,
                                                         TBL_PUBLISHERS.dot(DBKey.PK_ID),
                                                         Sort.Unsorted))
                        .addGroupDomain(
                                // Sort by NAME_OB
                                new DomainExpression(
                                        new Domain.Builder(
                                                BooklistGroup.BlgDBKey.SORT_PUBLISHER,
                                                SqLiteDataType.Text)
                                                .build(),
                                        TBL_PUBLISHERS.dot(DBKey.PUBLISHER.NAME_OB),
                                        Sort.Asc))
                        .addGroupDomain(
                                new DomainExpression(DOM_FK_PUBLISHER,
                                                     TBL_BOOK_PUBLISHER,
                                                     Sort.Unsorted));
            }

            case BooklistGroup.BOOKSHELF: {
                // We use the foreign ID to create the key-domain.
                // It is NOT used to display the data; instead we use
                // BookshelfBooklistGroup#displayDomainExpression.
                return new GroupKey(id, R.string.lbl_bookshelf, "shelf",
                                    new DomainExpression(DOM_FK_BOOKSHELF,
                                                         TBL_BOOKSHELF.dot(DBKey.PK_ID),
                                                         Sort.Unsorted))
                        .addGroupDomain(
                                // Sort by Bookshelf name
                                new DomainExpression(
                                        new Domain.Builder(
                                                BooklistGroup.BlgDBKey.SORT_BOOKSHELF,
                                                SqLiteDataType.Text)
                                                .build(),
                                        TBL_BOOKSHELF.dot(DBKey.BOOKSHELF.NAME),
                                        Sort.Asc))
                        .addGroupDomain(
                                new DomainExpression(DOM_FK_BOOKSHELF,
                                                     TBL_BOOK_BOOKSHELF,
                                                     Sort.Unsorted));
            }

            case BooklistGroup.TAGS_GENRE: {
                // We use the foreign ID to create the key-domain.
                // It is NOT used to display the data; instead we use
                // TagsBooklistGroup#displayDomainExpression.
                return new GroupKey(id, R.string.lbl_tags, "tg",
                                    new DomainExpression(DOM_FK_TAG,
                                                         TBL_TAGS.dot(DBKey.PK_ID),
                                                         Sort.Unsorted))
                        .addGroupDomain(
                                // Sort by tag name
                                new DomainExpression(
                                        new Domain.Builder(
                                                BooklistGroup.BlgDBKey.SORT_TAG,
                                                SqLiteDataType.Text)
                                                .build(),
                                        TBL_TAGS.dot(DBKey.TAGS.TAG),
                                        Sort.Asc))
                        .addGroupDomain(
                                new DomainExpression(DOM_FK_TAG,
                                                     TBL_BOOK_TAG,
                                                     Sort.Unsorted));
            }
            case BooklistGroup.IDENTIFIER: {
                // We use the foreign ID to create the key-domain.
                // It is NOT used to display the data; instead we use
                // IdentifierBooklistGroup#displayDomainExpression.
                return new GroupKey(id, R.string.lbl_identifiers, "gkids",
                                    new DomainExpression(DOM_FK_IDENTIFIER,
                                                         TBL_IDENTIFIERS.dot(DBKey.PK_ID),
                                                         Sort.Unsorted))
                        .addGroupDomain(
                                // Sort by Identifier key
                                new DomainExpression(
                                        new Domain.Builder(
                                                BooklistGroup.BlgDBKey.SORT_IDENTIFIER,
                                                SqLiteDataType.Text)
                                                .build(),
                                        TBL_IDENTIFIERS.dot(DBKey.IDENTIFIERS.KEY),
                                        Sort.Asc))
                        .addGroupDomain(
                                new DomainExpression(DOM_FK_IDENTIFIER,
                                                     TBL_BOOK_IDENTIFIER,
                                                     Sort.Unsorted));
            }

            // Data without a linked table uses the display name as the key domain.
            case BooklistGroup.COLOR: {
                return new GroupKey(id, R.string.lbl_color, "col",
                                    new DomainExpression(DOM_BOOK_COLOR, TBL_BOOKS, Sort.Asc));
            }
            case BooklistGroup.FORMAT: {
                return new GroupKey(id, R.string.lbl_format, "fmt",
                                    new DomainExpression(DOM_BOOK_FORMAT, TBL_BOOKS, Sort.Asc));
            }
            case BooklistGroup.LANGUAGE: {
                // Formatting is done using the iso3 code after fetching.
                return new GroupKey(id, R.string.lbl_language, "lng",
                                    new DomainExpression(
                                            DOM_BOOK_LANGUAGE,
                                            TBL_BOOKS,
                                            Sort.Unsorted))
                        .addGroupDomain(
                                // Sorting:
                                // link with TBL_LANG_MAPPINGS to get the full name to SORT on.
                                // If no mapping is found, use the original book language field.
                                // The latter happens if the book language is not a "real" language.
                                new DomainExpression(
                                        new Domain.Builder(
                                                BooklistGroup.BlgDBKey.SORT_LANGUAGE,
                                                SqLiteDataType.Text)
                                                .build(),
                                        "COALESCE("
                                        + DBDefinitions.ALIAS_LANG_MAPPINGS_LANGUAGE
                                        + "." + DBKey.LANG_MAPPING.DISPLAY_NAME
                                        + ","
                                        + TBL_BOOKS.dot(DOM_BOOK_LANGUAGE)
                                        + ")",
                                        Sort.Asc));
            }
            case BooklistGroup.ORIGINAL_LANGUAGE: {
                // Formatting is done using the iso3 code after fetching.
                return new GroupKey(id, R.string.lbl_original_language, "lngor",
                                    new DomainExpression(
                                            DOM_TRANSLATION_ORIGINAL_LANGUAGE,
                                            TBL_BOOKS,
                                            Sort.Unsorted))
                        .addGroupDomain(
                                // Sorting:
                                // link with TBL_LANG_MAPPINGS to get the full name to SORT on.
                                // If no mapping is found, use the original book language field.
                                // The latter happens if the book language is not a "real" language.
                                new DomainExpression(
                                        new Domain.Builder(
                                                BooklistGroup.BlgDBKey
                                                        .SORT_TRANSLATION_ORIGINAL_LANGUAGE,
                                                SqLiteDataType.Text)
                                                .build(),
                                        "COALESCE("
                                        + DBDefinitions.ALIAS_LANG_MAPPINGS_ORIGINAL_LANGUAGE
                                        + "." + DBKey.LANG_MAPPING.DISPLAY_NAME
                                        + ","
                                        + TBL_BOOKS.dot(DOM_TRANSLATION_ORIGINAL_LANGUAGE)
                                        + ")",
                                        Sort.Asc));
            }
            case BooklistGroup.LOCATION: {
                return new GroupKey(id, R.string.lbl_location, "loc",
                                    new DomainExpression(DOM_BOOK_LOCATION, TBL_BOOKS, Sort.Asc));
            }
            case BooklistGroup.CONDITION: {
                return new GroupKey(id, R.string.lbl_condition, "bk_cnd",
                                    new DomainExpression(DOM_BOOK_CONDITION, TBL_BOOKS, Sort.Desc));
            }
            case BooklistGroup.RATING: {
                // Formatting is done after fetching; sort with highest rated first.
                // The data is cast to an integer as a precaution/paranoia,
                // but also to avoid having to post-process every row in code.
                return new GroupKey(id, R.string.lbl_rating, "rt",
                                    new DomainExpression(
                                            DOM_BOOK_RATING,
                                            "CAST(" + TBL_BOOKS.dot(DBKey.RATING) + " AS INTEGER)",
                                            Sort.Desc));
            }
            case BooklistGroup.LENDING: {
                // This will be a LEFT OUTER JOIN, so coerce missing rows to ''
                return new GroupKey(id, R.string.lbl_lend_out, "l",
                                    new DomainExpression(
                                            DOM_LOANEE,
                                            "COALESCE("
                                            + TBL_BOOK_LOANEE.dot(DBKey.LOANEE_NAME)
                                            + ",'')",
                                            Sort.Asc));
            }

            // the others here below are custom key domains
            case BooklistGroup.READ_STATUS: {
                // Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_group_read_and_unread, "r",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.READ_STATUS,
                                                    SqLiteDataType.Text)
                                                    .notNull()
                                                    .build(),
                                            // Ww could also test for books where the start/end
                                            // dates are set and the flag is NOT set...
                                            // But that situation should never occur anyhow... flw
                                            CASE
                                            + _WHEN_ + ReadStatus.W_READ
                                            + _THEN_ + ReadStatus.Read.getId()
                                            + _WHEN_ + ReadStatus.W_READING
                                            + _THEN_ + ReadStatus.Reading.getId()
                                            + _ELSE_ + ReadStatus.Unread.getId()
                                            + _END,
                                            Sort.Asc));
            }

            case BooklistGroup.AUTHOR_FAMILY_NAME_1ST_CHAR: {
                // Uses the OrderBy column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_group_1st_char_author_family_name, "af1",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.AUTHOR_FAMILY_NAME_1CHAR,
                                                    SqLiteDataType.Text)
                                                    .notNull()
                                                    .build(),
                                            "UPPER(SUBSTR("
                                            + TBL_AUTHORS.dot(DBKey.AUTHOR.FAMILY_NAME_OB)
                                            + ",1,1))",
                                            Sort.Asc));
            }
            case BooklistGroup.SERIES_TITLE_1ST_CHAR: {
                // Uses the OrderBy column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_group_1st_char_series_title, "st1",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.SERIES_TITLE_1CHAR,
                                                    SqLiteDataType.Text)
                                                    .notNull()
                                                    .build(),
                                            "UPPER(SUBSTR("
                                            + TBL_SERIES.dot(DBKey.SERIES.TITLE_OB)
                                            + ",1,1))",
                                            Sort.Asc));
            }
            case BooklistGroup.PUBLISHER_NAME_1ST_CHAR: {
                // Uses the OB column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_group_1st_char_publisher_name, "p1",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.PUBLISHER_NAME_1CHAR,
                                                    SqLiteDataType.Text)
                                                    .notNull()
                                                    .build(),
                                            "UPPER(SUBSTR("
                                            + TBL_PUBLISHERS.dot(DBKey.PUBLISHER.NAME_OB)
                                            + ",1,1))",
                                            Sort.Asc));
            }
            case BooklistGroup.BOOK_TITLE_1ST_CHAR: {
                // Uses the OB column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_group_1st_char_book_title, "bt1",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.BOOK_TITLE_1CHAR,
                                                    SqLiteDataType.Text)
                                                    .notNull()
                                                    .build(),
                                            "UPPER(SUBSTR("
                                            + TBL_BOOKS.dot(DBKey.TITLE_OB)
                                            + ",1,1))",
                                            Sort.Asc));
            }

            case BooklistGroup.DATE_PUBLISHED_YEAR: {
                // UTC. Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_date_published_year, "yrp",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.PUB_YEAR,
                                                    SqLiteDataType.Integer).build(),
                                            year(false,
                                                 TBL_BOOKS.dot(DBKey.PUBLICATION_DATE)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_PUBLISHED);
            }
            case BooklistGroup.DATE_PUBLISHED_MONTH: {
                // UTC. Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_date_published_month, "mp",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.PUB_MONTH,
                                                    SqLiteDataType.Integer).build(),
                                            month(false,
                                                  TBL_BOOKS.dot(DBKey.PUBLICATION_DATE)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_PUBLISHED);
            }


            case BooklistGroup.DATE_FIRST_PUBLICATION_YEAR: {
                // UTC. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_first_publication_year, "yfp",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.FIRST_PUB_YEAR,
                                                    SqLiteDataType.Integer).build(),
                                            year(false,
                                                 TBL_BOOKS.dot(DBKey.FIRST_PUBLICATION_DATE)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_FIRST_PUBLICATION);
            }
            case BooklistGroup.DATE_FIRST_PUBLICATION_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_date_first_publication_month, "mfp",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.FIRST_PUB_MONTH,
                                                    SqLiteDataType.Integer).build(),
                                            month(false,
                                                  TBL_BOOKS.dot(DBKey.FIRST_PUBLICATION_DATE)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_FIRST_PUBLICATION);
            }


            case BooklistGroup.DATE_ACQUIRED_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_acquired_year, "yac",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.ACQUIRED_YEAR,
                                                    SqLiteDataType.Integer).build(),
                                            year(true,
                                                 TBL_BOOKS.dot(DBKey.DATE_ACQUIRED),
                                                 TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_ACQUIRED);
            }
            case BooklistGroup.DATE_ACQUIRED_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_date_acquired_month, "mac",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.ACQUIRED_MONTH,
                                                    SqLiteDataType.Integer).build(),
                                            month(true,
                                                  TBL_BOOKS.dot(DBKey.DATE_ACQUIRED),
                                                  TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_ACQUIRED);
            }
            case BooklistGroup.DATE_ACQUIRED_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_acquired_day, "dac",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.ACQUIRED_DAY,
                                                    SqLiteDataType.Integer).build(),
                                            day(true,
                                                TBL_BOOKS.dot(DBKey.DATE_ACQUIRED),
                                                TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_ACQUIRED);
            }


            case BooklistGroup.DATE_ADDED_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_added_year, "ya",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.ADDED_YEAR,
                                                    SqLiteDataType.Integer).build(),
                                            year(true,
                                                 TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_ADDED);
            }
            case BooklistGroup.DATE_ADDED_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_date_added_month, "ma",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.ADDED_DAY,
                                                    SqLiteDataType.Integer).build(),
                                            month(true,
                                                  TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_ADDED);
            }
            case BooklistGroup.DATE_ADDED_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_added_day, "da",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.ADDED_MONTH,
                                                    SqLiteDataType.Integer).build(),
                                            day(true,
                                                TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_ADDED);
            }


            case BooklistGroup.DATE_LAST_UPDATE_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_last_updated_year, "yu",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.LAST_UPD_YEAR,
                                                    SqLiteDataType.Integer).build(),
                                            year(true,
                                                 TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_LAST_UPDATED);
            }
            case BooklistGroup.DATE_LAST_UPDATE_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_date_last_updated_month, "mu",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.LAST_UPD_MONTH,
                                                    SqLiteDataType.Integer).build(),
                                            month(true,
                                                  TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_LAST_UPDATED);
            }
            case BooklistGroup.DATE_LAST_UPDATE_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_last_updated_day, "du",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.LAST_UPD_DAY,
                                                    SqLiteDataType.Integer).build(),
                                            day(true,
                                                TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_LAST_UPDATED);
            }


            case BooklistGroup.DATE_READ_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_read_year, "yr",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.READ_YEAR,
                                                    SqLiteDataType.Integer).build(),
                                            year(true,
                                                 TBL_BOOKS.dot(DBKey.READ_END__DATE)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_READ_END)
                        .addGroupDomain(BD_BOOK_IS_READ);
            }
            case BooklistGroup.DATE_READ_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(id, R.string.lbl_date_read_month, "mr",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.READ_MONTH,
                                                    SqLiteDataType.Integer).build(),
                                            month(true,
                                                  TBL_BOOKS.dot(DBKey.READ_END__DATE)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_READ_END)
                        .addGroupDomain(BD_BOOK_IS_READ);
            }
            case BooklistGroup.DATE_READ_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(id, R.string.lbl_date_read_day, "dr",
                                    new DomainExpression(
                                            new Domain.Builder(
                                                    BooklistGroup.BlgDBKey.READ_DAY,
                                                    SqLiteDataType.Integer).build(),
                                            day(true,
                                                TBL_BOOKS.dot(DBKey.READ_END__DATE)),
                                            Sort.Desc))
                        .addBaseDomain(BD_DATE_READ_END)
                        .addGroupDomain(BD_BOOK_IS_READ);
            }

            // The key domain for a book is not used but we define one
            // to prevents any potential null issues.
            case BooklistGroup.BOOK: {
                return new GroupKey(id, R.string.lbl_book, "b",
                                    new DomainExpression(DOM_TITLE, TBL_BOOKS, Sort.Unsorted));
            }
            default:
                throw new IllegalArgumentException(String.valueOf(id));
        }
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
}
