/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.os.Bundle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.hardbacknutter.nevertoomanybooks.BundleMock;
import com.hardbacknutter.nevertoomanybooks.R;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormatMapperTest {

    @Mock
    protected Context mContext;

    @Mock
    protected Bundle mBookData;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        mBookData = BundleMock.mock();
        mContext = mock(Context.class);

        when(mContext.getString(R.string.book_format_paperback)).thenReturn("Paperback");
        when(mContext.getString(R.string.book_format_softcover)).thenReturn("Softcover");
        when(mContext.getString(R.string.book_format_dimensions)).thenReturn("Dim");
    }

    @Test
    void basic() {
        Mapper mapper = new FormatMapper();
        String key = mapper.getKey();
        mBookData.putString(key, "pb");
        mapper.map(mContext, mBookData);
        assertEquals("Paperback", mBookData.getString(key));

        mBookData.putString(key, "Dimensions 5x4");
        mapper.map(mContext, mBookData);
        assertEquals("Dim 5x4", mBookData.getString(key));
    }
}
