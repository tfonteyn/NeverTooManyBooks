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

/**
 * Class layered on top of XmlFilter to implement a simple set of XML filters to extract
 * data from an XML file and return the results in a collection of nested Bundle objects.
 *
 * @author Philip Warner
 */
public class SimpleXmlFilter {
    @Nullable
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
    @Nullable
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
    @Nullable
    private static final XmlHandler mTextHandler = new XmlHandler() {
        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext c = (BuilderContext) context.userArg;
            c.getData().putString(c.collectField, context.body.trim());
        }
    };
    @Nullable
    private static final XmlHandler mLongHandler = new XmlHandler() {

        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext c = (BuilderContext) context.userArg;
            final String name = c.collectField;
            try {
                long l = Long.parseLong(context.body.trim());
                c.getData().putLong(name, l);
            } catch (Exception ignore) {
                // Ignore but don't add
            }
        }
    };
    @Nullable
    private static final XmlHandler mDoubleHandler = new XmlHandler() {

        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext c = (BuilderContext) context.userArg;
            final String name = c.collectField;
            try {
                double d = Double.parseDouble(context.body.trim());
                c.getData().putDouble(name, d);
            } catch (Exception ignore) {
                // Ignore but don't add
            }
        }
    };
    @Nullable
    private static final XmlHandler mBooleanHandler = new XmlHandler() {

        @Override
        public void process(@NonNull ElementContext context) {
            final BuilderContext c = (BuilderContext) context.userArg;
            final String name = c.collectField;
            try {
                boolean b = textToBoolean(context.body.trim());
                c.getData().putBoolean(name, b);
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

    SimpleXmlFilter(@NonNull final XmlFilter root) {
        mRootFilter = root;
    }

    private static boolean textToBoolean(@Nullable final String s) {
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
    SimpleXmlFilter isArray(@NonNull final String arrayName) {
        mContexts.get(mContexts.size() - 1).setArray(arrayName);
        return this;
    }

    @NonNull
    SimpleXmlFilter isArrayItem() {
        mContexts.get(mContexts.size() - 1).setArrayItem();
        return this;
    }

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
    SimpleXmlFilter popTo(@NonNull final String tag) {
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

    @NonNull
    SimpleXmlFilter booleanAttr(@NonNull final String key, @NonNull final String attrName) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new BooleanAttrFilter(key, attrName));
        return this;
    }

    @NonNull
    SimpleXmlFilter doubleAttr(@NonNull final String attrName, @NonNull final String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new DoubleAttrFilter(key, attrName));
        return this;
    }

    @NonNull
    SimpleXmlFilter longAttr(@NonNull final String attrName, @NonNull final String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new LongAttrFilter(key, attrName));
        return this;
    }

    @NonNull
    SimpleXmlFilter stringAttr(@NonNull final String attrName, @NonNull final String key) {
        List<AttrFilter> attrs = getAttrFilters();
        attrs.add(new StringAttrFilter(key, attrName));
        return this;
    }

    private void setCollector(@NonNull final String tag, @NonNull XmlHandler handler, @NonNull final String fieldName) {
        s(tag);
        setCollector(handler, fieldName);
        pop();
    }

    private void setCollector(@NonNull final XmlHandler handler, @NonNull final String fieldName) {
        BuilderContext c = mContexts.get(mContexts.size() - 1);
        c.collectField = fieldName;
        c.finishHandler = handler;
    }

    @NonNull
    SimpleXmlFilter booleanBody(@NonNull final String fieldName) {
        setCollector(mBooleanHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter booleanBody(@NonNull final String tag, @NonNull final String fieldName) {
        setCollector(tag, mBooleanHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter doubleBody(@NonNull final String fieldName) {
        setCollector(mDoubleHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter doubleBody(@NonNull final String tag, @NonNull final String fieldName) {
        setCollector(tag, mDoubleHandler, fieldName);
        return this;
    }

    @NonNull
    public SimpleXmlFilter longBody(@NonNull final String fieldName) {
        setCollector(mLongHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter longBody(@NonNull final String tag, @NonNull final String fieldName) {
        setCollector(tag, mLongHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter stringBody(@NonNull final String fieldName) {
        setCollector(mTextHandler, fieldName);
        return this;
    }

    @NonNull
    SimpleXmlFilter stringBody(@NonNull final String tag, @NonNull final String fieldName) {
        setCollector(tag, mTextHandler, fieldName);
        return this;
    }

    public interface XmlListener {
        void onStart(@NonNull final SimpleXmlFilter.BuilderContext bc, @NonNull final ElementContext c);

        void onFinish(@NonNull final SimpleXmlFilter.BuilderContext bc, @NonNull final ElementContext c);
    }

    public interface DataStoreProvider {
        void addArrayItem(@NonNull final Bundle b);

        @NonNull
        Bundle getData();
    }

    public static class BuilderContext implements DataStoreProvider {
        @NonNull
        public final DataStoreProvider parent;

        String collectField;
        @Nullable
        List<AttrFilter> attrs = null;
        @Nullable
        XmlListener listener = null;
        @Nullable
        XmlHandler finishHandler = null;
        @Nullable
        private final XmlFilter mFilter;
        @Nullable
        private Bundle mLocalBundle = null;
        @Nullable
        private ArrayList<Bundle> mArrayItems = null;

        private boolean mIsArray = false;
        @Nullable
        private String mArrayName = null;

        private boolean mIsArrayItem = false;

        BuilderContext(@NonNull final XmlFilter root,
                       @NonNull final DataStoreProvider parent,
                       @NonNull final List<String> tags) {
            this.parent = parent;
            mFilter = XmlFilter.buildFilter(root, tags);
            if (mFilter != null) {
                mFilter.setStartAction(mHandleStart, this);
                mFilter.setEndAction(mHandleFinish, this);
            }
        }

        public void addArrayItem(@NonNull final Bundle bundle) {
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
            if (mLocalBundle == null) {
                throw new IllegalStateException("Bundle not pushed!");
            }
            Bundle b = mLocalBundle;
            mLocalBundle = null;
            return b;
        }

        public boolean isArray() {
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

    public class DataStore implements DataStoreProvider {
        @NonNull
        private final Bundle mData;

        DataStore() {
            mData = new Bundle();
        }

        @Override
        public void addArrayItem(@NonNull final Bundle b) {
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

        AttrFilter(@NonNull final String key, @NonNull final String name) {
            this.name = name;
            this.key = key;
        }

        protected abstract void put(@NonNull final BuilderContext context, @NonNull final String value);
    }

    private class StringAttrFilter extends AttrFilter {
        StringAttrFilter(@NonNull final String key, @NonNull final String name) {
            super(key, name);
        }

        public void put(@NonNull final BuilderContext context, @NonNull final String value) {
            context.getData().putString(this.key, value);
        }
    }

    private class LongAttrFilter extends AttrFilter {
        LongAttrFilter(@NonNull final String key, @NonNull final String name) {
            super(key, name);
        }

        public void put(@NonNull final BuilderContext context, @NonNull final String value) {
            context.getData().putLong(this.key, Long.parseLong(value));
        }
    }

    private class DoubleAttrFilter extends AttrFilter {
        DoubleAttrFilter(@NonNull final String key, @NonNull final String name) {
            super(key, name);
        }

        public void put(@NonNull final BuilderContext context, @NonNull final String value) {
            context.getData().putDouble(this.key, Double.parseDouble(value));
        }
    }

    private class BooleanAttrFilter extends AttrFilter {
        BooleanAttrFilter(@NonNull final String key, @NonNull final String name) {
            super(key, name);
        }

        public void put(@NonNull final BuilderContext context, @NonNull final String value) {
            boolean b = textToBoolean(value.trim());
            context.getData().putBoolean(this.key, b);
        }
    }
}
