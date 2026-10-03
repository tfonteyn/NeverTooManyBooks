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

/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, ReadWriteLock-optimized fork of Android's {@code java.net.InMemoryCookieStore}.
 * Original code taken from android-35 sources.
 * <p>
 * https://github.com/AndroidSDKSources/android-sdk-sources-for-api-level-35/blob/master/java/net/InMemoryCookieStore.java
 * <p>
 * Modifications over standard Android InMemoryCookieStore:
 * <br>1. Uses ReentrantReadWriteLock instead of single ReentrantLock to reduce lock contention.
 * <br>2. Restores secondary domainIndex for O(1) domain lookups.
 * <br>3. Adds {@link #getCookiesIncludingExpired()}.
 * <br>4. In {@link #add(URI, HttpCookie)}, forces 1-day max age override for ISFDB domain cookies.
 */
public class BiscuitStore2
        implements CookieStore {

    private static final long ONE_DAY_IN_SECONDS = 86400;
    private static final String ERROR_COOKIE_IS_NULL = "cookie is null";

    // the cookies are indexed by associated uri (if present)
    private final Map<URI, List<HttpCookie>> uriIndex;

    // the cookies are indexed by domain
    private final Map<String, List<HttpCookie>> domainIndex;

    // use ReentrantReadWriteLock instead of ReentrantLock for read concurrency
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(false);
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    /**
     * Constructor.
     */
    public BiscuitStore2() {
        uriIndex = new HashMap<>();
        domainIndex = new HashMap<>();
    }

    @Override
    public void add(@Nullable final URI uri,
                    @NonNull final HttpCookie cookie) {
        // argument can't be null
        //noinspection ConstantValue
        if (cookie == null) {
            throw new NullPointerException(ERROR_COOKIE_IS_NULL);
        }

        // ISFDB produces broken cookies with an invalid max-age.
        // We simply force a one-day max-age.
        if (cookie.getDomain() != null && cookie.getDomain().endsWith("isfdb.org")) {
            cookie.setMaxAge(ONE_DAY_IN_SECONDS);
        }

        writeLock.lock();
        try {
            // Android-changed: Android supports clearing cookies. http://b/33034917
            // They are cleared by adding the cookie with max-age: 0.
            //if (cookie.getMaxAge() != 0) {
            addIndex(uriIndex, getEffectiveURI(uri), cookie);
            if (cookie.getDomain() != null) {
                addIndex(domainIndex, cookie.getDomain().toLowerCase(Locale.ROOT), cookie);
            }
            //}
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<HttpCookie> get(final URI uri) {
        // argument can't be null
        if (uri == null) {
            throw new NullPointerException("uri is null");
        }

        final List<HttpCookie> cookies = new ArrayList<>();
        final List<HttpCookie> expiredToPurge = new ArrayList<>();

        // BEGIN Android-changed: InMemoryCookieStore ignores scheme (http/https). b/25897688
        readLock.lock();
        try {
            // check domainIndex first
            checkDomainIndex(cookies, expiredToPurge, uri.getHost());
            // check uriIndex then
            checkUriIndex(cookies, expiredToPurge, getEffectiveURI(uri));
        } finally {
            readLock.unlock();
        }

        if (!expiredToPurge.isEmpty()) {
            purgeExpiredCookies(expiredToPurge);
        }
        return cookies;
    }

    /**
     * Get all cookies in cookie store, except those have expired.
     *
     * @return list
     *
     * @see #getCookiesIncludingExpired()
     */
    @Override
    public List<HttpCookie> getCookies() {
        List<HttpCookie> rt = new ArrayList<>();
        final List<HttpCookie> expiredToPurge = new ArrayList<>();

        readLock.lock();
        try {
            for (final List<HttpCookie> list : uriIndex.values()) {
                for (final HttpCookie cookie : list) {
                    if (cookie.hasExpired()) {
                        expiredToPurge.add(cookie);
                    } else if (!rt.contains(cookie)) {
                        rt.add(cookie);
                    }
                }
            }
        } finally {
            rt = Collections.unmodifiableList(rt);
            readLock.unlock();
        }

        if (!expiredToPurge.isEmpty()) {
            purgeExpiredCookies(expiredToPurge);
        }

        return rt;
    }

    /**
     * Get all cookies in cookie store, <strong>including</strong> those which have expired.
     *
     * @return list
     *
     * @see #getCookies()
     */
    @NonNull
    public List<HttpCookie> getCookiesIncludingExpired() {
        List<HttpCookie> rt = new ArrayList<>();

        readLock.lock();
        try {
            for (final List<HttpCookie> list : uriIndex.values()) {
                for (final HttpCookie cookie : list) {
                    if (!rt.contains(cookie)) {
                        rt.add(cookie);
                    }
                }
            }
        } finally {
            rt = Collections.unmodifiableList(rt);
            readLock.unlock();
        }

        return rt;
    }

    @Override
    public List<URI> getURIs() {
        // App compat. Return URI with no cookies. http://b/65538736
        readLock.lock();
        try {
            final List<URI> result = new ArrayList<>(uriIndex.keySet());
            result.remove(null);
            return Collections.unmodifiableList(result);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean remove(@Nullable final URI uri,
                          @NonNull final HttpCookie ck) {
        // argument can't be null
        //noinspection ConstantValue
        if (ck == null) {
            throw new NullPointerException(ERROR_COOKIE_IS_NULL);
        }

        writeLock.lock();
        try {
            final URI effectiveURI = getEffectiveURI(uri);
            final List<HttpCookie> cookies = uriIndex.get(effectiveURI);
            boolean removed = false;
            if (cookies != null) {
                removed = cookies.remove(ck);
                if (cookies.isEmpty()) {
                    uriIndex.remove(effectiveURI);
                }
            }
            if (ck.getDomain() != null) {
                removeFromIndex(domainIndex, ck.getDomain().toLowerCase(Locale.ROOT), ck);
            }
            return removed;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean removeAll() {
        writeLock.lock();
        try {
            final boolean result = !uriIndex.isEmpty();
            uriIndex.clear();
            domainIndex.clear();
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    private void purgeExpiredCookies(final List<HttpCookie> expiredList) {
        writeLock.lock();
        try {
            for (final List<HttpCookie> list : uriIndex.values()) {
                list.removeAll(expiredList);
            }
            for (final List<HttpCookie> list : domainIndex.values()) {
                list.removeAll(expiredList);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Variation of {@link HttpCookie#domainMatches(String, String)}.
     * <p>
     * This is almost the same except for one difference: It won't reject cookies
     * when the 'H' part of the domain contains a dot ('.').
     * I.E.: RFC 2965 section 3.3.2 says that if host is x.y.domain.com
     * and the cookie domain is .domain.com, then it should be rejected.
     * However that's not how the real world works. Browsers don't reject and
     * some sites, like yahoo.com do actually expect these cookies to be
     * passed along.
     * And should be used for 'old' style cookies (aka Netscape type of cookies)
     */
    private boolean netscapeDomainMatches(@Nullable final String domain,
                                          @Nullable final String host) {
        if (domain == null || host == null) {
            return false;
        }

        // if there's no embedded dot in domain and domain is not .local
        final boolean isLocalDomain = ".local".equalsIgnoreCase(domain);
        int embeddedDotInDomain = domain.indexOf('.');
        if (embeddedDotInDomain == 0) {
            embeddedDotInDomain = domain.indexOf('.', 1);
        }
        if (!isLocalDomain
            && (embeddedDotInDomain == -1 || embeddedDotInDomain == domain.length() - 1)) {
            return false;
        }

        // if the host name contains no dot and the domain name is .local
        final int firstDotInHost = host.indexOf('.');
        if (firstDotInHost == -1 && isLocalDomain) {
            return true;
        }

        final int domainLength = domain.length();
        final int lengthDiff = host.length() - domainLength;
        if (lengthDiff == 0) {
            // if the host name and the domain name are just string-compare equal
            return host.equalsIgnoreCase(domain);
        } else if (lengthDiff > 0) {
            // need to check H & D component
            return host.substring(lengthDiff).equalsIgnoreCase(domain);
        } else if (lengthDiff == -1) {
            // if domain is actually .host
            return domain.charAt(0) == '.' && host.equalsIgnoreCase(domain.substring(1));
        }

        return false;
    }

    /**
     * Check the {@link #domainIndex} for cookies.
     *
     * @param cookies        [OUT] contains the found cookies
     * @param expiredToPurge [OUT] contains expired cookies to remove after reading
     * @param host           for which cookies should be returned
     */
    private void checkDomainIndex(@NonNull final List<HttpCookie> cookies,
                                  @NonNull final List<HttpCookie> expiredToPurge,
                                  @Nullable final String host) {
        if (host == null) {
            return;
        }

        // Android-changed: InMemoryCookieStore ignores scheme (http/https). b/25897688
        for (final Map.Entry<String, List<HttpCookie>> entry : domainIndex.entrySet()) {
            final String domain = entry.getKey();
            final List<HttpCookie> lst = entry.getValue();

            for (final HttpCookie c : lst) {
                if (c.getVersion() == 0 && netscapeDomainMatches(domain, host)
                    || c.getVersion() == 1 && HttpCookie.domainMatches(domain, host)) {

                    if (!c.hasExpired()) {
                        // don't add twice
                        if (!cookies.contains(c)) {
                            cookies.add(c);
                        }
                    } else {
                        expiredToPurge.add(c);
                    }
                }
            }
        }
    }

    /**
     * Check the {@link #uriIndex} for cookies.
     *
     * @param cookies        [OUT] contains the found cookies
     * @param expiredToPurge [OUT] contains expired cookies to remove after reading
     * @param comparator     the prediction to decide whether or not
     *                       a cookie in index should be returned
     */
    private void checkUriIndex(@NonNull final List<HttpCookie> cookies,
                               @NonNull final List<HttpCookie> expiredToPurge,
                               @Nullable final Comparable<URI> comparator) {
        if (comparator == null) {
            return;
        }

        // Android-changed: InMemoryCookieStore ignores scheme (http/https). b/25897688
        for (final Map.Entry<URI, List<HttpCookie>> entry : uriIndex.entrySet()) {
            final URI index = entry.getKey();

            if (index == comparator || index != null && comparator.compareTo(index) == 0) {
                final List<HttpCookie> indexedCookies = entry.getValue();

                if (indexedCookies != null) {
                    for (final HttpCookie ck : indexedCookies) {
                        if (!ck.hasExpired()) {
                            // don't add twice
                            if (!cookies.contains(ck)) {
                                cookies.add(ck);
                            }
                        } else {
                            expiredToPurge.add(ck);
                        }
                    }
                }
            }
        }
    }

    /**
     * Add 'cookie' indexed by 'index' into 'indexStore'.
     *
     * @param <T> type of the index
     */
    private <T> void addIndex(@NonNull final Map<T, List<HttpCookie>> indexStore,
                              @Nullable final T index,
                              @NonNull final HttpCookie cookie) {
        // Android-changed: "index" can be null.
        // We only use the URI based index on Android and we want to support null URIs. The
        // underlying store is a HashMap which will support null keys anyway.
        List<HttpCookie> cookies = indexStore.get(index);
        if (cookies != null) {
            // there may already have the same cookie, so remove it first
            cookies.remove(cookie);
            cookies.add(cookie);
        } else {
            cookies = new ArrayList<>();
            cookies.add(cookie);
            indexStore.put(index, cookies);
        }
    }

    private void removeFromIndex(@NonNull final Map<String, List<HttpCookie>> indexStore,
                                 @Nullable final String index,
                                 @NonNull final HttpCookie cookie) {
        if (index == null) {
            return;
        }
        final List<HttpCookie> cookies = indexStore.get(index);
        if (cookies != null) {
            cookies.remove(cookie);
            if (cookies.isEmpty()) {
                indexStore.remove(index);
            }
        }
    }

    /**
     * For cookie purpose, the effective uri should only be {@code http://host}.
     * The path will be taken into account when path-match algorithm applied.
     *
     * @param uri to transform
     *
     * @return http based uri
     */
    @Nullable
    private URI getEffectiveURI(@Nullable final URI uri) {
        if (uri == null) {
            return null;
        }
        try {
            return new URI("http", uri.getHost(), null, null, null);
        } catch (@NonNull final URISyntaxException ignored) {
            return uri;
        }
    }
}
