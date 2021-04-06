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
package com.hardbacknutter.nevertoomanybooks.booklist.style;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks._mocks.StylePersistenceLayerBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextScaleTest
        extends Base {

    private StylePersistenceLayer mLayerMock;

    @BeforeEach
    public void setUp() {
        super.setUp();
        mLayerMock = new StylePersistenceLayerBundle();
    }

    @Test
    void cc() {
        final TextScale t1 = new TextScale(false, mLayerMock);

        final TextScale t2 = new TextScale(false, mLayerMock, t1);
        assertEquals(t1, t2);
    }
}
