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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PCollection;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PInt;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PString;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.ElementContext;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * For now, only INFO, Preferences and Styles are implemented.
 * <p>
 * TODO: unify the handling of simple elements and set/list elements.
 */
public class XmlImporter
        implements Importer {

    private static final String TAG = "XmlImporter";

    private static final String ERROR_UNABLE_TO_PROCESS_XML_ENTITY =
            "Unable to process XML entity ";

    private static final int BUFFER_SIZE = 32768;

    private static final Pattern QUOT_PATTERN = Pattern.compile("&quot;", Pattern.LITERAL);
    private static final Pattern APOS_PATTERN = Pattern.compile("&apos;", Pattern.LITERAL);
    private static final Pattern LT_PATTERN = Pattern.compile("&lt;", Pattern.LITERAL);
    private static final Pattern GT_PATTERN = Pattern.compile("&gt;", Pattern.LITERAL);
    private static final Pattern AMP_PATTERN = Pattern.compile("&amp;", Pattern.LITERAL);

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    @Nullable
    private final Locale mLocale;

    /**
     * Stack for popping tags on if we go into one.
     * This is of course overkill, just to handle the list/set set,
     * but it's clean and future proof
     */
    private final Deque<TagInfo> mTagStack = new ArrayDeque<>();
    /** a simple Holder for the current tag name and attributes. */
    private TagInfo mTag;

    /**
     * Constructor.
     * <p>
     *
     * @param locale (optional)
     */
    public XmlImporter(@Nullable final Locale locale) {
        mLocale = locale;
        mDb = new DAO();
    }

    /**
     * Read the info block from an XML stream.
     *
     * @param entity to read
     * @param info   object to populate
     *
     * @throws IOException on failure
     */
    public void doBackupInfoBlock(@NonNull final ReaderEntity entity,
                                  @NonNull final BackupInfo info)
            throws IOException {
        InputStreamReader reader = new InputStreamReader(entity.getInputStream(),
                                                         StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReaderNoClose(reader, BUFFER_SIZE);
        fromXml(in, new InfoReader(info), null);
    }

    /**
     * Read Styles from an XML stream.
     *
     * @param context          Current context
     * @param entity           to read
     * @param progressListener Progress and cancellation provider
     *
     * @throws IOException on failure
     */
    public int doStyles(@NonNull final Context context,
                        @NonNull final ReaderEntity entity,
                        @Nullable final ProgressListener progressListener)
            throws IOException {
        InputStreamReader reader = new InputStreamReader(entity.getInputStream(),
                                                         StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReaderNoClose(reader, BUFFER_SIZE);
        StylesReader stylesReader = new StylesReader(context, mDb);
        fromXml(in, stylesReader, progressListener);

        return stylesReader.getStylesRead();
    }

    /**
     * Read Preferences from an XML stream.
     * <p>
     * <strong>Note:</strong> the passed in SharedPreferences is dependent on the caller.
     *
     * @param entity           to read
     * @param prefs            object to populate
     * @param progressListener Progress and cancellation provider
     *
     * @throws IOException on failure
     */
    public void doPreferences(@NonNull final ReaderEntity entity,
                              @NonNull final SharedPreferences prefs,
                              @Nullable final ProgressListener progressListener)
            throws IOException {
        InputStreamReader reader = new InputStreamReader(entity.getInputStream(),
                                                         StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReaderNoClose(reader, BUFFER_SIZE);
        SharedPreferences.Editor editor = prefs.edit();
        fromXml(in, new PreferencesReader(editor), progressListener);
        editor.apply();
    }

    /**
     * Not supported for now.
     * <p>
     * <br>{@inheritDoc}
     */
    @NonNull
    @Override
    public Results doBooks(@NonNull final Context context,
                           @NonNull final InputStream importStream,
                           @Nullable final CoverFinder coverFinder,
                           @NonNull final ProgressListener progressListener) {
        throw new UnsupportedOperationException();
    }

    /**
     * counterpart of {@link XmlExporter} #encodeString}
     * <p>
     * Only String 'value' tags need decoding.
     * <p>
     * decode the bare essentials only. To decode all possible entities we could add the Apache
     * 'lang' library I suppose.... maybe some day.
     */
    private String decodeString(@Nullable final String data) {
        if (data == null || "null".equalsIgnoreCase(data) || data.trim().isEmpty()) {
            return "";
        }

        // must be last of the entities
        String result = data.trim();
        result = AMP_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement("&"));
        result = GT_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement(">"));
        result = LT_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement("<"));
        result = APOS_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement("'"));
        result = QUOT_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement("\""));
        return result;
    }

    /**
     * Internal routine to update the passed EntityAccessor from an XML file.
     *
     * @param progressListener (optional) Progress and cancellation provider
     *
     * @throws IOException on failure
     */
    private void fromXml(@NonNull final BufferedReader in,
                         @NonNull final EntityReader<String> accessor,
                         @Nullable final ProgressListener progressListener)
            throws IOException {

        // we need an uber-root to hang our tree on.
        XmlFilter rootFilter = new XmlFilter("");

        // Allow reading BookCatalogue archive data.
        buildLegacyFilters(rootFilter, accessor);
        // Current version filters
        buildFilters(rootFilter, accessor);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        DefaultHandler handler = new XmlResponseParser(rootFilter);

        try {
            SAXParser parser = factory.newSAXParser();
            InputSource is = new InputSource();
            is.setCharacterStream(in);
            parser.parse(is, handler);
            // wrap parser exceptions in an IOException
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "fromXml", e);
            }
            throw new IOException(e);
        }
    }

    private void buildFilters(@NonNull final XmlFilter rootFilter,
                              @NonNull final EntityReader<String> accessor) {
        String listRootElement = accessor.getListRoot();
        String rootElement = accessor.getElementRoot();
        // used to read in Set/List data
        final List<String> currentStringList = new ArrayList<>();

        // A new element under the root
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement)
                 .setStartAction(elementContext -> {
                     // use as top-tag
                     mTag = new TagInfo(elementContext);
                     // we only have a version on the top tag, not on every tag.
                     String version = elementContext.getAttributes().getValue(XmlTags.ATTR_VERSION);

                     if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                         Log.d(TAG, "fromXml|NEW-ELEMENT"
                                    + "|localName=" + elementContext.getLocalName()
                                    + "|tag=" + mTag);
                     }
                     accessor.startElement(version == null ? 0 : Integer.parseInt(version), mTag);
                 })
                 .setEndAction(elementContext -> accessor.endElement());

        // typed tag starts. for both attribute and body based elements.
        XmlFilter.XmlHandler startTypedTag = elementContext -> {
            mTagStack.push(mTag);
            mTag = new TagInfo(elementContext);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Log.d(TAG, "fromXml|startTypedTag"
                           + "|localName=" + elementContext.getLocalName() + "|tag=" + mTag);
            }
            // if we have a value attribute, this tag is done. Handle here.
            if (mTag.value != null) {
                switch (mTag.type) {
                    case XmlTags.XML_STRING:
                        // attribute Strings are encoded.
                        accessor.putString(mTag.name, decodeString(mTag.value));
                        break;

                    case XmlTags.XML_BOOLEAN:
                        accessor.putBoolean(mTag.name, Boolean.parseBoolean(mTag.value));
                        break;

                    case XmlTags.XML_INT:
                        accessor.putInt(mTag.name, Integer.parseInt(mTag.value));
                        break;

                    case XmlTags.XML_LONG:
                        accessor.putLong(mTag.name, Long.parseLong(mTag.value));
                        break;

                    case XmlTags.XML_FLOAT:
                        accessor.putFloat(mTag.name, ParseUtils.parseFloat(mTag.value, mLocale));
                        break;

                    case XmlTags.XML_DOUBLE:
                        accessor.putDouble(mTag.name, ParseUtils.parseDouble(mTag.value, mLocale));
                        break;

                    default:
                        Logger.warnWithStackTrace(App.getAppContext(), TAG,
                                                  "mTag.type=" + mTag.type);
                        break;
                }
                mTag = mTagStack.pop();
            }
        };

        // the end of a typed tag with a body
        XmlFilter.XmlHandler endTypedTag = elementContext -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Log.d(TAG, "fromXml|endTypedTag"
                           + "|localName=" + elementContext.getLocalName() + "|tag=" + mTag);
            }
            try {
                switch (mTag.type) {
                    case XmlTags.XML_STRING:
                        // body Strings use CDATA
                        accessor.putString(mTag.name, elementContext.getBody());
                        break;

                    case XmlTags.XML_SET:
                        accessor.putStringSet(mTag.name, currentStringList);
                        // cleanup, ready for the next Set
                        currentStringList.clear();
                        break;

                    case XmlTags.XML_LIST:
                        accessor.putStringList(mTag.name, currentStringList);
                        // cleanup, ready for the next List
                        currentStringList.clear();
                        break;

                    case XmlTags.XML_SERIALIZABLE:
                        accessor.putSerializable(mTag.name, Base64.decode(elementContext.getBody(),
                                                                          Base64.DEFAULT));
                        break;

                    default:
                        Logger.warnWithStackTrace(App.getAppContext(), TAG,
                                                  "mTag.type=" + mTag.type);
                        break;
                }

                mTag = mTagStack.pop();

            } catch (@NonNull final RuntimeException e) {
                throw new RuntimeException(ERROR_UNABLE_TO_PROCESS_XML_ENTITY + mTag.name
                                           + '(' + mTag.type + ')', e);
            }
        };

        // typed tags that only use a value attribute only need action on the start of a tag
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_BOOLEAN)
                 .setStartAction(startTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_INT)
                 .setStartAction(startTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_LONG)
                 .setStartAction(startTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_FLOAT)
                 .setStartAction(startTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_DOUBLE)
                 .setStartAction(startTypedTag);

        // typed tags that have bodies.
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_STRING)
                 .setStartAction(startTypedTag)
                 .setEndAction(endTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_SET)
                 .setStartAction(startTypedTag)
                 .setEndAction(endTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_LIST)
                 .setStartAction(startTypedTag)
                 .setEndAction(endTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlTags.XML_SERIALIZABLE)
                 .setStartAction(startTypedTag)
                 .setEndAction(endTypedTag);

        /*
         * The exporter is generating List/Set tags with String/Int sub tags properly,
         * but importing an Element in a Collection is always done as a String in a List (for now?)
         */
        // set/list elements with attributes.
        XmlFilter.XmlHandler startElementInCollection = elementContext -> {
            mTagStack.push(mTag);
            mTag = new TagInfo(elementContext);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Log.d(TAG, "fromXml|startElementInCollection"
                           + "|localName=" + elementContext.getLocalName() + "|tag=" + mTag);
            }

            // if we have a value attribute, this tag is done. Handle here.
            if (mTag.value != null) {
                // yes, switch is silly here. But let's keep it generic and above all, clear!
                switch (mTag.type) {
                    case XmlTags.XML_BOOLEAN:
                    case XmlTags.XML_INT:
                    case XmlTags.XML_LONG:
                    case XmlTags.XML_FLOAT:
                    case XmlTags.XML_DOUBLE:
                        currentStringList.add(mTag.value);
                        break;

                    default:
                        Logger.warnWithStackTrace(App.getAppContext(), TAG,
                                                  "mTag.type=" + mTag.type);
                        break;
                }

                mTag = mTagStack.pop();
            }
        };

        // set/list elements with bodies.
        XmlFilter.XmlHandler endElementInCollection = elementContext -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Log.d(TAG, "fromXml|endElementInCollection"
                           + "|localName=`" + elementContext.getLocalName() + "|tag=" + mTag);
            }

            // handle tags with bodies.
            try {
                // yes, switch is silly here. But let's keep it generic and above all, clear!
                switch (mTag.type) {
                    // No support for list/set inside a list/set (no point)
                    case XmlTags.XML_SERIALIZABLE:
                        // serializable is indeed just added as a string...
                        // this 'case' is only here for completeness sake.
                    case XmlTags.XML_STRING:
                        // body strings use CDATA
                        currentStringList.add(elementContext.getBody());
                        break;

                    default:
                        Logger.warnWithStackTrace(App.getAppContext(), TAG,
                                                  "mTag.type=" + mTag.type);
                        break;
                }

                mTag = mTagStack.pop();

            } catch (@NonNull final RuntimeException e) {
                throw new RuntimeException(ERROR_UNABLE_TO_PROCESS_XML_ENTITY + mTag, e);
            }
        };


        // Set<String>. The String's are body based.
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement,
                              XmlTags.XML_SET, XmlTags.XML_STRING)
                 .setStartAction(startElementInCollection)
                 .setEndAction(endElementInCollection);
        // List<String>. The String's are body based.
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement,
                              XmlTags.XML_LIST, XmlTags.XML_STRING)
                 .setStartAction(startElementInCollection)
                 .setEndAction(endElementInCollection);

        // Set<Integer>. The int's are attribute based.
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement,
                              XmlTags.XML_SET, XmlTags.XML_INT)
                 .setStartAction(startElementInCollection);
        // List<Integer>. The int's are attribute based.
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement,
                              XmlTags.XML_LIST, XmlTags.XML_INT)
                 .setStartAction(startElementInCollection);
    }

    /**
     * Creates an XmlFilter that can read BookCatalogue Info and Preferences XML format.
     * <p>
     * This legacy format was flat, had a fixed tag name ('item') and used an attribute 'type'.
     * indicating int,string,...
     */
    private void buildLegacyFilters(@NonNull final XmlFilter rootFilter,
                                    @NonNull final EntityReader<String> accessor) {

        XmlFilter.buildFilter(rootFilter, "collection", "item")
                 .setStartAction(elementContext -> {

                     mTag = new TagInfo(elementContext);

                     if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                         Log.d(TAG, "buildLegacyFilters|StartAction"
                                    + "|localName=" + elementContext.getLocalName()
                                    + "|tag=" + mTag);
                     }
                 })
                 .setEndAction(elementContext -> {
                     if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                         Log.d(TAG, "buildLegacyFilters|EndAction"
                                    + "|localName=" + elementContext.getLocalName()
                                    + "|tag=" + mTag);
                     }
                     try {
                         String body = elementContext.getBody();
                         switch (mTag.type) {
                             case "Int":
                                 accessor.putInt(mTag.name, Integer.parseInt(body));
                                 break;
                             case "Long":
                                 accessor.putLong(mTag.name, Long.parseLong(body));
                                 break;
                             case "Flt":
                                 // no Locales
                                 accessor.putFloat(mTag.name, Float.parseFloat(body));
                                 break;
                             case "Dbl":
                                 // no Locales
                                 accessor.putDouble(mTag.name, Double.parseDouble(body));
                                 break;
                             case "Str":
                                 accessor.putString(mTag.name, body);
                                 break;
                             case "Bool":
                                 accessor.putBoolean(mTag.name, Boolean.parseBoolean(body));
                                 break;
                             case "Serial":
                                 accessor.putSerializable(mTag.name,
                                                          Base64.decode(body, Base64.DEFAULT));
                                 break;

                             default:
                                 throw new UnexpectedValueException(mTag.type);
                         }

                     } catch (@NonNull final NumberFormatException e) {
                         throw new RuntimeException(ERROR_UNABLE_TO_PROCESS_XML_ENTITY + mTag, e);
                     }
                 });
    }

    @Override
    public void close() {
        try {
            // now do some cleaning
            mDb.purge();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(App.getAppContext(), TAG, e);
        }
        mDb.close();
    }

    /**
     * Class to provide access to a subset of the methods of collections.
     *
     * @param <K> Type of the collection key
     */
    interface EntityReader<K> {

        /**
         * @return the tag name for the list
         */
        @NonNull
        String getListRoot();

        /**
         * @return the tag name for an element in the list
         */
        @NonNull
        String getElementRoot();

        /**
         * Callback at the start of each element in the list.
         *
         * @param version of the XML schema for this element, or 0 if not present
         * @param tag     the info about the top tag
         */
        void startElement(int version,
                          @NonNull TagInfo tag);

        /**
         * Callback at the end of each element in the list.
         */
        void endElement();

        /**
         * Subtag of an element consisting of name/value pairs for each potentially supported type.
         */
        void putString(@NonNull K key,
                       @NonNull String value);

        void putBoolean(@NonNull K key,
                        boolean value);

        void putInt(@NonNull K key,
                    int value);

        default void putFloat(@NonNull final K key,
                              final float value) {
            throw new UnexpectedValueException("Float, key=" + key);
        }

        default void putLong(@NonNull final K key,
                             final long value) {
            throw new UnexpectedValueException("Long, key=" + key);
        }

        default void putDouble(@NonNull final K key,
                               final double value) {
            throw new UnexpectedValueException("Double, key=" + key);
        }

        /**
         * Note the values can be passed in as a List or a Set,
         * but store should be a Set of some type.
         */
        default void putStringSet(@NonNull final K key,
                                  @NonNull final Iterable<String> value) {
            throw new UnexpectedValueException("StringSet, key=" + key);
        }

        /**
         * Note the values can be passed in as a List or a Set,
         * but store should be a List of some type.
         */
        default void putStringList(@NonNull final K key,
                                   @NonNull final Iterable<String> value) {
            throw new UnexpectedValueException("StringList, key=" + key);
        }

        default void putSerializable(@NonNull final K key,
                                     @NonNull final Serializable value) {
            throw new UnexpectedValueException("Serializable, key=" + key);
        }
    }

    /**
     * Value class to preserve data while parsing XML input.
     */
    static class TagInfo {

        /** attribute with the key into the collection. */
        @NonNull
        final String name;
        /**
         * value attribute (e.g. int,boolean,...),
         * not used when the tag body is used (String,..).
         * <p>
         * optional.
         */
        @Nullable
        final String value;
        /**
         * - current use: the type of the element as set by the tag itself.
         * - BookCatalogue backward compatibility: the type attribute of a generic 'item' tag.
         */
        @NonNull
        String type;
        /** optional. 0 if none. */
        int id;

        /**
         * Constructor.
         * <p>
         * <strong>Important:</strong> a tag called "item" will trigger BookCatalogue parsing:
         * the 'type' attribute will be read and be used as the tag-name.
         *
         * @param elementContext of the XML tag
         */
        TagInfo(@NonNull final ElementContext elementContext) {
            Attributes attrs = elementContext.getAttributes();

            type = elementContext.getLocalName();
            // BookCatalogue used a fixed tag "item", with the type as an attribute
            if ("item".equals(type)) {
                type = attrs.getValue("type");
            }
            name = attrs.getValue(XmlTags.ATTR_NAME);
            String idStr = attrs.getValue(XmlTags.ATTR_ID);
            if (idStr != null) {
                try {
                    id = Integer.parseInt(idStr);
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(App.getAppContext(), TAG, e, "attr=" + name, "idStr=" + idStr);
                }
            }
            value = attrs.getValue(XmlTags.ATTR_VALUE);
        }

        @Override
        @NonNull
        public String toString() {
            return "TagInfo{"
                   + "name=`" + name + '`'
                   + ", type=`" + type + '`'
                   + ", id=" + id
                   + ", value=`" + value + '`'
                   + '}';
        }
    }

    /**
     * Supports a *single* {@link XmlTags#XML_INFO} block,
     * enclosed inside a {@link XmlTags#XML_INFO_LIST}.
     */
    static class InfoReader
            implements EntityReader<String> {

        @NonNull
        private final Bundle mBundle;

        InfoReader(@NonNull final BackupInfo info) {
            mBundle = info.getBundle();
        }

        @Override
        @NonNull
        public String getListRoot() {
            return XmlTags.XML_INFO_LIST;
        }

        @Override
        @NonNull
        public String getElementRoot() {
            return XmlTags.XML_INFO;
        }

        @Override
        public void startElement(final int version,
                                 @NonNull final TagInfo tag) {
        }

        @Override
        public void endElement() {
        }

        @Override
        public void putString(@NonNull final String key,
                              @NonNull final String value) {
            mBundle.putString(key, value);
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            mBundle.putBoolean(key, value);
        }

        @Override
        public void putInt(@NonNull final String key,
                           final int value) {
            mBundle.putInt(key, value);
        }

        @Override
        public void putLong(@NonNull final String key,
                            final long value) {
            mBundle.putLong(key, value);
        }

        @Override
        public void putFloat(@NonNull final String key,
                             final float value) {
            mBundle.putFloat(key, value);
        }

        @Override
        public void putDouble(@NonNull final String key,
                              final double value) {
            mBundle.putDouble(key, value);
        }

        @Override
        public void putSerializable(@NonNull final String key,
                                    @NonNull final Serializable value) {
            mBundle.putSerializable(key, value);
        }
    }

    /**
     * Supports a *single* {@link XmlTags#XML_PREFERENCES} block,
     * enclosed inside a {@link XmlTags#XML_PREFERENCES_LIST}.
     */
    static class PreferencesReader
            implements EntityReader<String> {

        private final SharedPreferences.Editor mEditor;

        /**
         * Constructor.
         *
         * @param editor to write to
         */
        PreferencesReader(@NonNull final SharedPreferences.Editor editor) {
            mEditor = editor;
        }

        @Override
        @NonNull
        public String getListRoot() {
            return XmlTags.XML_PREFERENCES_LIST;
        }

        @Override
        @NonNull
        public String getElementRoot() {
            return XmlTags.XML_PREFERENCES;
        }

        @Override
        public void startElement(final int version,
                                 @NonNull final TagInfo tag) {
        }

        @Override
        public void endElement() {
        }

        @Override
        public void putString(@NonNull final String key,
                              @NonNull final String value) {
            mEditor.putString(key, value);
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            mEditor.putBoolean(key, value);
        }

        @Override
        public void putInt(@NonNull final String key,
                           final int value) {
            mEditor.putInt(key, value);
        }

        @Override
        public void putLong(@NonNull final String key,
                            final long value) {
            mEditor.putLong(key, value);
        }

        @Override
        public void putFloat(@NonNull final String key,
                             final float value) {
            mEditor.putFloat(key, value);
        }

        @Override
        public void putStringSet(@NonNull final String key,
                                 @NonNull final Iterable<String> values) {
            Set<String> valueSet = new HashSet<>();
            for (String s : values) {
                valueSet.add(s);
            }
            mEditor.putStringSet(key, valueSet);
        }

        @Override
        public void putStringList(@NonNull final String key,
                                  @NonNull final Iterable<String> values) {
            mEditor.putString(key, TextUtils.join(",", values));
        }
    }

    /**
     * Supports a *list* of {@link XmlTags#XML_STYLE} block,
     * enclosed inside a {@link XmlTags#XML_STYLE_LIST}
     * <p>
     * See {@link XmlExporter} :
     * * Filters and Groups are flattened.
     * * - each filter has a tag
     * * - actual groups are written as a set of ID's (kinds)
     * * - each preference in a group has a tag.
     */
    static class StylesReader
            implements EntityReader<String> {

        @NonNull
        private final Context mContext;
        /** Database Access. */
        @NonNull
        private final DAO mDb;

        private BooklistStyle mStyle;

        /** a collection of all Preferences (including from *all* groups). */
        private Map<String, PPref> mStylePrefs;

        private int mStylesRead;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param db      Database Access
         */
        StylesReader(@NonNull final Context context,
                     @NonNull final DAO db) {
            mContext = context;
            mDb = db;
        }

        int getStylesRead() {
            return mStylesRead;
        }

        @Override
        @NonNull
        public String getListRoot() {
            return XmlTags.XML_STYLE_LIST;
        }

        @NonNull
        @Override
        public String getElementRoot() {
            return XmlTags.XML_STYLE;
        }

        /**
         * The start of a Style element.
         * <p>
         * Creates a new BooklistStyle, and sets it as the 'current' one ready for writes.
         * <p>
         * <br>{@inheritDoc}
         */
        @Override
        public void startElement(final int version,
                                 @NonNull final TagInfo tag) {
            if (tag.name.isEmpty()) {
                throw new IllegalArgumentException();
            }
            // create a new Style object. This will not have any groups assigned to it...
            mStyle = new BooklistStyle(tag.id, tag.name);
            //... and hence, the Style Preferences won't have any group Preferences either.
            mStylePrefs = mStyle.getPreferences(true);
            // So loop all groups, and get their Preferences.
            // Do NOT add the group itself to the style at this point as our import
            // might not actually have it.
            for (BooklistGroup group : BooklistGroup.getAllGroups(mStyle.getUuid(),
                                                                  mStyle.isUserDefined())) {
                mStylePrefs.putAll(group.getPreferences());
            }
        }

        /**
         * The end of a Style element.
         * Update the groups Preferences and save the style
         */
        @Override
        public void endElement() {
            // we now have the groups themselves (one of the 'flat' prefs) set on the style,
            // so transfer their specific Preferences.
            for (BooklistGroup group : mStyle.getGroups()) {
                mStyle.updatePreferences(mContext, group.getPreferences());
            }
            // add to the menu of preferred styles if needed.
            if (mStyle.isPreferred()) {
                BooklistStyle.Helper.addPreferredStyle(mStyle);
            }

            // the prefs are written on the fly, but we still need the db entry saved.
            mStyle.save(mDb);

            mStylesRead++;
        }

        @Override
        public void putString(@NonNull final String key,
                              @NonNull final String value) {
            PString p = (PString) mStylePrefs.get(key);
            if (p != null) {
                p.set(value);
            }
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            PBoolean p = (PBoolean) mStylePrefs.get(key);
            if (p != null) {
                p.set(value);
            }
        }

        @Override
        public void putInt(@NonNull final String key,
                           final int value) {
            PInt p = (PInt) mStylePrefs.get(key);
            if (p != null) {
                p.set(value);
            }
        }

        @Override
        public void putStringSet(@NonNull final String key,
                                 @NonNull final Iterable<String> value) {
            PCollection p = (PCollection) mStylePrefs.get(key);
            if (p != null) {
                p.set(value);
            }
        }

        @Override
        public void putStringList(@NonNull final String key,
                                  @NonNull final Iterable<String> value) {
            PCollection p = (PCollection) mStylePrefs.get(key);
            if (p != null) {
                p.set(value);
            }
        }
    }

    /**
     * The sax parser closes streams, which is not good on a Tar archive entry.
     */
    static class BufferedReaderNoClose
            extends BufferedReader {

        BufferedReaderNoClose(@NonNull final Reader in,
                              @SuppressWarnings("SameParameterValue") final int flags) {
            super(in, flags);
        }

        @Override
        public void close() {
            // ignore the close call from the SAX parser.
            // We'll close it ourselves when appropriate.
        }
    }
}
