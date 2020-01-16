/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_ACQUIRED_DAY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_ACQUIRED_MONTH;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_ACQUIRED_YEAR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_ADDED_DAY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_ADDED_MONTH;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_ADDED_YEAR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_FIRST_PUBLICATION_MONTH;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_FIRST_PUBLICATION_YEAR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_LAST_UPDATED_DAY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_LAST_UPDATED_MONTH;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_LAST_UPDATED_YEAR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_PUBLISHED_MONTH;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_PUBLISHED_YEAR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_READ_DAY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_READ_MONTH;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_DATE_READ_YEAR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_READ_STATUS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_SERIES_TITLE_LETTER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_TITLE_LETTER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * Class representing a single level in the booklist hierarchy.
 * <p>
 * There is a one-to-one mapping with the members of a {@link RowKind}.
 * <p>
 * IMPORTANT: The {@link #mDomains} must be set at runtime each time but that is ok as
 * they are only needed at list build time. They are NOT stored.
 * <p>
 * <strong>Note:</strong> the way preferences are implemented means that all groups will add their
 * properties to the persisted state of a style. Not just the groups which are active/present
 * for that state. This is fine, as they won't get used unless activated.
 * <p>
 * Parcelable: needed by {@link  BooklistStyle}
 */
public class BooklistGroup
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<BooklistGroup> CREATOR =
            new Creator<BooklistGroup>() {
                @Override
                public BooklistGroup createFromParcel(@NonNull final Parcel source) {
                    return new BooklistGroup(source);
                }

                @Override
                public BooklistGroup[] newArray(final int size) {
                    return new BooklistGroup[size];
                }
            };

    /** Flag indicating the style is user-defined -> our prefs must be persisted. */
    final boolean mIsUserDefinedStyle;
    /**
     * The name of the Preference file (comes from the style that contains this group.
     * <p>
     * When set to the empty string, the global preferences will be used.
     */
    @NonNull
    final String mUuid;
    /** The kind of row/group we represent, see {@link RowKind}. */
    @RowKind.Id
    private final int mId;
    /**
     * The domains represented by this group.
     * Set at runtime by builder based on current group and outer groups
     */
    @Nullable
    private ArrayList<DomainDefinition> mDomains;

    /**
     * Constructor.
     *
     * @param id                 of group to create
     * @param uuid               UUID of the style
     * @param isUserDefinedStyle {@code true} if the group properties should be persisted
     */
    private BooklistGroup(@RowKind.Id final int id,
                          @NonNull final String uuid,
                          final boolean isUserDefinedStyle) {
        this.mId = id;
        mUuid = uuid;
        mIsUserDefinedStyle = isUserDefinedStyle;
        initPrefs();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private BooklistGroup(@NonNull final Parcel in) {
        mId = in.readInt();
        //noinspection ConstantConditions
        mUuid = in.readString();
        mIsUserDefinedStyle = in.readInt() != 0;
        mDomains = new ArrayList<>();
        in.readList(mDomains, getClass().getClassLoader());
        // now the prefs
        initPrefs();
    }

    /**
     * Create a new BooklistGroup of the specified kind, creating any specific
     * subclasses as necessary.
     *
     * @param id                 of group to create
     * @param uuid               UUID of the style
     * @param isUserDefinedStyle {@code true} if the group properties should be persisted
     *
     * @return a group based on the passed in kind
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    public static BooklistGroup newInstance(@RowKind.Id final int id,
                                            @NonNull final String uuid,
                                            final boolean isUserDefinedStyle) {
        switch (id) {
            case RowKind.AUTHOR:
                return new BooklistAuthorGroup(uuid, isUserDefinedStyle);
            case RowKind.SERIES:
                return new BooklistSeriesGroup(uuid, isUserDefinedStyle);

            default:
                return new BooklistGroup(id, uuid, isUserDefinedStyle);
        }
    }

    /**
     * Get a list of BooklistGroups, one for each defined RowKind.
     *
     * @param uuid               UUID of the style
     * @param isUserDefinedStyle {@code true} if the group properties should be persisted
     *
     * @return the list
     */
    @NonNull
    public static List<BooklistGroup> getAllGroups(@NonNull final String uuid,
                                                   final boolean isUserDefinedStyle) {
        List<BooklistGroup> list = new ArrayList<>();
        for (@RowKind.Id int id : RowKind.getAllGroupKinds()) {
            list.add(newInstance(id, uuid, isUserDefinedStyle));
        }
        return list;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * When one preference is visible, make the category visible.
     *
     * @param category to set
     */
    static void setCategoryVisibility(@NonNull final PreferenceCategory category) {
        boolean catVis = false;
        int i = 0;
        while (!catVis && i < category.getPreferenceCount()) {
            if (category.getPreference(i).isVisible()) {
                catVis = true;
            }
            i++;
        }
        category.setVisible(catVis);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mId);
        dest.writeString(mUuid);
        dest.writeInt(mIsUserDefinedStyle ? 1 : 0);
        dest.writeList(mDomains);
        // now the prefs for this class (none on this level for now)
    }

    @RowKind.Id
    public int getId() {
        return mId;
    }

    public String getName(@NonNull final Context context) {
        return RowKind.get(mId).getName(context);
    }

    @NonNull
    public DomainDefinition getFormattedDomain() {
        return RowKind.get(mId).getFormattedDomain();
    }

    @NonNull
    String getFormattedDomainExpression() {
        return RowKind.get(mId).getFormattedDomainExpression();
    }

    /**
     * Get the domains represented by this group.
     *
     * @return the domains for this group and its outer groups.
     */
    @Nullable
    ArrayList<DomainDefinition> getDomains() {
        return mDomains;
    }

    /**
     * Set the domains represented by this group.
     *
     * @param domains list of domains.
     */
    void setDomains(@Nullable final ArrayList<DomainDefinition> domains) {
        mDomains = domains;
    }

    /**
     * Only ever init the Preferences if you have a valid UUID.
     */
    void initPrefs() {
    }

    /**
     * Get the Preference objects that this group will contribute to a Style.
     *
     * @return a map with the prefs
     */
    public Map<String, PPref> getPreferences() {
        return new LinkedHashMap<>();
    }

    @NonNull
    CompoundKey getCompoundKey() {
        return RowKind.get(mId).getCompoundKey();
    }

    /**
     * Preference UI support.
     * <p>
     * This method can be called multiple times.
     * <p>
     * Implementations should add the preferences only if they are not already present
     * and if the current method call has visibility set to true.
     * <p>
     * Visibility of individual preferences should always be updated.
     * <p>
     * Add the Preference objects that this group will contribute to a Style.
     * TODO: could/should do this from xml instead I suppose.
     *
     * @param screen  which hosts the prefs
     * @param visible whether to make the preferences visible
     */
    public void addPreferences(@NonNull final PreferenceScreen screen,
                               final boolean visible) {
    }

    /**
     * Specialized BooklistGroup representing a Series group. Includes extra attributes based
     * on preferences.
     */
    public static class BooklistSeriesGroup
            extends BooklistGroup
            implements Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<BooklistSeriesGroup> CREATOR =
                new Creator<BooklistSeriesGroup>() {
                    @Override
                    public BooklistSeriesGroup createFromParcel(@NonNull final Parcel source) {
                        return new BooklistSeriesGroup(source);
                    }

                    @Override
                    public BooklistSeriesGroup[] newArray(final int size) {
                        return new BooklistSeriesGroup[size];
                    }
                };

        /** Show a book under each Series it appears in. */
        private PBoolean mAllSeries;

        /**
         * Constructor.
         *
         * @param uuid               UUID of the style
         * @param isUserDefinedStyle Flag to indicate this is a user style or a builtin style
         */
        BooklistSeriesGroup(@NonNull final String uuid,
                            final boolean isUserDefinedStyle) {
            super(RowKind.SERIES, uuid, isUserDefinedStyle);
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private BooklistSeriesGroup(@NonNull final Parcel in) {
            super(in);
            initPrefs();
            mAllSeries.set(in);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllSeries.writeToParcel(dest);
        }

        @Override
        void initPrefs() {
            super.initPrefs();
            mAllSeries = new PBoolean(Prefs.pk_bob_books_under_multiple_series, mUuid,
                                      mIsUserDefinedStyle);
        }

        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            Map<String, PPref> map = super.getPreferences();
            map.put(mAllSeries.getKey(), mAllSeries);
            return map;
        }

        @Override
        public void addPreferences(@NonNull final PreferenceScreen screen,
                                   final boolean visible) {

            PreferenceCategory category = screen.findPreference(Prefs.psk_style_series);
            if (category != null) {
                Context context = screen.getContext();

                SwitchPreference pShowAll = category
                        .findPreference(Prefs.pk_bob_books_under_multiple_series);
                if (visible && pShowAll == null) {
                    String description = context.getString(R.string.lbl_series);
                    pShowAll = new SwitchPreference(context);
                    pShowAll.setTitle(R.string.pt_bob_books_under_multiple_series);
                    pShowAll.setIcon(R.drawable.ic_functions);
                    pShowAll.setKey(Prefs.pk_bob_books_under_multiple_series);
                    pShowAll.setDefaultValue(false);

                    pShowAll.setSummaryOn(context.getString(
                            R.string.pv_bob_books_under_multiple_each_1s, description));
                    pShowAll.setSummaryOff(context.getString(
                            R.string.pv_bob_books_under_multiple_primary_1s_only, description));
                    category.addPreference(pShowAll);
                }
                if (pShowAll != null) {
                    pShowAll.setVisible(visible);
                }

                setCategoryVisibility(category);
            }
        }

        boolean showAll() {
            return mAllSeries.isTrue();
        }
    }

    /**
     * Specialized BooklistGroup representing an Author group. Includes extra attributes based
     * on preferences.
     */
    public static class BooklistAuthorGroup
            extends BooklistGroup
            implements Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<BooklistAuthorGroup> CREATOR =
                new Creator<BooklistAuthorGroup>() {
                    @Override
                    public BooklistAuthorGroup createFromParcel(@NonNull final Parcel source) {
                        return new BooklistAuthorGroup(source);
                    }

                    @Override
                    public BooklistAuthorGroup[] newArray(final int size) {
                        return new BooklistAuthorGroup[size];
                    }
                };

        /** Support for 'Show All Authors of Book' property. */
        private PBoolean mAllAuthors;
        /** Support for 'Show Given Name First' property. Default: false. */
        private PBoolean mGivenNameFirst;

        /**
         * Constructor.
         *
         * @param uuid               UUID of the style
         * @param isUserDefinedStyle Flag to indicate this is a user style or a builtin style
         */
        BooklistAuthorGroup(@NonNull final String uuid,
                            final boolean isUserDefinedStyle) {
            super(RowKind.AUTHOR, uuid, isUserDefinedStyle);
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private BooklistAuthorGroup(@NonNull final Parcel in) {
            super(in);
            mAllAuthors.set(in);
            mGivenNameFirst.set(in);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllAuthors.writeToParcel(dest);
            mGivenNameFirst.writeToParcel(dest);
        }

        @Override
        protected void initPrefs() {
            super.initPrefs();
            mAllAuthors = new PBoolean(Prefs.pk_bob_books_under_multiple_authors, mUuid,
                                       mIsUserDefinedStyle);
            mGivenNameFirst = new PBoolean(Prefs.pk_bob_format_author_name, mUuid,
                                           mIsUserDefinedStyle);
        }

        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            Map<String, PPref> map = super.getPreferences();
            map.put(mAllAuthors.getKey(), mAllAuthors);
            map.put(mGivenNameFirst.getKey(), mGivenNameFirst);
            return map;
        }

        @Override
        public void addPreferences(@NonNull final PreferenceScreen screen,
                                   final boolean visible) {

            PreferenceCategory category = screen.findPreference(Prefs.psk_style_author);
            if (category != null) {
                Context context = screen.getContext();

                SwitchPreference pShowAll = category
                        .findPreference(Prefs.pk_bob_books_under_multiple_authors);
                if (visible && pShowAll == null) {
                    pShowAll = new SwitchPreference(context);
                    String description = context.getString(R.string.lbl_author);
                    pShowAll.setTitle(R.string.pt_bob_books_under_multiple_authors);
                    pShowAll.setIcon(R.drawable.ic_functions);
                    pShowAll.setKey(Prefs.pk_bob_books_under_multiple_authors);
                    pShowAll.setDefaultValue(false);
                    pShowAll.setSummaryOn(context.getString(
                            R.string.pv_bob_books_under_multiple_each_1s, description));
                    pShowAll.setSummaryOff(context.getString(
                            R.string.pv_bob_books_under_multiple_primary_1s_only, description));

                    category.addPreference(pShowAll);
                }
                if (pShowAll != null) {
                    pShowAll.setVisible(visible);
                }

                SwitchPreference pGivenNameFirst = category
                        .findPreference(Prefs.pk_bob_format_author_name);
                if (visible && pGivenNameFirst == null) {
                    pGivenNameFirst = new SwitchPreference(context);
                    pGivenNameFirst.setTitle(R.string.pt_bob_authors_display);
                    pGivenNameFirst.setIcon(R.drawable.ic_reorder);
                    pGivenNameFirst.setKey(Prefs.pk_bob_format_author_name);
                    pGivenNameFirst.setDefaultValue(false);
                    pGivenNameFirst.setSummaryOn(R.string.pv_bob_author_name_given_first);
                    pGivenNameFirst.setSummaryOff(R.string.pv_bob_author_name_family_first);

                    category.addPreference(pGivenNameFirst);
                }
                if (pGivenNameFirst != null) {
                    pGivenNameFirst.setVisible(visible);
                }

                setCategoryVisibility(category);
            }
        }

        boolean showAll() {
            return mAllAuthors.isTrue();
        }

        /**
         * @return {@code true} if we want "given-names last-name" formatted authors.
         */
        boolean showAuthorGivenNameFirst() {
            return mGivenNameFirst.isTrue();
        }

        @Author.Type
        public int getType() {
            return Author.TYPE_UNKNOWN;
        }
    }

    /**
     * Get a RowKind with the static method: {@link #get(int kind)}.
     * <p>
     * We create them all once at startup and keep them cached,
     * so the RowKind class is for al intent and purpose static!
     * <p>
     * <strong>Never change these ids</strong>
     */
    public static final class RowKind {

        /** See {@link Id}. */
        // The code relies on BOOK being == 0
        public static final int BOOK = 0;
        public static final int AUTHOR = 1;
        public static final int SERIES = 2;
        public static final int GENRE = 3;
        public static final int PUBLISHER = 4;
        public static final int READ_STATUS = 5;
        public static final int LOANED = 6;
        public static final int DATE_PUBLISHED_YEAR = 7;
        public static final int DATE_PUBLISHED_MONTH = 8;
        public static final int TITLE_LETTER = 9;
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
        public static final int DATE_FIRST_PUB_YEAR = 27;
        public static final int DATE_FIRST_PUB_MONTH = 28;
        public static final int COLOR = 29;
        public static final int SERIES_TITLE_LETTER = 30;
        /** Log tag. */
        private static final String TAG = "RowKind";
        // NEWTHINGS: ROW_KIND_x
        // the highest valid index of kinds - ALWAYS to be updated after adding a row kind...
        private static final int ROW_KIND_MAX = 30;
        private static final Map<Integer, RowKind> ALL_KINDS = new UniqueMap<>();

        static {
            RowKind rowKind;

            rowKind = new RowKind(BOOK, R.string.lbl_book, "")
                    .setFormattedDomain(DOM_TITLE, TBL_BOOKS.dot(DOM_TITLE));
            ALL_KINDS.put(rowKind.mId, rowKind);


            rowKind = new RowKind(AUTHOR, R.string.lbl_author, "a")
                    .setKey(DOM_FK_AUTHOR, TBL_AUTHORS.dot(DOM_PK_ID))
                    // the source expression is not used
                    .setFormattedDomain(DOM_AUTHOR_FORMATTED, "''");
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(SERIES, R.string.lbl_series, "s")
                    .setKey(DOM_FK_SERIES, TBL_SERIES.dot(DOM_PK_ID))
                    .setFormattedDomain(DOM_SERIES_TITLE, TBL_SERIES.dot(DOM_SERIES_TITLE));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(PUBLISHER, R.string.lbl_publisher, "p")
                    .setDomain(DOM_BOOK_PUBLISHER, TBL_BOOKS.dot(DOM_BOOK_PUBLISHER));
            ALL_KINDS.put(rowKind.mId, rowKind);


            rowKind = new RowKind(BOOKSHELF, R.string.lbl_bookshelf, "shelf")
                    .setDomain(DOM_BOOKSHELF, TBL_BOOKSHELF.dot(DOM_BOOKSHELF));
            ALL_KINDS.put(rowKind.mId, rowKind);


            rowKind = new RowKind(LOANED, R.string.lbl_loaned, "l")
                    .setDomain(DOM_LOANEE, DAO.SqlColumns.EXP_BOOK_LOANEE_OR_EMPTY);
            ALL_KINDS.put(rowKind.mId, rowKind);




            rowKind = new RowKind(READ_STATUS, R.string.lbl_read_and_unread, "r")
                    .setDomain(DOM_RK_READ_STATUS, TBL_BOOKS.dot(DOM_BOOK_READ));
            ALL_KINDS.put(rowKind.mId, rowKind);


            rowKind = new RowKind(GENRE, R.string.lbl_genre, "g")
                    .setDomain(DOM_BOOK_GENRE, TBL_BOOKS.dot(DOM_BOOK_GENRE));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(LOCATION, R.string.lbl_location, "loc")
                    .setDomain(DOM_BOOK_LOCATION, TBL_BOOKS.dot(DOM_BOOK_LOCATION));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(LANGUAGE, R.string.lbl_language, "lng")
                    .setDomain(DOM_BOOK_LANGUAGE, TBL_BOOKS.dot(DOM_BOOK_LANGUAGE));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(FORMAT, R.string.lbl_format, "fmt")
                    .setDomain(DOM_BOOK_FORMAT, TBL_BOOKS.dot(DOM_BOOK_FORMAT));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(COLOR, R.string.lbl_color, "col")
                    .setDomain(DOM_BOOK_COLOR, TBL_BOOKS.dot(DOM_BOOK_COLOR));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(RATING, R.string.lbl_rating, "rt")
                    .setDomain(DOM_BOOK_RATING,
                               "CAST(" + TBL_BOOKS.dot(DOM_BOOK_RATING) + " AS INTEGER)");
            ALL_KINDS.put(rowKind.mId, rowKind);


            // uses the OrderBy column
            rowKind = new RowKind(TITLE_LETTER, R.string.style_builtin_first_letter_book_title, "t")
                    .setDomain(DOM_RK_TITLE_LETTER,
                               "upper(SUBSTR(" + TBL_BOOKS.dot(DOM_TITLE_OB) + ",1,1))");
            ALL_KINDS.put(rowKind.mId, rowKind);

            // uses the OrderBy column
            rowKind = new RowKind(SERIES_TITLE_LETTER,
                                  R.string.style_builtin_first_letter_series_title, "st")
                    .setDomain(DOM_RK_SERIES_TITLE_LETTER,
                               "upper(SUBSTR(" + TBL_SERIES.dot(DOM_SERIES_TITLE_OB) + ",1,1))");
            ALL_KINDS.put(rowKind.mId, rowKind);



            rowKind = new RowKind(DATE_PUBLISHED_YEAR, R.string.lbl_publication_year, "yrp")
                    .setDomain(DOM_RK_DATE_PUBLISHED_YEAR,
                               DAO.SqlColumns.year(TBL_BOOKS.dot(DOM_DATE_PUBLISHED), false));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_PUBLISHED_MONTH, R.string.lbl_publication_month, "mp")
                    .setDomain(DOM_RK_DATE_PUBLISHED_MONTH,
                               DAO.SqlColumns.month(TBL_BOOKS.dot(DOM_DATE_PUBLISHED), false));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_FIRST_PUB_YEAR, R.string.lbl_first_pub_year, "yfp")
                    .setDomain(DOM_RK_DATE_FIRST_PUBLICATION_YEAR,
                               DAO.SqlColumns.year(TBL_BOOKS.dot(DOM_DATE_FIRST_PUB), false));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_FIRST_PUB_MONTH, R.string.lbl_first_pub_month, "mfp")
                    .setDomain(DOM_RK_DATE_FIRST_PUBLICATION_MONTH,
                               DAO.SqlColumns.month(TBL_BOOKS.dot(DOM_DATE_FIRST_PUB), false));
            ALL_KINDS.put(rowKind.mId, rowKind);


            rowKind = new RowKind(DATE_ADDED_YEAR, R.string.lbl_added_year, "ya")
                    .setDomain(DOM_RK_DATE_ADDED_YEAR,
                               DAO.SqlColumns.year(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_ADDED_MONTH, R.string.lbl_added_month, "ma")
                    .setDomain(DOM_RK_DATE_ADDED_MONTH,
                               DAO.SqlColumns.month(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_ADDED_DAY, R.string.lbl_added_day, "da")
                    .setDomain(DOM_RK_DATE_ADDED_DAY,
                               DAO.SqlColumns.dayGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_READ_YEAR, R.string.lbl_read_year, "yr")
                    .setDomain(DOM_RK_DATE_READ_YEAR,
                               DAO.SqlColumns.year(TBL_BOOKS.dot(DOM_BOOK_READ_END), false));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_READ_MONTH, R.string.lbl_read_month, "mr")
                    .setDomain(DOM_RK_DATE_READ_MONTH,
                               DAO.SqlColumns.month(TBL_BOOKS.dot(DOM_BOOK_READ_END), false));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_READ_DAY, R.string.lbl_read_day, "dr")
                    .setDomain(DOM_RK_DATE_READ_DAY,
                               DAO.SqlColumns.dayGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_YEAR, R.string.lbl_update_year, "yu")
                    .setDomain(DOM_RK_DATE_LAST_UPDATED_YEAR,
                               DAO.SqlColumns.year(TBL_BOOKS.dot(DOM_DATE_LAST_UPDATED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_MONTH, R.string.lbl_update_month, "mu")
                    .setDomain(DOM_RK_DATE_LAST_UPDATED_MONTH,
                               DAO.SqlColumns
                                       .month(TBL_BOOKS.dot(DOM_DATE_LAST_UPDATED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_DAY, R.string.lbl_update_day, "du")
                    .setDomain(DOM_RK_DATE_LAST_UPDATED_DAY,
                               DAO.SqlColumns.dayGlob(TBL_BOOKS.dot(DOM_DATE_LAST_UPDATED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_YEAR, R.string.lbl_date_acquired_year, "yac")
                    .setDomain(DOM_RK_DATE_ACQUIRED_YEAR,
                               DAO.SqlColumns
                                       .year(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_MONTH, R.string.lbl_date_acquired_month, "mac")
                    .setDomain(DOM_RK_DATE_ACQUIRED_MONTH,
                               DAO.SqlColumns
                                       .month(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_DAY, R.string.lbl_date_acquired_day, "dac")
                    .setDomain(DOM_RK_DATE_ACQUIRED_DAY,
                               DAO.SqlColumns.dayGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true));
            ALL_KINDS.put(rowKind.mId, rowKind);


            // NEWTHINGS: ROW_KIND_x

            if (BuildConfig.DEBUG /* always */) {
                // Developer sanity check (for() loop starting at 1)
                if (BOOK != 0) {
                    throw new UnexpectedValueException(BOOK);
                }

                // Developer sanity check
                Collection<String> prefixes = new HashSet<>();
                for (
                        @Id
                        int id = 0; id <= ROW_KIND_MAX; id++) {
                    if (!ALL_KINDS.containsKey(id)) {
                        throw new IllegalStateException("Missing id: " + id);
                    }

                    //noinspection ConstantConditions
                    String prefix = ALL_KINDS.get(id).mKeyPrefix;
                    if (!prefixes.add(prefix)) {
                        throw new IllegalStateException("Duplicate prefix: " + prefix);
                    }
                }
            }
        }

        @Id
        private final int mId;
        @StringRes
        private final int mLabelId;
        @SuppressWarnings("FieldNotUsedInToString")
        @NonNull
        private final String mKeyPrefix;
        @Nullable
        private CompoundKey mCompoundKey;
        @Nullable
        private DomainDefinition mFormattedDomain;
        @Nullable
        private String mFormattedDomainExpression;

        /**
         * Constructor.
         *
         * @param id        1 to max. The id==0 should be created with the no-args constructor.
         * @param labelId   User displayable label
         * @param keyPrefix the prefix (as short as possible) to use for the compound key
         */
        private RowKind(@Id final int id,
                        @StringRes final int labelId,
                        @NonNull final String keyPrefix) {
            mId = id;
            mLabelId = labelId;
            mKeyPrefix = keyPrefix;
        }

        /**
         * @param id to create
         *
         * @return a cached instance of a RowKind
         */
        @NonNull
        public static RowKind get(@Id final int id) {
            //noinspection ConstantConditions
            return ALL_KINDS.get(id);
        }

        /**
         * Get the set of all valid <strong>Group</strong> values.
         * In other words: all valid kinds, <strong>except</strong> the BOOK.
         *
         * @return Set
         */
        @NonNull
        static Set<Integer> getAllGroupKinds() {
            Set<Integer> set = ALL_KINDS.keySet();
            set.remove(BOOK);
            return set;
        }

        /**
         * Format the source string according to the kind.
         *
         * <strong>Developer note::</strong> this is not (yet) complete,
         * CHECK if the desired kind is covered.
         * Also see {@link BooklistAdapter.GenericStringHolder#setText(String, int)}
         * TODO: come up with a clean solution to merge these.
         *
         * @param context Current context
         * @param id      for this row
         * @param source  text to reformat
         *
         * @return Formatted string, or original string when no special format
         *         was needed or on any failure
         */
        @NonNull
        public static String format(@NonNull final Context context,
                                    @Id final int id,
                                    @NonNull final String source) {
            switch (id) {
                case READ_STATUS: {
                    switch (source) {
                        case "0":
                            return context.getString(R.string.lbl_unread);
                        case "1":
                            return context.getString(R.string.lbl_read);
                        default:
                            return context.getString(R.string.hint_empty_read_status);
                    }
                }
                case LANGUAGE: {
                    if (source.isEmpty()) {
                        return context.getString(R.string.hint_empty_language);
                    } else {
                        return LanguageUtils.getDisplayName(context, source);
                    }
                }
                case RATING: {
                    if (source.isEmpty()) {
                        return context.getString(R.string.hint_empty_rating);
                    }
                    try {
                        int i = Integer.parseInt(source);
                        // If valid, get the name
                        if (i >= 0 && i <= Book.RATING_STARS) {
                            return context.getResources()
                                          .getQuantityString(R.plurals.n_stars, i, i);
                        }
                    } catch (@NonNull final NumberFormatException e) {
                        Logger.error(context, TAG, e);
                    }
                    return source;
                }

                case DATE_ACQUIRED_MONTH:
                case DATE_ADDED_MONTH:
                case DATE_LAST_UPDATE_MONTH:
                case DATE_PUBLISHED_MONTH:
                case DATE_READ_MONTH: {
                    if (source.isEmpty()) {
                        return context.getString(R.string.hint_empty_month);
                    }
                    try {
                        int i = Integer.parseInt(source);
                        // If valid, get the short name
                        if (i > 0 && i <= 12) {
                            return DateUtils.getMonthName(i, false);
                        }
                    } catch (@NonNull final NumberFormatException e) {
                        Logger.error(context, TAG, e);
                    }
                    return source;
                }

                case AUTHOR:
                case BOOKSHELF:
                case BOOK:
                case DATE_ACQUIRED_DAY:
                case DATE_ACQUIRED_YEAR:
                case DATE_ADDED_DAY:
                case DATE_ADDED_YEAR:
                case DATE_FIRST_PUB_MONTH:
                case DATE_FIRST_PUB_YEAR:
                case DATE_LAST_UPDATE_DAY:
                case DATE_LAST_UPDATE_YEAR:
                case DATE_PUBLISHED_YEAR:
                case DATE_READ_DAY:
                case DATE_READ_YEAR:
                case FORMAT:
                case COLOR:
                case GENRE:
                case LOANED:
                case LOCATION:
                case PUBLISHER:
                case SERIES:
                case TITLE_LETTER:
                case SERIES_TITLE_LETTER:
                    // no specific formatting done.
                    return source;

                default:
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "format"
                                   + "|source=" + source
                                   + "|id=" + id);
                    }
                    throw new UnexpectedValueException(id);
            }
        }


        /**
         * Key and formatted domain is the same when the domain is NOT a foreign key.
         *
         * @param domain to use
         */
        @NonNull
        RowKind setDomain(@NonNull final DomainDefinition domain,
                          @NonNull final String sourceExpression) {
            mCompoundKey = new CompoundKey(mKeyPrefix, domain, sourceExpression);
            mFormattedDomain = domain;
            mFormattedDomainExpression = sourceExpression;
            return this;
        }

        /**
         * Set the Key.
         *
         * @param domain to use
         */
        @NonNull
        RowKind setKey(@NonNull final DomainDefinition domain,
                       @NonNull final String sourceExpression) {
            mCompoundKey = new CompoundKey(mKeyPrefix, domain, sourceExpression);
            return this;
        }

        /**
         * Set the FormattedDomain.
         *
         * @param domain to use
         */
        @NonNull
        RowKind setFormattedDomain(@NonNull final DomainDefinition domain,
                                   @NonNull final String sourceExpression) {
            mFormattedDomain = domain;
            mFormattedDomainExpression = sourceExpression;
            return this;
        }

        /**
         * Get the domain that will be used to display data to the user.
         * i.e. the domain for the row in the book list.
         *
         * @return the domain
         */
        @NonNull
        public DomainDefinition getFormattedDomain() {
            return Objects.requireNonNull(mFormattedDomain);
        }


        /**
         * Get the SQL source expression for the DisplayDomain.
         *
         * @return sql column expression.
         */
        @NonNull
        String getFormattedDomainExpression() {
            return Objects.requireNonNull(mFormattedDomainExpression);
        }


        /**
         * Compound key of this RowKind ({@link BooklistGroup}).
         * <p>
         * The name will be of the form 'prefix/id' where 'prefix' is the prefix specific
         * to the RowKind, and 'id' the id of the row, e.g. 's/18' for Series with id=18
         *
         * @return the key
         */
        @NonNull
        CompoundKey getCompoundKey() {
            return Objects.requireNonNull(mCompoundKey);
        }

        @NonNull
        String getName(@NonNull final Context context) {
            return context.getString(mLabelId);
        }

        @Override
        @NonNull
        public String toString() {
            return "RowKind{"
                   + "name=" + App.getAppContext().getString(mLabelId)
                   + ", mId=" + mId
                   + ", mCompoundKey=" + mCompoundKey
                   + ", mFormattedDomain=" + mFormattedDomain
                   + ", mFormattedDomainExpression=`" + mFormattedDomainExpression + '`'
                   + '}';
        }

        @IntDef({RowKind.BOOK,

                 RowKind.AUTHOR,
                 RowKind.SERIES,
                 RowKind.GENRE,
                 RowKind.PUBLISHER,
                 RowKind.READ_STATUS,

                 RowKind.LOANED,

                 RowKind.TITLE_LETTER,
                 RowKind.SERIES_TITLE_LETTER,
                 RowKind.FORMAT,
                 RowKind.COLOR,
                 RowKind.LOCATION,
                 RowKind.LANGUAGE,
                 RowKind.RATING,
                 RowKind.BOOKSHELF,

                 RowKind.DATE_PUBLISHED_YEAR,
                 RowKind.DATE_PUBLISHED_MONTH,
                 RowKind.DATE_FIRST_PUB_YEAR,
                 RowKind.DATE_FIRST_PUB_MONTH,

                 RowKind.DATE_READ_YEAR,
                 RowKind.DATE_READ_MONTH,
                 RowKind.DATE_READ_DAY,

                 RowKind.DATE_ADDED_YEAR,
                 RowKind.DATE_ADDED_MONTH,
                 RowKind.DATE_ADDED_DAY,

                 RowKind.DATE_LAST_UPDATE_YEAR,
                 RowKind.DATE_LAST_UPDATE_MONTH,
                 RowKind.DATE_LAST_UPDATE_DAY,

                 RowKind.DATE_ACQUIRED_YEAR,
                 RowKind.DATE_ACQUIRED_MONTH,
                 RowKind.DATE_ACQUIRED_DAY,
                })
        @Retention(RetentionPolicy.SOURCE)
        public @interface Id {

        }
    }

    /**
     * Represents a collection of domains that make a unique key for a given {@link RowKind}.
     */
    static class CompoundKey
            implements Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<CompoundKey> CREATOR =
                new Creator<CompoundKey>() {
                    @Override
                    public CompoundKey createFromParcel(@NonNull final Parcel source) {
                        return new CompoundKey(source);
                    }

                    @Override
                    public CompoundKey[] newArray(final int size) {
                        return new CompoundKey[size];
                    }
                };

        /** Unique prefix used to represent a key in the hierarchy. */
        @NonNull
        private final String prefix;

        /** List of domains in the key. */
        @NonNull
        private final DomainDefinition domain;
        @NonNull
        private final String expression;

        /**
         * Constructor.
         *
         * @param prefix Unique prefix used to represent a key in the hierarchy.
         */
        CompoundKey(@NonNull final String prefix,
                    @NonNull final DomainDefinition keyDomain,
                    @NonNull final String keyExpression) {
            this.prefix = prefix;
            this.domain = keyDomain;
            this.expression = keyExpression;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private CompoundKey(@NonNull final Parcel in) {
            //noinspection ConstantConditions
            prefix = in.readString();
            //noinspection ConstantConditions
            domain = in.readParcelable(getClass().getClassLoader());
            //noinspection ConstantConditions
            expression = in.readString();
        }

        /**
         * Get the unique prefix used to represent a key in the hierarchy.
         *
         * @return prefix, never {@code null} but will be empty for a BOOK.
         */
        @NonNull
        String getPrefix() {
            return prefix;
        }

        @NonNull
        DomainDefinition getDomain() {
            return domain;
        }

        @NonNull
        String getExpression() {
            return expression;
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(prefix);
            dest.writeParcelable(domain, flags);
            dest.writeString(expression);
        }

        @NonNull
        @Override
        public String toString() {
            return "CompoundKey{"
                   + "prefix='" + prefix + '\''
                   + ", domain=" + domain
                   + ", expression=" + expression
                   + '}';
        }
    }
}

