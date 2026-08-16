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

package com.hardbacknutter.nevertoomanybooks.core.network;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpLanguageHeaderTest {

    @Test
    void withEnglish() {
        final String s = HttpLanguageHeader.create(Locale.GERMANY, Locale.UK);
        assertEquals("de-DE,de;q=0.9,en-GB;q=0.8,en;q=0.7", s);
    }

    @Test
    void withoutEnglish() {
        final String s = HttpLanguageHeader.create(Locale.GERMANY, Locale.FRANCE);
        assertEquals("de-DE,de;q=0.9,fr-FR;q=0.8,fr;q=0.7,en;q=0.6", s);
    }

    @Test
    void dupsWithEnglish() {
        final String s = HttpLanguageHeader.create(Locale.UK, Locale.US);
        assertEquals("en-GB,en;q=0.9,en-US;q=0.8", s);
    }

    @Test
    void dupsWithEnglish2() {
        final String s = HttpLanguageHeader.create(Locale.UK, Locale.ENGLISH);
        assertEquals("en-GB,en;q=0.9", s);
    }

    @Test
    void dupsWithoutEnglish() {
        final String s = HttpLanguageHeader.create(Locale.GERMANY, Locale.GERMANY);
        assertEquals("de-DE,de;q=0.9,en;q=0.8", s);
    }
}
