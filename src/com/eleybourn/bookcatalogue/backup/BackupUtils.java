/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.backup;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlResponseParser;
import com.eleybourn.bookcatalogue.utils.RTE;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Utility functions for backup/restore code
 *
 * @author pjw
 */
public class BackupUtils {

    private static final String COLLECTION = "collection";
    private static final String ITEM = "item";
    private static final String NAME = "name";

    private static final String TYPE = "type";
    private static final String TYPE_INTEGER = "Int";
    private static final String TYPE_LONG = "Long";
    private static final String TYPE_DOUBLE = "Dbl";
    private static final String TYPE_FLOAT = "Flt";
    private static final String TYPE_BOOLEAN = "Bool";
    private static final String TYPE_STRING = "Str";
    private static final String TYPE_SERIALIZABLE = "Serial";

    /**
     * Write preferences to an XML stream.
     */
    public static void preferencesToXml(@NonNull final BufferedWriter out,
                                        @NonNull final SharedPreferences prefs) throws IOException {
        final PreferencesAccessor a = new PreferencesAccessor(prefs);
        collectionToXml(out, a);
    }

    /**
     * Read preferences from an XML stream.
     */
    public static void preferencesFromXml(@NonNull final BufferedReader in,
                                          @NonNull final SharedPreferences prefs) throws IOException {
        final PreferencesAccessor a = new PreferencesAccessor(prefs);
        a.beginEdit();
        collectionFromXml(in, a);
        a.endEdit();
    }

    /**
     * Write Bundle to an XML stream.
     */
    public static void bundleToXml(@NonNull final BufferedWriter out,
                                   @NonNull final Bundle bundle) throws IOException {
        final BundleAccessor a = new BundleAccessor(bundle);
        collectionToXml(out, a);
    }

    /**
     * Read Bundle from an XML stream.
     */
    @NonNull
    public static Bundle bundleFromXml(@NonNull final BufferedReader in) throws IOException {
        final Bundle bundle = new Bundle();
        final BundleAccessor a = new BundleAccessor(bundle);
        collectionFromXml(in, a);
        return bundle;
    }

    /**
     * Internal routine to send the passed CollectionAccessor data to an XML file.
     */
    private static void collectionToXml(@NonNull final BufferedWriter out,
                                        @NonNull final CollectionAccessor<String> col) throws IOException {
        out.append("<" + COLLECTION + ">\n");
        for (String key : col.keySet()) {
            final String type;
            final String value;
            Object object = col.get(key);
            if (object instanceof Integer) {
                type = BackupUtils.TYPE_INTEGER;
                value = object.toString();
            } else if (object instanceof Long) {
                type = BackupUtils.TYPE_LONG;
                value = object.toString();
            } else if (object instanceof Float) {
                type = BackupUtils.TYPE_FLOAT;
                value = object.toString();
            } else if (object instanceof Double) {
                type = BackupUtils.TYPE_DOUBLE;
                value = object.toString();
            } else if (object instanceof String) {
                type = BackupUtils.TYPE_STRING;
                value = object.toString();
            } else if (object instanceof Boolean) {
                type = BackupUtils.TYPE_BOOLEAN;
                value = object.toString();
            } else if (object instanceof Serializable) {
                type = BackupUtils.TYPE_SERIALIZABLE;
                value = Base64.encodeObject((Serializable) object);
            } else {
                if (object == null) {
                    throw new NullPointerException();
                }
                throw new RTE.IllegalTypeException(object.getClass().getCanonicalName());
            }
            out.append("<item name=\"").append(key).append("\" type=\"").append(type).append("\">")
                    .append(value)
                    .append("</item>\n");
        }
        out.append("</" + COLLECTION + ">\n");
    }

    /**
     * Internal routine to update the passed CollectionAccessor from an XML file.
     */
    private static void collectionFromXml(@NonNull final BufferedReader in, @NonNull final CollectionAccessor<String> accessor) throws IOException {
        final Bundle bundle = new Bundle();
        final XmlFilter rootFilter = new XmlFilter("");
        final ItemInfo info = new ItemInfo();

        XmlFilter filter = XmlFilter.buildFilter(rootFilter, COLLECTION, ITEM);
        Objects.requireNonNull(filter);

        filter.setStartAction(new XmlHandler() {
            @Override
            public void process(@NonNull ElementContext context) {
                info.name = context.attributes.getValue(NAME);
                info.type = context.attributes.getValue(TYPE);
            }
        }, null);

        filter.setEndAction(new XmlHandler() {
            @Override
            public void process(@NonNull ElementContext context) {
                try {
                    accessor.putItem(bundle, info.name, info.type, context.body);
                } catch (IOException e) {
                    Logger.error(e);
                    throw new RuntimeException("Unable to process XML entity " + info.name + " (" + info.type + ")", e);
                }
            }
        }, null);

        final XmlResponseParser handler = new XmlResponseParser(rootFilter);
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final SAXParser parser;
        try {
            parser = factory.newSAXParser();
        } catch (@NonNull SAXException | ParserConfigurationException e) {
            Logger.error(e);
            throw new IOException("Unable to create XML parser", e);
        }

        final InputSource is = new InputSource();
        is.setCharacterStream(in);
        try {
            parser.parse(is, handler);
        } catch (SAXException e) {
            Logger.error(e);
            throw new IOException("Malformed XML");
        }

    }

