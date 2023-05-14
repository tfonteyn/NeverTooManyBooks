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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class WrappedArrayList<E>
        implements List<E> {

    @NonNull
    private final ArrayList<E> list;

    protected WrappedArrayList(@NonNull final ArrayList<E> list) {
        this.list = list;
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(@Nullable final Object o) {
        return list.contains(o);
    }

    @NonNull
    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    @NonNull
    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @NonNull
    @Override
    public <T> T[] toArray(@NonNull final T[] a) {
        //noinspection SuspiciousToArrayCall
        return list.toArray(a);
    }

    @Override
    public boolean add(final E e) {
        return list.add(e);
    }

    @Override
    public boolean remove(@Nullable final Object o) {
        return list.remove(o);
    }

    @Override
    public boolean containsAll(@NonNull final Collection<?> collection) {
        return list.containsAll(collection);
    }

    @Override
    public boolean addAll(@NonNull final Collection<? extends E> collection) {
        return list.addAll(collection);
    }

    @Override
    public boolean addAll(final int index,
                          @NonNull final Collection<? extends E> collection) {
        return list.addAll(index, collection);
    }

    @Override
    public boolean removeAll(@NonNull final Collection<?> collection) {
        return list.removeAll(collection);
    }

    @Override
    public boolean retainAll(@NonNull final Collection<?> collection) {
        return list.retainAll(collection);
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    @Nullable
    public E get(final int index) {
        return list.get(index);
    }

    @Override
    public E set(final int index,
                 final E element) {
        return list.set(index, element);
    }

    @Override
    public void add(final int index,
                    final E element) {
        list.add(index, element);
    }

    @Override
    public E remove(final int index) {
        return list.remove(index);
    }

    @Override
    public int indexOf(@Nullable final Object o) {
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(@Nullable final Object o) {
        return list.lastIndexOf(o);
    }

    @NonNull
    @Override
    public ListIterator<E> listIterator() {
        return list.listIterator();
    }

    @NonNull
    @Override
    public ListIterator<E> listIterator(final int index) {
        return list.listIterator(index);
    }

    @NonNull
    @Override
    public List<E> subList(final int fromIndex,
                           final int toIndex) {
        return list.subList(fromIndex, toIndex);
    }
}
