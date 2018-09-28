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
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Class to hold author data. Used in lists and import/export.
 *
 * @author Philip Warner
 */
public class Author implements Serializable, Utils.ItemWithIdFixup {
    private static final long serialVersionUID = 4597779234440821872L;
    public long id;
    public String familyName;
    public String givenNames;

    /**
     * Constructor that will attempt to parse a single string into an author name.
     */
    public Author(@NonNull final String name) {
        fromString(name);
    }

    /**
     * Constructor without ID.
     *
     * @param family Family name
     * @param given  Given names
     */
    public Author(@NonNull final String family, @NonNull final String given) {
        this(0L, family, given);
    }

    /**
     * Constructor
     *
     * @param id     ID of author in DB (0 if not in DB)
     * @param family Family name
     * @param given  Given names
     */
    public Author(long id, @NonNull final String family, @NonNull final String given) {
        this.id = id;
        familyName = family.trim();
        givenNames = given.trim();
    }

    /**
     * This will return the parsed author name based on a String.
     * The name can be in either "family, given" or "given family" format.
     *
     * @param name a String containing the name e.g. "Isaac Asimov" or "Asimov, Isaac"
     *
     * @return a String array containing the family and given names. e.g. ['Asimov', 'Isaac']
     */
    public static Author toAuthor(@NonNull final String name) {
        int commaIndex = name.indexOf(",");
        if (commaIndex > 0) {
            // we have a comma
            return new Author(name.substring(0, commaIndex).trim(), name.substring(commaIndex + 1).trim());
        } else {
            StringBuilder family = new StringBuilder();
            int fLen = 1;
            String[] names = name.split(" ");
            if (names.length > 2) {
                String sName = names[names.length - 2];
                /* e.g. Ursula Le Guin or Marianne De Pierres FIXME: needs internationalisation or at least add some more like 'Van', 'Des' !*/
                if (sName.matches("[LlDd]e")) {
                    family.append(names[names.length - 2]).append(" ");
                    fLen = 2;
                }
                sName = names[names.length - 1];
                /* e.g. Foo Bar Jr  FIXME: needs internationalisation ? */
                if (sName.matches("[Jj]r|[Jj]unior|[Ss]r|[Ss]enior")) {
                    family.append(names[names.length - 2]).append(" ");
                    fLen = 2;
                }
            }
            family.append(names[names.length - 1]);

            StringBuilder given = new StringBuilder();
            for (int i = 0; i < names.length - fLen; i++) {
                given.append(names[i]).append(" ");
            }

            return new Author(family.toString().trim(), given.toString().trim() );
        }
    }

    /**
     * Return the 'human readable' version of the name (eg. 'Isaac Asimov').
     *
     * @return formatted name
     */
    @NonNull
    public String getDisplayName() {
        if (givenNames != null && !givenNames.isEmpty())
            return givenNames + " " + familyName;
        else
            return familyName;
    }

    /**
     * Return the name in a sortable form (eg. 'Asimov, Isaac')
     *
     * @return formatted name
     */
    @NonNull
    public String getSortName() {
        if (givenNames != null && !givenNames.isEmpty())
            return familyName + ", " + givenNames;
        else
            return familyName;
    }

    // Support for encoding to a text file
    @Override
    @NonNull
    public String toString() {
        // Always use givenNames even if blanks because we need to KNOW they are blank. There
        // is a slim chance that family name may contain spaces (eg. 'Anonymous Anarchists').
        return ArrayUtils.encodeListItem(',', familyName) + ", " + ArrayUtils.encodeListItem(',', givenNames);
    }

    private void fromString(@NonNull final String name) {
        ArrayList<String> sa = ArrayUtils.decodeList(',', name);
        if (sa.size() > 0) {
            if (sa.size() < 2) {
                // We have a name with no comma. Parse it the usual way.
                Author author = toAuthor(name);
                familyName = author.familyName;
                givenNames = author.givenNames;
            } else {
                familyName = sa.get(0).trim();
                givenNames = sa.get(1).trim();
            }
        }
    }

    /**
     * Replace local details from another author
     *
     * @param source Author to copy
     */
    public void copyFrom(@NonNull final Author source) {
        familyName = source.familyName;
        givenNames = source.givenNames;
        id = source.id;
    }

    @Override
    public long fixupId(@NonNull final CatalogueDBAdapter db) {
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
     * case SENSITIVE !
     */
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        Author author = (Author) o;
        if (id == 0 || author.id == 0) {
            return Objects.equals(familyName, author.familyName) && Objects.equals(givenNames, author.givenNames);
        }

        return (id == author.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, familyName, givenNames);
    }
}
