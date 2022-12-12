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
 * The HTTPTokener extends the JSONTokener to provide additional methods
 * for the parsing of HTTP headers.
 *
 * @author JSON.org
 * @version 2015-12-09
 */
public class HTTPTokener
        extends JSONTokener {

    /**
     * Construct an HTTPTokener from a string.
     *
     * @param string A source string.
     */
    public HTTPTokener(final String string) {
        super(string);
    }


    /**
     * Get the next token or string. This is used in parsing HTTP headers.
     *
     * @return A String.
     *
     * @throws JSONException if a syntax error occurs
     */
    public String nextToken()
            throws JSONException {
        char c;
        final char q;
        final StringBuilder sb = new StringBuilder();
        do {
            c = next();
        } while (Character.isWhitespace(c));
        if (c == '"' || c == '\'') {
            q = c;
            for (; ; ) {
                c = next();
                if (c < ' ') {
                    throw syntaxError("Unterminated string.");
                }
                if (c == q) {
                    return sb.toString();
                }
                sb.append(c);
            }
        }
        for (; ; ) {
            if (c == 0 || Character.isWhitespace(c)) {
                return sb.toString();
            }
            sb.append(c);
            c = next();
        }
    }
}
