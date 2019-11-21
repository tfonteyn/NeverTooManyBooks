/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.utils.xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.SearchBooksApiHandler;

/**
 * A class to help parsing Sax Xml output. For Goodreads XML output, 90% of the XML can be
 * thrown away but we do need to ensure we get the tags from the right context. The XmlFilter
 * objects build a tree of filters and XmlHandler objects that make this process more manageable.
 * <p>
 * See {@link SearchBooksApiHandler} for an example of usage.
 */
public class XmlFilter {

    /** The tag for this specific filter. */
    @NonNull
    private final String mTagName;

    /** A HashMap to ensure that there are no more than one sub-filter per tag at a given level. */
    private final Map<String, XmlFilter> mSubFilterHash = new HashMap<>();

    /** List of sub-filters for this filter. */
    private final Collection<XmlFilter> mSubFilters = new ArrayList<>();

    /** Action to perform, if any, when the associated tag is started. */
    @Nullable
    private XmlHandler mStartAction;
    /** Optional parameter put in context before action is called. */
    private Object mStartArg;

    /** Action to perform, if any, when the associated tag is finished. */
    @Nullable
    private XmlHandler mEndAction;
    /** Optional parameter put in context before action is called. */
    private Object mEndArg;

    /**
     * Constructor.
     *
     * @param pattern The tag that this filter handles
     */
    public XmlFilter(@NonNull final String pattern) {
        mTagName = pattern;
    }

    /**
     * Static method to add a filter to a passed tree and return the matching XmlFilter.
     *
     * @param root    Root XmlFilter object.
     * @param filters Names of tags to add to tree, if not present.
     *
     * @return The filter matching the final tag name passed.
     */
    @NonNull
    public static XmlFilter buildFilter(@NonNull final XmlFilter root,
                                        @NonNull final String... filters) {
        if (filters.length <= 0) {
            throw new IllegalArgumentException("no filters");
        }
        return buildFilter(root, 0, Arrays.asList(filters).iterator());
    }

    /**
     * Static method to add a filter to a passed tree and return the matching XmlFilter.
     *
     * @param root    Root XmlFilter object.
     * @param filters Names of tags to add to tree, if not present.
     *
     * @return The filter matching the final tag name passed.
     */
    @NonNull
    static XmlFilter buildFilter(@NonNull final XmlFilter root,
                                 @NonNull final Collection<String> filters) {
        if (filters.isEmpty()) {
            throw new IllegalArgumentException("no filters");
        }
        return buildFilter(root, 0, filters.iterator());
    }

    /**
     * Internal implementation of method to add a filter to a passed tree and return
     * the matching XmlFilter.
     * This is called recursively to process the filter list.
     *
     * @param root     Root XmlFilter object.
     * @param depth    Recursion depth
     * @param iterator Names of tags to add to tree, if not present.
     *
     * @return The filter matching the final tag name passed.
     */
    @NonNull
    private static XmlFilter buildFilter(@NonNull final XmlFilter root,
                                         final int depth,
                                         @NonNull final Iterator<String> iterator) {
        //if (!root.matches(filters[depth]))
        //    throw new RuntimeException("Filter at depth=" + depth +
        //                               " does not match first filter parameter");

        final String curr = iterator.next();
        XmlFilter sub = root.getSubFilter(curr);
        if (sub == null) {
            sub = new XmlFilter(curr);
            root.addFilter(sub);
        }
        if (!iterator.hasNext()) {
            // At end
            return sub;
        } else {
            // We are still finding leaf
            return buildFilter(sub, depth + 1, iterator);
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
        return mTagName.equalsIgnoreCase(tag);
    }

    /**
     * Find a sub-filter for the passed context.
     * Currently just used local_name from the context.
     *
     * @param context Current ElementContext
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
        for (XmlFilter f : mSubFilters) {
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
        if (mStartAction != null) {
            context.setUserArg(mStartArg);
            mStartAction.process(context);
        }
    }

    /**
     * Called when associated tag is finished.
     *
     * @param context Current ElementContext
     */
    void processEnd(@NonNull final ElementContext context) {
        if (mEndAction != null) {
            context.setUserArg(mEndArg);
            mEndAction.process(context);
        }
    }

    /**
     * @return the tag that this filter will match
     */
    @NonNull
    private String getTagName() {
        return mTagName;
    }

    /**
     * Set the action to perform when the tag associated with this filter is started.
     *
     * @param startAction XmlHandler to call
     *
     * @return This XmlFilter, to allow chaining
     */
    @NonNull
    public XmlFilter setStartAction(@NonNull final XmlHandler startAction) {
        return setStartAction(startAction, null);
    }

    @NonNull
    public XmlFilter setStartAction(@NonNull final XmlHandler startAction,
                                    @Nullable final Object userArg) {
        if (mStartAction != null) {
            throw new IllegalStateException("Start Action already set");
        }
        mStartAction = startAction;
        mStartArg = userArg;
        return this;
    }

    /**
     * Set the action to perform when the tag associated with this filter is finished.
     *
     * @param endAction XmlHandler to call
     *
     * @return This XmlFilter, to allow chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public XmlFilter setEndAction(@NonNull final XmlHandler endAction) {
        return setEndAction(endAction, null);
    }

    @NonNull
    public XmlFilter setEndAction(@NonNull final XmlHandler endAction,
                                  @Nullable final Object userArg) {
        if (mEndAction != null) {
            throw new IllegalStateException("End Action already set");
        }
        mEndAction = endAction;
        mEndArg = userArg;
        return this;
    }


    /**
     * Add a filter at this level; ensure it is unique.
     *
     * @param filter filter to add
     */
    private void addFilter(@NonNull final XmlFilter filter) {
        String lcPat = filter.getTagName().toLowerCase(App.getSystemLocale());
        if (mSubFilterHash.containsKey(lcPat)) {
            throw new RuntimeException("Filter " + filter.getTagName() + " already exists");
        }
        mSubFilterHash.put(lcPat, filter);
        mSubFilters.add(filter);
    }

    /** Interface definition for filter handlers. */
    public interface XmlHandler {

        void process(@NonNull ElementContext elementContext);
    }

}
