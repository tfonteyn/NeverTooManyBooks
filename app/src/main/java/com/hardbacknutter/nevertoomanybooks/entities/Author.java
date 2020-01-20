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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListItem;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.SelectableItem;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StringList;

/**
 * Class to hold author data.
 *
 * <strong>Note:</strong> "type" is a column of {@link DBDefinitions#TBL_BOOK_AUTHOR}
 * So this class does not strictly represent an Author, but a "BookAuthor"
 * When the type is disregarded, it is a real Author representation.
 */
public class Author
        implements Parcelable, ItemWithFixableId, Entity {

    /** {@link Parcelable}. */
    public static final Creator<Author> CREATOR =
            new Creator<Author>() {
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

    /** primary or only writer. i.e. in contrast to any of the below. */
    public static final int TYPE_WRITER = 1;
//    public static final int TYPE_ = 1 << 1;
//    public static final int TYPE_ = 1 << 2;
//    public static final int TYPE_ = 1 << 3;

    /** translator. */
    public static final int TYPE_TRANSLATOR = 1 << 4;
    /** foreword/afterword/introduction etc. */
    public static final int TYPE_INTRODUCTION = 1 << 5;
    /** editor (e.g. of an anthology). */
    public static final int TYPE_EDITOR = 1 << 6;
    /** generic collaborator. */
    public static final int TYPE_CONTRIBUTOR = 1 << 7;


    /** cover artist. */
    public static final int TYPE_COVER_ARTIST = 1 << 8;
    /** cover artist. */
    public static final int TYPE_COVER_INKING = 1 << 9;

//    public static final int TYPE_ = 1 << 10;
    /** cover colorist. */
    public static final int TYPE_COVER_COLORIST = 1 << 11;


    /** Internal art work; could be illustrations, or the pages of a comic. */
    public static final int TYPE_ARTIST = 1 << 12;
    /** Internal art work (if addition to artwork); comics. */
    public static final int TYPE_INKING = 1 << 13;

//    public static final int TYPE_ = 1 << 14;

    /** internal colorist. */
    public static final int TYPE_COLORIST = 1 << 15;

    //    15 more bits available in the higher word (minus the sign-bit)


    /** String encoding use: separator between family name and given-names. */
    public static final char NAME_SEPARATOR = ',';
    private static final Map<Integer, Integer> TYPES = new LinkedHashMap<>();
    /** All valid bits for the type. */
    private static final int TYPE_MASK =
            TYPE_UNKNOWN
            | TYPE_WRITER
            | TYPE_TRANSLATOR | TYPE_INTRODUCTION | TYPE_EDITOR | TYPE_CONTRIBUTOR
            | TYPE_COVER_ARTIST | TYPE_COVER_INKING | TYPE_COVER_COLORIST
            | TYPE_ARTIST | TYPE_INKING | TYPE_COLORIST;
    /**
     * ENHANCE: author middle name; needs internationalisation ?
     * <p>
     * Ursula Le Guin
     * Marianne De Pierres
     * A. E. Van Vogt
     * Rip Von Ronkel
     */
    private static final Pattern FAMILY_NAME_PREFIX =
            Pattern.compile("(le|de|van|von)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /**
     * ENHANCE: author name suffixes; needs internationalisation ? probably not.
     * <p>
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
    private static final Pattern FAMILY_NAME_SUFFIX =
            Pattern.compile("jr\\.|jr|junior|sr\\.|sr|senior|II|III",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /*
     * NEWTHINGS: author type: add the label for the type
     *
     * This is a LinkedHashMap, so the order below is the order they will show up on the screen.
     */
    static {
        TYPES.put(TYPE_WRITER, R.string.lbl_author_type_writer);

        TYPES.put(TYPE_TRANSLATOR, R.string.lbl_author_type_translator);
        TYPES.put(TYPE_INTRODUCTION, R.string.lbl_author_type_intro);
        TYPES.put(TYPE_EDITOR, R.string.lbl_author_type_editor);
        TYPES.put(TYPE_CONTRIBUTOR, R.string.lbl_author_type_contributor);

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
     * @param id        ID of the Author in the database.
     * @param cursorRow with data
     */
    public Author(final long id,
                  @NonNull final CursorRow cursorRow) {
        mId = id;
        mFamilyName = cursorRow.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
        mGivenNames = cursorRow.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
        mIsComplete = cursorRow.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
        if (cursorRow.contains(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK)) {
            mType = cursorRow.getInt(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK);
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
        mIsComplete = in.readInt() != 0;
        mType = in.readInt();
    }

    @NonNull
    public static Author createUnknownAuthor(@NonNull final Context context) {
        String unknown = context.getString(R.string.unknown).toUpperCase(Locale.getDefault());
        return new Author(unknown, "");
    }

    /**
     * Parse a string into a family/given name pair.
     * If the string contains a comma (and the part after it is not a recognised suffix)
     * then the string is assumed to be in the format of "family, given-names"
     * All other formats are decoded as complete as possible.
     * <p>
     * Recognised pre/suffixes: see {@link #FAMILY_NAME_PREFIX} and {@link #FAMILY_NAME_SUFFIX}
     * <ul>Not covered:
     * <li>multiple, and not concatenated, family names.</li>
     * <li>more than 1 un-encoded comma.</li>
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
    public static Author fromString(@NonNull final String name) {
        String uName = ParseUtils.unEscape(name);

        List<String> tmp = StringList.newInstance().decode(uName, NAME_SEPARATOR, true);
        if (tmp.size() > 1) {
            Matcher matchSuffix = FAMILY_NAME_SUFFIX.matcher(tmp.get(1));
            if (!matchSuffix.find()) {
                // not a suffix, assume the names are already formatted.
                return new Author(tmp.get(0), tmp.get(1));
            } else {
                // concatenate without the comma. Further processing will take care of the suffix.
                uName = tmp.get(0) + ' ' + tmp.get(1);
            }
        }

        String[] names = uName.split(" ");
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
        StringBuilder buildFamilyName = new StringBuilder();
        // the position to check, start at the end.
        int pos = names.length - 1;

        Matcher matchSuffix = FAMILY_NAME_SUFFIX.matcher(names[pos]);
        if (!matchSuffix.find()) {
            // no suffix.
            buildFamilyName.append(names[pos]);
            pos--;
        } else {
            // suffix and the element before it are part of the last name.
            buildFamilyName.append(names[pos - 1]).append(' ').append(names[pos]);
            pos -= 2;
        }

        // the last name could also have a prefix
        Matcher matchMiddleName = FAMILY_NAME_PREFIX.matcher(names[pos]);
        if (matchMiddleName.find()) {
            // insert it at the front of the family name
            buildFamilyName.insert(0, names[pos] + ' ');
            pos--;
        }

        // everything else are considered given names
        StringBuilder buildGivenNames = new StringBuilder();
        for (int i = 0; i <= pos; i++) {
            buildGivenNames.append(names[i]).append(' ');
        }

        return new Author(buildFamilyName.toString(), buildGivenNames.toString());
    }

    public boolean isComplete() {
        return mIsComplete;
    }

    public void setComplete(final boolean complete) {
        mIsComplete = complete;
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
        mType = type & TYPE_MASK;
    }

    /**
     * Convenience method to set the Type.
     *
     * @param type list of bits
     */
    public void setType(@NonNull final Iterable<Integer> type) {
        int bitmask = 0;
        for (Integer bit : type) {
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
        mType |= type & TYPE_MASK;
    }

    /**
     * Gets a complete list of Types each reflecting the Author being that type or not.
     *
     * @return list with {@link SelectableItem}
     */
    @NonNull
    public ArrayList<CheckListItem> getEditableTypeList(@NonNull final Context context) {

        ArrayList<CheckListItem> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : TYPES.entrySet()) {
            Integer key = entry.getKey();
            boolean selected = (key & mType) != 0;
            list.add(new SelectableItem(context.getString(entry.getValue()), key, selected));
        }
        return list;
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    /**
     * Return the 'human readable' version of the name (e.g. 'Isaac Asimov').
     *
     * @param context Current context
     *
     * @return formatted Author name
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        if (!mGivenNames.isEmpty()) {
            return mGivenNames + ' ' + mFamilyName;
        } else {
            return mFamilyName;
        }
    }

    /**
     * Return the name in a sortable form (e.g. 'Asimov, Isaac').
     *
     * @param context Current context
     *
     * @return formatted name
     */
    @NonNull
    public String getSorting(@NonNull final Context context) {
        if (!mGivenNames.isEmpty()) {
            if (Prefs.sortAuthorGivenNameFirst(context)) {
                return mGivenNames + ' ' + mFamilyName;
            } else {
                return mFamilyName + ", " + mGivenNames;
            }
        } else {
            return mFamilyName;
        }
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
    public String getTypeLabels(@NonNull final Context context) {
        if (mType != TYPE_UNKNOWN) {
            List<String> list = Csv.bitmaskToList(context, TYPES, mType);
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

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mFamilyName);
        dest.writeString(mGivenNames);
        dest.writeInt(mIsComplete ? 1 : 0);
        dest.writeInt(mType);
    }

    /**
     * Write the extra data to the JSON object.
     *
     * @param data which {@link #fromJson(JSONObject)} will read
     *
     * @throws JSONException on failure
     */
    public void toJson(@NonNull final JSONObject data)
            throws JSONException {

        if (mIsComplete) {
            data.put(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, true);
        }
        if (mType != Author.TYPE_UNKNOWN) {
            data.put(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK, mType);
        }
    }

    /**
     * Read the extra data from the JSON object.
     *
     * @param data as written by {@link #toJson(JSONObject)}
     */
    public void fromJson(@NonNull final JSONObject data) {

        if (data.has(DBDefinitions.KEY_AUTHOR_IS_COMPLETE)) {
            mIsComplete = data.optBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
        } else if (data.has("complete")) {
            mIsComplete = data.optBoolean("complete");
        }

        if (data.has(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK)) {
            setType(data.optInt(DBDefinitions.KEY_AUTHOR_TYPE_BITMASK));
        } else if (data.has("type")) {
            setType(data.optInt("type"));
        }
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
     * ENHANCE: The Locale of the Author should be based on the main language the author writes in.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final DAO db,
                            @NonNull final Locale userLocale) {
        return userLocale;
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final DAO db,
                      @NonNull final Locale locale) {
        mId = db.getAuthorId(context, this, locale);
        return mId;
    }

    /**
     * An Author with a given set of Family and Given-names is defined by a unique ID.<br>
     * The other fields are not significant in a list of Authors.
     * <ul>
     * <li>The 'isComplete' is a user setting.</li>
     * <li>The 'type' is on a per book basis.</li>
     * </ul>
     */
    @SuppressWarnings("SameReturnValue")
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Equality: <strong>id, family and given-names</strong>.
     *
     * @return hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(mId, mFamilyName, mGivenNames);
    }

    /**
     * Equality: <strong>id, family and given-names</strong>.
     * <p>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same ID<br>
     * AND family/given-names are equal</li>
     * <li>if both are 'new' check if family/given-names are equal</li>
     * <p>
     * <strong>Compare is CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.<br>
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Author that = (Author) obj;
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
        return "Author{"
               + "mId=" + mId
               + ", mFamilyName=`" + mFamilyName + '`'
               + ", mGivenNames=`" + mGivenNames + '`'
               + ", mIsComplete=" + mIsComplete
               + ", mType=0b" + Integer.toBinaryString(mType)
               + '}';
    }

    @IntDef(flag = true,
            value = {TYPE_UNKNOWN,
                     TYPE_WRITER,
                     TYPE_TRANSLATOR, TYPE_INTRODUCTION, TYPE_EDITOR, TYPE_CONTRIBUTOR,
                     TYPE_COVER_ARTIST, TYPE_COVER_INKING, TYPE_COVER_COLORIST,
                     TYPE_ARTIST, TYPE_INKING, TYPE_COLORIST})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {

    }
}
