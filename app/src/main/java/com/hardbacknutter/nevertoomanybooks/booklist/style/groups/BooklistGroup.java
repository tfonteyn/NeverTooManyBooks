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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DAOSql;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_BOOKSHELF_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_PUBLISHER_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_SERIES_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
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
 *      <li>modify {@link Booklist#build} to add the necessary grouped/sorted domains</li>
 *      <li>modify {@link BooklistAdapter#onCreateViewHolder} ; If it is just a string field it can
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
    public static final int AUTHOR = 1;
    public static final int SERIES = 2;
    public static final int GENRE = 3;
    public static final int PUBLISHER = 4;
    public static final int READ_STATUS = 5;
    public static final int LENDING = 6;
    public static final int DATE_PUBLISHED_YEAR = 7;
    public static final int DATE_PUBLISHED_MONTH = 8;
    public static final int BOOK_TITLE_LETTER = 9;
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
    public static final int BOOKSHELF = 23;
    public static final int DATE_ACQUIRED_YEAR = 24;
    public static final int DATE_ACQUIRED_MONTH = 25;
    public static final int DATE_ACQUIRED_DAY = 26;
    public static final int DATE_FIRST_PUBLICATION_YEAR = 27;
    public static final int DATE_FIRST_PUBLICATION_MONTH = 28;
    public static final int COLOR = 29;
    public static final int SERIES_TITLE_LETTER = 30;
    public static final int CONDITION = 31;

    /**
     * NEWTHINGS: BooklistGroup.KEY
     * The highest valid index of GroupKey - ALWAYS to be updated after adding a group key.
     */
    @VisibleForTesting
    public static final int GROUP_KEY_MAX = 31;

    /** The {@link StylePersistenceLayer} to use. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    final StylePersistenceLayer mPersistence;
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
        mGroupKey = GroupKey.getGroupKey(mId);

        mPersisted = isPersistent;
        mStyle = style;
        mPersistence = mStyle.getPersistenceLayer();
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
        mPersisted = isPersistent;
        mStyle = style;
        mPersistence = mStyle.getPersistenceLayer();

        mId = group.mId;
        mGroupKey = GroupKey.getGroupKey(mId);
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
            list.add(newInstance(id, style instanceof UserStyle, style));
        }
        return list;
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
    public DomainExpression getDisplayDomain() {
        return mGroupKey.getKeyDomain();
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
    public ArrayList<DomainExpression> getGroupDomains() {
        return mGroupKey.getGroupDomains();
    }

    /**
     * Get the domains that this group adds to the lowest level (book).
     * This is used to build the list table.
     * <p>
     * Override as needed.
     *
     * @return list
     */
    public ArrayList<DomainExpression> getBaseDomains() {
        return mGroupKey.getBaseDomains();
    }

    /**
     * Get the domains for this group <strong>and its outer groups</strong>
     * This is used to build the triggers.
     *
     * @return list
     */
    @Nullable
    public ArrayList<Domain> getAccumulatedDomains() {
        return mAccumulatedDomains;
    }

    /**
     * Set the accumulated domains represented by this group <strong>and its outer groups</strong>.
     *
     * @param accumulatedDomains list of domains.
     */
    public void setAccumulatedDomains(@Nullable final ArrayList<Domain> accumulatedDomains) {
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
        return mPersisted == that.mPersisted
               && mId == that.mId
               && mGroupKey.equals(that.mGroupKey)
               && Objects.equals(mAccumulatedDomains, that.mAccumulatedDomains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPersistence, mPersisted, mId, mGroupKey, mAccumulatedDomains,
                            // UUID only
                            mStyle.getUuid());
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

             BOOK_TITLE_LETTER,
             SERIES_TITLE_LETTER,

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

        // Date based groups have to sort on the full date for cases
        // where we don't have all separate year,month,day fields.
        private static final DomainExpression DATE_PUBLISHED =
                new DomainExpression(DOM_BOOK_DATE_PUBLISHED, null, DomainExpression.SORT_DESC);
        private static final DomainExpression DATE_FIRST_PUBLICATION =
                new DomainExpression(DOM_DATE_FIRST_PUBLICATION, null, DomainExpression.SORT_DESC);
        private static final DomainExpression BOOK_IS_READ =
                new DomainExpression(DOM_BOOK_READ, null, DomainExpression.SORT_DESC);
        private static final DomainExpression DATE_READ_END =
                new DomainExpression(DOM_BOOK_DATE_READ_END, null, DomainExpression.SORT_DESC);
        private static final DomainExpression DATE_ADDED =
                new DomainExpression(DOM_UTC_ADDED, null, DomainExpression.SORT_DESC);
        private static final DomainExpression DATE_LAST_UPDATED =
                new DomainExpression(DOM_UTC_LAST_UPDATED, null, DomainExpression.SORT_DESC);
        private static final DomainExpression DATE_ACQUIRED =
                new DomainExpression(DOM_BOOK_DATE_ACQUIRED, null, DomainExpression.SORT_DESC);

        /** Cache for the static GroupKey instances. */
        private static final Map<Integer, GroupKey> ALL = new UniqueMap<>();

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
         * @param domain     the domain to get the actual data from the Cursor
         * @param expression sql column expression for constructing the Cursor
         * @param sorted     optional sorting
         */
        private GroupKey(@StringRes final int labelId,
                         @NonNull final String keyPrefix,
                         @NonNull final Domain domain,
                         @NonNull final String expression,
                         @DomainExpression.Sorting final int sorted) {
            mLabelId = labelId;
            mKeyPrefix = keyPrefix;
            mKeyDomain = new DomainExpression(domain, expression, sorted);
        }

        /**
         * GroupKey factory constructor.
         *
         * @param id for the desired group key
         *
         * @return new GroupKey instance
         */
        private static GroupKey createGroupKey(@Id final int id) {
            // NEWTHINGS: BooklistGroup.KEY
            switch (id) {
                // The key domain for a book is not used for now, but using the title makes sense.
                case BOOK: {
                    return new GroupKey(R.string.lbl_book, "b",
                                        DOM_TITLE, TBL_BOOKS.dot(KEY_TITLE),
                                        DomainExpression.SORT_UNSORTED);
                }

                // Data with a linked table use the foreign key ID as the key domain.
                case AUTHOR: {
                    // We use the foreign ID to create the key.
                    // We override the display domain in the BooklistGroup.
                    // We do not sort on the key domain but add the OB column in the BooklistGroup.
                    return new GroupKey(R.string.lbl_author, "a",
                                        DOM_FK_AUTHOR, TBL_AUTHORS.dot(KEY_PK_ID),
                                        DomainExpression.SORT_UNSORTED)

                            .addGroupDomain(
                                    // Group by id (we want the id available and there is
                                    // a chance two Authors will have the same name)
                                    new DomainExpression(DOM_FK_AUTHOR,
                                                         TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR)))
                            .addGroupDomain(
                                    // Group by complete-flag
                                    new DomainExpression(DOM_AUTHOR_IS_COMPLETE,
                                                         TBL_AUTHORS.dot(KEY_AUTHOR_IS_COMPLETE)));
                }
                case SERIES: {
                    // We use the foreign ID to create the key.
                    // We override the display domain in the BooklistGroup.
                    // We do not sort on the key domain but add the OB column instead
                    return new GroupKey(R.string.lbl_series, "s",
                                        DOM_FK_SERIES, TBL_SERIES.dot(KEY_PK_ID),
                                        DomainExpression.SORT_UNSORTED)

                            .addGroupDomain(
                                    // Group and sort by the OB column
                                    new DomainExpression(DOM_BL_SERIES_SORT,
                                                         TBL_SERIES.dot(KEY_SERIES_TITLE_OB),
                                                         DomainExpression.SORT_ASC))
                            .addGroupDomain(
                                    // Group by id (we want the id available and there is
                                    // a chance two Series will have the same name)
                                    new DomainExpression(DOM_FK_SERIES,
                                                         TBL_BOOK_SERIES.dot(KEY_FK_SERIES)))
                            .addGroupDomain(
                                    // Group by complete-flag
                                    new DomainExpression(DOM_SERIES_IS_COMPLETE,
                                                         TBL_SERIES.dot(KEY_SERIES_IS_COMPLETE)))
                            .addBaseDomain(
                                    // The series number in the base data in sorted order.
                                    // This field is NOT displayed.
                                    // Casting it as a float allows for the possibility of 3.1,
                                    // or even 3.1|Omnibus 3-10" as a series number.
                                    new DomainExpression(DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT,
                                                         "CAST(" + TBL_BOOK_SERIES
                                                                 .dot(KEY_BOOK_NUM_IN_SERIES)
                                                         + " AS REAL)",
                                                         DomainExpression.SORT_ASC))
                            .addBaseDomain(
                                    // The series number in the base data in sorted order.
                                    // This field is displayed.
                                    // Covers non-numeric data (where the above float would fail)
                                    new DomainExpression(DOM_BOOK_NUM_IN_SERIES,
                                                         TBL_BOOK_SERIES
                                                                 .dot(KEY_BOOK_NUM_IN_SERIES),
                                                         DomainExpression.SORT_ASC));

                }
                case PUBLISHER: {
                    // We use the foreign ID to create the key.
                    // We override the display domain in the BooklistGroup.
                    // We do not sort on the key domain but add the OB column instead
                    return new GroupKey(R.string.lbl_publisher, "p",
                                        DOM_FK_PUBLISHER, TBL_PUBLISHERS.dot(KEY_PK_ID),
                                        DomainExpression.SORT_UNSORTED)

                            .addGroupDomain(
                                    // Group and sort by the OB column
                                    new DomainExpression(DOM_BL_PUBLISHER_SORT,
                                                         TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME_OB),
                                                         DomainExpression.SORT_ASC)
                                           )
                            .addGroupDomain(
                                    // Group by id (we want the id available and there is
                                    // a chance two Publishers will have the same name)
                                    new DomainExpression(DOM_FK_PUBLISHER,
                                                         TBL_BOOK_PUBLISHER.dot(KEY_FK_PUBLISHER)));
                }
                case BOOKSHELF: {
                    // We use the foreign ID to create the key.
                    // We override the display domain in the BooklistGroup.
                    return new GroupKey(R.string.lbl_bookshelf, "shelf",
                                        DOM_FK_BOOKSHELF, TBL_BOOKSHELF.dot(KEY_PK_ID),
                                        DomainExpression.SORT_UNSORTED)

                            .addGroupDomain(
                                    // Group and sort by the NAME column
                                    new DomainExpression(DOM_BL_BOOKSHELF_SORT,
                                                         TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME),
                                                         DomainExpression.SORT_ASC)
                                           )
                            .addGroupDomain(
                                    // Group by id (we want the id available)
                                    new DomainExpression(DOM_FK_BOOKSHELF,
                                                         TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOKSHELF)));
                }

                // Data without a linked table use the display name as the key domain.
                case COLOR: {
                    return new GroupKey(R.string.lbl_color, "col",
                                        DOM_BOOK_COLOR, TBL_BOOKS.dot(KEY_COLOR),
                                        DomainExpression.SORT_ASC);
                }
                case FORMAT: {
                    return new GroupKey(R.string.lbl_format, "fmt",
                                        DOM_BOOK_FORMAT, TBL_BOOKS.dot(KEY_FORMAT),
                                        DomainExpression.SORT_ASC);
                }
                case GENRE: {
                    return new GroupKey(R.string.lbl_genre, "g",
                                        DOM_BOOK_GENRE, TBL_BOOKS.dot(KEY_GENRE),
                                        DomainExpression.SORT_ASC);
                }
                case LANGUAGE: {
                    // Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_language, "lng",
                                        DOM_BOOK_LANGUAGE, TBL_BOOKS.dot(KEY_LANGUAGE),
                                        DomainExpression.SORT_ASC);
                }
                case LOCATION: {
                    return new GroupKey(R.string.lbl_location, "loc",
                                        DOM_BOOK_LOCATION, TBL_BOOKS.dot(KEY_LOCATION),
                                        DomainExpression.SORT_ASC);
                }
                case CONDITION: {
                    return new GroupKey(R.string.lbl_condition, "bk_cnd",
                                        DOM_BOOK_CONDITION, TBL_BOOKS.dot(KEY_BOOK_CONDITION),
                                        DomainExpression.SORT_DESC);
                }
                case RATING: {
                    // Formatting is done after fetching
                    // Sort with highest rated first
                    // The data is cast to an integer as a precaution.
                    return new GroupKey(R.string.lbl_rating, "rt",
                                        DOM_BOOK_RATING,
                                        "CAST(" + TBL_BOOKS.dot(KEY_RATING) + " AS INTEGER)",
                                        DomainExpression.SORT_DESC);
                }

                // the others here below are custom key domains
                case LENDING: {
                    return new GroupKey(R.string.lbl_lend_out, "l",
                                        DOM_LOANEE, DAOSql.SqlColumns.EXP_BOOK_LOANEE_OR_EMPTY,
                                        DomainExpression.SORT_ASC);
                }
                case READ_STATUS: {
                    // Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_read_and_unread, "r",
                                        new Domain.Builder("blg_rd_sts", ColumnInfo.TYPE_TEXT)
                                                .notNull()
                                                .build(),
                                        TBL_BOOKS.dot(KEY_READ),
                                        DomainExpression.SORT_ASC);
                }
                case BOOK_TITLE_LETTER: {
                    // Uses the OrderBy column so we get the re-ordered version if applicable.
                    // Formatting is done in the sql expression.
                    return new GroupKey(R.string.style_builtin_first_letter_book_title, "t",
                                        new Domain.Builder("blg_tit_let", ColumnInfo.TYPE_TEXT)
                                                .notNull()
                                                .build(),
                                        "upper(SUBSTR(" + TBL_BOOKS.dot(KEY_TITLE_OB) + ",1,1))",
                                        DomainExpression.SORT_ASC);
                }
                case SERIES_TITLE_LETTER: {
                    // Uses the OrderBy column so we get the re-ordered version if applicable.
                    // Formatting is done in the sql expression.
                    return new GroupKey(R.string.style_builtin_first_letter_series_title, "st",
                                        new Domain.Builder("blg_ser_tit_let", ColumnInfo.TYPE_TEXT)
                                                .notNull()
                                                .build(),
                                        "upper(SUBSTR(" + TBL_SERIES.dot(KEY_SERIES_TITLE_OB)
                                        + ",1,1))",
                                        DomainExpression.SORT_ASC);
                }

                case DATE_PUBLISHED_YEAR: {
                    // UTC. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_publication_year, "yrp",
                                        new Domain.Builder("blg_pub_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns.year(
                                                TBL_BOOKS.dot(KEY_BOOK_DATE_PUBLISHED), false),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_PUBLISHED);
                }
                case DATE_PUBLISHED_MONTH: {
                    // UTC. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_publication_month, "mp",
                                        new Domain.Builder("blg_pub_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns.month(
                                                TBL_BOOKS.dot(KEY_BOOK_DATE_PUBLISHED), false),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_PUBLISHED);
                }

                case DATE_FIRST_PUBLICATION_YEAR: {
                    // UTC. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_first_pub_year, "yfp",
                                        new Domain.Builder("blg_1pub_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns
                                                .year(TBL_BOOKS.dot(KEY_DATE_FIRST_PUBLICATION),
                                                      false),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_FIRST_PUBLICATION);
                }
                case DATE_FIRST_PUBLICATION_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_first_pub_month, "mfp",
                                        new Domain.Builder("blg_1pub_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns
                                                .month(TBL_BOOKS.dot(KEY_DATE_FIRST_PUBLICATION),
                                                       false),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_FIRST_PUBLICATION);
                }

                case DATE_ACQUIRED_YEAR: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_date_acquired_year, "yac",
                                        new Domain.Builder("blg_acq_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns
                                                .year(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_ACQUIRED);
                }
                case DATE_ACQUIRED_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_date_acquired_month, "mac",
                                        new Domain.Builder("blg_acq_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns
                                                .month(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_ACQUIRED);
                }
                case DATE_ACQUIRED_DAY: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_date_acquired_day, "dac",
                                        new Domain.Builder("blg_acq_d", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns
                                                .day(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_ACQUIRED);
                }


                case DATE_ADDED_YEAR: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_added_year, "ya",
                                        new Domain.Builder("blg_add_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns.year(TBL_BOOKS.dot(KEY_UTC_ADDED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_ADDED);
                }
                case DATE_ADDED_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_added_month, "ma",
                                        new Domain.Builder("blg_add_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns.month(TBL_BOOKS.dot(KEY_UTC_ADDED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_ADDED);
                }
                case DATE_ADDED_DAY: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_added_day, "da",
                                        new Domain.Builder("blg_add_d", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns.day(TBL_BOOKS.dot(KEY_UTC_ADDED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_ADDED);
                }

                case DATE_LAST_UPDATE_YEAR: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_update_year, "yu",
                                        new Domain.Builder("blg_upd_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns
                                                .year(TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_LAST_UPDATED);
                }
                case DATE_LAST_UPDATE_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_update_month, "mu",
                                        new Domain.Builder("blg_upd_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns
                                                .month(TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_LAST_UPDATED);
                }
                case DATE_LAST_UPDATE_DAY: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_update_day, "du",
                                        new Domain.Builder("blg_upd_d", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns
                                                .day(TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_LAST_UPDATED);
                }

                case DATE_READ_YEAR: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_read_year, "yr",
                                        new Domain.Builder("blg_rd_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns.year(TBL_BOOKS.dot(KEY_READ_END), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_READ_END)
                            .addGroupDomain(BOOK_IS_READ);
                }
                case DATE_READ_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_read_month, "mr",
                                        new Domain.Builder("blg_rd_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns.month(TBL_BOOKS.dot(KEY_READ_END), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_READ_END)
                            .addGroupDomain(BOOK_IS_READ);
                }
                case DATE_READ_DAY: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_read_day, "dr",
                                        new Domain.Builder("blg_rd_d", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAOSql.SqlColumns.day(TBL_BOOKS.dot(KEY_READ_END), true),
                                        DomainExpression.SORT_DESC)
                            .addBaseDomain(DATE_READ_END)
                            .addGroupDomain(BOOK_IS_READ);
                }

                default:
                    throw new IllegalArgumentException(String.valueOf(id));
            }
        }

        /**
         * External entry point to create/get a GroupKey.
         *
         * @param id of group to get
         *
         * @return instance
         */
        @NonNull
        @VisibleForTesting
        public static GroupKey getGroupKey(@Id final int id) {
            GroupKey groupKey = ALL.get(id);
            if (groupKey == null) {
                groupKey = createGroupKey(id);
                ALL.put(id, groupKey);
            }
            return groupKey;
        }

        @NonNull
        String getLabel(@NonNull final Context context) {
            return context.getString(mLabelId);
        }

        @NonNull
        private GroupKey addGroupDomain(@NonNull final DomainExpression domainExpression) {
            // this is a static setup. We don't check on developer mistakenly adding duplicates!
            mGroupDomains.add(domainExpression);
            return this;
        }

        @NonNull
        private GroupKey addBaseDomain(@NonNull final DomainExpression domainExpression) {
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
        DomainExpression getKeyDomain() {
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
        ArrayList<DomainExpression> getGroupDomains() {
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
        ArrayList<DomainExpression> getBaseDomains() {
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
                   + "mLabelId=`" + App.getLogString(mLabelId) + '`'
                   + ", mKeyPrefix=`" + mKeyPrefix + '`'
                   + ", mKeyDomain=" + mKeyDomain
                   + ", mSecondaryDomains=" + mGroupDomains
                   + ", mBookDomains=" + mBaseDomains
                   + '}';
        }
    }
}

