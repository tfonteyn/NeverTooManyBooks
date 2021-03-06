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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.StringList;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Represents an Author.
 *
 * <strong>Note:</strong> "type" is a column of {@link DBDefinitions#TBL_BOOK_AUTHOR}
 * So this class does not strictly represent an Author, but a "BookAuthor"
 * When the type is disregarded, it is a real Author representation.
 * <p>
 * Author types:
 * <a href="http://www.loc.gov/marc/relators/relaterm.html">
 * http://www.loc.gov/marc/relators/relaterm.html</a>
 * <p>
 * ENHANCE: add a column 'real-name-id' with the id of another author entry
 * i.e one entry typed 'pseudonym' with the 'real-name-id' column pointing to the real name entry.
 */
public class Author
        implements Entity, Mergeable {

    /** {@link Parcelable}. */
    public static final Creator<Author> CREATOR = new Creator<Author>() {
        @Override
        public Author createFromParcel(@NonNull final Parcel source) {
            return new Author(source);
        }

        @Override
        public Author[] newArray(final int size) {
            return new Author[size];
        }
    };

    /*
     * {@link DBDefinitions#DOM_BOOK_AUTHOR_TYPE_BITMASK}.
     * NEWTHINGS: author type: add a bit flag
     * Never change the bit value!
     */

    /** Generic Author; the default. A single person created the book. */
    public static final int TYPE_UNKNOWN = 0;

    /** WRITER: primary or only writer. i.e. in contrast to any of the below. */
    public static final int TYPE_WRITER = 1;

    /** WRITER: not distinguished for now. If we do, use TYPE_ORIGINAL_SCRIPT_WRITER = 1 << 1; */
    public static final int TYPE_ORIGINAL_SCRIPT_WRITER = TYPE_WRITER;

    /** WRITER: the foreword. */
    public static final int TYPE_FOREWORD = 1 << 2;
    /** WRITER: the afterword. */
    public static final int TYPE_AFTERWORD = 1 << 3;
    /** WRITER: translator. */
    public static final int TYPE_TRANSLATOR = 1 << 4;
    /** WRITER: introduction. (some sites makes a distinction with a foreword). */
    public static final int TYPE_INTRODUCTION = 1 << 5;


    /** editor (e.g. of an anthology). */
    public static final int TYPE_EDITOR = 1 << 6;
    /** generic collaborator. */
    public static final int TYPE_CONTRIBUTOR = 1 << 7;


    /** ARTIST: cover. */
    public static final int TYPE_COVER_ARTIST = 1 << 8;
    /** ARTIST: cover inking (if different from above). */
    public static final int TYPE_COVER_INKING = 1 << 9;

    /** Audio books. */
    public static final int TYPE_NARRATOR = 1 << 10;

    /** COLOR: cover. */
    public static final int TYPE_COVER_COLORIST = 1 << 11;


    /** ARTIST: art work; could be illustrations, or the pages of a comic. */
    public static final int TYPE_ARTIST = 1 << 12;
    /** ARTIST: art work inking (if different from above). */
    public static final int TYPE_INKING = 1 << 13;

    // unused for now
    // public static final int TYPE_ = 1 << 14;

    /** COLOR: internal colorist. */
    public static final int TYPE_COLORIST = 1 << 15;

    /**
     * Any: indicate that this name entry is a pseudonym.
     * ENHANCE: pseudonym flag on its own this is not much use; need to link it with the real name
     */
    public static final int TYPE_PSEUDONYM = 1 << 16;

    /**
     * All valid bits for the type.
     * NEWTHINGS: author type: add to the mask
     */
    public static final int TYPE_BITMASK_ALL =
            TYPE_UNKNOWN
            | TYPE_WRITER | TYPE_ORIGINAL_SCRIPT_WRITER | TYPE_FOREWORD | TYPE_AFTERWORD
            | TYPE_TRANSLATOR | TYPE_INTRODUCTION | TYPE_EDITOR | TYPE_CONTRIBUTOR
            | TYPE_COVER_ARTIST | TYPE_COVER_INKING | TYPE_NARRATOR | TYPE_COVER_COLORIST
            | TYPE_ARTIST | TYPE_INKING | TYPE_COLORIST | TYPE_PSEUDONYM;

    /** Maps the type-bit to a string resource for the type-label. */
    private static final Map<Integer, Integer> TYPES = new LinkedHashMap<>();
    /**
     * ENHANCE: author middle name; needs internationalisation ?
     * <p>
     * Ursula Le Guin
     * Marianne De Pierres
     * A. E. Van Vogt
     * Rip Von Ronkel
     */
    private static final Pattern FAMILY_NAME_PREFIX_PATTERN =
            Pattern.compile("(le|de|van|von)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /**
     * j/s lower or upper case
     * <p>
     * Foo Bar Jr.
     * Foo Bar Jr
     * Foo Bar Junior
     * Foo Bar Sr.
     * Foo Bar Sr
     * Foo Bar Senior
     * Foo Bar II
     * Charles Emerson Winchester III
     * <p>
     * same as above, but with comma:
     * Foo Bar, Jr.
     * <p>
     * <p>
     * Not covered yet, and seen in the wild:
     * "James jr. Tiptree" -> suffix as a middle name.
     * "Dr. Asimov" -> titles... pre or suffixed
     */
    private static final Pattern FAMILY_NAME_SUFFIX_PATTERN =
            Pattern.compile("jr\\.|jr|junior|sr\\.|sr|senior|II|III",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /*
     * NEWTHINGS: author type: add the label for the type
     * This is a LinkedHashMap, so the order below is the order they will show up on the screen.
     */
    static {
        TYPES.put(TYPE_WRITER, R.string.lbl_author_type_writer);

        TYPES.put(TYPE_TRANSLATOR, R.string.lbl_author_type_translator);
        TYPES.put(TYPE_INTRODUCTION, R.string.lbl_author_type_intro);
        TYPES.put(TYPE_EDITOR, R.string.lbl_author_type_editor);
        TYPES.put(TYPE_CONTRIBUTOR, R.string.lbl_author_type_contributor);
        TYPES.put(TYPE_NARRATOR, R.string.lbl_author_type_narrator);

        TYPES.put(TYPE_ARTIST, R.string.lbl_author_type_artist);
        TYPES.put(TYPE_INKING, R.string.lbl_author_type_inking);
        TYPES.put(TYPE_COLORIST, R.string.lbl_author_type_colorist);

        TYPES.put(TYPE_COVER_ARTIST, R.string.lbl_author_type_cover_artist);
        TYPES.put(TYPE_COVER_INKING, R.string.lbl_author_type_cover_inking);
        TYPES.put(TYPE_COVER_COLORIST, R.string.lbl_author_type_cover_colorist);
    }

    /** Row ID. */
    private long mId;
    /** Family name(s). */
    @NonNull
    private String mFamilyName;
    /** Given name(s). */
    @NonNull
    private String mGivenNames;
    /** whether we have all we want from this Author. */
    private boolean mIsComplete;
    /** Bitmask. */
    @Type
    private int mType = TYPE_UNKNOWN;

    /**
     * Constructor.
     *
     * @param familyName Family name
     * @param givenNames Given names
     */
    public Author(@NonNull final String familyName,
                  @NonNull final String givenNames) {
        mFamilyName = familyName.trim();
        mGivenNames = givenNames.trim();
    }

    /**
     * Constructor.
     *
     * @param familyName Family name
     * @param givenNames Given names
     * @param isComplete whether an Author is completed, i.e if the user has all they
     *                   want from this Author.
     */
    public Author(@NonNull final String familyName,
                  @NonNull final String givenNames,
                  final boolean isComplete) {
        mFamilyName = familyName.trim();
        mGivenNames = givenNames.trim();
        mIsComplete = isComplete;
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the Author in the database.
     * @param rowData with data
     */
    public Author(final long id,
                  @NonNull final DataHolder rowData) {
        mId = id;
        mFamilyName = rowData.getString(DBKey.KEY_AUTHOR_FAMILY_NAME);
        mGivenNames = rowData.getString(DBKey.KEY_AUTHOR_GIVEN_NAMES);
        mIsComplete = rowData.getBoolean(DBKey.BOOL_AUTHOR_IS_COMPLETE);

        if (rowData.contains(DBKey.KEY_BOOK_AUTHOR_TYPE_BITMASK)) {
            mType = rowData.getInt(DBKey.KEY_BOOK_AUTHOR_TYPE_BITMASK);
        }
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Author(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mFamilyName = in.readString();
        //noinspection ConstantConditions
        mGivenNames = in.readString();
        mIsComplete = in.readByte() != 0;
        mType = in.readInt();
    }

    @NonNull
    public static Author createUnknownAuthor(@NonNull final Context context) {
        final String unknownAuthor = context.getString(R.string.unknown_author);
        return new Author(unknownAuthor, "");
    }

    /**
     * Parse a string into a family/given name pair.
     * If the string contains a comma (and the part after it is not a recognised suffix)
     * then the string is assumed to be in the format of "family, given-names"
     * All other formats are decoded as complete as possible.
     * <p>
     * Recognised pre/suffixes: see {@link #FAMILY_NAME_PREFIX_PATTERN}
     * and {@link #FAMILY_NAME_SUFFIX_PATTERN}
     * <ul>Not covered:
     *      <li>multiple, and not concatenated, family names.</li>
     *      <li>more than 1 un-encoded comma.</li>
     * </ul>
     *
     * <strong>Note:</strong> uses a simple String decoder.
     * Any complex decoding for JSON format must be done before calling here.
     *
     * @param name a String containing the name
     *
     * @return Author
     */
    @NonNull
    public static Author from(@NonNull final String name) {
        String uName = ParseUtils.unEscape(name);

        // take into account that there can be escaped commas....
        // do we really need this except when reading from a backup ?
        final List<String> tmp = StringList.newInstance().decode(uName, ',', true);
        if (tmp.size() > 1) {
            final Matcher suffixMatcher = FAMILY_NAME_SUFFIX_PATTERN.matcher(tmp.get(1));
            if (suffixMatcher.find()) {
                // concatenate without the comma. Further processing will take care of the suffix.
                uName = tmp.get(0) + ' ' + tmp.get(1);
            } else {
                // not a suffix, assume the names are already formatted.
                return new Author(tmp.get(0), tmp.get(1));
            }
        }

        final String[] names = uName.split(" ");
        // two easy cases
        switch (names.length) {
            case 1:
                return new Author(names[0], "");
            case 2:
                return new Author(names[1], names[0]);
            default:
                break;
        }

        // we have 3 or more parts, check the family name for suffixes and prefixes
        final StringBuilder buildFamilyName = new StringBuilder();
        // the position to check, start at the end.
        int pos = names.length - 1;

        final Matcher suffixMatcher = FAMILY_NAME_SUFFIX_PATTERN.matcher(names[pos]);
        if (suffixMatcher.find()) {
            // suffix and the element before it are part of the last name.
            buildFamilyName.append(names[pos - 1]).append(' ').append(names[pos]);
            pos -= 2;
        } else {
            // no suffix.
            buildFamilyName.append(names[pos]);
            pos--;
        }

        // the last name could also have a prefix
        final Matcher middleNameMatcher = FAMILY_NAME_PREFIX_PATTERN.matcher(names[pos]);
        if (middleNameMatcher.find()) {
            // insert it at the front of the family name
            buildFamilyName.insert(0, names[pos] + ' ');
            pos--;
        }

        // everything else are considered given names
        final StringBuilder buildGivenNames = new StringBuilder();
        for (int i = 0; i <= pos; i++) {
            buildGivenNames.append(names[i]).append(' ');
        }

        return new Author(buildFamilyName.toString(), buildGivenNames.toString());
    }

    /**
     * Get the global default for this preference.
     *
     * @return {@code true} if we want "given-names family" formatted authors.
     */
    private static boolean isShowGivenNameFirst() {
        return ServiceLocator.getGlobalPreferences()
                             .getBoolean(Prefs.pk_show_author_name_given_first, false);
    }

    /**
     * Get the Authors. If there is more than one, we get the first Author + an ellipsis.
     *
     * @param context Current context
     * @param authors list to condense
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public static String getCondensedNames(@NonNull final Context context,
                                           @NonNull final List<Author> authors) {
        // could/should? use AuthorListFormatter
        if (authors.isEmpty()) {
            return "";
        } else {
            final String text = authors.get(0).getLabel(context);
            if (authors.size() > 1) {
                return context.getString(R.string.and_others, text);
            }
            return text;
        }
    }

    /**
     * Passed a list of Author, remove duplicates.
     * Consolidates author/- and author/type.
     * <p>
     * ENHANCE: Add aliases table to allow further pruning
     * (e.g. Joe Haldeman == Joe W Haldeman).
     *
     * @param list         List to clean up
     * @param context      Current context
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return {@code true} if the list was modified.
     */
    public static boolean pruneList(@NonNull final Collection<Author> list,
                                    @NonNull final Context context,
                                    final boolean lookupLocale,
                                    @NonNull final Locale bookLocale) {
        if (list.isEmpty()) {
            return false;
        }

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        final EntityMerger<Author> entityMerger = new EntityMerger<>(list);
        while (entityMerger.hasNext()) {
            final Author current = entityMerger.next();

            final Locale locale;
            if (lookupLocale) {
                locale = current.getLocale(context, bookLocale);
            } else {
                locale = bookLocale;
            }

            // Don't lookup the locale a 2nd time.
            authorDao.fixId(context, current, false, locale);
            entityMerger.merge(current);
        }

        return entityMerger.isListModified();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mFamilyName);
        dest.writeString(mGivenNames);
        dest.writeByte((byte) (mIsComplete ? 1 : 0));
        dest.writeInt(mType);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the 'complete' status of the Author.
     *
     * @return {@code true} if the Author is complete
     */
    public boolean isComplete() {
        return mIsComplete;
    }

    /**
     * Set the 'complete' status of the Author.
     *
     * @param isComplete Flag indicating the user considers this Author to be 'complete'
     */
    public void setComplete(final boolean isComplete) {
        mIsComplete = isComplete;
    }

    @Type
    public int getType() {
        return mType;
    }

    /**
     * Set the type(s).
     *
     * @param type to set
     */
    public void setType(@Type final int type) {
        mType = type & TYPE_BITMASK_ALL;
    }

    /**
     * Convenience method to set the Type.
     *
     * @param type list of bits
     */
    public void setType(@NonNull final Iterable<Integer> type) {
        int bitmask = 0;
        for (final Integer bit : type) {
            bitmask += bit;
        }
        mType = bitmask;
    }

    /**
     * Add a type to the current type(s).
     *
     * @param type to add
     */
    public void addType(@Type final int type) {
        mType |= type & TYPE_BITMASK_ALL;
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    /**
     * Return the <strong>preferred</strong> 'human readable' version of the name.
     *
     * @param context Current context
     *
     * @return formatted name
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return getFormattedName(isShowGivenNameFirst());
    }

    /**
     * Return the <strong>specified</strong> 'human readable' version of the name.
     *
     * @param givenNameFirst {@code true} if we want "given-names family-name" formatted name.
     *                       {@code false} for "last-family, first-names"
     *
     * @return formatted name
     */
    @NonNull
    public String getFormattedName(final boolean givenNameFirst) {
        if (mGivenNames.isEmpty()) {
            return mFamilyName;
        } else {
            if (givenNameFirst) {
                return mGivenNames + ' ' + mFamilyName;
            } else {
                return mFamilyName + ", " + mGivenNames;
            }
        }
    }

    /**
     * Return the preferred 'human readable' version of the name,
     * combined (if enabled) with the author type.
     * The latter uses HTML formatting.
     *
     * @param context Current context
     *
     * @return HTML formatted name and type.
     */
    @NonNull
    public String getExtLabel(@NonNull final Context context) {
        String authorLabel = getLabel(context);
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        if (DBKey.isUsed(global, DBKey.KEY_BOOK_AUTHOR_TYPE_BITMASK)) {
            final String type = getTypeLabels(context);
            if (!type.isEmpty()) {
                authorLabel += " <small><i>" + type + "</i></small>";
            }
        }
        return authorLabel;
    }

    /**
     * Get a CSV string with the type of this author; or the empty string if no specific types
     * are set.
     *
     * @param context Current context
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    private String getTypeLabels(@NonNull final Context context) {
        if (mType != TYPE_UNKNOWN) {
            final List<String> list = TYPES.entrySet()
                                           .stream()
                                           .filter(entry -> (entry.getKey() & (long) mType) != 0)
                                           .map(entry -> context.getString(entry.getValue()))
                                           .collect(Collectors.toList());

            if (!list.isEmpty()) {
                return context.getString(R.string.brackets, TextUtils.join(", ", list));
            }
        }
        return "";
    }


    /**
     * Syntax sugar to set the names in one call.
     *
     * @param familyName Family name
     * @param givenNames Given names
     */
    public void setName(@NonNull final String familyName,
                        @NonNull final String givenNames) {
        mFamilyName = familyName;
        mGivenNames = givenNames;
    }

    @NonNull
    public String getFamilyName() {
        return mFamilyName;
    }

    @NonNull
    public String getGivenNames() {
        return mGivenNames;
    }

    /**
     * Replace local details from another author.
     *
     * @param source            Author to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public void copyFrom(@NonNull final Author source,
                         final boolean includeBookFields) {
        mFamilyName = source.mFamilyName;
        mGivenNames = source.mGivenNames;
        mIsComplete = source.mIsComplete;
        if (includeBookFields) {
            mType = source.mType;
        }
    }

    /**
     * Get the Locale of the actual item; e.g. a book written in Spanish should
     * return an Spanish Locale even if for example the user runs the app in German,
     * and the device in Danish.
     * <p>
     * An item not using a locale, should just returns the fallbackLocale itself.
     *
     * @param context    Current context
     * @param userLocale Locale to use if the item does not have a Locale of its own.
     *
     * @return the item Locale, or the userLocale.
     */
    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final Locale userLocale) {
        //ENHANCE: The Author Locale should be based on the main language the author writes in.
        return userLocale;
    }

    @Override
    public boolean merge(@NonNull final Mergeable mergeable) {
        final Author incoming = (Author) mergeable;

        // always combine the types
        mType = mType | incoming.getType();

        // if this object has no id, and the incoming has an id, then we copy the id.
        if (mId == 0 && incoming.getId() > 0) {
            mId = incoming.getId();
        }
        return true;
    }

    /**
     * Diacritic neutral version of {@link  #hashCode()} without id.
     *
     * @return hashcode
     */
    public int asciiHashCodeNoId() {
        return Objects.hash(ParseUtils.toAscii(mFamilyName), ParseUtils.toAscii(mGivenNames));
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mFamilyName, mGivenNames);
    }

    /**
     * Equality: <strong>id, family and given-names</strong>.
     * <ul>
     *   <li>'type' is on a per book basis. See {@link #pruneList}.</li>
     *   <li>'isComplete' is a user setting and is ignored.</li>
     * </ul>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Author that = (Author) obj;
        // if both 'exist' but have different ID's -> different.
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        return Objects.equals(mFamilyName, that.mFamilyName)
               && Objects.equals(mGivenNames, that.mGivenNames);
    }

    @Override
    @NonNull
    public String toString() {
        final StringJoiner sj = new StringJoiner(",", "Type{", "}");

        if ((mType & TYPE_WRITER) != 0) {
            sj.add("TYPE_WRITER");
        }
//        if ((mType & TYPE_ORIGINAL_SCRIPT_WRITER) != 0) {
//            sj.add("TYPE_ORIGINAL_SCRIPT_WRITER");
//        }
        if ((mType & TYPE_FOREWORD) != 0) {
            sj.add("TYPE_FOREWORD");
        }
        if ((mType & TYPE_AFTERWORD) != 0) {
            sj.add("TYPE_AFTERWORD");
        }


        if ((mType & TYPE_TRANSLATOR) != 0) {
            sj.add("TYPE_TRANSLATOR");
        }
        if ((mType & TYPE_INTRODUCTION) != 0) {
            sj.add("TYPE_INTRODUCTION");
        }
        if ((mType & TYPE_EDITOR) != 0) {
            sj.add("TYPE_EDITOR");
        }
        if ((mType & TYPE_CONTRIBUTOR) != 0) {
            sj.add("TYPE_CONTRIBUTOR");
        }


        if ((mType & TYPE_COVER_ARTIST) != 0) {
            sj.add("TYPE_COVER_ARTIST");
        }
        if ((mType & TYPE_COVER_INKING) != 0) {
            sj.add("TYPE_COVER_INKING");
        }
        if ((mType & TYPE_NARRATOR) != 0) {
            sj.add("TYPE_NARRATOR");
        }
        if ((mType & TYPE_COVER_COLORIST) != 0) {
            sj.add("TYPE_COVER_COLORIST");
        }


        if ((mType & TYPE_ARTIST) != 0) {
            sj.add("TYPE_ARTIST");
        }
        if ((mType & TYPE_INKING) != 0) {
            sj.add("TYPE_INKING");
        }
        if ((mType & TYPE_COLORIST) != 0) {
            sj.add("TYPE_COLORIST");
        }
        if ((mType & TYPE_PSEUDONYM) != 0) {
            sj.add("TYPE_PSEUDONYM");
        }

        return "Author{"
               + "mId=" + mId
               + ", mFamilyName=`" + mFamilyName + '`'
               + ", mGivenNames=`" + mGivenNames + '`'
               + ", mIsComplete=" + mIsComplete
               + ", mType=0b" + Integer.toBinaryString(mType)
               + ", mType=" + sj.toString()
               + '}';
    }

    public enum Details {
        Full, Normal, Short
    }

    // NEWTHINGS: author type: add to the IntDef
    @IntDef(flag = true,
            value = {TYPE_UNKNOWN,
                     TYPE_WRITER, TYPE_FOREWORD, TYPE_AFTERWORD,
                     TYPE_TRANSLATOR, TYPE_INTRODUCTION, TYPE_EDITOR, TYPE_CONTRIBUTOR,
                     TYPE_COVER_ARTIST, TYPE_COVER_INKING, TYPE_NARRATOR, TYPE_COVER_COLORIST,
                     TYPE_ARTIST, TYPE_INKING, TYPE_COLORIST, TYPE_PSEUDONYM
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {

    }
}
