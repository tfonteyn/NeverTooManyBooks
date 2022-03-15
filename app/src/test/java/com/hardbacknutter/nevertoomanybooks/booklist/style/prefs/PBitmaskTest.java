/*
 * @Copyright 2018-2021 HardBackNutter
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

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks._mocks.StylePersistenceLayerBundle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PBitmaskTest
        extends Base {

    private static final int MASK = 255;
    private StylePersistenceLayer mLayerMock;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        mLayerMock = new StylePersistenceLayerBundle();
    }

    @Test
    void cc() {
        final PBitmask p1 = new PBitmask(false, mLayerMock, "no.p1", 0, MASK);
        p1.set(123);

        final PBitmask p2 = new PBitmask(false, mLayerMock, p1);
        assertEquals(p1, p2);
        assertEquals(123, (int) p2.getValue());
    }
}
