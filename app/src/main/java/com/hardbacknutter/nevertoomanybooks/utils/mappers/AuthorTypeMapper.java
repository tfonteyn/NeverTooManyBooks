/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Translate author types/roles into our own internal type codes.
 * <p>
 * Not based on {@link Mapper}, as the mapping is not done on a Book/Bundle key.
 * <p>
 * Note the Locale is passed into the mapping method, and not in the constructor.
 */
public class AuthorTypeMapper {

    /** Log tag. */
    private static final String TAG = "AuthorTypeMapper";

    private static final Map<String, Integer> MAPPINGS = new HashMap<>();

    // use all lowercase keys (unless they are diacritic)
    // NEWTHINGS: author type: add site-labels as needed
    static {
        // English
        MAPPINGS.put("author", Author.TYPE_WRITER);
        MAPPINGS.put("adapter", Author.TYPE_WRITER);

        MAPPINGS.put("original script writer", Author.TYPE_ORIGINAL_SCRIPT_WRITER);

        MAPPINGS.put("narrator", Author.TYPE_NARRATOR);
        MAPPINGS.put("reading", Author.TYPE_NARRATOR);

        MAPPINGS.put("illuminator", Author.TYPE_ARTIST);
        MAPPINGS.put("illustrator", Author.TYPE_ARTIST);
        MAPPINGS.put("illustrations", Author.TYPE_ARTIST);

        MAPPINGS.put("coverart", Author.TYPE_COVER_ARTIST);
        MAPPINGS.put("cover artist", Author.TYPE_COVER_ARTIST);
        MAPPINGS.put("cover illustrator", Author.TYPE_COVER_ARTIST);

        MAPPINGS.put("colorist", Author.TYPE_COLORIST);

        MAPPINGS.put("pseudonym", Author.TYPE_PSEUDONYM);

        MAPPINGS.put("editor", Author.TYPE_EDITOR);

        MAPPINGS.put("translator", Author.TYPE_TRANSLATOR);
        MAPPINGS.put("translator, annotations", Author.TYPE_TRANSLATOR | Author.TYPE_CONTRIBUTOR);

        MAPPINGS.put("preface", Author.TYPE_FOREWORD);
        MAPPINGS.put("foreword", Author.TYPE_FOREWORD);
        MAPPINGS.put("foreword by", Author.TYPE_FOREWORD);
        MAPPINGS.put("afterword", Author.TYPE_AFTERWORD);

        MAPPINGS.put("introduction", Author.TYPE_INTRODUCTION);

        MAPPINGS.put("contributor", Author.TYPE_CONTRIBUTOR);
        MAPPINGS.put("additional material", Author.TYPE_CONTRIBUTOR);


        // French, unless listed above
        MAPPINGS.put("text", Author.TYPE_WRITER);
        MAPPINGS.put("auteur", Author.TYPE_WRITER);
        MAPPINGS.put("scénario", Author.TYPE_WRITER);
        MAPPINGS.put("dessins", Author.TYPE_ARTIST);
        MAPPINGS.put("dessin", Author.TYPE_ARTIST);
        MAPPINGS.put("Inker", Author.TYPE_INKING);
        MAPPINGS.put("avec la contribution de", Author.TYPE_CONTRIBUTOR);
        MAPPINGS.put("contribution", Author.TYPE_CONTRIBUTOR);
        MAPPINGS.put("couleurs", Author.TYPE_COLORIST);
        MAPPINGS.put("traduction", Author.TYPE_TRANSLATOR);

        // Dutch, unless listed above
        MAPPINGS.put("scenario", Author.TYPE_WRITER);
        MAPPINGS.put("tekeningen", Author.TYPE_ARTIST);
        MAPPINGS.put("inkting", Author.TYPE_INKING);
        MAPPINGS.put("inkting cover", Author.TYPE_COVER_INKING);
        MAPPINGS.put("inkleuring", Author.TYPE_COLORIST);
        MAPPINGS.put("vertaler", Author.TYPE_TRANSLATOR);
        MAPPINGS.put("lettering", Author.TYPE_LETTERING);

        // German, unless listed above
        MAPPINGS.put("autor", Author.TYPE_WRITER);
        MAPPINGS.put("Übersetzer", Author.TYPE_TRANSLATOR);
        MAPPINGS.put("Übersetzung", Author.TYPE_TRANSLATOR);

        // Spanish, unless listed above
        MAPPINGS.put("escritor", Author.TYPE_WRITER);
        MAPPINGS.put("traductor", Author.TYPE_TRANSLATOR);
        MAPPINGS.put("ilustrador", Author.TYPE_ARTIST);
        MAPPINGS.put("dibujos", Author.TYPE_ARTIST);

        // Italian, unless listed above
        MAPPINGS.put("testi", Author.TYPE_WRITER);
        MAPPINGS.put("disegni", Author.TYPE_ARTIST);

        // There are obviously MANY missing.... both for the listed languages above and for
        // other languages not even considered here.
        // Will need to add them when/as they show up.
        // Maybe better if this is done in an external file on a per language basis ?
    }

    public int map(@NonNull final Locale locale,
                   @NonNull final String typeName) {
        final Integer mapped = MAPPINGS.get(typeName.toLowerCase(locale).trim());
        if (mapped != null) {
            return mapped;
        }

        // unknown, log it for future enhancement.
        LoggerFactory.getLogger().w(TAG, "map|typeName=`" + typeName + "`");
        return Author.TYPE_UNKNOWN;
    }
}
