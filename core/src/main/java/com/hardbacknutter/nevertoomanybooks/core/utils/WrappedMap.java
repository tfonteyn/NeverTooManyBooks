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
package com.hardbacknutter.nevertoomanybooks.core.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class WrappedMap<K, V>
        implements Map<K, V> {

    @NonNull
    private final Map<K, V> map;

    WrappedMap(@NonNull final Map<K, V> map) {
        this.map = map;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(@Nullable final Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(@Nullable final Object value) {
        return map.containsValue(value);
    }

    @Nullable
    @Override
    public V get(@Nullable final Object key) {
        return map.get(key);
    }

    @Nullable
    @Override
    public V put(final K key,
                 final V value) {
        return map.put(key, value);
    }

    @Nullable
    @Override
    public V remove(@Nullable final Object key) {
        return map.remove(key);
    }

    @Override
    public void putAll(@NonNull final Map<? extends K, ? extends V> map) {
        this.map.putAll(map);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @NonNull
    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @NonNull
    @Override
    public Collection<V> values() {
        return map.values();
    }

    @NonNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }
}