    /**
     * Class to provide access to a subset of the methods of collections.
     *
     * @param <T> Type of the collection key
     *
     * @author pjw
     */
    private interface CollectionAccessor<T> {
        /** Get the collection of keys */
        Set<T> keySet();

        /** Get the object for the specified key */
        @Nullable
        Object get(@NonNull final T key);

        /** Process the passed item to store in the collection */
        void putItem(@NonNull final Bundle bundle, @NonNull final String key,
                     @NonNull final String type, @NonNull final String value) throws IOException;
    }

    /**
     * Collection accessor for bundles
     *
     * @author pjw
     */
    private static class BundleAccessor implements CollectionAccessor<String> {
        @NonNull
        private final Bundle mBundle;

        BundleAccessor(@NonNull final Bundle b) {
            mBundle = b;
        }

        @Override
        public Set<String> keySet() {
            return mBundle.keySet();
        }

        @Override
        @Nullable
        public Object get(@NonNull final String key) {
            return mBundle.get(key);
        }

        @Override
        public void putItem(@NonNull final Bundle bundle,
                            @NonNull final String key,
                            @NonNull final String type,
                            @NonNull final String value) throws IOException {
            switch (type) {
                case TYPE_INTEGER:
                    mBundle.putInt(key, Integer.parseInt(value));
                    break;
                case TYPE_LONG:
                    mBundle.putLong(key, Long.parseLong(value));
                    break;
                case TYPE_FLOAT:
                    mBundle.putFloat(key, Float.parseFloat(value));
                    break;
                case TYPE_DOUBLE:
                    mBundle.putDouble(key, Double.parseDouble(value));
                    break;
                case TYPE_STRING:
                    mBundle.putString(key, value);
                    break;
                case TYPE_BOOLEAN:
                    mBundle.putBoolean(key, Boolean.parseBoolean(value));
                    break;
                case TYPE_SERIALIZABLE:
                    //ENHANCE API 26
                    // byte[] blob = java.util.Base64.getDecoder().decode(value);

                    byte[] blob = Base64.decode(value);
                    mBundle.putSerializable(key, blob);
                    break;
            }
        }
    }

    /**
     * Collection accessor for SharedPreferences
     *
     * @author pjw
     */
    private static class PreferencesAccessor implements CollectionAccessor<String> {
        @NonNull
        final SharedPreferences mPrefs;
        final Map<String, ?> mMap;
        Editor mEditor;

        PreferencesAccessor(@NonNull final SharedPreferences prefs) {
            mPrefs = prefs;
            mMap = prefs.getAll();
        }

        void beginEdit() {
            mEditor = mPrefs.edit();
            mEditor.clear();
        }

        void endEdit() {
            mEditor.apply();
            mEditor = null;
        }

        @NonNull
        @Override
        public Set<String> keySet() {
            return mMap.keySet();
        }

        @Override
        @Nullable
        public Object get(@NonNull final String key) {
            return mMap.get(key);
        }

        @Override
        public void putItem(@NonNull final Bundle bundle, @NonNull final String key,
                            @NonNull final String type, @NonNull final String value) {
            switch (type) {
                case TYPE_INTEGER:
                    mEditor.putInt(key, Integer.parseInt(value));
                    break;
                case TYPE_LONG:
                    mEditor.putLong(key, Long.parseLong(value));
                    break;
                case TYPE_FLOAT:
                    mEditor.putFloat(key, Float.parseFloat(value));
                    break;
                case TYPE_STRING:
                    mEditor.putString(key, value);
                    break;
                case TYPE_BOOLEAN:
                    mEditor.putBoolean(key, Boolean.parseBoolean(value));
                    break;
                default:
                    throw new RTE.IllegalTypeException(type);
            }
        }
    }

    /**
     * Record to preserve data while parsing XML input
     *
     * @author pjw
     */
    private static class ItemInfo {
        public String type;
        String name;
    }
}
