/*
 * @Copyright 2018-2025 HardBackNutter
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

/**
 * Configuration object for the JSON parser. The configuration is immutable.
 */
@SuppressWarnings("ALL")
public class JSONParserConfiguration
        extends ParserConfiguration {
    /**
     * Used to indicate whether to overwrite duplicate key or not.
     */
    private boolean overwriteDuplicateKey;

    /**
     * Configuration with the default values.
     */
    public JSONParserConfiguration() {
        super();
        this.overwriteDuplicateKey = false;
    }

    /**
     * This flag, when set to true, instructs the parser to enforce strict mode when parsing JSON text.
     * Garbage chars at the end of the doc, unquoted string, and single-quoted strings are all disallowed.
     */
    private boolean strictMode;

    @Override
    protected JSONParserConfiguration clone() {
        JSONParserConfiguration clone = new JSONParserConfiguration();
        clone.overwriteDuplicateKey = overwriteDuplicateKey;
        clone.maxNestingDepth = maxNestingDepth;
        return clone;
    }

    /**
     * Defines the maximum nesting depth that the parser will descend before throwing an exception
     * when parsing a map into JSONObject or parsing a {@link java.util.Collection} instance into
     * JSONArray. The default max nesting depth is 512, which means the parser will throw a JsonException
     * if the maximum depth is reached.
     *
     * @param maxNestingDepth the maximum nesting depth allowed to the JSON parser
     *
     * @return The existing configuration will not be modified. A new configuration is returned.
     */
    @SuppressWarnings("unchecked")
    @Override
    public JSONParserConfiguration withMaxNestingDepth(final int maxNestingDepth) {
        JSONParserConfiguration clone = this.clone();
        clone.maxNestingDepth = maxNestingDepth;

        return clone;
    }

    /**
     * Controls the parser's behavior when meeting duplicate keys.
     * If set to false, the parser will throw a JSONException when meeting a duplicate key.
     * Or the duplicate key's value will be overwritten.
     *
     * @param overwriteDuplicateKey defines should the parser overwrite duplicate keys.
     *
     * @return The existing configuration will not be modified. A new configuration is returned.
     */
    public JSONParserConfiguration withOverwriteDuplicateKey(final boolean overwriteDuplicateKey) {
        JSONParserConfiguration clone = this.clone();
        clone.overwriteDuplicateKey = overwriteDuplicateKey;

        return clone;
    }

    /**
     * Sets the strict mode configuration for the JSON parser with default true value
     * <p>
     * When strict mode is enabled, the parser will throw a JSONException if it encounters an invalid character
     * immediately following the final ']' character in the input. This is useful for ensuring strict adherence to the
     * JSON syntax, as any characters after the final closing bracket of a JSON array are considered invalid.
     *
     * @return a new JSONParserConfiguration instance with the updated strict mode setting
     */
    public JSONParserConfiguration withStrictMode() {
        return withStrictMode(true);
    }

    /**
     * Sets the strict mode configuration for the JSON parser.
     * <p>
     * When strict mode is enabled, the parser will throw a JSONException if it encounters an invalid character
     * immediately following the final ']' character in the input. This is useful for ensuring strict adherence to the
     * JSON syntax, as any characters after the final closing bracket of a JSON array are considered invalid.
     *
     * @param mode a boolean value indicating whether strict mode should be enabled or not
     *
     * @return a new JSONParserConfiguration instance with the updated strict mode setting
     */
    public JSONParserConfiguration withStrictMode(final boolean mode) {
        JSONParserConfiguration clone = this.clone();
        clone.strictMode = mode;

        return clone;
    }

    /**
     * The parser's behavior when meeting duplicate keys, controls whether the parser should
     * overwrite duplicate keys or not.
     *
     * @return The <code>overwriteDuplicateKey</code> configuration value.
     */
    public boolean isOverwriteDuplicateKey() {
        return this.overwriteDuplicateKey;
    }

    /**
     * @return the current strict mode setting.
     */
    public boolean isStrictMode() {
        return this.strictMode;
    }
}
