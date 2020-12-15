/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface StylePersistenceLayer {

    /**
     * Remove the value for the given key.
     *
     * @param key preference key
     */
    void remove(@NonNull String key);

    /**
     * Set or remove a String value.
     *
     * @param key   preference key
     * @param value to set
     */
    void setString(@NonNull String key,
                   @Nullable String value);

    /**
     * Get a String value for the given key.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    String getNonGlobalString(@NonNull String key);

    /**
     * Get a String value for the given key.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    String getString(@NonNull String key);


    /**
     * Set or remove the boolean value for the given key.
     *
     * @param key   preference key
     * @param value to set
     */
    void setBoolean(@NonNull String key,
                    @Nullable Boolean value);

    /**
     * Get a Boolean value for the given key.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>{@code false}</li>
     * </ol>
     */
    boolean getNonGlobalBoolean(@NonNull String key);

    /**
     * Get a Boolean value for the given key.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    Boolean getBoolean(@NonNull String key);


    /**
     * Set or remove the int value for the given key.
     *
     * @param key   preference key
     * @param value to set
     */
    void setInt(@NonNull String key,
                @Nullable Integer value);

    /**
     * Get an Integer value for the given key.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    Integer getInteger(@NonNull String key);


    /**
     * Set or remove a bitmask (int) value, which is <strong>stored as a StringSet</strong>.
     *
     * @param key   preference key
     * @param value to set
     */
    void setBitmask(@NonNull String key,
                    @Nullable Integer value);

    /**
     * Get a bitmask (int) value, which is <strong>stored as a StringSet</strong>.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    Integer getBitmask(@NonNull String key);


    /**
     * Set or remove the single Integer value, which is <strong>stored as a String</strong>.
     *
     * @param key   preference key
     * @param value to set
     */
    void setStringedInt(@NonNull String key,
                        @Nullable Integer value);

    /**
     * Get a single Integer value, which is <strong>stored as a String</strong>.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    Integer getStringedInt(@NonNull String key);

    /**
     * Set or remove a list of Integer values, which are <strong>stored as a CSV String</strong>.
     *
     * @param key   preference key
     * @param value to set
     */
    void setStringedIntList(@NonNull String key,
                            @Nullable List<Integer> value);

    /**
     * Get a list of Integer values, which are <strong>stored as a CSV String</strong>.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    ArrayList<Integer> getStringedIntList(@NonNull String key);

    /**
     * Set or remove a list of Integer values, which are <strong>stored as a StringSet</strong>.
     *
     * @param key   preference key
     * @param value to set
     */
    void setIntList(@NonNull String key,
                    @Nullable List<Integer> value);

    /**
     * Get a list of Integer values, which are <strong>stored as a StringSet</strong>.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    ArrayList<Integer> getIntList(@NonNull String key);
}
