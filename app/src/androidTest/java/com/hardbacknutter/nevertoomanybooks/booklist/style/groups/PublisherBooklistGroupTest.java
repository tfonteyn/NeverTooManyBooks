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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.content.Context;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Styles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PublisherBooklistGroupTest
        extends BaseDBTest {

    @Test
    public void cc() {
        final Context context = mSl.getLocalizedAppContext();
        final Styles styles = mSl.getStyles();
        final ListStyle s1 = styles.getStyle(context, BuiltinStyle.UUID_FOR_TESTING_ONLY);
        assertNotNull(s1);

        final BooklistGroup g1 = BooklistGroup.newInstance(BooklistGroup.PUBLISHER, s1);

        final BooklistGroup g2 = new PublisherBooklistGroup(s1, (PublisherBooklistGroup) g1);
        assertEquals(g1, g2);
    }
}
