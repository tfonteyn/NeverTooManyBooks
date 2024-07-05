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
 * Configuration base object for parsers. The configuration is immutable.
 */
@SuppressWarnings("ALL")
public class ParserConfiguration {
    /**
     * Used to indicate there's no defined limit to the maximum nesting depth when parsing a document.
     */
    public static final int UNDEFINED_MAXIMUM_NESTING_DEPTH = -1;

    /**
     * The default maximum nesting depth when parsing a document.
     */
    public static final int DEFAULT_MAXIMUM_NESTING_DEPTH = 512;

    /**
     * Specifies if values should be kept as strings (<code>true</code>), or if
     * they should try to be guessed into JSON values (numeric, boolean, string)
     */
    protected boolean keepStrings;

    /**
     * The maximum nesting depth when parsing a document.
     */
    protected int maxNestingDepth;

    /**
     * Constructs a new ParserConfiguration with default settings.
     */
    public ParserConfiguration() {
        this.keepStrings = false;
        this.maxNestingDepth = DEFAULT_MAXIMUM_NESTING_DEPTH;
    }

    /**
     * Constructs a new ParserConfiguration with the specified settings.
     *
     * @param keepStrings     A boolean indicating whether to preserve strings during parsing.
     * @param maxNestingDepth An integer representing the maximum allowed nesting depth.
     */
    protected ParserConfiguration(final boolean keepStrings,
                                  final int maxNestingDepth) {
        this.keepStrings = keepStrings;
        this.maxNestingDepth = maxNestingDepth;
    }

    /**
     * Provides a new instance of the same configuration.
     */
    @Override
    @NonNull
    protected ParserConfiguration clone() {
        // future modifications to this method should always ensure a "deep"
        // clone in the case of collections. i.e. if a Map is added as a configuration
        // item, a new map instance should be created and if possible each value in the
        // map should be cloned as well. If the values of the map are known to also
        // be immutable, then a shallow clone of the map is acceptable.
        return new ParserConfiguration(
                this.keepStrings,
                this.maxNestingDepth
        );
    }

    /**
     * When parsing the XML into JSONML, specifies if values should be kept as strings (<code>true</code>), or if
     * they should try to be guessed into JSON values (numeric, boolean, string)
     *
     * @return The <code>keepStrings</code> configuration value.
     */
    public boolean isKeepStrings() {
        return this.keepStrings;
    }

    /**
     * When parsing the XML into JSONML, specifies if values should be kept as strings (<code>true</code>), or if
     * they should try to be guessed into JSON values (numeric, boolean, string)
     *
     * @param newVal new value to use for the <code>keepStrings</code> configuration option.
     * @param <T>    the type of the configuration object
     *
     * @return The existing configuration will not be modified. A new configuration is returned.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <T extends ParserConfiguration> T withKeepStrings(final boolean newVal) {
        T newConfig = (T) this.clone();
        newConfig.keepStrings = newVal;
        return newConfig;
    }

    /**
     * The maximum nesting depth that the parser will descend before throwing an exception
     * when parsing the XML into JSONML.
     *
     * @return the maximum nesting depth set for this configuration
     */
    public int getMaxNestingDepth() {
        return maxNestingDepth;
    }

    /**
     * Defines the maximum nesting depth that the parser will descend before throwing an exception
     * when parsing the XML into JSONML. The default max nesting depth is 512, which means the parser
     * will throw a JsonException if the maximum depth is reached.
     * Using any negative value as a parameter is equivalent to setting no limit to the nesting depth,
     * which means the parses will go as deep as the maximum call stack size allows.
     *
     * @param maxNestingDepth the maximum nesting depth allowed to the XML parser
     * @param <T>             the type of the configuration object
     *
     * @return The existing configuration will not be modified. A new configuration is returned.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <T extends ParserConfiguration> T withMaxNestingDepth(int maxNestingDepth) {
        T newConfig = (T) this.clone();

        if (maxNestingDepth > UNDEFINED_MAXIMUM_NESTING_DEPTH) {
            newConfig.maxNestingDepth = maxNestingDepth;
        } else {
            newConfig.maxNestingDepth = UNDEFINED_MAXIMUM_NESTING_DEPTH;
        }

        return newConfig;
    }
}
