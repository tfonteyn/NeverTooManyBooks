/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.database.cursors.ColumnMapper;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.utils.StringList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to hold author data.
 *
 * @author Philip Warner
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

    /** String encoding use. */
    private static final char FIELD_SEPARATOR = ',';

    /**
     * ENHANCE: author middle name; needs internationalisation ?
     * <p>
     * Ursula Le Guin
     * Marianne De Pierres
     * A. E. Van Vogt
     * Rip Von Ronkel
     */
    private static final Pattern FAMILY_NAME_PREFIX = Pattern.compile("[LlDd]e|[Vv][oa]n");
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
    private static final Pattern FAMILY_NAME_SUFFIX = Pattern
            .compile("[Jj]r\\.|[Jj]r|[Jj]unior|[Ss]r\\.|[Ss]r|[Ss]enior|II|III");

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
     * Constructor without ID.
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
     * @param id     ID of the Author in the database.
     * @param mapper for the cursor.
     */
    public Author(final long id,
                  @NonNull final ColumnMapper mapper) {
        mId = id;
        mFamilyName = mapper.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
        mGivenNames = mapper.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
        mIsComplete = mapper.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
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
    }

    /**
     * Parse a string into a family/given name pair.
     * The name can be in either "family, given" or "given family" format.
     * <p>
     * Special rules, see {@link #FAMILY_NAME_PREFIX} and {@link #FAMILY_NAME_SUFFIX}
     * <p>
     * Not covered:
     * - multiple, and not concatenated, family names.
     * - more then 1 un-encoded comma.
     *
     * <p>
     * TODO: would be nice to redo with a rules based approach.
     *
     * @param name a String containing the name
     *
     * @return Author
     */
    @NonNull
    public static Author fromString(@NonNull String name) {
        List<String> tmp = new StringList<String>().decode(name, true, FIELD_SEPARATOR);
        if (tmp.size() > 1) {
            Matcher matchSuffix = FAMILY_NAME_SUFFIX.matcher(tmp.get(1));
            if (!matchSuffix.find()) {
                // not a suffix, assume the names are already formatted.
                return new Author(tmp.get(0), tmp.get(1));
            } else {
                // concatenate without the comma. Further processing will take care of the suffix.
                name = tmp.get(0) + ' ' + tmp.get(1);
            }
        }

        String[] names = name.split(" ");
        // two easy cases
        switch (names.length) {
            case 1:
                return new Author(names[0], "");
            case 2:
                return new Author(names[1], names[0]);
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

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
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

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mFamilyName);
        dest.writeString(mGivenNames);
        dest.writeInt(mIsComplete ? 1 : 0);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
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
     * Return the 'human readable' version of the name (e.g. 'Isaac Asimov').
     *
     * @return formatted Author name
     */
    @NonNull
    public String getLabel() {
        if (!mGivenNames.isEmpty()) {
            return mGivenNames + ' ' + mFamilyName;
        } else {
            return mFamilyName;
        }
    }

    /**
     * Return the name in a sortable form (e.g. 'Asimov, Isaac').
     *
     * @return formatted name
     */
    @NonNull
    public String getSortName() {
        if (!mGivenNames.isEmpty()) {
            return mFamilyName + ", " + mGivenNames;
        } else {
            return mFamilyName;
        }
    }


    @Override
    @NonNull
    public String toString() {
        return "Author{"
                + "mId=" + mId
                + ", mFamilyName=`" + mFamilyName + '`'
                + ", mGivenNames=`" + mGivenNames + '`'
                + ", mIsComplete=" + mIsComplete
                + '}';
    }

    /**
     * Support for encoding to a text file.
     *
     * @return the object encoded as a String.
     * <p>
     * "familyName, givenName"
     */
    public String stringEncoded() {
        // Always use givenNames even if blank because we need to KNOW they are blank.
        // There is a slim chance that family name may contain spaces (e.g. 'Anonymous Anarchists').
        return StringList.escapeListItem(mFamilyName, FIELD_SEPARATOR)
                + FIELD_SEPARATOR + ' '
                + StringList.escapeListItem(mGivenNames, FIELD_SEPARATOR);
    }

    /**
     * Replace local details from another author.
     *
     * @param source Author to copy from
     */
    public void copyFrom(@NonNull final Author source) {
        mFamilyName = source.mFamilyName;
        mGivenNames = source.mGivenNames;
        mIsComplete = source.mIsComplete;
    }

    /**
     * Stopgap.... makes the code elsewhere clean.
     * <p>
     * ENHANCE: The locale of the Author should be based on the main language the author writes in.
     * Not implemented for now. So we cheat.
     *
     * @return the locale of the Author
     */
    @Override
    @NonNull
    public Locale getLocale() {
        return LocaleUtils.getPreferredLocale();
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final DAO db,
                      @NonNull final Locale authorLocale) {
        mId = db.getAuthorId(this, authorLocale);
        return mId;
    }

    /**
     * Each author is defined exactly by a unique ID.
     */
    @SuppressWarnings("SameReturnValue")
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Equality.
     * <p>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     * AND all other fields are equal</li>
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes even with identical id.
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
        // one or both are 'new' or their ID's are the same.
        return Objects.equals(mFamilyName, that.mFamilyName)
                && Objects.equals(mGivenNames, that.mGivenNames)
                && (mIsComplete == that.mIsComplete);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mFamilyName, mGivenNames);
    }
}
