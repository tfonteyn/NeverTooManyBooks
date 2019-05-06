package com.eleybourn.bookcatalogue.backup.xml;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;

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
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ProgressListener;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.backup.archivebase.ReaderEntity;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.booklist.prefs.PBoolean;
import com.eleybourn.bookcatalogue.booklist.prefs.PCollection;
import com.eleybourn.bookcatalogue.booklist.prefs.PInt;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.booklist.prefs.PString;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.xml.ElementContext;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

/**
 * For now, only INFO, Preferences and Styles are implemented.
 * <p>
 * TODO: unify the handling of simple elements and set/list elements.
 */
public class XmlImporter
        implements Importer {

    private static final String UNABLE_TO_PROCESS_XML_ENTITY_ERROR =
            "Unable to process XML entity ";

    public static final int BUFFER_SIZE = 32768;

    @NonNull
    private final DBA mDb;
    @NonNull
    private final ImportSettings mSettings;

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
     */
    public XmlImporter() {
        mDb = new DBA();
        mSettings = new ImportSettings();
        mSettings.what = ExportSettings.ALL;
    }

    /**
     * Constructor.
     *
     * @param settings the import settings
     */
    public XmlImporter(@NonNull final ImportSettings settings) {
        mDb = new DBA();
        settings.validate();
        mSettings = settings;
    }

    /**
     * counterpart of {@link XmlExporter} #encodeString}
     * <p>
     * Only String 'value' tags need decoding.
     * <p>
     * decode the bare essentials only. To decode all possible entities we could add the Apache
     * 'lang' library I suppose.... maybe some day.
     */
    private static String decodeString(@Nullable final String data) {
        if (data == null || "null".equalsIgnoreCase(data) || data.trim().isEmpty()) {
            return "";
        }

        return data.replace("&quot;", "\"")
                   .replace("&apos;", "'")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   // must be last of the entities
                   .replace("&amp;", "&");
    }

    /**
     * Not supported for now.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public int doBooks(@NonNull final InputStream importStream,
                       @Nullable final CoverFinder coverFinder,
                       @NonNull final ProgressListener listener) {
        throw new UnsupportedOperationException();
    }

    /**
     * WIP: the backup reader already checks the type/what.
     * Intention is to move all logic for xml based entities to this class.
     *
     * @param entity to restore
     *
     * @throws IOException on failure
     */
    public void doEntity(@NonNull final ReaderEntity entity,
                         @NonNull final ProgressListener listener)
            throws IOException {
        final BufferedReader in = new BufferedReaderNoClose(
                new InputStreamReader(entity.getStream(), StandardCharsets.UTF_8), BUFFER_SIZE);

        //noinspection SwitchStatementWithTooFewBranches
        switch (entity.getType()) {
            case BooklistStyles:
                if ((mSettings.what & ImportSettings.BOOK_LIST_STYLES) != 0) {
                    fromXml(in, listener, new StylesReader(mDb));
                }
                break;

            default:
                throw new IllegalStateException("type=" + entity.getType());
        }
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
        final BufferedReader in = new BufferedReaderNoClose(
                new InputStreamReader(entity.getStream(), StandardCharsets.UTF_8), BUFFER_SIZE);
        fromXml(in, null, new InfoReader(info));
    }

    /**
     * Read mStylePrefs from an XML stream into a given SharedPreferences object.
     *
     * @param entity   to read
     * @param listener Progress and cancellation provider
     * @param prefs    object to populate
     *
     * @throws IOException on failure
     */
    public void doPreferences(@NonNull final ReaderEntity entity,
                              @NonNull final ProgressListener listener,
                              @NonNull final SharedPreferences prefs)
            throws IOException {
        final BufferedReader in = new BufferedReaderNoClose(
                new InputStreamReader(entity.getStream(), StandardCharsets.UTF_8), BUFFER_SIZE);
        SharedPreferences.Editor editor = prefs.edit();
        fromXml(in, listener, new PreferencesReader(editor));
        editor.apply();
    }

    /**
     * Internal routine to update the passed EntityAccessor from an XML file.
     *
     * @param listener (optional) Progress and cancellation provider
     *
     * @throws IOException on failure
     */
    private void fromXml(@NonNull final BufferedReader in,
                         @Nullable final ProgressListener listener,
                         @NonNull final EntityReader<String> accessor)
            throws IOException {

        // we need an uber-root to hang our tree on.
        XmlFilter rootFilter = new XmlFilter("");

        // used to read in Set data
        final Set<String> currentStringSet = new HashSet<>();

        // Allow reading pre-v200 archive data.
        createPreV200Filter(rootFilter, accessor);

        String listRootElement = accessor.getListRoot();
        String rootElement = accessor.getElementRoot();

        // A new element under the root
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement)
                 .setStartAction(context -> {
                     // use as top-tag
                     mTag = new TagInfo(context);
                     // we only have a version on the top tag, not on every tag.
                     String version = context.getAttributes().getValue(XmlTags.ATTR_VERSION);

                     if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                         Logger.debug(this, "fromXml",
                                      "NEW-ELEMENT",
                                      "localName=`" + context.getLocalName() + '`', mTag);
                     }
                     accessor.startElement(version == null ? 0 : Integer.parseInt(version), mTag);
                 })
                 .setEndAction(context -> accessor.endElement());

        // typed tag starts. for both attribute and body based elements.
        XmlFilter.XmlHandler startTypedTag = context -> {
            mTagStack.push(mTag);
            mTag = new TagInfo(context);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Logger.debug(this, "fromXml",
                             "startTypedTag",
                             "localName=`" + context.getLocalName() + '`', mTag);
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
                        accessor.putFloat(mTag.name, Float.parseFloat(mTag.value));
                        break;

                    case XmlTags.XML_DOUBLE:
                        accessor.putDouble(mTag.name, Double.parseDouble(mTag.value));
                        break;
                }
                mTag = mTagStack.pop();
            }
        };

        // the end of a typed tag with a body
        XmlFilter.XmlHandler endTypedTag = context -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Logger.debug(this, "fromXml",
                             "endTypedTag",
                             "localName=`" + context.getLocalName() + '`', mTag);
            }
            try {
                switch (mTag.type) {
                    case XmlTags.XML_STRING:
                        // body Strings use CDATA
                        accessor.putString(mTag.name, context.getBody());
                        break;

                    case XmlTags.XML_SET:
                    case XmlTags.XML_LIST:
                        accessor.putStringSet(mTag.name, currentStringSet);
                        // cleanup, ready for the next Set
                        currentStringSet.clear();
                        break;

                    case XmlTags.XML_SERIALIZABLE:
                        accessor.putSerializable(mTag.name,
                                                 Base64.decode(context.getBody(), Base64.DEFAULT));
                        break;

                    default:
                        Logger.warnWithStackTrace(this, "Unknown type: " + mTag.type);
                        break;
                }

                mTag = mTagStack.pop();

            } catch (RuntimeException e) {
                Logger.error(this, e);
                throw new RuntimeException(UNABLE_TO_PROCESS_XML_ENTITY_ERROR + mTag.name
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
         * but importing an Element in a Collection is always done as a String in a Set (for now?)
         */
        // set/list elements with attributes.
        XmlFilter.XmlHandler startElementInCollection = context -> {
            mTagStack.push(mTag);
            mTag = new TagInfo(context);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Logger.debug(this, "fromXml",
                             "startElementInCollection",
                             "localName=`" + context.getLocalName() + '`', mTag);
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
                        currentStringSet.add(mTag.value);
                        break;
                }

                mTag = mTagStack.pop();
            }
        };

        // set/list elements with bodies.
        XmlFilter.XmlHandler endElementInCollection = context -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                Logger.debug(this, "fromXml",
                             "endElementInCollection",
                             "localName=`" + context.getLocalName() + '`', mTag);
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
                        currentStringSet.add(context.getBody());
                        break;
                }

                mTag = mTagStack.pop();

            } catch (RuntimeException e) {
                Logger.error(this, e);
                throw new RuntimeException(UNABLE_TO_PROCESS_XML_ENTITY_ERROR + mTag, e);
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


        // Let the parsing quest begin.
        final XmlResponseParser handler = new XmlResponseParser(rootFilter);
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final SAXParser parser;
        try {
            parser = factory.newSAXParser();
        } catch (SAXException | ParserConfigurationException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e);
            }
            throw new IOException("Unable to create XML parser", e);
        }

        final InputSource is = new InputSource();
        is.setCharacterStream(in);
        try {
            parser.parse(is, handler);
        } catch (SAXException e) {
            Logger.error(this, e);
            throw new IOException("Malformed XML");
        }
    }

    /**
     * Creates an XmlFilter that can read pre-v200 Info and Preferences XML format.
     * <p>
     * This legacy format was flat, had a fixed tag name ('item') and used an attribute 'type'.
     * indicating int,string,...
     */
    private void createPreV200Filter(@NonNull final XmlFilter rootFilter,
                                     @NonNull final EntityReader<String> accessor) {

        XmlFilter.buildFilter(rootFilter, "collection", "item")
                 .setStartAction(context -> {

                     mTag = new TagInfo(context);

                     if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                         Logger.debug(this, "createPreV200Filter",
                                      "StartAction",
                                      "localName=`" + context.getLocalName() + '`', mTag);
                     }
                 })
                 .setEndAction(context -> {
                     if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                         Logger.debug(this, "createPreV200Filter",
                                      "EndAction",
                                      "localName=`" + context.getLocalName() + '`', mTag);
                     }
                     try {
                         String body = context.getBody();
                         switch (mTag.type) {
                             case "Int":
                                 accessor.putInt(mTag.name, Integer.parseInt(body));
                                 break;
                             case "Long":
                                 accessor.putLong(mTag.name, Long.parseLong(body));
                                 break;
                             case "Flt":
                                 accessor.putFloat(mTag.name, Float.parseFloat(body));
                                 break;
                             case "Dbl":
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
                                 throw new IllegalTypeException(mTag.type);
                         }

                     } catch (NumberFormatException e) {
                         throw new RuntimeException(UNABLE_TO_PROCESS_XML_ENTITY_ERROR + mTag, e);
                     }
                 });
    }

    @Override
    public void close() {
        try {
            // now do some cleaning
            mDb.purge();
        } catch (RuntimeException e) {
            Logger.error(this, e);
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

        void putLong(@NonNull K key,
                     long value);

        void putFloat(@NonNull K key,
                      float value);

        void putDouble(@NonNull K key,
                       double value);

        void putStringSet(@NonNull K key,
                          @NonNull Set<String> value);

        void putSerializable(@NonNull K key,
                             @NonNull Serializable value);
    }

    /**
     * Record to preserve data while parsing XML input.
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
         * - pre-v200 backward compatibility: the type attribute of a generic 'item' tag.
         */
        @NonNull
        String type;
        /** optional. 0 if none. */
        int id;

        /**
         * Constructor.
         * <p>
         * Important: a tag called "item" will trigger pre-v200 parsing: the 'type' attribute will
         * be read and be used as the tagname.
         *
         * @param elementContext of the XML tag
         */
        TagInfo(@NonNull final ElementContext elementContext) {
            Attributes attrs = elementContext.getAttributes();

            type = elementContext.getLocalName();
            // Legacy pre-v200 used a fixed tag "item", with the type as an attribute
            if ("item".equals(type)) {
                type = attrs.getValue("type");
            }
            name = attrs.getValue(XmlTags.ATTR_NAME);
            String idStr = attrs.getValue(XmlTags.ATTR_ID);
            if (idStr != null) {
                try {
                    id = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    if (BuildConfig.DEBUG /* WARN */) {
                        Logger.warn(this, "TagInfo",
                                    "invalid id in xml t: " + name);
                    }
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

        private final BackupInfo mInfo;

        @NonNull
        private final Bundle mBundle;

        InfoReader(@NonNull final BackupInfo info) {
            mInfo = info;
            mBundle = mInfo.getBundle();
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
        public void putStringSet(@NonNull final String key,
                                 @NonNull final Set<String> value) {
            throw new IllegalTypeException("Collection<String>");
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
                                 @NonNull final Set<String> value) {
            mEditor.putStringSet(key, value);

        }

        @Override
        public void putDouble(@NonNull final String key,
                              final double value) {
            throw new IllegalTypeException(XmlTags.XML_DOUBLE);
        }

        @Override
        public void putSerializable(@NonNull final String key,
                                    @NonNull final Serializable value) {
            throw new IllegalTypeException(XmlTags.XML_SERIALIZABLE);
        }
    }

    /**
     * Supports a *list* of {@link XmlTags#XML_STYLE} block,
     * enclosed inside a {@link XmlTags#XML_STYLE_LIST}
     * <p>
     * See {@link XmlExporter} :
     * * Filters and Groups are flattened.
     * * - each filter has a tag
     * * - actual groups are written as a set of id's (kinds)
     * * - each preference in a group has a tag.
     */
    static class StylesReader
            implements EntityReader<String> {

        private final DBA mDb;

        private BooklistStyle mStyle;

        /** a collection of *ALL* Preferences (including from *all* groups). */
        private Map<String, PPref> mStylePrefs;

        /**
         * Constructor.
         *
         * @param db the database
         */
        StylesReader(@NonNull final DBA db) {
            mDb = db;
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
            for (BooklistGroup group : BooklistGroup.getAllGroups(mStyle)) {
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
                mStyle.updatePreferences(group.getPreferences());
            }
            // add to the menu of preferred styles if needed.
            if (mStyle.isPreferred()) {
                BooklistStyles.addPreferredStyle(mStyle);
            }

            // the prefs are written on the fly, but we still need the db entry saved.
            mStyle.save(mDb);
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            PBoolean p = (PBoolean) mStylePrefs.get(key);
            //noinspection ConstantConditions
            p.set(value);
        }

        @Override
        public void putInt(@NonNull final String key,
                           final int value) {
            PInt p = (PInt) mStylePrefs.get(key);
            //noinspection ConstantConditions
            p.set(value);
        }

        @Override
        public void putString(@NonNull final String key,
                              @NonNull final String value) {
            PString p = (PString) mStylePrefs.get(key);
            //noinspection ConstantConditions
            p.set(value);
        }

        @Override
        public void putStringSet(@NonNull final String key,
                                 @NonNull final Set<String> value) {
            PCollection p = (PCollection) mStylePrefs.get(key);
            //noinspection ConstantConditions
            p.set(value);
        }

        @Override
        public void putLong(@NonNull final String key,
                            final long value) {
            throw new IllegalTypeException(XmlTags.XML_LONG);
        }

        @Override
        public void putFloat(@NonNull final String key,
                             final float value) {
            throw new IllegalTypeException(XmlTags.XML_FLOAT);
        }

        @Override
        public void putDouble(@NonNull final String key,
                              final double value) {
            throw new IllegalTypeException(XmlTags.XML_DOUBLE);
        }

        @Override
        public void putSerializable(@NonNull final String key,
                                    @NonNull final Serializable value) {
            throw new IllegalTypeException(XmlTags.XML_SERIALIZABLE);
        }
    }

    /**
     * The sax parser closes streams, which is not good on a Tar archive entry.
     *
     * @author pjw
     */
    public static class BufferedReaderNoClose
            extends BufferedReader {

        BufferedReaderNoClose(@NonNull final Reader in,
                              @SuppressWarnings("SameParameterValue") final int flags) {
            super(in, flags);
        }

        @Override
        public void close() {
            // ignore the close call from the SAX parser. We'll close it ourselves when appropriate.
        }
    }
}
