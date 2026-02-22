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

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorMergeTest {

    private static final String ISAAC_ASIMOV = "Isaac Asimov";

    private static void checksResult(@NonNull final Context context,
                                     @NonNull final Author author1,
                                     @NonNull final Author author2) {
        final AuthorMergeHelper h = new AuthorMergeHelper();
        final boolean merged = h.merge(context, author1, author2);
        assertTrue(merged);
        assertEquals("Asimov", author1.getFamilyName());
        assertEquals("Isaac", author1.getGivenNames());
        assertEquals(AuthorRole.EDITOR | AuthorRole.WRITER,
                     author1.getRole());

        assertEquals("1920-01-02", author1.getBirthDate().orElse(null));
        assertEquals("1992-04-06", author1.getDeathDate().orElse(null));
    }

    @Test
    void merge01() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final Author author1 = Author.from(ISAAC_ASIMOV);
        author1.setRole(AuthorRole.WRITER);
        author1.setBirthDate("1920-01-02");
        author1.setDeathDate("1992-04-06");

        final Author author2 = Author.from(ISAAC_ASIMOV);
        author2.setRole(AuthorRole.EDITOR);
        author2.setBirthDate("1920");
        author2.setDeathDate("1992");

        checksResult(context, author1, author2);
    }

    @Test
    void merge02() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final Author author1 = Author.from(ISAAC_ASIMOV);
        author1.setRole(AuthorRole.WRITER);
        author1.setBirthDate("1920");
        author1.setDeathDate("1992");

        final Author author2 = Author.from(ISAAC_ASIMOV);
        author2.setRole(AuthorRole.EDITOR);
        author2.setBirthDate("1920-01-02");
        author2.setDeathDate("1992-04-06");

        checksResult(context, author1, author2);
    }

    @Test
    void merge03() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final Author author1 = Author.from(ISAAC_ASIMOV);
        author1.setRole(AuthorRole.WRITER);

        final Author author2 = Author.from(ISAAC_ASIMOV);
        author2.setRole(AuthorRole.EDITOR);
        author2.setBirthDate("1920-01-02");
        author2.setDeathDate("1992-04-06");

        checksResult(context, author1, author2);
    }

    @Test
    void merge04() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final Author author1 = Author.from(ISAAC_ASIMOV);
        author1.setRole(AuthorRole.WRITER);
        author1.setBirthDate("1920-01-02");
        author1.setDeathDate("1992-04-06");

        final Author author2 = Author.from(ISAAC_ASIMOV);
        author2.setRole(AuthorRole.EDITOR);

        checksResult(context, author1, author2);
    }

    @Test
    void merge05() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final Author author1 = Author.from(ISAAC_ASIMOV);
        author1.setRole(AuthorRole.WRITER);
        author1.setBirthDate("1920-01-02");

        final Author author2 = Author.from(ISAAC_ASIMOV);
        author2.setRole(AuthorRole.EDITOR);
        author2.setDeathDate("1992-04-06");

        checksResult(context, author1, author2);
    }
}
