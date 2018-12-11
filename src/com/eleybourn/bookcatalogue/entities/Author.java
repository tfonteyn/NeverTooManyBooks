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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to hold author data. Used in lists and import/export.
 *
 * @author Philip Warner
 */
public class Author implements Parcelable, Utils.ItemWithIdFixup {

    public static final Creator<Author> CREATOR = new Creator<Author>() {
        @Override
        public Author createFromParcel(Parcel in) {
            return new Author(in);
        }

        @Override
        public Author[] newArray(int size) {
            return new Author[size];
        }
    };
    private static final char SEPARATOR = ',';
    /**
     * ENHANCE: author middle name; needs internationalisation ?
     *
     * Ursula Le Guin
     * Marianne De Pierres
     * A. E. Van Vogt
     * Rip Von Ronkel
     */
    private static final Pattern FAMILY_NAME_PREFIX = Pattern.compile("[LlDd]e|[Vv][oa]n");
    /**
     * ENHANCE author name suffixes; needs internationalisation ? probably not.
     *
     * j/s lower or upper case
     *
     * Foo Bar Jr.
     * Foo Bar Jr
     * Foo Bar Junior
     * Foo Bar Sr.
     * Foo Bar Sr
     * Foo Bar Senior
     * Foo Bar II
     * Charles Emerson Winchester III
     *
     * same as above, but with comma:
     * Foo Bar, Jr.
     *
     *
     * Not covered yet, and seen in the wild:
     * "James jr. Tiptree" -> suffix as a middle name.
     * "Dr. Asimov" -> titles... pre or suffixed
     */
    private static final Pattern FAMILY_NAME_SUFFIX = Pattern.compile("[Jj]r\\.|[Jj]r|[Jj]unior|[Ss]r\\.|[Ss]r|[Ss]enior|II|III");
    public long id;
    public String familyName;
    public String givenNames;
    public boolean isComplete;

    /**
     * Constructor that will attempt to parse a single string into an author name.
     */
    public Author(final @NonNull String name) {
        fromString(name);
    }

    /**
     * Constructor without ID.
     *
     * @param familyName Family name
     * @param givenNames Given names
     */
    public Author(final @NonNull String familyName, final @NonNull String givenNames) {
        this(0L, familyName, givenNames, false);
    }

    /**
     * Constructor without ID.
     *
     * @param familyName Family name
     * @param givenNames Given names
     * @param isComplete whether an Author is completed, i.e if the user hs all he wants from this Author.
     */
    public Author(final @NonNull String familyName, final @NonNull String givenNames, final boolean isComplete) {
        this(0L, familyName, givenNames, isComplete);
    }

    /**
     * Constructor
     *
     * @param id         ID of author in DB (0 if not in DB)
     * @param familyName Family name
     * @param givenNames Given names
     * @param isComplete whether an Author is completed, i.e if the user has all he wants from this Author.
     */
    public Author(final long id,
                  final @NonNull String familyName,
                  final @NonNull String givenNames,
                  final boolean isComplete) {
        this.id = id;
        this.familyName = familyName.trim();
        this.givenNames = givenNames.trim();
        this.isComplete = isComplete;
    }

