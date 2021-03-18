/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;

public class JSoupHelper {

    private static final String ATTR_CHECKED = "checked";
    private static final String ATTR_STYLE = "style";
    private static final Pattern ATTR_STYLE_PATTERN = Pattern.compile("[:;]");

    /**
     * Get the String value, or {@code null} if the element was not found.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return String
     */
    @Nullable
    public String getString(@NonNull final Element root,
                            @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            return element.val();
        }
        return null;
    }

    /**
     * Get the Integer value, or {@code null} if the element was not found.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return Integer
     */
    @Nullable
    public Integer getInt(@NonNull final Element root,
                          @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            final String val = element.val();
            if (!val.isEmpty()) {
                try {
                    return Integer.parseInt(val);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return null;
    }

    /**
     * Get the Double value, or {@code null} if the element was not found.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return Double
     */
    @Nullable
    public Double getDouble(@NonNull final Element root,
                            @NonNull final String id) {
        final Element element = root.getElementById(id);
        if (element != null) {
            final String val = element.val();
            if (!val.isEmpty()) {
                try {
                    return Double.parseDouble(val);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return null;
    }

    /**
     * Get the boolean value, or {@code null} if the element was not found.
     *
     * @param root to start the lookup
     * @param id   to lookup
     *
     * @return boolean
     */
    @Nullable
    public Boolean getBoolean(@NonNull final Element root,
                              @NonNull final String id) {
        final Element checkbox = root.getElementById(id);
        if (checkbox != null && "checkbox".equalsIgnoreCase(checkbox.attr("type"))) {
            return ATTR_CHECKED.equalsIgnoreCase(checkbox.attr(ATTR_CHECKED));
        }
        return null;
    }

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
        return keymaps;
    }
}
