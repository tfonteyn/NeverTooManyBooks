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

package com.eleybourn.bookcatalogue.entities;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;

/**
 * Class to hold author data.
 *
 * @author Philip Warner
 */
public class Author
        implements Parcelable, Utils.ItemWithIdFixup {

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
    private long mId;
    /** Family name(s). */
    private String mFamilyName;
    /** Given name(s). */
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
     * @param id         ID of author in DB (0 if not in DB)
     * @param familyName Family name
     * @param givenNames Given names
     * @param isComplete whether an Author is completed, i.e if the user has all they
     *                   want from this Author.
     */
    public Author(final long id,
                  @NonNull final String familyName,
                  @NonNull final String givenNames,
                  final boolean isComplete) {
        mId = id;
        mFamilyName = familyName.trim();
        mGivenNames = givenNames.trim();
        mIsComplete = isComplete;
    }

    /**
     * Full constructor.
     *
     * @param mapper for the cursor.
     */
    public Author(@NonNull final ColumnMapper mapper) {
        mId = mapper.getLong(DOM_PK_ID);
        mFamilyName = mapper.getString(DOM_AUTHOR_FAMILY_NAME);
        mGivenNames = mapper.getString(DOM_AUTHOR_GIVEN_NAMES);
        mIsComplete = mapper.getBoolean(DOM_AUTHOR_IS_COMPLETE);
    }

    /** {@link Parcelable}. */
    protected Author(@NonNull final Parcel in) {
        mId = in.readLong();
        mFamilyName = in.readString();
        mGivenNames = in.readString();
        mIsComplete = in.readByte() != 0;
    }

    public static boolean setComplete(@NonNull final DBA db,
                                      final long id,
                                      final boolean isComplete) {
        // load from database
        Author author = db.getAuthor(id);
        //noinspection ConstantConditions
        author.setComplete(isComplete);
        int rowsAffected = db.updateAuthor(author);
        return rowsAffected == 1;
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
     */
    public static Author fromString(@NonNull String name) {
        List<String> fngn = new StringList<String>().decode(FIELD_SEPARATOR, name, true);
        if (fngn.size() > 1) {
            Matcher matchSuffix = FAMILY_NAME_SUFFIX.matcher(fngn.get(1));
            if (!matchSuffix.find()) {
                // not a suffix, assume the names are already formatted.
                return new Author(fngn.get(0), fngn.get(1));
            } else {
                // concatenate without the comma. Further processing will take care of the suffix.
                name = fngn.get(0) + ' ' + fngn.get(1);
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
        dest.writeByte((byte) (mIsComplete ? 1 : 0));
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    public String getFamilyName() {
        return mFamilyName;
    }

    public String getGivenNames() {
        return mGivenNames;
    }

    /**
     * Return the 'human readable' version of the name (eg. 'Isaac Asimov').
     *
     * @return formatted name
     */
    @NonNull
    public String getDisplayName() {
        if (mGivenNames != null && !mGivenNames.isEmpty()) {
            return mGivenNames + ' ' + mFamilyName;
        } else {
            return mFamilyName;
        }
    }

    /**
     * Return the name in a sortable form (eg. 'Asimov, Isaac').
     *
     * @return formatted name
     */
    @NonNull
    public String getSortName() {
        if (mGivenNames != null && !mGivenNames.isEmpty()) {
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
        // There is a slim chance that family name may contain spaces (eg. 'Anonymous Anarchists').
        return StringList.escapeListItem(FIELD_SEPARATOR, mFamilyName)
                + FIELD_SEPARATOR + ' '
                + StringList.escapeListItem(FIELD_SEPARATOR, mGivenNames);
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
     * Finds the Author by using all fields except the id.
     * Then 'fixup' the local id with the id from the database.
     *
     * @param db database
     *
     * @return the id.
     */
    @Override
    public long fixupId(@NonNull final DBA db) {
        mId = db.getAuthorIdByName(mFamilyName, mGivenNames);
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
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) or their id's are the same
     * AND all their other fields are equal
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
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
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }

        return Objects.equals(mFamilyName, that.mFamilyName)
                && Objects.equals(mGivenNames, that.mGivenNames)
                && (mIsComplete == that.mIsComplete);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mFamilyName, mGivenNames);
    }
}
