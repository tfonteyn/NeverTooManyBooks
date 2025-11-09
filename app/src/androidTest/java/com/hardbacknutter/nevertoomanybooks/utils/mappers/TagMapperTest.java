/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import android.util.Log;

import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TagMapperTest
        extends BaseDBTest {

    private static final String TAG = "TagMapperTest";

    private final List<TagMapping> mappings = List.of(
            new TagMapping("one", Set.of("toOne")),
            new TagMapping("tag", Set.of("t1", "t2")),
            new TagMapping("removeMe", Set.of())
    );

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void doTest() {
        final TagMapper tagMapper = new TagMapper(context, mappings);

        final List<Tag> tags = List.of(new Tag("qwerty"),
                                       new Tag("one"),
                                       new Tag("tag"),
                                       new Tag("removeMe"),
                                       new Tag("anotherOne"));

        final List<Tag> result = tagMapper.map(context, tags);
        Log.d(TAG, result.toString());

        assertEquals(5, result.size());
        int i = 0;
        assertEquals("qwerty", result.get(i++).getName());
        assertEquals("toOne", result.get(i++).getName());
        assertEquals("t1", result.get(i++).getName());
        assertEquals("t2", result.get(i++).getName());
        assertEquals("anotherOne", result.get(i++).getName());
    }
}