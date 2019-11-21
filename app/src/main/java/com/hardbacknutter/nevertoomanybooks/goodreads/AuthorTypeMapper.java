/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;

import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_ARTIST;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_COLORIST;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_CONTRIBUTOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_COVER_INKING;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_EDITOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_INKING;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_TRANSLATOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_UNKNOWN;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_WRITER;

/**
 * Translate Goodreads author types (roles) into our native type codes.
 * <p>
 * Not based on MapperBase, as the value is a real integer
 * and not a resource id.
 */
public class AuthorTypeMapper {

    private static final String TAG = "AuthorTypeMapper";

    private static final Map<String, Integer> MAPPER = new HashMap<>();

    // use all lowercase keys!
    static {
        // English
        MAPPER.put("Illustrator", TYPE_ARTIST);
        MAPPER.put("Illustrations", TYPE_ARTIST);
        MAPPER.put("Colorist", TYPE_COLORIST);
        MAPPER.put("Editor", TYPE_EDITOR);
        MAPPER.put("Contributor", TYPE_CONTRIBUTOR);
        MAPPER.put("Translator", TYPE_TRANSLATOR);

        // French, unless listed above
        MAPPER.put("Text", TYPE_WRITER);
        MAPPER.put("Scénario", TYPE_WRITER);
        MAPPER.put("Dessins", TYPE_ARTIST);
        MAPPER.put("Dessin", TYPE_ARTIST);
        MAPPER.put("Avec la contribution de", TYPE_CONTRIBUTOR);
        MAPPER.put("Contribution", TYPE_CONTRIBUTOR);
        MAPPER.put("Couleurs", TYPE_COLORIST);

        // Dutch, unless listed above
        MAPPER.put("Scenario", TYPE_WRITER);
        MAPPER.put("Tekeningen", TYPE_ARTIST);
        MAPPER.put("Inkting", TYPE_INKING);
        MAPPER.put("Inkting cover", TYPE_COVER_INKING);
        MAPPER.put("Inkleuring", TYPE_COLORIST);

        // German, unless listed above
        MAPPER.put("Übersetzer", TYPE_TRANSLATOR);

        // Italian, unless listed above
        MAPPER.put("Testi", TYPE_WRITER);
        MAPPER.put("Disegni", TYPE_ARTIST);

        // Current (2019-08-21) strings have been seen on Goodreads.
        // There are obviously MANY missing.... both for the listed languages above and for
        // other languages not even considered here.
        // Will need to add them when/as they show up.
        // Maybe better if this is done in an external file on a per language basis ?
        // Maybe some day see if we can pull a full list from Goodreads?

    }

    public static int map(@NonNull final String typeName) {
        Integer mapped = MAPPER.get(typeName.trim());
        if (mapped != null) {
            return mapped;
        }

        // unknown, log it for future enhancement.
        Logger.warn(TAG, "map", "typeName=`" + typeName + "`");
        return TYPE_UNKNOWN;
    }
}
