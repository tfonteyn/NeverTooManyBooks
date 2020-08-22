/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.xml.ElementContext;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;

/**
 * Class layered on top of {@link XmlFilter} to implement a simple set of XML filters to extract
 * data from an XML file and return the results in a collection of nested Bundle objects.
 */
public class SimpleXmlFilter {

    @NonNull
    private static final Consumer<ElementContext> mHandleStart = elementContext -> {
        BuilderContext bc = (BuilderContext) elementContext.getUserArg();
        if (bc.isArray()) {
            bc.initArray();
        }
        if (bc.isArrayItem()) {
            bc.pushBundle();
        }
        if (bc.listener != null) {
            bc.listener.onStart(bc, elementContext);
        }
        final List<AttrFilter> attrs = bc.attrs;
        if (attrs != null) {
            for (AttrFilter f : attrs) {
                final String value = elementContext.getAttributes().getValue(f.name);
                if (value != null) {
                    try {
                        f.put(bc, value);
                    } catch (@NonNull final RuntimeException ignore) {
                        // Could not be parsed....just ignore
                    }
                }
            }
        }
    };

    @NonNull
    private static final Consumer<ElementContext> mHandleFinish = elementContext -> {
        final BuilderContext bc = (BuilderContext) elementContext.getUserArg();
        if (bc.finishHandler != null) {
            bc.finishHandler.accept(elementContext);
        }
        if (bc.listener != null) {
            bc.listener.onFinish(bc, elementContext);
        }
        if (bc.isArrayItem()) {
            Bundle b = bc.popBundle();
            bc.addArrayItem(b);
        }
        if (bc.isArray()) {
            bc.saveArray();
        }
    };

    @NonNull
    private static final Consumer<ElementContext> mTextHandler = elementContext -> {
        final BuilderContext c = (BuilderContext) elementContext.getUserArg();
        c.getData().putString(c.collectField, elementContext.getBody());
    };

