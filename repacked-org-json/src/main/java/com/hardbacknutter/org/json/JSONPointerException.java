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

package com.hardbacknutter.org.json;

/*
Public Domain.
*/

/**
 * The JSONPointerException is thrown by {@link JSONPointer} if an error occurs
 * during evaluating a pointer.
 * 
 * @author JSON.org
 * @version 2016-05-13
 */
@SuppressWarnings("ALL")
public class JSONPointerException
        extends JSONException {
    private static final long serialVersionUID = 8872944667561856751L;

    /**
     * Constructs a new JSONPointerException with the specified error message.
     *
     * @param message The detail message describing the reason for the exception.
     */
    public JSONPointerException(String message) {
        super(message);
    }

    /**
     * Constructs a new JSONPointerException with the specified error message and cause.
     *
     * @param message The detail message describing the reason for the exception.
     * @param cause   The cause of the exception.
     */
    public JSONPointerException(String message,
                                Throwable cause) {
        super(message, cause);
    }

}