    protected Author(Parcel in) {
        id = in.readLong();
        familyName = in.readString();
        givenNames = in.readString();
        isComplete = in.readByte() != 0;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(final boolean complete) {
        isComplete = complete;
    }

    public static boolean setComplete(final CatalogueDBAdapter db,
                                      final long id,
                                      final boolean isComplete) {
        Author author = null;
        try {
            // load from database
            author = db.getAuthor(id);
            //noinspection ConstantConditions
            author.setComplete(isComplete);
            return (db.updateAuthor(author) == 1);
        } catch (Exception e) {
            // log but ignore
            Logger.error(e,"failed to set Author id=" + id + " to complete=" + isComplete);
            // rollback
            if (author != null) {
                author.setComplete(!isComplete);
            }
            return false;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(familyName);
        dest.writeString(givenNames);
        dest.writeByte((byte) (isComplete ? 1 : 0));
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * This will parse a string into a family/given name pair
     * The name can be in either "family, given" or "given family" format.
     *
     * Special rules, see {@link #FAMILY_NAME_PREFIX} and {@link #FAMILY_NAME_SUFFIX}
     *
     * Not covered are people with multiple, and not concatenated, last names.
     *
     * TODO: would be nice to redo with a rules based approach.
     *
     * @param name a String containing the name
     */
    private void fromString(@NonNull String name) {
        int commaIndex = name.indexOf(",");
        if (commaIndex > 0) {
            String beforeComma = name.substring(0, commaIndex).trim();
            String behindComma = name.substring(commaIndex + 1).trim();
            Matcher matchSuffix = FAMILY_NAME_SUFFIX.matcher(behindComma);
            if (!matchSuffix.find()) {
                // not a suffix, assume the names are already formatted.
                familyName = beforeComma;
                givenNames = behindComma;
                return;
            } else {
                // concatenate without the comma. Further processing will take care of the suffix.
                name = beforeComma + " " + behindComma;
            }
        }

        String[] names = name.split(" ");
        // two easy cases
        switch (names.length) {
            case 1:
                familyName = names[0];
                givenNames = "";
                return;
            case 2:
                familyName = names[1];
                givenNames = names[0];
                return;
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
            buildFamilyName.append(names[pos - 1]).append(" ").append(names[pos]);
            pos -= 2;
        }

        // the last name could also have a prefix
        Matcher matchMiddleName = FAMILY_NAME_PREFIX.matcher(names[pos]);
        if (matchMiddleName.find()) {
            // insert it at the front of the family name
            buildFamilyName.insert(0, names[pos] + " ");
            pos--;
        }

        // everything else are considered given names
        StringBuilder buildGivenNames = new StringBuilder();
        for (int i = 0; i <= pos; i++) {
            buildGivenNames.append(names[i]).append(" ");
        }

        familyName = buildFamilyName.toString().trim();
        givenNames = buildGivenNames.toString().trim();
    }

    /**
     * Return the 'human readable' version of the name (eg. 'Isaac Asimov').
     *
     * @return formatted name
     */
    @NonNull
    public String getDisplayName() {
        if (givenNames != null && !givenNames.isEmpty()) {
            return givenNames + " " + familyName;
        } else {
            return familyName;
        }
    }

    /**
     * Return the name in a sortable form (eg. 'Asimov, Isaac')
     *
     * @return formatted name
     */
    @NonNull
    public String getSortName() {
        if (givenNames != null && !givenNames.isEmpty()) {
            return familyName + ", " + givenNames;
        } else {
            return familyName;
        }
    }

    /**
     * Support for encoding to a text file
     *
     * @return the object encoded as a String.
     *
     * "familyName, givenName"
     */
    @Override
    @NonNull
    public String toString() {
        // Always use givenNames even if blanks because we need to KNOW they are blank.
        // There is a slim chance that family name may contain spaces (eg. 'Anonymous Anarchists').
        return StringList.encodeListItem(SEPARATOR, familyName) + SEPARATOR + " " + StringList.encodeListItem(SEPARATOR, givenNames);
    }

    /**
     * Replace local details from another author
     *
     * @param source Author to copy
     */
    public void copyFrom(final @NonNull Author source) {
        familyName = source.familyName;
        givenNames = source.givenNames;
        isComplete = source.isComplete;
        id = source.id;
    }

    @Override
    public long fixupId(final @NonNull CatalogueDBAdapter db) {
        this.id = db.getAuthorIdByName(this.familyName, this.givenNames);
        return this.id;
    }

    /**
     * Each author is defined exactly by a unique ID.
     */
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Two are the same if:
     *
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) or their id's are the same
     *   AND all their other fields are equal
     *
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
     */
    @Override
    public boolean equals(final @Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Author that = (Author) o;
        if (this.id == 0 || that.id == 0 || this.id == that.id) {
            return Objects.equals(this.familyName, that.familyName)
                    && Objects.equals(this.givenNames, that.givenNames)
                    && (this.isComplete == that.isComplete);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, familyName, givenNames);
    }
}
