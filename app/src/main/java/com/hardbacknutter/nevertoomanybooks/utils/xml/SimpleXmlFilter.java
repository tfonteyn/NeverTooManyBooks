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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter.XmlHandler;

/**
 * Class layered on top of {@link XmlFilter} to implement a simple set of XML filters to extract
 * data from an XML file and return the results in a collection of nested Bundle objects.
 */
public class SimpleXmlFilter {

    @NonNull
    private static final XmlHandler mHandleStart = context -> {
        BuilderContext bc = (BuilderContext) context.getUserArg();
        if (bc.isArray()) {
            bc.initArray();
        }
        if (bc.isArrayItem()) {
            bc.pushBundle();
        }
        if (bc.listener != null) {
            bc.listener.onStart(bc, context);
        }
        List<AttrFilter> attrs = bc.attrs;
        if (attrs != null) {
            for (AttrFilter f : attrs) {
                final String name = f.name;
                final String value = context.getAttributes().getValue(name);
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
    private static final XmlHandler mHandleFinish = context -> {
        final BuilderContext bc = (BuilderContext) context.getUserArg();
        if (bc.finishHandler != null) {
            bc.finishHandler.process(context);
        }
        if (bc.listener != null) {
            bc.listener.onFinish(bc, context);
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
    private static final XmlHandler mTextHandler = context -> {
        final BuilderContext c = (BuilderContext) context.getUserArg();
        c.getData().putString(c.collectField, context.getBody());
    };

    @NonNull
    private static final XmlHandler mLongHandler = context -> {
        final BuilderContext bc = (BuilderContext) context.getUserArg();
        final String name = bc.collectField;
        try {
            long l = Long.parseLong(context.getBody());
            bc.getData().putLong(name, l);
        } catch (@NonNull final NumberFormatException ignore) {
        }
    };
    @NonNull
    private static final XmlHandler mBooleanHandler = context -> {
        final BuilderContext bc = (BuilderContext) context.getUserArg();
        final String name = bc.collectField;
        try {
            boolean b = textToBoolean(context.getBody());
            bc.getData().putBoolean(name, b);
        } catch (@NonNull final NumberFormatException ignore) {
            // Ignore but don't add
        }
    };
    /** See constructor. */
    @NonNull
    private final XmlHandler mDoubleHandler;
    @NonNull
    private final XmlFilter mRootFilter;
    private final ArrayList<BuilderContext> mContexts = new ArrayList<>();
    private final ArrayList<String> mTags = new ArrayList<>();
    private final DataStore mRootData = new DataStore();

    /** Whether Float and Double parsing routines use Locales or not. */
    private final boolean mFloatUsesLocales;

    /**
     * Constructor.
     * <p>
     * Locale independent.
     *
     * @param root filter
     */
    public SimpleXmlFilter(@NonNull final XmlFilter root) {
        this(root, false);
    }

    /**
     * Constructor.
     *
     * <strong>Note:</strong> 2019-08-30: currently only used by Goodreads code which
     * is Locale.US only; i.e. floatUsesLocales is always {@code false}.
     *
     * @param root             filter
     * @param floatUsesLocales take Locales into account when parsing strings or not.
     */
    public SimpleXmlFilter(@NonNull final XmlFilter root,
                           final boolean floatUsesLocales) {
        mRootFilter = root;
        mFloatUsesLocales = floatUsesLocales;

        mDoubleHandler = context -> {
            final BuilderContext bc = (BuilderContext) context.getUserArg();
            final String name = bc.collectField;
            try {
                double d;
                if (mFloatUsesLocales) {
                    d = ParseUtils.parseDouble(context.getBody());
                } else {
                    d = Double.parseDouble(context.getBody());
                }
                bc.getData().putDouble(name, d);

            } catch (@NonNull final NumberFormatException ignore) {
            }
        };
    }

    private static boolean textToBoolean(@Nullable final String s)
            throws NumberFormatException {
        if (s == null || s.isEmpty()) {
            return false;
        }

        switch (s.trim().toLowerCase(App.getSystemLocale())) {
            case "true":
            case "t":
                return true;
            case "false":
            case "f":
                return false;
            default:
                return Long.parseLong(s) != 0;
        }
    }

    @NonNull
    public SimpleXmlFilter asArray(@NonNull final String arrayName) {
        mContexts.get(mContexts.size() - 1).setArray(arrayName);
        return this;
    }

    @NonNull
    public SimpleXmlFilter asArrayItem() {
        mContexts.get(mContexts.size() - 1).setArrayItem();
        return this;
    }

    /**
     * Start tag.
     *
     * @param tag that starts
     *
     * @return this for chaining.
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
    public SimpleXmlFilter popTo(@SuppressWarnings("SameParameterValue")
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
        attrs.add(new DoubleAttrFilter(key, attrName, mFloatUsesLocales));
        return this;
    }

    @NonNull
    public SimpleXmlFilter longAttr(@NonNull final String attrName,
                                    @NonNull final String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new LongAttrFilter(key, attrName));
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    @NonNull
    public SimpleXmlFilter stringAttr(@NonNull final String attrName,
                                      @NonNull final String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new StringAttrFilter(key, attrName));
        return this;
    }

    private void setCollector(@NonNull final String tag,
                              @NonNull final XmlHandler handler,
                              @NonNull final String fieldName) {
        s(tag);
        setCollector(handler, fieldName);
        pop();
    }

    private void setCollector(@NonNull final XmlHandler handler,
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
    public SimpleXmlFilter booleanBody(@NonNull final String tag,
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
    public SimpleXmlFilter doubleBody(@NonNull final String tag,
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
    public SimpleXmlFilter longBody(@NonNull final String tag,
                                    @NonNull final String fieldName) {
        setCollector(tag, mLongHandler, fieldName);
        return this;
    }

    @NonNull
    public SimpleXmlFilter stringBody(@NonNull final String fieldName) {
        setCollector(mTextHandler, fieldName);
        return this;
    }

    @NonNull
    public SimpleXmlFilter stringBody(@NonNull final String tag,
                                      @NonNull final String fieldName) {
        setCollector(tag, mTextHandler, fieldName);
        return this;
    }

    public interface XmlListener {

        @SuppressWarnings("EmptyMethod")
        void onStart(@NonNull SimpleXmlFilter.BuilderContext bc,
                     @NonNull ElementContext c);

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
        XmlHandler finishHandler;

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
                       @NonNull final List<String> tags) {
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
                throw new IllegalStateException("Bundle already pushed!");
            }
            mLocalBundle = new Bundle();
        }

        @NonNull
        Bundle popBundle() {
            Objects.requireNonNull(mLocalBundle, "Bundle not pushed!");
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

        /** Whether Float and Double parsing routines use Locales or not. */
        private final boolean mFloatUsesLocales;

        DoubleAttrFilter(@NonNull final String key,
                         @NonNull final String name,
                         final boolean floatUsesLocales) {
            super(key, name);
            mFloatUsesLocales = floatUsesLocales;
        }

        public void put(@NonNull final BuilderContext context,
                        @NonNull final String value) {
            if (mFloatUsesLocales) {
                context.getData().putDouble(key, ParseUtils.parseDouble(value));
            } else {
                context.getData().putDouble(key, Double.parseDouble(value));

            }
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
            boolean b = textToBoolean(value.trim());
            context.getData().putBoolean(key, b);
        }
    }
}
