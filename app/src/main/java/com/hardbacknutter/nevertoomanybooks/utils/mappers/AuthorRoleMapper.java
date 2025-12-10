/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Translate author types/roles into our own internal type codes.
 * <p>
 * Not based on {@link Mapper}, as the mapping is not done on a Book/Bundle key.
 * <p>
 * Note the Locale is passed into the mapping method, and not in the constructor.
 */
public class AuthorRoleMapper {

    /** Log tag. */
    private static final String TAG = "AuthorRoleMapper";

    private static final Map<String, Integer> MAPPINGS = new HashMap<>();

    // use all lowercase keys (unless they are diacritic)
    // NEWTHINGS: author role: add site-labels as needed
    static {
        // English
        MAPPINGS.put("author", AuthorRole.WRITER);
        MAPPINGS.put("writer", AuthorRole.WRITER);
        MAPPINGS.put("adapter", AuthorRole.WRITER);

        MAPPINGS.put("original script writer", AuthorRole.ORIGINAL_SCRIPT_WRITER);

        MAPPINGS.put("narrator", AuthorRole.NARRATOR);
        MAPPINGS.put("reading", AuthorRole.NARRATOR);

        MAPPINGS.put("illuminator", AuthorRole.ARTIST);
        MAPPINGS.put("illustrator", AuthorRole.ARTIST);
        MAPPINGS.put("illustrations", AuthorRole.ARTIST);

        MAPPINGS.put("coverart", AuthorRole.COVER_ARTIST);
        MAPPINGS.put("cover art", AuthorRole.COVER_ARTIST);
        MAPPINGS.put("cover artist", AuthorRole.COVER_ARTIST);
        MAPPINGS.put("cover illustrator", AuthorRole.COVER_ARTIST);
        MAPPINGS.put("cover photographs", AuthorRole.COVER_ARTIST);

        MAPPINGS.put("colorist", AuthorRole.COLORIST);

        MAPPINGS.put("pseudonym", AuthorRole.WRITER);

        MAPPINGS.put("editor", AuthorRole.EDITOR);

        MAPPINGS.put("translator", AuthorRole.TRANSLATOR);
        MAPPINGS.put("translator, annotations", AuthorRole.TRANSLATOR | AuthorRole.CONTRIBUTOR);

        MAPPINGS.put("preface", AuthorRole.FOREWORD);
        MAPPINGS.put("foreword", AuthorRole.FOREWORD);
        MAPPINGS.put("foreword by", AuthorRole.FOREWORD);
        MAPPINGS.put("afterword", AuthorRole.AFTERWORD);
        MAPPINGS.put("postface", AuthorRole.AFTERWORD);

        MAPPINGS.put("introduction", AuthorRole.INTRODUCTION);

        MAPPINGS.put("contributor", AuthorRole.CONTRIBUTOR);
        MAPPINGS.put("additional material", AuthorRole.CONTRIBUTOR);


        // French, unless listed above
        MAPPINGS.put("text", AuthorRole.WRITER);
        MAPPINGS.put("auteur", AuthorRole.WRITER);
        MAPPINGS.put("scénario", AuthorRole.WRITER);
        MAPPINGS.put("dessins", AuthorRole.ARTIST);
        MAPPINGS.put("dessin", AuthorRole.ARTIST);
        MAPPINGS.put("inker", AuthorRole.INKING);
        MAPPINGS.put("avec la contribution de", AuthorRole.CONTRIBUTOR);
        MAPPINGS.put("contribution", AuthorRole.CONTRIBUTOR);
        MAPPINGS.put("couleurs", AuthorRole.COLORIST);
        MAPPINGS.put("traduction", AuthorRole.TRANSLATOR);

        // Dutch, unless listed above
        MAPPINGS.put("scenario", AuthorRole.WRITER);
        MAPPINGS.put("tekeningen", AuthorRole.ARTIST);
        MAPPINGS.put("inkting", AuthorRole.INKING);
        MAPPINGS.put("inkting cover", AuthorRole.COVER_INKING);
        MAPPINGS.put("inkleuring", AuthorRole.COLORIST);
        MAPPINGS.put("vertaler", AuthorRole.TRANSLATOR);
        MAPPINGS.put("lettering", AuthorRole.LETTERING);

        // German, unless listed above
        MAPPINGS.put("autor", AuthorRole.WRITER);
        MAPPINGS.put("autorin", AuthorRole.WRITER);
        MAPPINGS.put("künstler", AuthorRole.ARTIST);
        MAPPINGS.put("mitwirkender", AuthorRole.CONTRIBUTOR);
        MAPPINGS.put("mitwirkende", AuthorRole.CONTRIBUTOR);
        MAPPINGS.put("sonstige", AuthorRole.CONTRIBUTOR);
        MAPPINGS.put("verfasser eines geleitworts", AuthorRole.FOREWORD);
        MAPPINGS.put("verfasser von ergänzendem text", AuthorRole.AFTERWORD);
        MAPPINGS.put("verfasser eines vorworts", AuthorRole.FOREWORD);
        MAPPINGS.put("vorwort", AuthorRole.FOREWORD);
        MAPPINGS.put("nachwort", AuthorRole.AFTERWORD);
        MAPPINGS.put("verfasser", AuthorRole.WRITER);
        MAPPINGS.put("übersetzer", AuthorRole.TRANSLATOR);
        MAPPINGS.put("übersetzung", AuthorRole.TRANSLATOR);
        MAPPINGS.put("letterer", AuthorRole.LETTERING);
        MAPPINGS.put("herausgeber", AuthorRole.EDITOR);
        MAPPINGS.put("zeichnungen", AuthorRole.ARTIST);
        MAPPINGS.put("zeichner", AuthorRole.ARTIST);

        // Spanish, unless listed above
        MAPPINGS.put("escritor", AuthorRole.WRITER);
        MAPPINGS.put("traductor", AuthorRole.TRANSLATOR);
        MAPPINGS.put("ilustrador", AuthorRole.ARTIST);
        MAPPINGS.put("dibujos", AuthorRole.ARTIST);

        // Italian, unless listed above
        MAPPINGS.put("testi", AuthorRole.WRITER);
        MAPPINGS.put("disegni", AuthorRole.ARTIST);

        // There are obviously MANY missing.... both for the listed languages above and for
        // other languages not even considered here.
        // Will need to add them when/as they show up.
        // Maybe better if this is done in an external file on a per language basis ?
    }

    /**
     * Map the given role-name to an Author role code.
     *
     * @param locale   Current Locale
     * @param roleName to map; can be a CSV list
     *
     * @return mapped author role, or {@link AuthorRole#UNKNOWN}.
     */
    public int map(@NonNull final Locale locale,
                   @NonNull final String roleName) {
        final String[] names = roleName.split(",");
        final int mapped = Arrays
                .stream(names)
                .map(name -> MAPPINGS.get(name.toLowerCase(locale).strip()))
                .filter(Objects::nonNull)
                .mapToInt(role -> role)
                .reduce(AuthorRole.UNKNOWN, (a, b) -> a | b);

        // If unknown, log it for future addition.
        if (mapped == AuthorRole.UNKNOWN) {
            LoggerFactory.getLogger().w(TAG, "map|roleName=`" + roleName + "`");
        }

        return mapped;
    }
}
