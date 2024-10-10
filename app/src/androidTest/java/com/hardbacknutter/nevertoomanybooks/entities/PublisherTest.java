/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.os.Parcel;

import androidx.test.filters.MediumTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@MediumTest
@SuppressWarnings("MissingJavadoc")
public class PublisherTest
        extends BaseDBTest {

    private static final String SOME_PUBLISHER = "Some publisher";
    private static final String THE_PUBLISHER = "The publisher";
    private static final String PUBLISHER_THE = "publisher, The";
    private static final String JOSE_PUBLISHER = "José publisher";
    private static final String JOSE_PUBLISHER_VARIANT = "Jose publisher";

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void parcelling() {
        final Publisher publisher = new Publisher(SOME_PUBLISHER);

        final Parcel parcel = Parcel.obtain();
        publisher.writeToParcel(parcel, publisher.describeContents());
        parcel.setDataPosition(0);
        final Publisher pPublisher = Publisher.CREATOR.createFromParcel(parcel);

        assertEquals(pPublisher, publisher);

        assertEquals(pPublisher.getId(), publisher.getId());
        assertEquals(pPublisher.getName(), publisher.getName());
    }

    @Test
    public void prunePublisherNames01()
            throws DaoWriteException {
        final Locale bookLocale = Locale.getDefault();
        final PublisherDao publisherDao = serviceLocator.getPublisherDao();

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // keep, position 0
        publisher = new Publisher(SOME_PUBLISHER);
        publisherDao.fixId(context, publisher, bookLocale);
        long id0 = publisher.getId();
        if (id0 == 0) {
            id0 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(1001);
        list.add(publisher);

        // keep, position 1
        publisher = new Publisher(THE_PUBLISHER);
        publisherDao.fixId(context, publisher, bookLocale);
        long id1 = publisher.getId();
        if (id1 == 0) {
            id1 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(1002);
        list.add(publisher);

        // DISCARD ! The base data is different, but the id already exists.
        publisher = new Publisher(PUBLISHER_THE);
        publisher.setId(1002);
        list.add(publisher);

        final boolean modified = publisherDao.pruneList(context, list, item -> bookLocale);

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 2, list.size());

        assertTrue(id0 > 0);
        assertTrue(id1 > 0);

        publisher = list.get(0);
        assertEquals(id0, publisher.getId());
        assertEquals(SOME_PUBLISHER, publisher.getName());

        publisher = list.get(1);
        assertEquals(id1, publisher.getId());
        assertEquals(THE_PUBLISHER, publisher.getName());
    }

    @Test
    public void prunePublisherNames02()
            throws DaoWriteException {
        final Locale bookLocale = Locale.getDefault();
        final PublisherDao publisherDao = serviceLocator.getPublisherDao();

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // Keep; list will not be modified
        publisher = new Publisher(SOME_PUBLISHER);
        publisherDao.fixId(context, publisher, bookLocale);
        long id0 = publisher.getId();
        if (id0 == 0) {
            id0 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(1001);
        list.add(publisher);

        // Keep; list will not be modified
        publisher = new Publisher(THE_PUBLISHER);
        publisherDao.fixId(context, publisher, bookLocale);
        long id1 = publisher.getId();
        if (id1 == 0) {
            id1 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(0);
        list.add(publisher);

        // Discard; reordered but same as position 1
        publisher = new Publisher(PUBLISHER_THE);
        publisherDao.fixId(context, publisher, bookLocale);
        long id2 = publisher.getId();
        if (id2 == 0) {
            id2 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(1002);
        list.add(publisher);

        final boolean modified = publisherDao.pruneList(context, list, item -> bookLocale);

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 2, list.size());

        assertTrue(id0 > 0);
        assertTrue(id1 > 0);
        assertTrue(id2 > 0);

        publisher = list.get(0);
        assertEquals(id0, publisher.getId());
        assertEquals(SOME_PUBLISHER, publisher.getName());

        publisher = list.get(1);
        assertEquals(id1, publisher.getId());
        assertEquals(THE_PUBLISHER, publisher.getName());
    }

    @Test
    public void prunePublisherNames03()
            throws DaoWriteException {
        final Locale bookLocale = Locale.getDefault();
        final PublisherDao publisherDao = serviceLocator.getPublisherDao();

        final List<Publisher> list = new ArrayList<>();
        Publisher publisher;

        // keep, position 0
        publisher = new Publisher(SOME_PUBLISHER);
        publisherDao.fixId(context, publisher, bookLocale);
        long id0 = publisher.getId();
        if (id0 == 0) {
            id0 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(1001);
        list.add(publisher);

        // keep, position 1
        publisher = new Publisher(THE_PUBLISHER);
        publisherDao.fixId(context, publisher, bookLocale);
        long id1 = publisher.getId();
        if (id1 == 0) {
            id1 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(1002);
        list.add(publisher);

        // Discard; reordered but same as position 1
        publisher = new Publisher(PUBLISHER_THE);
        publisherDao.fixId(context, publisher, bookLocale);
        long id2 = publisher.getId();
        if (id2 == 0) {
            id2 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(0);
        list.add(publisher);

        // Discard in favour of position 0
        publisher = new Publisher(SOME_PUBLISHER);
        publisher.setId(0);
        list.add(publisher);

        // Keep, but merge with the next entry and copy the id=1003
        publisher = new Publisher(JOSE_PUBLISHER);
        publisherDao.fixId(context, publisher, bookLocale);
        long id3 = publisher.getId();
        if (id3 == 0) {
            id3 = publisherDao.insert(context, publisher, bookLocale);
        }
        publisher.setId(0);
        list.add(publisher);

        // Discard; diacritic wins
        publisher = new Publisher(JOSE_PUBLISHER_VARIANT);
        publisher.setId(1003);
        list.add(publisher);

        final boolean modified = publisherDao.pruneList(context, list, item -> bookLocale);

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 3, list.size());

        assertTrue(id0 > 0);
        assertTrue(id1 > 0);
        assertTrue(id2 > 0);
        assertTrue(id3 > 0);

        publisher = list.get(0);
        assertEquals(id0, publisher.getId());
        assertEquals(SOME_PUBLISHER, publisher.getName());

        publisher = list.get(1);
        assertEquals(id1, publisher.getId());
        assertEquals(THE_PUBLISHER, publisher.getName());

        publisher = list.get(2);
        assertEquals(id3, publisher.getId());
        assertEquals(JOSE_PUBLISHER, publisher.getName());
    }
}
