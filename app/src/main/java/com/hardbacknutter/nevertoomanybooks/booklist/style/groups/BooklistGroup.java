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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;

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
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * Class representing a single level in the booklist hierarchy.
 * <p>
 * There is a one-to-one mapping with a {@link GroupKey},
 * the latter providing a lightweight (final) object without user preferences.
 * The BooklistGroup encapsulates the {@link GroupKey}, adding user/temp stuff.
 * <p>
 * <p>
 * How to add a new Group:
 * <ol>
 *      <li>add it to {@link GroupKey} and update {@link #GROUP_KEY_MAX}</li>
 *      <li>if necessary add new domain to {@link DBDefinitions}</li>
 *      <li>modify {@link BooksOnBookshelfViewModel}
 *          to add the necessary grouped/sorted domains</li>
 *      <li>modify {@link BooklistAdapter#onCreateViewHolder}; If it is just a string field it can
 *          use a {@link BooklistAdapter}.GenericStringHolder otherwise add a new holder</li>
 * </ol>
 */
public class BooklistGroup {

    /**
     * The ID's for the groups. <strong>Never change these</strong>,
     * they get stored in prefs and styles.
     * <p>
     * Also: the code relies on BOOK being == 0
     */
    public static final int BOOK = 0;
    /** {@link AuthorBooklistGroup}. */
    public static final int AUTHOR = 1;
    /** {@link SeriesBooklistGroup}. */
    public static final int SERIES = 2;
    public static final int GENRE = 3;
    /** {@link PublisherBooklistGroup}. */
    public static final int PUBLISHER = 4;
    public static final int READ_STATUS = 5;
    public static final int LENDING = 6;
    public static final int DATE_PUBLISHED_YEAR = 7;
    public static final int DATE_PUBLISHED_MONTH = 8;
    public static final int BOOK_TITLE_1ST_LETTER = 9;
    public static final int DATE_ADDED_YEAR = 10;
    public static final int DATE_ADDED_MONTH = 11;
    public static final int DATE_ADDED_DAY = 12;
    public static final int FORMAT = 13;
    public static final int DATE_READ_YEAR = 14;
    public static final int DATE_READ_MONTH = 15;
    public static final int DATE_READ_DAY = 16;
    public static final int LOCATION = 17;
    public static final int LANGUAGE = 18;
    public static final int DATE_LAST_UPDATE_YEAR = 19;
    public static final int DATE_LAST_UPDATE_MONTH = 20;
    public static final int DATE_LAST_UPDATE_DAY = 21;
    public static final int RATING = 22;
    /** {@link BookshelfBooklistGroup}. */
    public static final int BOOKSHELF = 23;
    public static final int DATE_ACQUIRED_YEAR = 24;
    public static final int DATE_ACQUIRED_MONTH = 25;
    public static final int DATE_ACQUIRED_DAY = 26;
    public static final int DATE_FIRST_PUBLICATION_YEAR = 27;
    public static final int DATE_FIRST_PUBLICATION_MONTH = 28;
    public static final int COLOR = 29;
    @SuppressWarnings("WeakerAccess")
    public static final int SERIES_TITLE_1ST_LETTER = 30;
    public static final int CONDITION = 31;

    /**
     * NEWTHINGS: BooklistGroup.KEY
     * The highest valid index of GroupKey - ALWAYS to be updated after adding a group key.
     */
    @VisibleForTesting
    public static final int GROUP_KEY_MAX = 31;

    // Date based groups have to sort on the full date for cases
    // where we don't have all separate year,month,day fields.
    private static final DomainExpression DATE_PUBLISHED =
            new DomainExpression(DOM_BOOK_DATE_PUBLISHED, DomainExpression.SORT_DESC);
    private static final DomainExpression DATE_FIRST_PUBLICATION =
            new DomainExpression(DOM_DATE_FIRST_PUBLICATION, DomainExpression.SORT_DESC);
    private static final DomainExpression BOOK_IS_READ =
            new DomainExpression(DOM_BOOK_READ, DomainExpression.SORT_DESC);
    private static final DomainExpression DATE_READ_END =
            new DomainExpression(DOM_BOOK_DATE_READ_END, DomainExpression.SORT_DESC);
    private static final DomainExpression DATE_ADDED =
            new DomainExpression(DOM_UTC_ADDED, DomainExpression.SORT_DESC);
    private static final DomainExpression DATE_LAST_UPDATED =
            new DomainExpression(DOM_UTC_LAST_UPDATED, DomainExpression.SORT_DESC);
    private static final DomainExpression DATE_ACQUIRED =
            new DomainExpression(DOM_BOOK_DATE_ACQUIRED, DomainExpression.SORT_DESC);

    private static final String CASE_WHEN_ = "CASE WHEN ";
    private static final String _WHEN_ = " WHEN ";
    private static final String _ELSE_ = " ELSE ";
    private static final String _END = " END";
    /** Cache for the static GroupKey instances. */
    private static final Map<Integer, GroupKey> GROUP_KEYS = new UniqueMap<>();
    /** The {@link StylePersistenceLayer} to use. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    final StylePersistenceLayer mPersistenceLayer;
    @NonNull
    final ListStyle mStyle;
    /** Flag indicating we should use the persistence store. */
    final boolean mPersisted;
    /** The type of row/group we represent, see {@link GroupKey}. */
    @Id
    private final int mId;
    /** The underlying group key object. */
    @NonNull
    private final GroupKey mGroupKey;
    /**
     * The domains represented by this group.
     * Set at <strong>runtime</strong> by the BooklistBuilder
     * based on current group <strong>and its outer groups</strong>
     */
    @Nullable
    private ArrayList<Domain> mAccumulatedDomains;

    /**
     * Constructor.
     *
     * @param id           of group to create
     * @param isPersistent flag
     * @param style        Style reference.
     */
    BooklistGroup(@Id final int id,
                  final boolean isPersistent,
                  @NonNull final ListStyle style) {
        mId = id;
        mGroupKey = initGroupKey();

        mPersisted = isPersistent;
        mStyle = style;
        mPersistenceLayer = mStyle.getPersistenceLayer();
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     * @param group        to copy from
     */
    public BooklistGroup(final boolean isPersistent,
                         @NonNull final ListStyle style,
                         @NonNull final BooklistGroup group) {
        mId = group.mId;
        mGroupKey = group.mGroupKey;

        mPersisted = isPersistent;
        mStyle = style;
        mPersistenceLayer = mStyle.getPersistenceLayer();
    }

    /**
     * Create a new BooklistGroup of the specified id, creating any specific
     * subclasses as necessary.
     *
     * @param id           of group to create
     * @param isPersistent flag
     * @param style        Style reference.
     *
     * @return instance
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    public static BooklistGroup newInstance(@Id final int id,
                                            final boolean isPersistent,
                                            @NonNull final ListStyle style) {
        switch (id) {
            case AUTHOR:
                return new AuthorBooklistGroup(isPersistent, style);
            case SERIES:
                return new SeriesBooklistGroup(isPersistent, style);
            case PUBLISHER:
                return new PublisherBooklistGroup(isPersistent, style);
            case BOOKSHELF:
                return new BookshelfBooklistGroup(isPersistent, style);

            default:
                return new BooklistGroup(id, isPersistent, style);
        }
    }

    /**
     * Get a list of BooklistGroup's, one for each defined {@link GroupKey}'s.
     *
     * @param style Style reference.
     *
     * @return the list
     */
    @SuppressLint("WrongConstant")
    @NonNull
    public static List<BooklistGroup> getAllGroups(@NonNull final ListStyle style) {
        final List<BooklistGroup> list = new ArrayList<>();
        // Get the set of all valid <strong>Group</strong> values.
        // In other words: all valid groups, <strong>except</strong> the BOOK.
        for (int id = 1; id <= GROUP_KEY_MAX; id++) {
            list.add(newInstance(id, style.isUserDefined(), style));
        }
        return list;
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
        return CASE_WHEN_ + fieldSpec + " GLOB '*-*-* *' "
               + " THEN datetime(" + fieldSpec + ", 'localtime')"
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
     * @param fieldSpec fully qualified field name
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private static String year(@NonNull String fieldSpec,
                               final boolean toLocal) {

        if (toLocal) {
            fieldSpec = localDateTimeExpression(fieldSpec);
        }
        return CASE_WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",1,4)"
               // invalid
               + " ELSE ''"
               + _END;
    }

    /**
     * Create a GLOB expression to get the 'month' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit.
     * We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private static String month(@NonNull String fieldSpec,
                                final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateTimeExpression(fieldSpec);
        }
        // YYYY-MM or YYYY-M
        return CASE_WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",6,2)"
               + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",6,1)"
               // invalid
               + " ELSE ''"
               + _END;
    }

    /**
     * Create a GLOB expression to get the 'day' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit,
     * and then by '-' and 1 or two digits.
     * We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private static String day(@NonNull String fieldSpec,
                              @SuppressWarnings("SameParameterValue") final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateTimeExpression(fieldSpec);
        }
        // Look for 4 leading numbers followed by 2 or 1 digit then another 2 or 1 digit.
        // YYYY-MM-DD or YYYY-M-DD or YYYY-MM-D or YYYY-M-D
        return CASE_WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",9,2)"
               //
               + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9][0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",8,2)"
               //
               + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",9,1)"
               //
               + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",8,1)"
               // invalid
               + " ELSE ''"
               + _END;
    }

    /**
     * Create/get a GroupKey. We create the keys only once and keep them in a static cache map.
     * This must be called <strong>after</strong> construction, i.e. from {@link #newInstance}.
     */
    @NonNull
    private GroupKey initGroupKey() {
        GroupKey groupKey = GROUP_KEYS.get(mId);
        if (groupKey == null) {
            groupKey = createGroupKey();
            GROUP_KEYS.put(mId, groupKey);
        }
        return groupKey;
    }

    /**
     * GroupKey factory constructor. Called <strong>ONCE</strong> for each group
     * during the lifetime of the app.
     * <p>
     * Specialized classes override this method. e.g. {@link AuthorBooklistGroup}.
     *
     * @return new GroupKey instance
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    public GroupKey createGroupKey() {
        // NEWTHINGS: BooklistGroup.KEY
        switch (mId) {
            // Data without a linked table uses the display name as the key domain.
            case COLOR: {
                return new GroupKey(R.string.lbl_color, "col",
                                    DOM_BOOK_COLOR, TBL_BOOKS.dot(DBKey.KEY_COLOR),
                                    DomainExpression.SORT_ASC);
            }
            case FORMAT: {
                return new GroupKey(R.string.lbl_format, "fmt",
                                    DOM_BOOK_FORMAT, TBL_BOOKS.dot(DBKey.KEY_FORMAT),
                                    DomainExpression.SORT_ASC);
            }
            case GENRE: {
                return new GroupKey(R.string.lbl_genre, "g",
                                    DOM_BOOK_GENRE, TBL_BOOKS.dot(DBKey.KEY_GENRE),
                                    DomainExpression.SORT_ASC);
            }
            case LANGUAGE: {
                // Formatting is done after fetching.
                return new GroupKey(R.string.lbl_language, "lng",
                                    DOM_BOOK_LANGUAGE, TBL_BOOKS.dot(DBKey.KEY_LANGUAGE),
                                    DomainExpression.SORT_ASC);
            }
            case LOCATION: {
                return new GroupKey(R.string.lbl_location, "loc",
                                    DOM_BOOK_LOCATION, TBL_BOOKS.dot(DBKey.KEY_LOCATION),
                                    DomainExpression.SORT_ASC);
            }
            case CONDITION: {
                return new GroupKey(R.string.lbl_condition, "bk_cnd",
                                    DOM_BOOK_CONDITION, TBL_BOOKS.dot(DBKey.KEY_BOOK_CONDITION),
                                    DomainExpression.SORT_DESC);
            }
            case RATING: {
                // Formatting is done after fetching
                // Sort with highest rated first
                // The data is cast to an integer as a precaution.
                return new GroupKey(R.string.lbl_rating, "rt",
                                    DOM_BOOK_RATING,
                                    "CAST(" + TBL_BOOKS.dot(DBKey.KEY_RATING) + " AS INTEGER)",
                                    DomainExpression.SORT_DESC);
            }

            // the others here below are custom key domains
            case LENDING: {
                return new GroupKey(R.string.lbl_lend_out, "l",
                                    DOM_LOANEE,
                                    "COALESCE(" + TBL_BOOK_LOANEE.dot(DBKey.KEY_LOANEE)
                                    + ",'')",
                                    DomainExpression.SORT_ASC);
            }
            case READ_STATUS: {
                // Formatting is done after fetching.
                return new GroupKey(R.string.lbl_read_and_unread, "r",
                                    new Domain.Builder("blg_rd_sts", ColumnInfo.TYPE_TEXT)
                                            .notNull()
                                            .build(),
                                    TBL_BOOKS.dot(DBKey.BOOL_READ),
                                    DomainExpression.SORT_ASC);
            }
            case BOOK_TITLE_1ST_LETTER: {
                // Uses the OrderBy column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                return new GroupKey(R.string.style_builtin_first_letter_book_title, "t",
                                    new Domain.Builder("blg_tit_let", ColumnInfo.TYPE_TEXT)
                                            .notNull()
                                            .build(),
                                    "upper(SUBSTR(" + TBL_BOOKS.dot(DBKey.KEY_TITLE_OB)
                                    + ",1,1))",
                                    DomainExpression.SORT_ASC);
            }
            case SERIES_TITLE_1ST_LETTER: {
                // Uses the OrderBy column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                return new GroupKey(R.string.style_builtin_first_letter_series_title, "st",
                                    new Domain.Builder("blg_ser_tit_let", ColumnInfo.TYPE_TEXT)
                                            .notNull()
                                            .build(),
                                    "upper(SUBSTR(" + TBL_SERIES.dot(DBKey.KEY_SERIES_TITLE_OB)
                                    + ",1,1))",
                                    DomainExpression.SORT_ASC);
            }

            case DATE_PUBLISHED_YEAR: {
                // UTC. Formatting is done after fetching.
                return new GroupKey(R.string.lbl_publication_year, "yrp",
                                    new Domain.Builder("blg_pub_y", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    year(TBL_BOOKS.dot(DBKey.DATE_BOOK_PUBLICATION), false),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_PUBLISHED);
            }
            case DATE_PUBLISHED_MONTH: {
                // UTC. Formatting is done after fetching.
                return new GroupKey(R.string.lbl_publication_month, "mp",
                                    new Domain.Builder("blg_pub_m", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    month(TBL_BOOKS.dot(DBKey.DATE_BOOK_PUBLICATION), false),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_PUBLISHED);
            }

            case DATE_FIRST_PUBLICATION_YEAR: {
                // UTC. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_first_pub_year, "yfp",
                                    new Domain.Builder("blg_1pub_y", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    year(TBL_BOOKS.dot(DBKey.DATE_FIRST_PUBLICATION), false),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_FIRST_PUBLICATION);
            }
            case DATE_FIRST_PUBLICATION_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(R.string.lbl_first_pub_month, "mfp",
                                    new Domain.Builder("blg_1pub_m", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    month(TBL_BOOKS.dot(DBKey.DATE_FIRST_PUBLICATION), false),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_FIRST_PUBLICATION);
            }

            case DATE_ACQUIRED_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_date_acquired_year, "yac",
                                    new Domain.Builder("blg_acq_y", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    year(TBL_BOOKS.dot(DBKey.DATE_ACQUIRED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_ACQUIRED);
            }
            case DATE_ACQUIRED_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(R.string.lbl_date_acquired_month, "mac",
                                    new Domain.Builder("blg_acq_m", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    month(TBL_BOOKS.dot(DBKey.DATE_ACQUIRED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_ACQUIRED);
            }
            case DATE_ACQUIRED_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_date_acquired_day, "dac",
                                    new Domain.Builder("blg_acq_d", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    day(TBL_BOOKS.dot(DBKey.DATE_ACQUIRED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_ACQUIRED);
            }


            case DATE_ADDED_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_added_year, "ya",
                                    new Domain.Builder("blg_add_y", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    year(TBL_BOOKS.dot(DBKey.UTC_DATE_ADDED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_ADDED);
            }
            case DATE_ADDED_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(R.string.lbl_added_month, "ma",
                                    new Domain.Builder("blg_add_m", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    month(TBL_BOOKS.dot(DBKey.UTC_DATE_ADDED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_ADDED);
            }
            case DATE_ADDED_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_added_day, "da",
                                    new Domain.Builder("blg_add_d", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    day(TBL_BOOKS.dot(DBKey.UTC_DATE_ADDED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_ADDED);
            }

            case DATE_LAST_UPDATE_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_update_year, "yu",
                                    new Domain.Builder("blg_upd_y", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    year(TBL_BOOKS.dot(DBKey.UTC_DATE_LAST_UPDATED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_LAST_UPDATED);
            }
            case DATE_LAST_UPDATE_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(R.string.lbl_update_month, "mu",
                                    new Domain.Builder("blg_upd_m", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    month(TBL_BOOKS.dot(DBKey.UTC_DATE_LAST_UPDATED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_LAST_UPDATED);
            }
            case DATE_LAST_UPDATE_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_update_day, "du",
                                    new Domain.Builder("blg_upd_d", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    day(TBL_BOOKS.dot(DBKey.UTC_DATE_LAST_UPDATED), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_LAST_UPDATED);
            }

            case DATE_READ_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_read_year, "yr",
                                    new Domain.Builder("blg_rd_y", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    year(TBL_BOOKS.dot(DBKey.DATE_READ_END), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_READ_END)
                        .addGroupDomain(BOOK_IS_READ);
            }
            case DATE_READ_MONTH: {
                // Local for the user. Formatting is done after fetching.
                return new GroupKey(R.string.lbl_read_month, "mr",
                                    new Domain.Builder("blg_rd_m", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    month(TBL_BOOKS.dot(DBKey.DATE_READ_END), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_READ_END)
                        .addGroupDomain(BOOK_IS_READ);
            }
            case DATE_READ_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                return new GroupKey(R.string.lbl_read_day, "dr",
                                    new Domain.Builder("blg_rd_d", ColumnInfo.TYPE_INTEGER)
                                            .build(),
                                    day(TBL_BOOKS.dot(DBKey.DATE_READ_END), true),
                                    DomainExpression.SORT_DESC)
                        .addBaseDomain(DATE_READ_END)
                        .addGroupDomain(BOOK_IS_READ);
            }

            // The key domain for a book is not used for now, but using the title makes sense.
            // This prevents a potential null issue
            case BOOK: {
                return new GroupKey(R.string.lbl_book, "b",
                                    DOM_TITLE, TBL_BOOKS.dot(DBKey.KEY_TITLE),
                                    DomainExpression.SORT_UNSORTED);
            }
            default:
                throw new IllegalArgumentException(String.valueOf(mId));
        }
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

    /**
     * Get the {@link GroupKey} id.
     *
     * @return id
     */
    @Id
    public int getId() {
        return mId;
    }

    @VisibleForTesting
    @NonNull
    public GroupKey getGroupKey() {
        return mGroupKey;
    }

    /**
     * Get the {@link GroupKey} displayable name.
     *
     * @param context Current context
     *
     * @return name
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return mGroupKey.getLabel(context);
    }

    /**
     * Create the expression for the key column: "/key=value".
     * A {@code null} value is reformatted as an empty string
     *
     * @return column expression
     */
    @NonNull
    public String getNodeKeyExpression() {
        return mGroupKey.getNodeKeyExpression();
    }

    /**
     * Get the domain that contains the displayable data.
     * This is used to build the list table.
     * <p>
     * By default, this is the key domain.
     * Override as needed in subclasses.
     *
     * @return domain to display
     */
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return mGroupKey.getKeyDomainExpression();
    }

    /**
     * Get the domains represented by this group.
     * This is used to build the list table.
     * <p>
     * Override as needed.
     *
     * @return list
     */
    @NonNull
    public ArrayList<DomainExpression> getGroupDomainExpressions() {
        return mGroupKey.getGroupDomainExpressions();
    }

    /**
     * Get the domains that this group adds to the lowest level (book).
     * This is used to build the list table.
     * <p>
     * Override as needed.
     *
     * @return list
     */
    @NonNull
    public ArrayList<DomainExpression> getBaseDomainExpressions() {
        return mGroupKey.getBaseDomainExpressions();
    }

    /**
     * Get the domains for this group <strong>and its outer groups</strong>
     * This is used to build the triggers.
     *
     * @return list
     */
    @NonNull
    public ArrayList<Domain> getAccumulatedDomains() {
        return Objects.requireNonNull(mAccumulatedDomains);
    }

    /**
     * Set the accumulated domains represented by this group <strong>and its outer groups</strong>.
     *
     * @param accumulatedDomains list of domains.
     */
    public void setAccumulatedDomains(@NonNull final ArrayList<Domain> accumulatedDomains) {
        mAccumulatedDomains = accumulatedDomains;
    }

    /**
     * Get the Preference objects that this group will contribute to a Style.
     *
     * @return a map with the prefs
     */
    @NonNull
    public Map<String, PPref<?>> getRawPreferences() {
        return new LinkedHashMap<>();
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
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BooklistGroup that = (BooklistGroup) o;
        // mPersisted/mStyle is NOT part of the values to compare!
        return mId == that.mId
               && Objects.equals(mGroupKey, that.mGroupKey)
               && Objects.equals(mAccumulatedDomains, that.mAccumulatedDomains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mGroupKey, mAccumulatedDomains);
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistGroup{"
               + "mId=" + mId
               + ", mStyle=" + mStyle.getUuid()
               + ", mPersisted=" + mPersisted
               + ", mGroupKey=" + mGroupKey
               + ", mAccumulatedDomains=" + mAccumulatedDomains
               + '}';
    }

    @IntDef({BOOK,

             AUTHOR,
             SERIES,
             PUBLISHER,
             BOOKSHELF,
             READ_STATUS,

             LENDING,

             BOOK_TITLE_1ST_LETTER,
             SERIES_TITLE_1ST_LETTER,

             GENRE,
             FORMAT,
             COLOR,
             LOCATION,
             LANGUAGE,
             RATING,

             CONDITION,

             DATE_PUBLISHED_YEAR,
             DATE_PUBLISHED_MONTH,
             DATE_FIRST_PUBLICATION_YEAR,
             DATE_FIRST_PUBLICATION_MONTH,

             DATE_READ_YEAR,
             DATE_READ_MONTH,
             DATE_READ_DAY,

             DATE_ADDED_YEAR,
             DATE_ADDED_MONTH,
             DATE_ADDED_DAY,

             DATE_LAST_UPDATE_YEAR,
             DATE_LAST_UPDATE_MONTH,
             DATE_LAST_UPDATE_DAY,

             DATE_ACQUIRED_YEAR,
             DATE_ACQUIRED_MONTH,
             DATE_ACQUIRED_DAY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Id {

    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public static final class GroupKey {

        /** User displayable label resource id. */
        @StringRes
        private final int mLabelId;
        /** Unique keyPrefix used to represent a key in the hierarchy. */
        @NonNull
        private final String mKeyPrefix;

        /** They key domain, which is by default also the display-domain. */
        @NonNull
        private final DomainExpression mKeyDomain;

        /**
         * Aside of the main display domain, a group can have extra domains that should
         * be fetched/sorted.
         */
        @NonNull
        private final ArrayList<DomainExpression> mGroupDomains = new ArrayList<>();

        /**
         * A group can add domains to the lowest level (the book).
         */
        @NonNull
        private final ArrayList<DomainExpression> mBaseDomains = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param labelId    User displayable label resource id
         * @param keyPrefix  the key prefix (as short as possible) to use for the compound key
         * @param keyDomain  the domain to get the actual data from the Cursor
         * @param expression sql column expression for constructing the Cursor
         * @param sorted     optional sorting
         */
        GroupKey(@StringRes final int labelId,
                 @NonNull final String keyPrefix,
                 @NonNull final Domain keyDomain,
                 @NonNull final String expression,
                 @DomainExpression.Sorting final int sorted) {
            mLabelId = labelId;
            mKeyPrefix = keyPrefix;
            mKeyDomain = new DomainExpression(keyDomain, expression, sorted);
        }

        @NonNull
        String getLabel(@NonNull final Context context) {
            return context.getString(mLabelId);
        }

        @NonNull
        GroupKey addGroupDomain(@NonNull final DomainExpression domainExpression) {
            // this is a static setup. We don't check on developer mistakenly adding duplicates!
            mGroupDomains.add(domainExpression);
            return this;
        }

        @NonNull
        GroupKey addBaseDomain(@NonNull final DomainExpression domainExpression) {
            // this is a static setup. We don't check on developer mistakenly adding duplicates!
            mBaseDomains.add(domainExpression);
            return this;
        }

        /**
         * Get the unique keyPrefix used to represent a key in the hierarchy.
         *
         * @return keyPrefix, never {@code null} but will be empty for a BOOK.
         */
        @VisibleForTesting
        @NonNull
        public String getKeyPrefix() {
            return mKeyPrefix;
        }

        /**
         * Create the expression for the node key column: "/key=value".
         * A {@code null} value is reformatted as an empty string.
         *
         * @return column expression
         */
        @NonNull
        String getNodeKeyExpression() {
            return "'/" + mKeyPrefix + "='||COALESCE(" + mKeyDomain.getExpression() + ",'')";
        }

        /**
         * Get the key domain.
         *
         * @return the key domain
         */
        @NonNull
        DomainExpression getKeyDomainExpression() {
            return mKeyDomain;
        }

        /**
         * Get the list of secondary domains.
         * <p>
         * Override in the {@link BooklistGroup} as needed.
         *
         * @return the list, can be empty.
         */
        @NonNull
        ArrayList<DomainExpression> getGroupDomainExpressions() {
            return mGroupDomains;
        }

        /**
         * Get the list of base (book) domains.
         * <p>
         * Override in the {@link BooklistGroup} as needed.
         *
         * @return the list, can be empty.
         */
        @NonNull
        ArrayList<DomainExpression> getBaseDomainExpressions() {
            return mBaseDomains;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final GroupKey groupKey = (GroupKey) o;
            return mLabelId == groupKey.mLabelId
                   && mKeyPrefix.equals(groupKey.mKeyPrefix)
                   && mKeyDomain.equals(groupKey.mKeyDomain)
                   && mGroupDomains.equals(groupKey.mGroupDomains)
                   && mBaseDomains.equals(groupKey.mBaseDomains);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mLabelId, mKeyPrefix, mKeyDomain, mGroupDomains, mBaseDomains);
        }

        @NonNull
        @Override
        public String toString() {
            return "GroupKey{"
                   + "mLabelId=`" + ServiceLocator.getAppContext().getString(mLabelId) + '`'
                   + ", mKeyPrefix=`" + mKeyPrefix + '`'
                   + ", mKeyDomain=" + mKeyDomain
                   + ", mSecondaryDomains=" + mGroupDomains
                   + ", mBookDomains=" + mBaseDomains
                   + '}';
        }
    }
}

