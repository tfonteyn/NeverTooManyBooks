/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;

public class JSoupHelper {

    private static final String ATTR_CHECKED = "checked";
    private static final String ATTR_STYLE = "style";
    private static final Pattern ATTR_STYLE_PATTERN = Pattern.compile("[:;]");

    /**
     * Get a non-empty String value.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return String
     */
    @NonNull
    public Optional<String> getNonEmptyString(@NonNull final Element root,
                                              @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            final String value = element.val();
            if (!value.isEmpty()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Get a positive (non-zero) int value.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return int
     */
    @NonNull
    public OptionalInt getPositiveInt(@NonNull final Element root,
                                      @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            final String val = element.val();
            if (!val.isEmpty()) {
                try {
                    final int value = Integer.parseInt(val);
                    if (value > 0) {
                        return OptionalInt.of(value);
                    }
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Get a positive (non-zero) long value.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return long
     */
    @NonNull
    public OptionalLong getPositiveLong(@NonNull final Element root,
                                        @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            final String val = element.val();
            if (!val.isEmpty()) {
                try {
                    final long value = Long.parseLong(val);
                    if (value > 0) {
                        return OptionalLong.of(value);
                    }
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Get a positive (can be zero) double value.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return double
     */
    @NonNull
    public OptionalDouble getPositiveOrZeroDouble(@NonNull final Element root,
                                                  @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            final String val = element.val();
            if (!val.isEmpty()) {
                try {
                    final double value = Double.parseDouble(val);
                    if (value >= 0L) {
                        return OptionalDouble.of(value);
                    }
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Get a boolean value.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return boolean
     */
    @NonNull
    public Optional<Boolean> getBoolean(@NonNull final Element root,
                                        @NonNull final String id) {
        final Element checkbox = root.getElementById(id);
        if (checkbox != null && "checkbox".equalsIgnoreCase(checkbox.attr("type"))) {
            return Optional.of(ATTR_CHECKED.equalsIgnoreCase(checkbox.attr(ATTR_CHECKED)));
        }
        return Optional.empty();
    }

    /**
     * Read the style attribute from the given Element and return a map
     * with each style element mapped to one or more values.
     * Interpretation of multi-value entries is up to the caller.
     *
     * @param element to parse
     *
     * @return unmodifiableMap
     */
    @SuppressWarnings("unused")
    @NonNull
    public Map<String, String[]> getStyleMap(@NonNull final Element element) {
        final Map<String, String[]> keymaps = new HashMap<>();
        if (!element.hasAttr(ATTR_STYLE)) {
            return keymaps;
        }

        final String[] list = ATTR_STYLE_PATTERN.split(element.attr(ATTR_STYLE));
        for (int i = 0; i < list.length; i += 2) {
            keymaps.put(list[i].trim(), list[i + 1].trim().split(" "));
        }
        return Collections.unmodifiableMap(keymaps);
    }
}
