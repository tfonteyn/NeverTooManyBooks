/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class UserStyleTest
        extends BaseDBTest {

    /**
     * Test fails with:
     * <p>
     * android.content.res.Resources$NotFoundException: Failed to resolve attribute 2130969324
     * at com.hardbacknutter.nevertoomanybooks.utils.AttrUtils.getDimensionPixelSize(AttrUtils.java:151)
     * at com.hardbacknutter.nevertoomanybooks.booklist.style.BaseStyle.getGroupRowHeight(BaseStyle.java:394)
     * <p>
     * because {@code com.google.android.material.R.attr.listPreferredItemHeightSmall}
     * cannot be resolved in androidTest?
     */
    @Test
    public void clone01() {
        final StylesHelper helper = serviceLocator.getStyles();
        final Optional<Style> s1 = helper.getStyle(BuiltinStyle.UUID_FOR_TESTING_ONLY);
        assertTrue(s1.isPresent());

        // clone a BuiltinStyle
        final UserStyle s2 = s1.get().clone(context);
        compare(context, s1.get(), s2);
        // clone a UserStyle
        compare(context, s2, s2.clone(context));
    }

    private void compare(@NonNull final Context context,
                         @NonNull final Style s1,
                         @NonNull final Style s2) {
        // new style, so no id, and a new uuid
        assertEquals(0, s2.getId());
        assertNotEquals(s1.getUuid(), s2.getUuid());
        assertFalse(s2.getUuid().isEmpty());

        assertEquals(s1.getLabel(context), s2.getLabel(context));

        assertEquals(s1.isPreferred(), s2.isPreferred());
        assertEquals(s1.getMenuPosition(), s2.getMenuPosition());

        assertEquals(s1.isShowAuthorByGivenName(), s2.isShowAuthorByGivenName());
        assertEquals(s1.isSortAuthorByGivenName(), s2.isSortAuthorByGivenName());

        assertEquals(s1.isShowHeaderField(BooklistHeader.SHOW_BOOK_COUNT),
                     s2.isShowHeaderField(BooklistHeader.SHOW_BOOK_COUNT));
        assertEquals(s1.isShowHeaderField(BooklistHeader.SHOW_STYLE_NAME),
                     s2.isShowHeaderField(BooklistHeader.SHOW_STYLE_NAME));
        assertEquals(s1.isShowHeaderField(BooklistHeader.SHOW_FILTERS),
                     s2.isShowHeaderField(BooklistHeader.SHOW_FILTERS));

        assertEquals(s1.getGroupRowHeight(context), s2.getGroupRowHeight(context));
        assertEquals(s1.getExpansionLevel(), s2.getExpansionLevel());
        assertEquals(s1.getPrimaryAuthorType(), s2.getPrimaryAuthorType());
        assertEquals(s1.getTextScale(), s2.getTextScale());
        assertEquals(s1.getGroupList(), s2.getGroupList());

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            assertEquals(s1.isShowBooks(item), s2.isShowBooks(item));
        }

        for (final Style.Screen item : Style.Screen.values()) {
            assertEquals(s1.getFieldVisibility(item).getBitValue(),
                         s2.getFieldVisibility(item).getBitValue());
        }

    }
}
