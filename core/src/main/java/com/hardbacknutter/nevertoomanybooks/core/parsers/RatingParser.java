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

package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.Optional;
import java.util.function.Function;

public class RatingParser {

    private static final String ERROR_MAX_MUST_BE_5_OR_10 = "max must be 5 or 10";

    private final boolean divBy2;
    @NonNull
    private final Function<String, Float> parser;

    /**
     * Constructor.
     * <p>
     * Parsing will use {@link Float#parseFloat(String)}.
     *
     * @param max either {@code 5} or {@code 10}
     *
     * @throws IllegalArgumentException if the max is different from 5 or 10
     */
    public RatingParser(final int max) {
        if (max != 5 && max != 10) {
            throw new IllegalArgumentException(ERROR_MAX_MUST_BE_5_OR_10);
        }
        parser = Float::parseFloat;
        divBy2 = max == 10;
    }

    /**
     * Constructor.
     *
     * @param realNumberParser the locale based parser to use
     * @param max              either {@code 5} or {@code 10}
     *
     * @throws IllegalArgumentException if the max is different from 5 or 10
     */
    public RatingParser(@NonNull final RealNumberParser realNumberParser,
                        final int max) {
        if (max != 5 && max != 10) {
            throw new IllegalArgumentException(ERROR_MAX_MUST_BE_5_OR_10);
        }
        parser = realNumberParser::parseFloat;
        divBy2 = max == 10;
    }

    /**
     * Parse and convert the given String.
     *
     * @param s to parse
     *
     * @return rating
     */
    @NonNull
    public Optional<Float> parse(@Nullable final String s) {
        if (s == null || s.isBlank() || "0".equals(s) || "0.0".equals(s)) {
            return Optional.empty();
        }

        try {
            final float rating = parser.apply(s);
            if (rating > 0) {
                return normalize(rating);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
        return Optional.empty();
    }

    /**
     * Normalize the given value.
     *
     * @param rating to process
     *
     * @return a value within the range 0..5
     */
    @NonNull
    public Optional<Float> normalize(final float rating) {
        if (Float.isNaN(rating)) {
            return Optional.empty();
        }

        float result;
        if (divBy2) {
            // 0.0 to 10.0 becomes an int 0..10
            // then divide by 2 to get 0.0..5.0
            result = (float) Math.round(rating) / 2;
        } else {
            // 0.0 to 5.0 becomes 0.0 to 10.0 which becomes an int 1..10
            // then divide by 2 to get 0..5
            result = (float) Math.round(2 * rating) / 2;
        }
        // paranoia
        result = MathUtils.clamp(result, (float) 0, (float) 5);

        if (result > 0) {
            return Optional.of(result);
        }
        return Optional.empty();
    }
}
