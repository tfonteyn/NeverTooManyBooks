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

package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.NonNull;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiscuitStoreComparisonTest {

    static Stream<Arguments> storeProvider() {
        return Stream.of(
                Arguments.of("BiscuitStore (Original)", (Supplier<CookieStore>) BiscuitStore::new),
                Arguments.of("BiscuitStore2 (Updated)", (Supplier<CookieStore>) BiscuitStore2::new)
        );
    }

    /**
     * See the SiteAuthModule for the ISFDB SearchEngine.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("storeProvider")
    @DisplayName("Overrides max-age to 86400 seconds for *.isfdb.org domains")
    void testIsfdbMaxAgeOverride(@NonNull final String name,
                                 @NonNull final Supplier<CookieStore> supplier)
            throws URISyntaxException {
        final CookieStore store = supplier.get();

        final URI uri = new URI("https://www.isfdb.org/cgi-bin/index.cgi");
        final HttpCookie cookie = new HttpCookie("session", "xyz123");
        cookie.setDomain("isfdb.org");
        // Initial invalid max-age
        cookie.setMaxAge(0);

        store.add(uri, cookie);

        final List<HttpCookie> cookies = store.get(uri);
        assertEquals(1, cookies.size());
        assertEquals(86400L, cookies.get(0).getMaxAge(),
                     "ISFDB cookies must be clamped to 24 hours (86400s)");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("storeProvider")
    @DisplayName("Does not alter max-age for non-ISFDB domains")
    void testNonIsfdbMaxAgePreserved(@NonNull final String name,
                                     @NonNull final Supplier<CookieStore> supplier)
            throws URISyntaxException {
        final CookieStore store = supplier.get();

        final URI uri = new URI("https://example.com/");
        final HttpCookie cookie = new HttpCookie("session", "abc456");
        cookie.setDomain("example.com");
        cookie.setMaxAge(3600L);

        store.add(uri, cookie);

        final List<HttpCookie> cookies = store.get(uri);
        assertEquals(1, cookies.size());
        assertEquals(3600L, cookies.get(0).getMaxAge(),
                     "Non-ISFDB cookie max-age should remain unchanged");
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("storeProvider")
    @DisplayName("Matches exact domain and domain wildcard hierarchy")
    void testDomainMatching(@NonNull final String name,
                            @NonNull final Supplier<CookieStore> supplier)
            throws URISyntaxException {

        final CookieStore store = supplier.get();

        final URI uri = new URI("https://specfic.isfdb.org/title.cgi");

        final HttpCookie exactCookie = new HttpCookie("exact", "1");
        exactCookie.setDomain("specfic.isfdb.org");

        final HttpCookie parentCookie = new HttpCookie("parent", "2");
        parentCookie.setDomain(".isfdb.org");

        store.add(uri, exactCookie);
        store.add(uri, parentCookie);

        final List<HttpCookie> retrieved = store.get(uri);
        assertEquals(2, retrieved.size(),
                     "Should match both exact host and parent domain cookies");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("storeProvider")
    @DisplayName("Handles domain case-insensitivity using ROOT locale")
    void testDomainCaseInsensitivity(@NonNull final String name,
                                     @NonNull final Supplier<CookieStore> supplier)
            throws URISyntaxException {

        final CookieStore store = supplier.get();

        final URI uri = new URI("HTTPS://WWW.ISFDB.ORG/");
        final HttpCookie cookie = new HttpCookie("auth", "token");
        cookie.setDomain("IsFdB.oRg");

        store.add(uri, cookie);

        final List<HttpCookie> retrieved = store.get(new URI("https://www.isfdb.org/"));
        assertEquals(1, retrieved.size());
        assertEquals("auth", retrieved.get(0).getName());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("storeProvider")
    @DisplayName("Properly removes cookie from domainIndex on remove()")
    void testRemoveFromDomainIndex(@NonNull final String name,
                                   @NonNull final Supplier<CookieStore> supplier)
            throws URISyntaxException {

        final CookieStore store = supplier.get();

        final URI uri = new URI("https://example.org/");
        final HttpCookie cookie = new HttpCookie("tracker", "123");
        cookie.setDomain("example.org");

        store.add(uri, cookie);
        assertTrue(store.remove(uri, cookie), "Cookie removal should return true");

        final List<HttpCookie> remaining = store.get(uri);
        assertTrue(remaining.isEmpty(), "Domain index should no longer return removed cookie");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("storeProvider")
    @DisplayName("Handles concurrent reads and writes safely under load")
    void testConcurrentReadsAndWrites(@NonNull final String name,
                                      @NonNull final Supplier<CookieStore> supplier)
            throws InterruptedException {
        final CookieStore store = supplier.get();
        final int threadCount = 4;
        final int operationsPerThread = 50;

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.execute(() -> {
                try {
                    // Align all threads to start simultaneously
                    startLatch.await();

                    final URI uri = new URI("https://isfdb.org/book/" + threadId);
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Writer operation
                        final HttpCookie cookie = new HttpCookie("key_" + threadId + "_" + j, "val_" + j);
                        cookie.setDomain("isfdb.org");
                        store.add(uri, cookie);

                        // Reader operation
                        store.get(uri);
                        store.getCookies();

                        // Cleanup operation
                        if (j % 10 == 0) {
                            store.remove(uri, cookie);
                        }
                    }
                } catch (@NonNull final Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Await completion with a generous timeout for emulator/device scheduling
        final boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Verify execution
        if (failure.get() != null) {
            throw new AssertionError("Concurrent thread failed with exception", failure.get());
        }
        assertTrue(completed, "Concurrent operations timed out before finishing execution");
    }
}
