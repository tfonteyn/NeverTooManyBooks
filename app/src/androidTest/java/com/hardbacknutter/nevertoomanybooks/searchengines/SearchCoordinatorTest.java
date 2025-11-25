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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.fail;

@SuppressWarnings("MissingJavadoc")
public class SearchCoordinatorTest
        extends BaseDBTest {

    private static final String TAG = "SearchCoordinatorTest";
    /** LiveData requirement. */
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();
    private SearchCoordinator coordinator;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        coordinator = new SearchCoordinator();
        coordinator.init(context, null);

        final Site ol = Site.Type.Data.getSite(EngineId.OpenLibrary);
        coordinator.setSiteList(List.of(ol));
    }

    @Test
    public void search01()
            throws InterruptedException {

        final List<BookSearchResult> receivedValues = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(2);

        coordinator.onSearchFinished().observeForever(msg -> msg.process(trigger -> {
            @Nullable
            final BookSearchResult result = coordinator.pollFinishedQueue();
            if (result == null) {
                return;
            }
            final Book book = result.getBook();
            Log.d("search01-" + result.getSearchId(), book.toString());

            receivedValues.add(result);
            latch.countDown();

            coordinator.retriggerSearchFinished();
        }));

        BookSearchCriteria criteria;

        criteria = new BookSearchCriteria();
        criteria.setFetchCovers(new boolean[]{false, false, false, false});
        criteria.setIsbnText("9781406358803");
        coordinator.search(criteria);

        criteria = new BookSearchCriteria();
        criteria.setFetchCovers(new boolean[]{false, false, false, false});
        criteria.setIsbnText("9781444727180");
        coordinator.search(criteria);

        // Wait for observer to receive the values
        if (!latch.await(60, TimeUnit.SECONDS)) {
            fail("LiveData value was never set or observer was too late.");
        }

        Log.d(TAG, receivedValues.toString());
    }
}
