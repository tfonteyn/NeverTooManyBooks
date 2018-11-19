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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to hold author data. Used in lists and import/export.
 *
 * @author Philip Warner
 */
public class Author implements Serializable, Utils.ItemWithIdFixup {

    private static final char SEPARATOR = ',';
    private static final long serialVersionUID = 4597779234440821872L;

    /**
     * FIXME: author middle name; needs internationalisation ?
     *
     * Ursula Le Guin
     * Marianne De Pierres
     * A. E. Van Vogt
     * Rip Von Ronkel
     *
     */
    private static final Pattern FAMILY_NAME_PREFIX = Pattern.compile("[LlDd]e|[Vv][oa]n");

    /**
     * FIXME: author name suffixes; needs internationalisation ? probably not.
     *
     * Foo Bar Jr.
     * Foo Bar Jr
     * Foo Bar Junior
     * Foo Bar Sr.
     * Foo Bar Sr
     * Foo Bar Senior
     * (j/s lower or upper case)
     *
     * Not covered yet, and seen in the wild:
     * "James Tiptree, Jr."  -> has comma which breaks algorithm
     * "James jr. Tiptree" -> suffix as a middle name.
     */
    private static final Pattern FAMILY_NAME_SUFFIX = Pattern.compile("[Jj]r\\.|[Jj]r|[Jj]unior|[Ss]r\\.|[Ss]r|[Ss]enior");

    public long id;
    public String familyName;
    public String givenNames;

    /**
     * Constructor that will attempt to parse a single string into an author name.
     */
    public Author(final @NonNull String name) {
        fromString(name);
    }

    /**
     * Constructor without ID.
     *
     * @param family Family name
     * @param given  Given names
     */
    public Author(final @NonNull String family, final @NonNull String given) {
        this(0L, family, given);
    }

    /**
     * Constructor
     *
     * @param id     ID of author in DB (0 if not in DB)
     * @param family Family name
     * @param given  Given names
     */
    public Author(long id, final @NonNull String family, final @NonNull String given) {
        this.id = id;
        familyName = family.trim();
        givenNames = given.trim();
    }

    /**
     * This will return the parsed author name based on a String.
     * The name can be in either "family, given" or "given family" format.
     *
     * Special rules, see {@link #FAMILY_NAME_PREFIX} and {@link #FAMILY_NAME_SUFFIX}
     *
     * Not covered are people with multiple, and not concatenated, last names.
     *
     * @param name a String containing the name
     *
     * @return a String array containing the family and given names.
     */
    public static Author toAuthor(final @NonNull String name) {
        int commaIndex = name.indexOf(",");
        // if we have a comma, assume the names are already formatted.
        if (commaIndex > 0) {
            return new Author(name.substring(0, commaIndex).trim(), name.substring(commaIndex + 1).trim());
        }

        String[] names = name.split(" ");
        // two easy cases
        switch (names.length) {
            case 1:
                // reminder: don't call new Author(names[0]) as that would recurse.
                return new Author(names[0], "");
            case 2:
                return new Author(names[1], names[0]);
        }

        // 3 or more parts, check the family name for any pre and suffixes
        StringBuilder familyName = new StringBuilder();
        // the position to check, start at the end.
        int pos = names.length - 1;

        Matcher matchSuffix = FAMILY_NAME_SUFFIX.matcher(names[pos]);
        if (!matchSuffix.find()) {
            // no suffix.
            familyName.append(names[pos]);
            pos--;
        } else {
            // suffix and the element before it are part of the last name.
            familyName.append(names[pos - 1]).append(" ").append(names[pos]);
            pos -= 2;
        }

        // the last name could also have a prefix
        Matcher matchMiddleName = FAMILY_NAME_PREFIX.matcher(names[pos]);
        if (matchMiddleName.find()) {
            // insert it at the front of the family name
            familyName.insert(0, names[pos] + " ");
            pos--;
        }

        // everything else are considered given names
        StringBuilder givenNames = new StringBuilder();
        for (int i = 0; i <=pos; i++) {
            givenNames.append(names[i]).append(" ");
        }

        return new Author(familyName.toString().trim(), givenNames.toString().trim());
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
     * Support for Serializable/encoding to a text file
     *
     * @return the object encoded as a String. If the format changes, update serialVersionUID
     *
     * "familyName, givenName"
     */
    @Override
    @NonNull
    public String toString() {
        // Always use givenNames even if blanks because we need to KNOW they are blank. There
        // is a slim chance that family name may contain spaces (eg. 'Anonymous Anarchists').
        return StringList.encodeListItem(SEPARATOR, familyName) + SEPARATOR + " " + StringList.encodeListItem(SEPARATOR, givenNames);
    }

    private void fromString(final @NonNull String name) {
        ArrayList<String> list = StringList.decode(SEPARATOR, name);
        if (list.size() > 0) {
            if (list.size() < 2) {
                // We have a name with no comma. Parse it the usual way.
                Author author = toAuthor(name);
                familyName = author.familyName;
                givenNames = author.givenNames;
            } else {
                familyName = list.get(0).trim();
                givenNames = list.get(1).trim();
            }
        }
    }

    /**
     * Replace local details from another author
     *
     * @param source Author to copy
     */
    public void copyFrom(final @NonNull Author source) {
        familyName = source.familyName;
        givenNames = source.givenNames;
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
     * Two authors are equal if:
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) but their names are equal
     * - ids are equal
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
        Author author = (Author) o;
        if (id == 0 || author.id == 0) {
            return Objects.equals(familyName, author.familyName)
                    && Objects.equals(givenNames, author.givenNames);
        }
        return (id == author.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, familyName, givenNames);
    }
}
