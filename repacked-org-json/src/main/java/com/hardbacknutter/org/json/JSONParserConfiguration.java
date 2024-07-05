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

import androidx.annotation.NonNull;

/**
 * Configuration object for the JSON parser. The configuration is immutable.
 */
public class JSONParserConfiguration
        extends ParserConfiguration {

    /**
     * Configuration with the default values.
     */
    public JSONParserConfiguration() {
        super();
    }

    @Override
    @NonNull
    protected JSONParserConfiguration clone() {
        return new JSONParserConfiguration();
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public JSONParserConfiguration withMaxNestingDepth(final int maxNestingDepth) {
        return super.withMaxNestingDepth(maxNestingDepth);
    }

}
