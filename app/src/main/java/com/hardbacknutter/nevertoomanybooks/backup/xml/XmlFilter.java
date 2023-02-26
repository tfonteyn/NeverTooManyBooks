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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The XmlFilter objects build a tree of filters and XmlHandler objects
 * that make this process more manageable.
 */
final class XmlFilter {

    /** The tag for this specific filter. */
    @NonNull
    private final String tagName;

    /** A HashMap to ensure that there are no more than one sub-filter per tag at a given level. */
    private final Map<String, XmlFilter> subFilterHash = new HashMap<>();

    /** List of sub-filters for this filter. */
    private final Collection<XmlFilter> subFilters = new ArrayList<>();
    /** Action to perform, if any, when the associated tag is started. */
    @Nullable
    private Consumer<ElementContext> startAction;
    /** Action to perform, if any, when the associated tag is finished. */
    @Nullable
    private Consumer<ElementContext> endAction;

    /**
     * Constructor for the root tag.
     */
    XmlFilter() {
        this("");

    }

    /**
     * Constructor.
     *
     * @param pattern The tag that this filter handles
     */
    private XmlFilter(@NonNull final String pattern) {
        tagName = pattern;
    }

    /**
     * Add a filter to the tree and return the matching XmlFilter.
     *
     * @param filters Names of tags to add to tree, if not present.
     *
     * @return The filter matching the final tag name passed.
     */
    @NonNull
    XmlFilter addFilter(@NonNull final String... filters) {
        if (filters.length == 0) {
            throw new IllegalArgumentException();
        }
        return addFilter(Arrays.asList(filters).iterator());
    }

    /**
     * Internal implementation of method to add a filter to the tree and return
     * the matching XmlFilter.
     * This is called recursively to process the filter list.
     *
     * @param iterator Names of tags to add to tree, if not present.
     *
     * @return The filter matching the final tag name passed.
     */
    @NonNull
    private XmlFilter addFilter(@NonNull final Iterator<String> iterator) {
        final String curr = iterator.next();
        XmlFilter sub = getSubFilter(curr);
        if (sub == null) {
            sub = new XmlFilter(curr);
            addFilter(sub);
        }
        if (iterator.hasNext()) {
            return sub.addFilter(iterator);
        } else {
            return sub;
        }
    }

    /**
     * Check if this filter matches the passed XML tag.
     *
     * @param tag Tag name
     *
     * @return Boolean indicating it matches.
     */
    private boolean matches(@Nullable final String tag) {
        return tagName.equalsIgnoreCase(tag);
    }

    /**
     * Find a sub-filter for the passed context.
     * Currently just used local_name from the context.
     *
     * @param context Current ElementContext
     *
     * @return Matching filter, or {@code null} if none found
     */
    @Nullable
    XmlFilter getSubFilter(@NonNull final ElementContext context) {
        return getSubFilter(context.getLocalName());
    }

    /**
     * Find a sub-filter based on the passed tag name.
     *
     * @param name XML tag name
     *
     * @return Matching filter, or {@code null} if none found
     */
    @Nullable
    private XmlFilter getSubFilter(@Nullable final String name) {
        for (final XmlFilter f : subFilters) {
            if (f.matches(name)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Called when associated tag is started.
     *
     * @param context Current ElementContext
     */
    void processStart(@NonNull final ElementContext context) {
        if (startAction != null) {
            startAction.accept(context);
        }
    }

    /**
     * Called when associated tag is finished.
     *
     * @param context Current ElementContext
     */
    void processEnd(@NonNull final ElementContext context) {
        if (endAction != null) {
            endAction.accept(context);
        }
    }

    /**
     * Get the name of the tag that this filter will match.
     *
     * @return name
     */
    @NonNull
    private String getTagName() {
        return tagName;
    }

    /**
     * Set the action to perform when the tag associated with this filter is started.
     *
     * @param startAction XmlHandler to call
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    XmlFilter setStartAction(@NonNull final Consumer<ElementContext> startAction) {
        this.startAction = startAction;
        return this;
    }

    /**
     * Set the action to perform when the tag associated with this filter is finished.
     *
     * @param endAction XmlHandler to call
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    XmlFilter setEndAction(@NonNull final Consumer<ElementContext> endAction) {
        this.endAction = endAction;
        return this;
    }

    /**
     * Add a filter at this level; ensure it is unique.
     *
     * @param filter filter to add
     *
     * @throws IllegalStateException if the filter already exists
     */
    private void addFilter(@NonNull final XmlFilter filter) {
        final String lcPat = filter.getTagName().toLowerCase(Locale.ENGLISH);
        if (subFilterHash.containsKey(lcPat)) {
            throw new IllegalStateException("Filter " + filter.getTagName() + " already exists");
        }
        subFilterHash.put(lcPat, filter);
        subFilters.add(filter);
    }
}
