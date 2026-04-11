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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublisherTest
        extends BaseDBTest {

    private static final String SOME_PUBLISHER = "Some publisher";
    private static final String THE_PUBLISHER = "The publisher";
    private static final String PUBLISHER_THE = "publisher, The";
    private static final String JOSE_PUBLISHER = "José publisher";
    private static final String JOSE_PUBLISHER_VARIANT = "Jose publisher";
    private PublisherDao publisherDao;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        publisherDao = serviceLocator.getPublisherDao();
    }

    @Test
    void bidi() {
        final Locale bookLocale = Locale.getDefault();

        final List<Publisher> list = new ArrayList<>();
        final Publisher p1;
        final Publisher p2;
        final Publisher p3;

        final String name1 = "Zsolnay, Paul";
        final String name2 = "Paul Zsolnay Verlag";

        p1 = new Publisher(name1);
        p1.setId(1467);
        list.add(p1);

        p2 = new Publisher(name2);
        p2.setId(1468);
        list.add(p2);

        // bidi character in front
        p3 = new Publisher("\u200E" + name2);
        // same id as p2
        p3.setId(1468);
        list.add(p3);

        assertNotEquals(p2, p3);

        final boolean modified = publisherDao.pruneList(context, list, false,
                                                        item -> bookLocale,
                                                        (p, l) -> {
                                                        });

        assertTrue(modified, list.toString());
        assertEquals(2, list.size(), list.toString());

        Publisher publisher;

        publisher = list.get(0);
        assertEquals(1467, publisher.getId());
        assertEquals(name1, publisher.getName());

        publisher = list.get(1);
        assertEquals(1468, publisher.getId());
        assertEquals(name2, publisher.getName());
    }

    @Test
    void prunePublisherNames01() {
        final Locale bookLocale = Locale.getDefault();

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // keep, position 0
        publisher = new Publisher(SOME_PUBLISHER);
        publisher.setId(1001);
        list.add(publisher);

        // keep, position 1
        publisher = new Publisher(THE_PUBLISHER);
        publisher.setId(1002);
        list.add(publisher);

        // DISCARD ! The base data is different, but the id already exists.
        publisher = new Publisher(PUBLISHER_THE);
        publisher.setId(1002);
        list.add(publisher);

        // Explicit: NO normalisation and NO idFixer
        final boolean modified = publisherDao.pruneList(context, list, false,
                                                        item -> bookLocale,
                                                        (p, l) -> {
                                                        });

        assertTrue(modified, list.toString());
        assertEquals(2, list.size(), list.toString());

        publisher = list.get(0);
        assertEquals(1001, publisher.getId());
        assertEquals(SOME_PUBLISHER, publisher.getName());

        publisher = list.get(1);
        assertEquals(1002, publisher.getId());
        assertEquals(THE_PUBLISHER, publisher.getName());
    }

    @Test
    void prunePublisherNames02() {
        final Locale bookLocale = Locale.getDefault();

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // Keep; list will not be modified
        publisher = new Publisher(SOME_PUBLISHER);
        publisher.setId(1001);
        list.add(publisher);

        // Discard; no id, and same data as the next element which HAS an id
        publisher = new Publisher(THE_PUBLISHER);
        publisher.setId(0);
        list.add(publisher);

        // Keep; same data as entry above, but WITH id, hence this on "wins"
        publisher = new Publisher(THE_PUBLISHER);
        publisher.setId(1002);
        list.add(publisher);

        // Explicit: NO normalisation
        final boolean modified = publisherDao.pruneList(context, list, false,
                                                        item -> bookLocale,
                                                        (p, l) -> {
                                                        });

        assertTrue(modified, list.toString());
        assertEquals(2, list.size(), list.toString());

        publisher = list.get(0);
        assertEquals(1001, publisher.getId());
        assertEquals(SOME_PUBLISHER, publisher.getName());

        publisher = list.get(1);
        assertEquals(1002, publisher.getId());
        assertEquals(THE_PUBLISHER, publisher.getName());
    }

    @Test
    void prunePublisherNames03() {
        final Locale bookLocale = Locale.getDefault();

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // keep, position 0
        publisher = new Publisher(SOME_PUBLISHER);
        publisher.setId(1001);
        list.add(publisher);

        // keep, position 1
        publisher = new Publisher(THE_PUBLISHER);
        publisher.setId(1002);
        list.add(publisher);

        // Discard in favour of position 0
        publisher = new Publisher(SOME_PUBLISHER);
        publisher.setId(0);
        list.add(publisher);

        // Keep, but merge with the next entry and use the id=1003
        publisher = new Publisher(JOSE_PUBLISHER);
        publisher.setId(0);
        list.add(publisher);

        // Discard; diacritic wins
        publisher = new Publisher(JOSE_PUBLISHER_VARIANT);
        publisher.setId(1003);
        list.add(publisher);

        // Explicit: NO normalisation
        final boolean modified = publisherDao.pruneList(context, list, false,
                                                        item -> bookLocale,
                                                        (p, l) -> {
                                                        });

        assertTrue(modified, list.toString());
        assertEquals(3, list.size(), list.toString());

        publisher = list.get(0);
        assertEquals(1001, publisher.getId());
        assertEquals(SOME_PUBLISHER, publisher.getName());

        publisher = list.get(1);
        assertEquals(1002, publisher.getId());
        assertEquals(THE_PUBLISHER, publisher.getName());

        publisher = list.get(2);
        assertEquals(1003, publisher.getId());
        assertEquals(JOSE_PUBLISHER, publisher.getName());
    }

    /**
     * Prune a list which contains both the non-reordered AND the reordered name (of a series).
     */
    @Test
    void pruneReorderedDuplications() {
        final Locale bookLocale = Locale.getDefault();

        final List<Publisher> list = new ArrayList<>();

        final Publisher p1 = Publisher.from(THE_PUBLISHER);
        p1.setId(1);
        list.add(p1);

        final Publisher p2 = Publisher.from(SOME_PUBLISHER);
        p2.setId(2);
        list.add(p2);

        final Publisher p3 = Publisher.from(PUBLISHER_THE);
        // Set the SAME id, so the only diff is the name!
        p3.setId(1);
        list.add(p3);

        // FORCE normalisation - this is the test for it... duh...
        final boolean modified = publisherDao.pruneList(context, list, true,
                                                        item -> bookLocale,
                                                        (p, l) -> {
                                                        });

        assertTrue(modified, list.toString());
        assertEquals(2, list.size());

        Publisher publisher;

        publisher = list.get(0);
        assertEquals(THE_PUBLISHER, publisher.getName());

        publisher = list.get(1);
        assertEquals(SOME_PUBLISHER, publisher.getName());
    }
}
