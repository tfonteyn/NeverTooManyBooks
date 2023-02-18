/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_AFTERWORD;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_ARTIST;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_COLORIST;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_CONTRIBUTOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_COVER_ARTIST;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_COVER_INKING;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_EDITOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_FOREWORD;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_INKING;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_INTRODUCTION;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_NARRATOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_ORIGINAL_SCRIPT_WRITER;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_PSEUDONYM;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_TRANSLATOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_UNKNOWN;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_WRITER;

/**
 * Translate author types/roles into our own internal type codes.
 * <p>
 * Not based on MapperBase, as the mapping has to be int based.
 */
public class AuthorTypeMapper {

    /** Log tag. */
    private static final String TAG = "AuthorTypeMapper";

    private static final Map<String, Integer> MAPPER = new HashMap<>();

    // use all lowercase keys (unless they are diacritic)
    // NEWTHINGS: author type: add site-labels as needed
    static {
        // English
        MAPPER.put("author", TYPE_WRITER);
        MAPPER.put("adapter", TYPE_WRITER);

        MAPPER.put("original script writer", TYPE_ORIGINAL_SCRIPT_WRITER);

        MAPPER.put("narrator", TYPE_NARRATOR);
        MAPPER.put("reading", TYPE_NARRATOR);

        MAPPER.put("illuminator", TYPE_ARTIST);
        MAPPER.put("illustrator", TYPE_ARTIST);
        MAPPER.put("illustrations", TYPE_ARTIST);

        MAPPER.put("coverart", TYPE_COVER_ARTIST);
        MAPPER.put("cover artist", TYPE_COVER_ARTIST);
        MAPPER.put("cover illustrator", TYPE_COVER_ARTIST);

        MAPPER.put("colorist", TYPE_COLORIST);

        MAPPER.put("pseudonym", TYPE_PSEUDONYM);

        MAPPER.put("editor", TYPE_EDITOR);

        MAPPER.put("translator", TYPE_TRANSLATOR);
        MAPPER.put("translator, annotations", TYPE_TRANSLATOR | TYPE_CONTRIBUTOR);

        MAPPER.put("preface", TYPE_FOREWORD);
        MAPPER.put("foreword", TYPE_FOREWORD);
        MAPPER.put("foreword by", TYPE_FOREWORD);
        MAPPER.put("afterword", TYPE_AFTERWORD);

        MAPPER.put("introduction", TYPE_INTRODUCTION);

        MAPPER.put("contributor", TYPE_CONTRIBUTOR);
        MAPPER.put("additional material", TYPE_CONTRIBUTOR);


        // French, unless listed above
        MAPPER.put("text", TYPE_WRITER);
        MAPPER.put("auteur", TYPE_WRITER);
        MAPPER.put("scénario", TYPE_WRITER);
        MAPPER.put("dessins", TYPE_ARTIST);
        MAPPER.put("dessin", TYPE_ARTIST);
        MAPPER.put("Inker", TYPE_INKING);
        MAPPER.put("avec la contribution de", TYPE_CONTRIBUTOR);
        MAPPER.put("contribution", TYPE_CONTRIBUTOR);
        MAPPER.put("couleurs", TYPE_COLORIST);
        MAPPER.put("traduction", TYPE_TRANSLATOR);

        // Dutch, unless listed above
        MAPPER.put("scenario", TYPE_WRITER);
        MAPPER.put("tekeningen", TYPE_ARTIST);
        MAPPER.put("inkting", TYPE_INKING);
        MAPPER.put("inkting cover", TYPE_COVER_INKING);
        MAPPER.put("inkleuring", TYPE_COLORIST);
        MAPPER.put("vertaler", TYPE_TRANSLATOR);

        // German, unless listed above
        MAPPER.put("autor", TYPE_WRITER);
        MAPPER.put("Übersetzer", TYPE_TRANSLATOR);
        MAPPER.put("Übersetzung", TYPE_TRANSLATOR);

        // Italian, unless listed above
        MAPPER.put("testi", TYPE_WRITER);
        MAPPER.put("disegni", TYPE_ARTIST);

        // There are obviously MANY missing.... both for the listed languages above and for
        // other languages not even considered here.
        // Will need to add them when/as they show up.
        // Maybe better if this is done in an external file on a per language basis ?
    }

    public int map(@NonNull final Locale locale,
                   @NonNull final String typeName) {
        final Integer mapped = MAPPER.get(typeName.toLowerCase(locale).trim());
        if (mapped != null) {
            return mapped;
        }

        // unknown, log it for future enhancement.
        ServiceLocator.getInstance().getLogger().warn(TAG, "map|typeName=`" + typeName + "`");
        return TYPE_UNKNOWN;
    }
}
