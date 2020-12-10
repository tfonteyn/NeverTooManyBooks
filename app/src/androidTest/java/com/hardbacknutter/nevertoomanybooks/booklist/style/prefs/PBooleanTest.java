/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style.prefs;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayerBundle;

import static org.junit.Assert.assertEquals;


public class PBooleanTest {

    private StylePersistenceLayer mLayerMock;

    @Before
    public void setupMock() {
        mLayerMock = new StylePersistenceLayerBundle();
    }

    @Test
    public void cc() {
        final PBoolean p1 = new PBoolean(false, mLayerMock, "no.p1");
        p1.set(true);

        final PBoolean p2 = new PBoolean(false, mLayerMock, p1);
        assertEquals(p1, p2);
        assertEquals(true, p2.getValue());
    }
}
