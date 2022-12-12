/*
 * @Copyright 2018-2022 HardBackNutter
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

package com.hardbacknutter.org.json;

/*
Public Domain.
 */

/**
 * The {@code JSONString} interface allows a {@code toJSONString()}
 * method so that a class can change the behavior of
 * {@code JSONObject.toString()}, {@code JSONArray.toString()},
 * and {@code JSONWriter.value(}Object{@code )}. The
 * {@code toJSONString} method will be used instead of the default behavior
 * of using the Object's {@code toString()} method and quoting the result.
 */
@FunctionalInterface
public interface JSONString {

    /**
     * The {@code toJSONString} method allows a class to produce its own JSON
     * serialization.
     *
     * @return A strictly syntactically correct JSON text.
     */
    String toJSONString();
}
