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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * Class representing a single level in the booklist hierarchy.
 * <p>
 * There is a one-to-one mapping with a {@link GroupKey},
 * the latter providing a lightweight (final) object without user preferences.
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

    /**
     * The ids for the groups. <strong>Never change these ids</strong>
     * <p>
     * Also: the code relies on BOOK being == 0
     */
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

    /**
     * NEWTHINGS: ROW_KIND_x
     * The highest valid index of kinds - ALWAYS to be updated after adding a row kind.
     */
    private static final int ROW_KIND_MAX = 30;

    /** Log tag. */
    private static final String TAG = "BooklistGroup";

    /** We create all GroupKey instances at startup and keep them cached. */
    private static final Map<Integer, GroupKey> GROUPS = new UniqueMap<>();

    static {
        GROUPS.put(BOOK, new GroupKey(R.string.lbl_book, "b",
                                      DOM_TITLE, TBL_BOOKS.dot(KEY_TITLE)));

        GROUPS.put(AUTHOR, new GroupKey(R.string.lbl_author, "a",
                                        DOM_FK_AUTHOR, TBL_AUTHORS.dot(KEY_PK_ID)));

        GROUPS.put(SERIES, new GroupKey(R.string.lbl_series, "s",
                                        DOM_FK_SERIES, TBL_SERIES.dot(KEY_PK_ID)));

        GROUPS.put(PUBLISHER, new GroupKey(R.string.lbl_publisher, "p",
                                           DOM_BOOK_PUBLISHER, TBL_BOOKS.dot(KEY_PUBLISHER)));

        GROUPS.put(BOOKSHELF, new GroupKey(R.string.lbl_bookshelf, "shelf",
                                           DOM_BOOKSHELF, TBL_BOOKSHELF.dot(KEY_BOOKSHELF)));

        GROUPS.put(LOANED, new GroupKey(R.string.lbl_loaned, "l",
                                        DOM_LOANEE, DAO.SqlColumns.EXP_BOOK_LOANEE_OR_EMPTY));

        GROUPS.put(GENRE, new GroupKey(R.string.lbl_genre, "g",
                                       DOM_BOOK_GENRE, TBL_BOOKS.dot(KEY_GENRE)));

        GROUPS.put(LOCATION, new GroupKey(R.string.lbl_location, "loc",
                                          DOM_BOOK_LOCATION, TBL_BOOKS.dot(KEY_LOCATION)));

        GROUPS.put(FORMAT, new GroupKey(R.string.lbl_format, "fmt",
                                        DOM_BOOK_FORMAT, TBL_BOOKS.dot(KEY_FORMAT)));

        GROUPS.put(COLOR, new GroupKey(R.string.lbl_color, "col",
                                       DOM_BOOK_COLOR, TBL_BOOKS.dot(KEY_COLOR)));


        // Formatting is done after fetching.
        GROUPS.put(LANGUAGE, new GroupKey(R.string.lbl_language, "lng",
                                          DOM_BOOK_LANGUAGE, TBL_BOOKS.dot(KEY_LANGUAGE)));

        // Formatting is done after fetching.
        GROUPS.put(RATING, new GroupKey(R.string.lbl_rating, "rt",
                                        DOM_BOOK_RATING,
                                        "CAST(" + TBL_BOOKS.dot(KEY_RATING) + " AS INTEGER)"));

        // Formatting is done after fetching.
        GROUPS.put(READ_STATUS,
                   new GroupKey(R.string.lbl_read_and_unread, "r",
                                new Domain.Builder("blg_rd_sts", ColumnInfo.TYPE_TEXT)
                                        .notNull()
                                        .build(),
                                TBL_BOOKS.dot(KEY_READ)));

        // Uses the OrderBy column so we get the re-ordered version if applicable.
        // Formatting is done in the sql expression.
        GROUPS.put(TITLE_LETTER,
                   new GroupKey(R.string.style_builtin_first_letter_book_title, "t",
                                new Domain.Builder("blg_tit_let", ColumnInfo.TYPE_TEXT)
                                        .notNull()
                                        .build(),
                                "upper(SUBSTR(" + TBL_BOOKS.dot(KEY_TITLE_OB)
                                + ",1,1))"));

        // Uses the OrderBy column so we get the re-ordered version if applicable.
        // Formatting is done in the sql expression.
        GROUPS.put(SERIES_TITLE_LETTER,
                   new GroupKey(R.string.style_builtin_first_letter_series_title, "st",
                                new Domain.Builder("blg_ser_tit_let", ColumnInfo.TYPE_TEXT)
                                        .notNull()
                                        .build(),
                                "upper(SUBSTR(" + TBL_SERIES.dot(KEY_SERIES_TITLE_OB)
                                + ",1,1))"));

        // UTC. Formatting is done in the sql expression.
        GROUPS.put(DATE_PUBLISHED_YEAR,
                   new GroupKey(R.string.lbl_publication_year, "yrp",
                                new Domain.Builder("blg_pub_y", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_DATE_PUBLISHED), false)));
        // UTC. Formatting is done after fetching.
        GROUPS.put(DATE_PUBLISHED_MONTH,
                   new GroupKey(R.string.lbl_publication_month, "mp",
                                new Domain.Builder("blg_pub_m", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.month(TBL_BOOKS.dot(KEY_DATE_PUBLISHED), false)));

        // UTC. Formatting is done in the sql expression.
        GROUPS.put(DATE_FIRST_PUB_YEAR,
                   new GroupKey(R.string.lbl_first_pub_year, "yfp",
                                new Domain.Builder("blg_1pub_y", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_DATE_FIRST_PUBLICATION),
                                                    false)));
        // UTC. Formatting is done after fetching.
        GROUPS.put(DATE_FIRST_PUB_MONTH,
                   new GroupKey(R.string.lbl_first_pub_month, "mfp",
                                new Domain.Builder("blg_1pub_m", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.month(TBL_BOOKS.dot(KEY_DATE_FIRST_PUBLICATION),
                                                     false)));

        // Local for the user. Formatting is done in the sql expression.
        GROUPS.put(DATE_ADDED_YEAR,
                   new GroupKey(R.string.lbl_added_year, "ya",
                                new Domain.Builder("blg_add_y", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_DATE_ADDED), true)));
        // Local for the user. Formatting is done after fetching.
        GROUPS.put(DATE_ADDED_MONTH,
                   new GroupKey(R.string.lbl_added_month, "ma",
                                new Domain.Builder("blg_add_m", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.month(TBL_BOOKS.dot(KEY_DATE_ADDED), true)));
        // Local for the user. Formatting is done in the sql expression.
        GROUPS.put(DATE_ADDED_DAY,
                   new GroupKey(R.string.lbl_added_day, "da",
                                new Domain.Builder("blg_add_d", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.day(TBL_BOOKS.dot(KEY_DATE_ADDED), true)));

        // Local for the user. Formatting is done in the sql expression.
        GROUPS.put(DATE_READ_YEAR,
                   new GroupKey(R.string.lbl_read_year, "yr",
                                new Domain.Builder("blg_rd_y", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_READ_END), true)));
        // Local for the user. Formatting is done after fetching.
        GROUPS.put(DATE_READ_MONTH,
                   new GroupKey(R.string.lbl_read_month, "mr",
                                new Domain.Builder("blg_rd_m", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.month(TBL_BOOKS.dot(KEY_READ_END), true)));
        // Local for the user. Formatting is done in the sql expression.
        GROUPS.put(DATE_READ_DAY,
                   new GroupKey(R.string.lbl_read_day, "dr",
                                new Domain.Builder("blg_rd_d", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.day(TBL_BOOKS.dot(KEY_READ_END), true)));

        // Local for the user. Formatting is done in the sql expression.
        GROUPS.put(DATE_LAST_UPDATE_YEAR,
                   new GroupKey(R.string.lbl_update_year, "yu",
                                new Domain.Builder("blg_upd_y", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_DATE_LAST_UPDATED), true)));
        // Local for the user. Formatting is done after fetching.
        GROUPS.put(DATE_LAST_UPDATE_MONTH,
                   new GroupKey(R.string.lbl_update_month, "mu",
                                new Domain.Builder("blg_upd_m", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.month(TBL_BOOKS.dot(KEY_DATE_LAST_UPDATED), true)));
        // Local for the user. Formatting is done in the sql expression.
        GROUPS.put(DATE_LAST_UPDATE_DAY,
                   new GroupKey(R.string.lbl_update_day, "du",
                                new Domain.Builder("blg_upd_d", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.day(TBL_BOOKS.dot(KEY_DATE_LAST_UPDATED), true)));

        // Local for the user. Formatting is done in the sql expression.
        GROUPS.put(DATE_ACQUIRED_YEAR,
                   new GroupKey(R.string.lbl_date_acquired_year, "yac",
                                new Domain.Builder("blg_acq_y", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true)));
        // Local for the user. Formatting is done after fetching.
        GROUPS.put(DATE_ACQUIRED_MONTH,
                   new GroupKey(R.string.lbl_date_acquired_month, "mac",
                                new Domain.Builder("blg_acq_m", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.month(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true)));
        // Local for the user. Formatting is done in the sql expression.
        GROUPS.put(DATE_ACQUIRED_DAY,
                   new GroupKey(R.string.lbl_date_acquired_day, "dac",
                                new Domain.Builder("blg_acq_d", ColumnInfo.TYPE_INTEGER).build(),
                                DAO.SqlColumns.day(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true)));

        // NEWTHINGS: ROW_KIND_x

        if (BuildConfig.DEBUG /* always */) {
            // Developer sanity check (for() loop starting at 1)
            if (BOOK != 0) {
                throw new UnexpectedValueException(BOOK);
            }

            // Developer sanity check
            Collection<String> prefixes = new HashSet<>();
            for (int id = 0; id <= ROW_KIND_MAX; id++) {
                GroupKey cKey = GROUPS.get(id);
                Objects.requireNonNull(cKey, "Missing id: " + id);

                String prefix = cKey.getKeyPrefix();
                if (!prefixes.add(prefix)) {
                    throw new IllegalStateException("Duplicate keyPrefix: " + prefix);
                }
            }
        }
    }

    /** Flag indicating the style is user-defined -> our prefs must be persisted. */
    final boolean mIsUserDefinedStyle;
    /**
     * The name of the Preference file (comes from the style that contains this group.
     * <p>
     * When set to the empty string, the global preferences will be used.
     */
    @NonNull
    final String mUuid;
    /** The kind of row/group we represent, see {@link GroupKey}. */
    @Id
    private final int mId;
    /**
     * The domains represented by this group.
     * Set at runtime by builder based on current group and outer groups
     */
    @Nullable
    private ArrayList<Domain> mDomains;

    /**
     * Constructor.
     *
     * @param id                 of group to create
     * @param uuid               UUID of the style
     * @param isUserDefinedStyle {@code true} if the group properties should be persisted
     */
    private BooklistGroup(@Id final int id,
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
    public static BooklistGroup newInstance(@Id final int id,
                                            @NonNull final String uuid,
                                            final boolean isUserDefinedStyle) {
        switch (id) {
            case AUTHOR:
                return new BooklistAuthorGroup(uuid, isUserDefinedStyle);
            case SERIES:
                return new BooklistSeriesGroup(uuid, isUserDefinedStyle);

            default:
                return new BooklistGroup(id, uuid, isUserDefinedStyle);
        }
    }

    /**
     * Get a list of BooklistGroup's, one for each defined {@link GroupKey}'s.
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
        // Get the set of all valid <strong>Group</strong> values.
        // In other words: all valid kinds, <strong>except</strong> the BOOK.
        Set<Integer> set = GROUPS.keySet();
        set.remove(BOOK);
        for (@Id int id : set) {
            list.add(newInstance(id, uuid, isUserDefinedStyle));
        }
        return list;
    }

    /**
     * When one preference is visible, make the category visible.
     *
     * @param category to set
     */
    private static void setCategoryVisibility(@NonNull final PreferenceCategory category) {
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
     * was needed or on any failure
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
                        Locale locale = LocaleUtils.getUserLocale(context);
                        return DateUtils.getMonthName(locale, i, false);
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

    @NonNull
    static GroupKey getCompoundKey(@Id final int id) {
        return Objects.requireNonNull(GROUPS.get(id));
    }

    @Id
    public int getId() {
        return mId;
    }

    public String getName(@NonNull final Context context) {
        return Objects.requireNonNull(GROUPS.get(mId)).getName(context);
    }

    /**
     * Compound key of this Group.
     * <p>
     * The name will be of the form 'keyPrefix/id' where 'keyPrefix' is the keyPrefix specific
     * to the Group, and 'id' the id of the row, e.g. 's/18' for Series with id=18
     *
     * @return the key
     */
    @NonNull
    GroupKey getCompoundKey() {
        return Objects.requireNonNull(GROUPS.get(mId));
    }

    /**
     * Get the domain that contains the displayable data.
     * <p>
     * By default, this is the key domain. Override if needed.
     *
     * @return domain to display
     */
    @NonNull
    Domain getDisplayDomain() {
        return Objects.requireNonNull(GROUPS.get(mId)).getDomain();
    }

    /**
     * Get the domain expression that contains the displayable data.
     * <p>
     * By default, this is the key domain. Override if needed.
     *
     * @return domain expression
     */
    @NonNull
    String getDisplayDomainExpression() {
        return Objects.requireNonNull(GROUPS.get(mId)).getExpression();
    }

    /**
     * Get the domains represented by this group.
     *
     * @return the domains for this group and its outer groups.
     */
    @Nullable
    ArrayList<Domain> getDomains() {
        return mDomains;
    }

    /**
     * Set the domains represented by this group.
     *
     * @param domains list of domains.
     */
    void setDomains(@Nullable final ArrayList<Domain> domains) {
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
    @NonNull
    public Map<String, PPref> getPreferences() {
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

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
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

    @IntDef({BOOK,

             AUTHOR,
             SERIES,
             PUBLISHER,
             BOOKSHELF,
             READ_STATUS,

             LOANED,

             TITLE_LETTER,
             SERIES_TITLE_LETTER,

             GENRE,
             FORMAT,
             COLOR,
             LOCATION,
             LANGUAGE,
             RATING,

             DATE_PUBLISHED_YEAR,
             DATE_PUBLISHED_MONTH,
             DATE_FIRST_PUB_YEAR,
             DATE_FIRST_PUB_MONTH,

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
             DATE_ACQUIRED_DAY,
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface Id {

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
            super(SERIES, uuid, isUserDefinedStyle);
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

        @NonNull
        @Override
        Domain getDisplayDomain() {
            return DBDefinitions.DOM_SERIES_TITLE;
        }

        @NonNull
        @Override
        String getDisplayDomainExpression() {
            return DBDefinitions.TBL_SERIES.dot(DBDefinitions.KEY_SERIES_TITLE);
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

        @NonNull
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            Map<String, PPref> map = super.getPreferences();
            map.put(mAllSeries.getKey(), mAllSeries);
            return map;
        }

        @Override
        public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                          final boolean visible) {

            PreferenceCategory category = screen.findPreference(Prefs.psk_style_series);
            if (category != null) {
                SwitchPreference pShowAll = category
                        .findPreference(Prefs.pk_bob_books_under_multiple_series);
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
            super(AUTHOR, uuid, isUserDefinedStyle);
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

        @NonNull
        @Override
        Domain getDisplayDomain() {
            return DOM_AUTHOR_FORMATTED;
        }

        @NonNull
        @Override
        String getDisplayDomainExpression() {
            return showAuthorGivenNameFirst()
                   ? DAO.SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                   : DAO.SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN;
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

        @NonNull
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            Map<String, PPref> map = super.getPreferences();
            map.put(mAllAuthors.getKey(), mAllAuthors);
            map.put(mGivenNameFirst.getKey(), mGivenNameFirst);
            return map;
        }

        @Override
        public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                          final boolean visible) {

            PreferenceCategory category = screen.findPreference(Prefs.psk_style_author);
            if (category != null) {
                SwitchPreference pShowAll = category
                        .findPreference(Prefs.pk_bob_books_under_multiple_authors);
                if (pShowAll != null) {
                    pShowAll.setVisible(visible);
                }

                SwitchPreference pGivenNameFirst = category
                        .findPreference(Prefs.pk_bob_format_author_name);
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

    static class GroupKey
            implements Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<GroupKey> CREATOR =
                new Creator<GroupKey>() {
                    @Override
                    public GroupKey createFromParcel(@NonNull final Parcel source) {
                        return new GroupKey(source);
                    }

                    @Override
                    public GroupKey[] newArray(final int size) {
                        return new GroupKey[size];
                    }
                };

        private final int mLabelId;
        /** Unique keyPrefix used to represent a key in the hierarchy. */
        @NonNull
        private final String mKeyPrefix;

        /** Domain for this key. */
        @NonNull
        private final Domain mDomain;
        /** Expression for this key. */
        @NonNull
        private final String mExpression;

        /**
         * Constructor.
         *
         * @param labelId    User displayable label resource id
         * @param keyPrefix  the key prefix (as short as possible) to use for the compound key
         * @param domain     the domain to get the actual data from the Cursor
         * @param expression sql column expression for constructing the Cursor
         */
        GroupKey(final int labelId,
                 @NonNull final String keyPrefix,
                 @NonNull final Domain domain,
                 @NonNull final String expression) {
            mLabelId = labelId;
            mKeyPrefix = keyPrefix;
            mDomain = domain;
            mExpression = expression;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private GroupKey(@NonNull final Parcel in) {
            mLabelId = in.readInt();
            //noinspection ConstantConditions
            mKeyPrefix = in.readString();
            //noinspection ConstantConditions
            mDomain = in.readParcelable(getClass().getClassLoader());
            //noinspection ConstantConditions
            mExpression = in.readString();
        }

        @NonNull
        String getName(@NonNull final Context context) {
            return context.getString(mLabelId);
        }

        /**
         * Get the unique keyPrefix used to represent a key in the hierarchy.
         *
         * @return keyPrefix, never {@code null} but will be empty for a BOOK.
         */
        @NonNull
        String getKeyPrefix() {
            return mKeyPrefix;
        }

        @NonNull
        Domain getDomain() {
            return mDomain;
        }

        @NonNull
        String getExpression() {
            return mExpression;
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(mLabelId);
            dest.writeString(mKeyPrefix);
            dest.writeParcelable(mDomain, flags);
            dest.writeString(mExpression);
        }

        @NonNull
        @Override
        public String toString() {
            return "GroupKey{"
                   + "mLabelId=`" + App.getAppContext().getString(mLabelId) + '`'
                   + ", mKeyPrefix='" + mKeyPrefix + '\''
                   + ", mDomain=" + mDomain
                   + ", mExpression=" + mExpression
                   + '}';
        }
    }
}

