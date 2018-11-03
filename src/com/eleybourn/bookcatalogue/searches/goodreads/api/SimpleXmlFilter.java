/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches.goodreads.api;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.XmlHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class layered on top of XmlFilter to implement a simple set of XML filters to extract
 * data from an XML file and return the results in a collection of nested Bundle objects.
 *
 * @author Philip Warner
 */
public class SimpleXmlFilter {
    @NonNull
    private static final XmlHandler mHandleStart = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            BuilderContext bc = (BuilderContext) context.userArg;

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
                    final String value = context.attributes.getValue(name);
                    if (value != null) {
                        try {
                            f.put(bc, value);
                        } catch (Exception e) {
                            // Could not be parsed....just ignore
                        }
                    }
                }
            }

        }
    };
    @NonNull
    private static final XmlHandler mHandleFinish = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext bc = (BuilderContext) context.userArg;
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

        }
    };
    @NonNull
    private static final XmlHandler mTextHandler = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext c = (BuilderContext) context.userArg;
            c.getData().putString(c.collectField, context.body.trim());
        }
    };
    @NonNull
    private static final XmlHandler mLongHandler = new XmlHandler() {

        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext bc = (BuilderContext) context.userArg;
            final String name = bc.collectField;
            try {
                long l = Long.parseLong(context.body.trim());
                bc.getData().putLong(name, l);
            } catch (Exception ignore) {
                // Ignore but don't add
            }
        }
    };
    @NonNull
    private static final XmlHandler mDoubleHandler = new XmlHandler() {

        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext bc = (BuilderContext) context.userArg;
            final String name = bc.collectField;
            try {
                double d = Double.parseDouble(context.body.trim());
                bc.getData().putDouble(name, d);
            } catch (Exception ignore) {
                // Ignore but don't add
            }
        }
    };
    @NonNull
    private static final XmlHandler mBooleanHandler = new XmlHandler() {

        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext bc = (BuilderContext) context.userArg;
            final String name = bc.collectField;
            try {
                boolean b = textToBoolean(context.body.trim());
                bc.getData().putBoolean(name, b);
            } catch (Exception ignore) {
                // Ignore but don't add
            }
        }
    };
    @NonNull
    private final XmlFilter mRootFilter;
    private final ArrayList<BuilderContext> mContexts = new ArrayList<>();
    private final ArrayList<String> mTags = new ArrayList<>();
    private final DataStore mRootData = new DataStore();

    SimpleXmlFilter(final @NonNull XmlFilter root) {
        mRootFilter = root;
    }

    private static boolean textToBoolean(final @Nullable String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }

        switch (s.trim().toLowerCase()) {
            case "true":
            case "t":
                return true;
            case "false":
            case "f":
                return false;
            default:
                long l = Long.parseLong(s);
                return (l != 0);
        }
    }

    @NonNull
    SimpleXmlFilter isArray(final @NonNull String arrayName) {
        mContexts.get(mContexts.size() - 1).setArray(arrayName);
        return this;
    }

    @NonNull
    SimpleXmlFilter isArrayItem() {
        mContexts.get(mContexts.size() - 1).setArrayItem();
        return this;
    }

    @NonNull
    public SimpleXmlFilter s(final @NonNull String tag) {
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

    @NonNull
    @SuppressWarnings("UnusedReturnValue")
    public SimpleXmlFilter done() {
        mTags.clear();
        mContexts.clear();
        return this;
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
    public SimpleXmlFilter setListener(XmlListener listener) {
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
    SimpleXmlFilter popTo(@SuppressWarnings("SameParameterValue") final @NonNull String tag) {
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
    SimpleXmlFilter booleanAttr(final @NonNull String key, final @NonNull String attrName) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new BooleanAttrFilter(key, attrName));
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    SimpleXmlFilter doubleAttr(final @NonNull String attrName, final @NonNull String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new DoubleAttrFilter(key, attrName));
        return this;
    }

    @NonNull
    SimpleXmlFilter longAttr(final @NonNull String attrName, final @NonNull String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new LongAttrFilter(key, attrName));
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    @NonNull
    SimpleXmlFilter stringAttr(final @NonNull String attrName, final @NonNull String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new StringAttrFilter(key, attrName));
        return this;
    }

    private void setCollector(final @NonNull String tag, @NonNull XmlHandler handler, final @NonNull String fieldName) {
        s(tag);
        setCollector(handler, fieldName);
        pop();
    }

    private void setCollector(final @NonNull XmlHandler handler, final @NonNull String fieldName) {
        BuilderContext c = mContexts.get(mContexts.size() - 1);
        c.collectField = fieldName;
        c.finishHandler = handler;
    }

    @NonNull
    SimpleXmlFilter booleanBody(final @NonNull String fieldName) {
        setCollector(mBooleanHandler, fieldName);
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    @NonNull
    SimpleXmlFilter booleanBody(final @NonNull String tag, final @NonNull String fieldName) {
        setCollector(tag, mBooleanHandler, fieldName);
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    SimpleXmlFilter doubleBody(final @NonNull String fieldName) {
        setCollector(mDoubleHandler, fieldName);
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    @NonNull
    SimpleXmlFilter doubleBody(final @NonNull String tag, final @NonNull String fieldName) {
        setCollector(tag, mDoubleHandler, fieldName);
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    public SimpleXmlFilter longBody(final @NonNull String fieldName) {
        setCollector(mLongHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter longBody(final @NonNull String tag, final @NonNull String fieldName) {
        setCollector(tag, mLongHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter stringBody(final @NonNull String fieldName) {
        setCollector(mTextHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter stringBody(final @NonNull String tag, final @NonNull String fieldName) {
        setCollector(tag, mTextHandler, fieldName);
        return this;
    }

    public interface XmlListener {
        @SuppressWarnings("EmptyMethod")
        void onStart(final @NonNull SimpleXmlFilter.BuilderContext bc, final @NonNull ElementContext c);

        void onFinish(final @NonNull SimpleXmlFilter.BuilderContext bc, final @NonNull ElementContext c);
    }

    public interface DataStoreProvider {
        void addArrayItem(final @NonNull Bundle b);

        @NonNull
        Bundle getData();
    }

    public static class BuilderContext implements DataStoreProvider {
        @NonNull
        public final DataStoreProvider parent;
        @Nullable
        private final XmlFilter mFilter;
        String collectField;
        @Nullable
        List<AttrFilter> attrs = null;
        @Nullable
        XmlListener listener = null;
        @Nullable
        XmlHandler finishHandler = null;
        @Nullable
        private Bundle mLocalBundle = null;
        @Nullable
        private ArrayList<Bundle> mArrayItems = null;

        private boolean mIsArray = false;
        @Nullable
        private String mArrayName = null;

        private boolean mIsArrayItem = false;

        BuilderContext(final @NonNull XmlFilter root,
                       final @NonNull DataStoreProvider parent,
                       final @NonNull List<String> tags) {
            this.parent = parent;
            mFilter = XmlFilter.buildFilter(root, tags);
            mFilter.setStartAction(mHandleStart, this);
            mFilter.setEndAction(mHandleFinish, this);
        }

        public void addArrayItem(final @NonNull Bundle bundle) {
            if (mArrayItems != null) {
                mArrayItems.add(bundle);
            } else {
                parent.addArrayItem(bundle);
            }
        }

        void initArray() {
            mArrayItems = new ArrayList<>();
        }

        void saveArray() {
            getData().putParcelableArrayList(mArrayName, mArrayItems);
            mArrayItems = null;
        }

        @Nullable
        public XmlFilter getFilter() {
            return mFilter;
        }

        @NonNull
        public Bundle getData() {
            if (mLocalBundle != null) {
                return mLocalBundle;
            } else {
                return parent.getData();
            }
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

        public boolean isArray() {
            return mIsArray;
        }

        void setArray(final @NonNull String name) {
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

    public class DataStore implements DataStoreProvider {
        @NonNull
        private final Bundle mData;

        DataStore() {
            mData = new Bundle();
        }

        @Override
        public void addArrayItem(final @NonNull Bundle b) {
            throw new IllegalStateException("Attempt to store array at root");
        }

        @Override
        @NonNull
        public Bundle getData() {
            return mData;
        }

    }

    private abstract class AttrFilter {
        @NonNull
        public final String name;
        @NonNull
        public final String key;

        AttrFilter(final @NonNull String key, final @NonNull String name) {
            this.name = name;
            this.key = key;
        }

        protected abstract void put(final @NonNull BuilderContext context, final @NonNull String value);
    }

    private class StringAttrFilter extends AttrFilter {
        StringAttrFilter(final @NonNull String key, final @NonNull String name) {
            super(key, name);
        }

        public void put(final @NonNull BuilderContext context, final @NonNull String value) {
            context.getData().putString(this.key, value);
        }
    }

    private class LongAttrFilter extends AttrFilter {
        LongAttrFilter(final @NonNull String key, final @NonNull String name) {
            super(key, name);
        }

        public void put(final @NonNull BuilderContext context, final @NonNull String value) {
            context.getData().putLong(this.key, Long.parseLong(value));
        }
    }

    private class DoubleAttrFilter extends AttrFilter {
        DoubleAttrFilter(final @NonNull String key, final @NonNull String name) {
            super(key, name);
        }

        public void put(final @NonNull BuilderContext context, final @NonNull String value) {
            context.getData().putDouble(this.key, Double.parseDouble(value));
        }
    }

    private class BooleanAttrFilter extends AttrFilter {
        BooleanAttrFilter(final @NonNull String key, final @NonNull String name) {
            super(key, name);
        }

        public void put(final @NonNull BuilderContext context, final @NonNull String value) {
            boolean b = textToBoolean(value.trim());
            context.getData().putBoolean(this.key, b);
        }
    }
}
