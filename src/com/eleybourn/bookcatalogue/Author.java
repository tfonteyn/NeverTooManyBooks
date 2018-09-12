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

package com.eleybourn.bookcatalogue;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

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

    /**
     * Support for creation via Parcelable.
     * This is primarily useful for passing ArrayList<Author> in Bundles to activities.
     */
    public static final Parcelable.Creator<Author> CREATOR = new Parcelable.Creator<Author>() {
        public Author createFromParcel(Parcel in) {
            return new Author(in);
        }

        public Author[] newArray(int size) {
            return new Author[size];
        }
    };

    public long id;
    public String familyName;
    public String givenNames;

    /**
     * Constructor that will attempt to parse a single string into an author name.
     */
    public Author(@NonNull final String name) {
        id = 0;
        fromString(name);
    }

    /**
     * Constructor without ID.
     *
     * @param family Family name
     * @param given  Given names
     */
    Author(@NonNull final String family, @NonNull final String given) {
        this(0L, family, given);
    }

    /**
     * Constructor
     *
     * @param id     ID of author in DB (0 if not in DB)
     * @param family Family name
     * @param given  Given names
     */
    Author(long id, @NonNull final String family, @NonNull final String given) {
        this.id = id;
        familyName = family.trim();
        givenNames = given.trim();
    }

    /**
     * Constructor using a Parcel.
     */
    private Author(@NonNull final Parcel in) {
        familyName = in.readString();
        givenNames = in.readString();
        id = in.readLong();
    }

    /**
     * Return the 'human readable' version of the name (eg. 'Iassc Asimov').
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

    private void fromString(@NonNull final String s) {
        ArrayList<String> sa = ArrayUtils.decodeList(',', s);
        if (sa.size() > 0) {
            if (sa.size() < 2) {
                // We have a name with no comma. Parse it the usual way.
                String[] names = CatalogueDBAdapter.processAuthorName(s);
                familyName = names[0];
                givenNames = names[1];
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
    void copyFrom(@NonNull final Author source) {
        familyName = source.familyName;
        givenNames = source.givenNames;
        id = source.id;
    }

    @Override
    public long fixupId(@NonNull final CatalogueDBAdapter db) {
        this.id = db.lookupAuthorId(this);
        return this.id;
    }

    @Override
    public long getId() {
        return id;
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
     * - one or both of them is 'new' (e.g. id == 0) and their names are equal
     * - ids are equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Author author = (Author) o;
        if (id == 0 || author.id == 0) {
            return Objects.equals(getDisplayName(), author.getDisplayName());
        }
        return (id == author.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getDisplayName());
    }
}
