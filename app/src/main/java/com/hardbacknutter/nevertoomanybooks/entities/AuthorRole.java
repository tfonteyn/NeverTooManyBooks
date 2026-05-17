/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

public final class AuthorRole {
    /** Generic Author; the default. A single person created the book. */
    public static final int UNKNOWN = 0;
    /**
     * {@link DBDefinitions#DOM_BOOK_AUTHOR_ROLE_BITMASK}.
     * NEWTHINGS: author role: add a bit flag
     * Never change the bit value!
     * <p>
     * WRITER: primary or only writer. i.e. in contrast to any of the below.
     */
    public static final int WRITER = 1;
    /**
     * WRITER: not distinguished for now. If we do, use {@code #ORIGINAL_SCRIPT_WRITER = 1 << 1;}
     * <p>
     * <strong>Dev. note:</strong> do NOT set "= WRITER" or...
     * 2024-04-20: Android Studio is completely [censored]ing up the code formatting in this class!
     * Each time we format the code, methods and variables jump around.
     * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
     * => fixed in IDEA 2026.2 EAP 1
     */
    public static final int ORIGINAL_SCRIPT_WRITER = 1;
    /** WRITER: the foreword. */
    public static final int FOREWORD = 1 << 2;
    /** WRITER: the afterword. */
    public static final int AFTERWORD = 1 << 3;
    /** WRITER: translator. */
    public static final int TRANSLATOR = 1 << 4;
    /** WRITER: introduction. (some sites makes a distinction with a foreword). */
    public static final int INTRODUCTION = 1 << 5;
    /** editor (e.g. of an anthology). */
    public static final int EDITOR = 1 << 6;
    /** generic collaborator. */
    public static final int CONTRIBUTOR = 1 << 7;
    /** ARTIST: cover. */
    public static final int COVER_ARTIST = 1 << 8;
    /** ARTIST: cover inking (if different from above). */
    public static final int COVER_INKING = 1 << 9;
    /** Audio books. */
    public static final int NARRATOR = 1 << 10;
    /** COLOR: cover. */
    public static final int COVER_COLORIST = 1 << 11;
    /** ARTIST: art work; could be illustrations, or the pages of a comic. */
    public static final int ARTIST = 1 << 12;
    /** ARTIST: art work inking (if different from above). */
    public static final int INKING = 1 << 13;
    /** WRITER/ARTIST: for comics and movies. */
    public static final int STORYBOARD = 1 << 14;
    /** COLOR: internal colorist. */
    public static final int COLORIST = 1 << 15;
    /**
     * Any: indicate that this name entry is a pseudonym.
     *
     * @deprecated as a flag, this is useless.
     *         (I think this flag is a legacy from when we had goodreads integration)
     */
    @Deprecated
    public static final int PSEUDONYM = 1 << 16;
    /** Comics only. */
    public static final int LETTERING = 1 << 17;
    /**
     * All valid bits for the role.
     * NEWTHINGS: author role: add to the mask
     */
    static final int BITMASK_ALL =
            UNKNOWN
            | WRITER | ORIGINAL_SCRIPT_WRITER | FOREWORD | AFTERWORD
            | TRANSLATOR | INTRODUCTION | EDITOR | CONTRIBUTOR
            | COVER_ARTIST | COVER_INKING | NARRATOR | COVER_COLORIST
            | ARTIST | INKING | STORYBOARD | COLORIST
            | LETTERING;
    /** Maps the role-bit to a string resource for the role-label. */
    static final Map<Integer, Integer> ROLES = new LinkedHashMap<>();

    /*
     * NEWTHINGS: author role: add the label for the role
     * This is a LinkedHashMap, so the order below is the order they will show up on the screen.
     */
    static {
        ROLES.put(WRITER, R.string.lbl_author_role_writer);
        ROLES.put(CONTRIBUTOR, R.string.lbl_author_role_contributor);
        ROLES.put(INTRODUCTION, R.string.lbl_author_role_intro);
        ROLES.put(FOREWORD, R.string.lbl_author_role_foreword);
        ROLES.put(AFTERWORD, R.string.lbl_author_role_afterword);

        ROLES.put(TRANSLATOR, R.string.lbl_author_role_translator);
        ROLES.put(EDITOR, R.string.lbl_author_role_editor);
        ROLES.put(NARRATOR, R.string.lbl_author_role_narrator);

        ROLES.put(ARTIST, R.string.lbl_author_role_artist);
        ROLES.put(INKING, R.string.lbl_author_role_inking);
        ROLES.put(COLORIST, R.string.lbl_author_role_colorist);
        ROLES.put(LETTERING, R.string.lbl_author_role_lettering);
        ROLES.put(STORYBOARD, R.string.lbl_author_role_storyboard);

        ROLES.put(COVER_ARTIST, R.string.lbl_author_role_cover_artist);
        ROLES.put(COVER_INKING, R.string.lbl_author_role_cover_inking);
        ROLES.put(COVER_COLORIST, R.string.lbl_author_role_cover_colorist);
    }

    private AuthorRole() {
    }

    // NEWTHINGS: author role: add to the IntDef
    @IntDef(flag = true,
            value = {UNKNOWN,
                    WRITER, FOREWORD, AFTERWORD,
                    TRANSLATOR, INTRODUCTION, EDITOR, CONTRIBUTOR,
                    COVER_ARTIST, COVER_INKING, NARRATOR, COVER_COLORIST,
                    ARTIST, INKING, STORYBOARD, COLORIST,
                    LETTERING
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Role {

    }
}
