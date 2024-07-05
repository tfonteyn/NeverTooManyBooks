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

import androidx.annotation.NonNull;

/**
 * Configuration object for the XML to JSONML parser. The configuration is immutable.
 */
@SuppressWarnings("ALL")
public class JSONMLParserConfiguration
        extends ParserConfiguration {

    /**
     * We can override the default maximum nesting depth if needed.
     */
    public static final int DEFAULT_MAXIMUM_NESTING_DEPTH = ParserConfiguration.DEFAULT_MAXIMUM_NESTING_DEPTH;

    /** Original Configuration of the XML to JSONML Parser. */
    public static final JSONMLParserConfiguration ORIGINAL
            = new JSONMLParserConfiguration();
    /** Original configuration of the XML to JSONML Parser except that values are kept as strings. */
    public static final JSONMLParserConfiguration KEEP_STRINGS
            = new JSONMLParserConfiguration().withKeepStrings(true);

    /**
     * Default parser configuration. Does not keep strings (tries to implicitly convert values).
     */
    public JSONMLParserConfiguration() {
        super();
        this.maxNestingDepth = DEFAULT_MAXIMUM_NESTING_DEPTH;
    }

    /**
     * Configure the parser string processing and use the default CDATA Tag Name as "content".
     * @param keepStrings <code>true</code> to parse all values as string.
     *      <code>false</code> to try and convert XML string values into a JSON value.
     * @param maxNestingDepth <code>int</code> to limit the nesting depth
     */
    protected JSONMLParserConfiguration(final boolean keepStrings,
                                        final int maxNestingDepth) {
        super(keepStrings, maxNestingDepth);
    }

    /**
     * Provides a new instance of the same configuration.
     */
    @Override
    @NonNull
    protected JSONMLParserConfiguration clone() {
        // future modifications to this method should always ensure a "deep"
        // clone in the case of collections. i.e. if a Map is added as a configuration
        // item, a new map instance should be created and if possible each value in the
        // map should be cloned as well. If the values of the map are known to also
        // be immutable, then a shallow clone of the map is acceptable.
        return new JSONMLParserConfiguration(
                this.keepStrings,
                this.maxNestingDepth
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public JSONMLParserConfiguration withKeepStrings(final boolean newVal) {
        return super.withKeepStrings(newVal);
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public JSONMLParserConfiguration withMaxNestingDepth(int maxNestingDepth) {
        return super.withMaxNestingDepth(maxNestingDepth);
    }
}
