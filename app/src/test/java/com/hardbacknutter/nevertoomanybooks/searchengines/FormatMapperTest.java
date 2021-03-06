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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.sync.FormatMapper;
import com.hardbacknutter.nevertoomanybooks.sync.Mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatMapperTest
        extends Base {

    @Test
    void basic() {
        setLocale(Locale.UK);
        final Mapper mapper = new FormatMapper();
        final String key = mapper.getKey();
        mRawData.putString(key, "pb");
        mapper.map(mContext, mRawData);
        assertEquals("Paperback", mRawData.getString(key));

        mRawData.putString(key, "Dimensions 5x4");
        mapper.map(mContext, mRawData);
        assertEquals("Dim 5x4", mRawData.getString(key));
    }
}
