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

public interface StylePersistenceLayer {

    void remove(@NonNull String key);

    void setString(@NonNull String key,
                   @Nullable String value);

    @Nullable
    String getNonGlobalString(@NonNull String key);

    @Nullable
    String getString(@NonNull String key);

    void setBoolean(@NonNull String key,
                    @Nullable Boolean value);

    boolean getNonGlobalBoolean(@NonNull String key);

    @Nullable
    Boolean getBoolean(@NonNull String key);

    void setInt(@NonNull String key,
                @Nullable Integer value);

    @Nullable
    Integer getInteger(@NonNull String key);

    void setStringedInt(@NonNull String key,
                        @Nullable Integer value);

    @Nullable
    Integer getStringedInt(@NonNull String key);

    void setIntList(@NonNull String key,
                    @Nullable ArrayList<Integer> value);

    @Nullable
    ArrayList<Integer> getIntList(@NonNull String key);

    void setBitmask(@NonNull String key,
                    @Nullable Integer value);

    @Nullable
    Integer getBitmask(@NonNull String key);
}