    @NonNull
    private static final Consumer<ElementContext> mLongHandler = elementContext -> {
        final BuilderContext bc = (BuilderContext) elementContext.getUserArg();
        final String name = bc.collectField;
        try {
            final long l = Long.parseLong(elementContext.getBody());
            bc.getData().putLong(name, l);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
    };
    @NonNull
    private static final Consumer<ElementContext> mBooleanHandler = elementContext -> {
        final BuilderContext bc = (BuilderContext) elementContext.getUserArg();
        final String name = bc.collectField;
        try {
            final boolean b = ParseUtils.parseBoolean(elementContext.getBody(), true);
            bc.getData().putBoolean(name, b);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
    };
    /** See constructor. */
    @NonNull
    private final Consumer<ElementContext> mDoubleHandler;
    @Nullable
    private final Locale mLocale;
    @NonNull
    private final XmlFilter mRootFilter;
    private final ArrayList<BuilderContext> mContexts = new ArrayList<>();
    private final ArrayList<String> mTags = new ArrayList<>();
    private final DataStoreProvider mRootData = new DataStore();

    /**
     * Constructor.
     *
     * @param root   filter
     * @param locale <strong>Must</strong> be the Locale for the source data.
     *               (and NOT simply the system/user).
     */
    SimpleXmlFilter(@NonNull final XmlFilter root,
                    @Nullable final Locale locale) {
        mRootFilter = root;
        mLocale = locale;

        // defined here due to compiler wanting mLocale to be defined.
        mDoubleHandler = elementContext -> {
            final BuilderContext bc = (BuilderContext) elementContext.getUserArg();
            final String name = bc.collectField;
            try {
                final double d = ParseUtils.parseDouble(elementContext.getBody(), mLocale);
                bc.getData().putDouble(name, d);
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        };

    }

    @NonNull
    SimpleXmlFilter asArray(@NonNull final String arrayName) {
        mContexts.get(mContexts.size() - 1).setArray(arrayName);
        return this;
    }

    @NonNull
    SimpleXmlFilter asArrayItem() {
        mContexts.get(mContexts.size() - 1).setArrayItem();
        return this;
    }

    /**
     * Start tag.
     *
     * @param tag that starts
     *
     * @return SimpleXmlFilter (for chaining)
     */
    @NonNull
    public SimpleXmlFilter s(@NonNull final String tag) {
        DataStoreProvider parent;

        mTags.add(tag);
        int size = mContexts.size();

        if (size == 0) {
            parent = mRootData;
        } else {
            parent = mContexts.get(size - 1);
        }

        mContexts.add(new BuilderContext(mRootFilter, parent, mTags));

        return this;
    }

    /**
     * Closing tag. Call this when done.
     */
    public void done() {
        mTags.clear();
        mContexts.clear();
    }

    @NonNull
    public Bundle getData() {
        return mRootData.getData();
    }

    @NonNull
    private List<AttrFilter> getAttrFilters() {
        BuilderContext c = mContexts.get(mContexts.size() - 1);
        if (c.attrs == null) {
            c.attrs = new ArrayList<>();
        }
        return c.attrs;
    }

    @NonNull
    public SimpleXmlFilter setListener(@NonNull final XmlListener listener) {
        BuilderContext c = mContexts.get(mContexts.size() - 1);
        c.listener = listener;
        return this;
    }

    @NonNull
    public SimpleXmlFilter pop() {
        mContexts.remove(mContexts.size() - 1);
        mTags.remove(mTags.size() - 1);
        return this;
    }

    @NonNull
    SimpleXmlFilter popTo(@SuppressWarnings("SameParameterValue")
                          @NonNull final String tag) {
        int last = mTags.size() - 1;
        while (!mTags.get(last).equalsIgnoreCase(tag)) {
            if (last == 0) {
                throw new RuntimeException("Unable to find parent tag :" + tag);
            }
            mContexts.remove(last);
            mTags.remove(last);
            last--;
        }
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    SimpleXmlFilter booleanAttr(@NonNull final String key,
                                @NonNull final String attrName) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new BooleanAttrFilter(key, attrName));
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    SimpleXmlFilter doubleAttr(@NonNull final String attrName,
                               @NonNull final String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new DoubleAttrFilter(key, attrName, mLocale));
        return this;
    }

    @NonNull
    SimpleXmlFilter longAttr(@NonNull final String attrName,
                             @NonNull final String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new LongAttrFilter(key, attrName));
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    @NonNull
    SimpleXmlFilter stringAttr(@NonNull final String attrName,
                               @NonNull final String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new StringAttrFilter(key, attrName));
        return this;
    }

    private void setCollector(@NonNull final String tag,
                              @NonNull final Consumer<ElementContext> handler,
                              @NonNull final String fieldName) {
        s(tag);
        setCollector(handler, fieldName);
        pop();
    }

    private void setCollector(@NonNull final Consumer<ElementContext> handler,
                              @NonNull final String fieldName) {
        BuilderContext c = mContexts.get(mContexts.size() - 1);
        c.collectField = fieldName;
        c.finishHandler = handler;
    }

    @SuppressWarnings("unused")
    @NonNull
    SimpleXmlFilter booleanBody(@NonNull final String fieldName) {
        setCollector(mBooleanHandler, fieldName);
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    @NonNull
    SimpleXmlFilter booleanBody(@NonNull final String tag,
                                @NonNull final String fieldName) {
        setCollector(tag, mBooleanHandler, fieldName);
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    SimpleXmlFilter doubleBody(@NonNull final String fieldName) {
        setCollector(mDoubleHandler, fieldName);
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    @NonNull
    SimpleXmlFilter doubleBody(@NonNull final String tag,
                               @NonNull final String fieldName) {
        setCollector(tag, mDoubleHandler, fieldName);
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    public SimpleXmlFilter longBody(@NonNull final String fieldName) {
        setCollector(mLongHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter longBody(@NonNull final String tag,
                             @NonNull final String fieldName) {
        setCollector(tag, mLongHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter stringBody(@NonNull final String fieldName) {
        setCollector(mTextHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter stringBody(@NonNull final String tag,
                               @NonNull final String fieldName) {
        setCollector(tag, mTextHandler, fieldName);
        return this;
    }

    public interface XmlListener {

        default void onStart(@NonNull final SimpleXmlFilter.BuilderContext bc,
                             @NonNull final ElementContext c) {

        }

        void onFinish(@NonNull SimpleXmlFilter.BuilderContext bc,
                      @NonNull ElementContext c);
    }

    interface DataStoreProvider {

        void addArrayItem(@NonNull Bundle bundle);

        @NonNull
        Bundle getData();
    }

    public static class BuilderContext
            implements DataStoreProvider {

        @NonNull
        private final DataStoreProvider parent;
        String collectField;
        @Nullable
        List<AttrFilter> attrs;
        @Nullable
        XmlListener listener;
        @Nullable
        Consumer<ElementContext> finishHandler;

        @Nullable
        private Bundle mLocalBundle;
        @Nullable
        private ArrayList<Bundle> mArrayItems;

        private boolean mIsArray;
        @Nullable
        private String mArrayName;

        private boolean mIsArrayItem;

        /**
         * Constructor.
         *
         * @param root the root filter
         */
        BuilderContext(@NonNull final XmlFilter root,
                       @NonNull final DataStoreProvider parent,
                       @NonNull final Collection<String> tags) {
            this.parent = parent;
            XmlFilter.buildFilter(root, tags)
                     .setStartAction(mHandleStart, this)
                     .setEndAction(mHandleFinish, this);
        }

        public void addArrayItem(@NonNull final Bundle bundle) {
            if (mArrayItems != null) {
                mArrayItems.add(bundle);
            } else {
                parent.addArrayItem(bundle);
            }
        }

        @NonNull
        public Bundle getData() {
            if (mLocalBundle != null) {
                return mLocalBundle;
            } else {
                return parent.getData();
            }
        }

        void initArray() {
            mArrayItems = new ArrayList<>();
        }

        void saveArray() {
            getData().putParcelableArrayList(mArrayName, mArrayItems);
            mArrayItems = null;
        }

        void pushBundle() {
            if (mLocalBundle != null) {
                throw new IllegalStateException(ErrorMsg.BUNDLE_ALREADY_PUSHED);
            }
            mLocalBundle = new Bundle();
        }

        @NonNull
        Bundle popBundle() {
            Objects.requireNonNull(mLocalBundle, ErrorMsg.BUNDLE_NOT_PUSHED);
            Bundle b = mLocalBundle;
            mLocalBundle = null;
            return b;
        }

        boolean isArray() {
            return mIsArray;
        }

        void setArray(@NonNull final String name) {
            mIsArray = true;
            mArrayName = name;
        }

        boolean isArrayItem() {
            return mIsArrayItem;
        }

        void setArrayItem() {
            mIsArrayItem = true;
        }
    }

    public static class DataStore
            implements DataStoreProvider {

        @NonNull
        private final Bundle mData;

        DataStore() {
            mData = new Bundle();
        }

        @Override
        public void addArrayItem(@NonNull final Bundle bundle) {
            throw new IllegalStateException("Attempt to store array at root");
        }

        @Override
        @NonNull
        public Bundle getData() {
            return mData;
        }

    }

    private abstract static class AttrFilter {

        @NonNull
        final String name;
        @NonNull
        final String key;

        AttrFilter(@NonNull final String key,
                   @NonNull final String name) {
            this.name = name;
            this.key = key;
        }

        protected abstract void put(@NonNull BuilderContext context,
                                    @NonNull String value);
    }

    private static class StringAttrFilter
            extends AttrFilter {

        StringAttrFilter(@NonNull final String key,
                         @NonNull final String name) {
            super(key, name);
        }

        public void put(@NonNull final BuilderContext context,
                        @NonNull final String value) {
            context.getData().putString(key, value);
        }
    }

    private static class LongAttrFilter
            extends AttrFilter {

        LongAttrFilter(@NonNull final String key,
                       @NonNull final String name) {
            super(key, name);
        }

        public void put(@NonNull final BuilderContext context,
                        @NonNull final String value) {
            context.getData().putLong(key, Long.parseLong(value));
        }
    }

    private static class DoubleAttrFilter
            extends AttrFilter {

        @Nullable
        private final Locale mLocale;

        /**
         * @param locale <strong>Must</strong> be the Locale for the source data.
         */
        DoubleAttrFilter(@NonNull final String key,
                         @NonNull final String name,
                         @Nullable final Locale locale) {
            super(key, name);
            mLocale = locale;
        }

        public void put(@NonNull final BuilderContext context,
                        @NonNull final String value) {
            context.getData().putDouble(key, ParseUtils.parseDouble(value, mLocale));
        }
    }

    private static class BooleanAttrFilter
            extends AttrFilter {

        BooleanAttrFilter(@NonNull final String key,
                          @NonNull final String name) {
            super(key, name);
        }

        public void put(@NonNull final BuilderContext context,
                        @NonNull final String value) {
            boolean b = ParseUtils.parseBoolean(value.trim(), true);
            context.getData().putBoolean(key, b);
        }
    }
}
