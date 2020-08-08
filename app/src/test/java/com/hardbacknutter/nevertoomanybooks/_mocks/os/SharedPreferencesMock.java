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
package com.hardbacknutter.nevertoomanybooks._mocks.os;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Mocks both {@link SharedPreferences} and {@link SharedPreferences.Editor}.
 */
public final class SharedPreferencesMock {

    @NonNull
    public static SharedPreferences create() {

        final HashMap<String, Object> map = new HashMap<>();
        final SharedPreferences sharedPreferences = Mockito.mock(SharedPreferences.class);

        final Answer<Object> unsupported = invocation -> {
            throw new UnsupportedOperationException();
        };

        final Answer<Object> getOrDefault = invocation -> {
            String key = (String) invocation.getArguments()[0];
            return map.containsKey(key) ? map.get(key) : invocation.getArguments()[1];
        };

        final Answer<Object> getAll = invocation -> map;

        doAnswer(invocation -> map.containsKey((String) invocation.getArguments()[0]))
                .when(sharedPreferences).contains(anyString());

        when(sharedPreferences.getString(anyString(), nullable(String.class)))
                .thenAnswer(getOrDefault);
        //noinspection unchecked
        when(sharedPreferences.getStringSet(anyString(), nullable(Set.class)))
                .thenAnswer(getOrDefault);

        when(sharedPreferences.getBoolean(anyString(), any(boolean.class)))
                .thenAnswer(getOrDefault);
        when(sharedPreferences.getLong(anyString(), any(long.class))).thenAnswer(getOrDefault);
        when(sharedPreferences.getInt(anyString(), any(int.class))).thenAnswer(getOrDefault);
        when(sharedPreferences.getFloat(anyString(), any(float.class))).thenAnswer(getOrDefault);

        when(sharedPreferences.getAll()).thenAnswer(getAll);

        doAnswer(unsupported).when(sharedPreferences).registerOnSharedPreferenceChangeListener(
                any(SharedPreferences.OnSharedPreferenceChangeListener.class));
        doAnswer(unsupported).when(sharedPreferences).unregisterOnSharedPreferenceChangeListener(
                any(SharedPreferences.OnSharedPreferenceChangeListener.class));

        /*
         * Setup SharedPreferences.Editor.
         */
        final SharedPreferences.Editor editor = Mockito.mock(SharedPreferences.Editor.class);
        when(sharedPreferences.edit()).thenReturn(editor);

        final Map<String, Object> editorMap = new HashMap<>();
        final Collection<String> editorRemovals = new ArrayList<>();
        final AtomicBoolean editorClearWasCalled = new AtomicBoolean(false);

        Answer<SharedPreferences.Editor> putAndChain = invocation -> {
            editorMap.put((String) invocation.getArguments()[0], invocation.getArguments()[1]);
            return editor;
        };

        doAnswer((Answer<SharedPreferences.Editor>) invocation -> {
            editorRemovals.add((String) invocation.getArguments()[0]);
            return editor;
        }).when(editor).remove(anyString());

        doAnswer(putAndChain).when(editor).putString(anyString(), nullable(String.class));
        //noinspection unchecked
        doAnswer(putAndChain).when(editor).putStringSet(anyString(), nullable(Set.class));

        doAnswer(putAndChain).when(editor).putBoolean(anyString(), any(boolean.class));
        doAnswer(putAndChain).when(editor).putLong(anyString(), any(long.class));
        doAnswer(putAndChain).when(editor).putInt(anyString(), any(int.class));
        doAnswer(putAndChain).when(editor).putFloat(anyString(), any(float.class));

        Answer<Void> apply = (Answer<Void>) invocation -> {
            // contract of apply states that clear/remove is run first
            if (editorClearWasCalled.get()) {
                map.clear();
            } else {
                editorRemovals.forEach(map::remove);
            }
            // and then copy the new values set
            map.putAll(editorMap);

            // clean up for next time
            editorClearWasCalled.set(false);
            editorRemovals.clear();
            return null;
        };

        doAnswer(apply).when(editor).apply();

        doAnswer(invocation -> {
            apply.answer(invocation);
            return true;
        }).when(editor).commit();

        doAnswer((Answer<SharedPreferences.Editor>) invocation -> {
            editorClearWasCalled.set(true);
            return editor;
        }).when(editor).clear();

        return sharedPreferences;
    }
}
