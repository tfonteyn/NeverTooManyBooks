package com.eleybourn.bookcatalogue.backup.xml;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
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
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * For now, only INFO, Preferences and Styles are implemented.
 */
public class XmlImporter
        implements Importer, Closeable {

    private static final String UNABLE_TO_PROCESS_XML_ENTITY_ERROR =
            "Unable to process XML entity ";

    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final ImportSettings mSettings;

    public XmlImporter() {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        mSettings = new ImportSettings();
        mSettings.what = ExportSettings.ALL;
    }

    public XmlImporter(@NonNull final ImportSettings settings) {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        settings.validate();
        mSettings = settings;
    }

    /**
     * @param importStream Stream for reading data
     * @param coverFinder  (Optional) object to find a file on the local device
     * @param listener     Progress and cancellation provider
     */
    @Override
    public int doImport(@NonNull final InputStream importStream,
                        @Nullable final CoverFinder coverFinder,
                        @NonNull final ImportListener listener)
            throws IOException {
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
                         @NonNull final ImportListener listener)
            throws IOException {
        final BufferedReader in =
                new BufferedReaderNoClose(new InputStreamReader(entity.getStream(),
                                                                XmlUtils.UTF8),
                                          XmlUtils.BUFFER_SIZE);

        switch (entity.getType()) {
            case BooklistStyles:
                if ((mSettings.what & ImportSettings.BOOK_LIST_STYLES) != 0) {
                    fromXml(in, listener, new StylesReader(mDb));
                }
                break;

            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Read the info block from an XML stream.
     *
     * @param entity to read
     * @param info   object to populate
     */
    @NonNull
    public void doBackupInfoBlock(@NonNull final ReaderEntity entity,
                                  @NonNull final BackupInfo info)
            throws IOException {
        final BufferedReader in = new BufferedReaderNoClose(
                new InputStreamReader(entity.getStream(), XmlUtils.UTF8), XmlUtils.BUFFER_SIZE);
        fromXml(in, null, new InfoReader(info));
    }

    /**
     * Read stylePrefs from an XML stream into a given SharedPreferences object.
     *
     * @param entity   to read
     * @param listener Progress and cancellation provider
     * @param prefs    object to populate
     */
    public void doPreferences(@NonNull final ReaderEntity entity,
                              @NonNull final ImportListener listener,
                              @NonNull final SharedPreferences prefs)
            throws IOException {
        final BufferedReader in = new BufferedReaderNoClose(
                new InputStreamReader(entity.getStream(), XmlUtils.UTF8), XmlUtils.BUFFER_SIZE);
        SharedPreferences.Editor editor = prefs.edit();
        fromXml(in, listener, new PreferencesReader(editor));
        editor.apply();
    }

    /**
     * Internal routine to update the passed EntityAccessor from an XML file.
     *
     * @param listener (optional) Progress and cancellation provider
     */
    private void fromXml(@NonNull final BufferedReader in,
                         @Nullable final ImportListener listener,
                         @NonNull final EntityReader<String> accessor)
            throws IOException {

        // we need an uber-root to hang our tree on.
        XmlFilter rootFilter = new XmlFilter("");
        // a simple Holder for the current tag name and attributes.
        final TagInfo tag = new TagInfo();
        // used to read in Set data
        final Set<String> currentStringSet = new HashSet<>();

        // Allow reading pre-v200 archive data.
        createdPreV200Filter(rootFilter, accessor, tag);

        String listRootElement = accessor.getListRoot();
        String rootElement = accessor.getElementRoot();

        // A new element in the list
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement)
                 .setStartAction(new XmlFilter.XmlHandler() {
                     @Override
                     public void process(@NonNull final XmlFilter.ElementContext context) {
                         String version = context.attributes.getValue(XmlUtils.ATTR_VERSION);
                         String id = context.attributes.getValue(XmlUtils.ATTR_ID);
                         String name = context.attributes.getValue(XmlUtils.ATTR_NAME);
                         if (DEBUG_SWITCHES.XML && BuildConfig.DEBUG) {
                             Logger.info(this, "StartAction|NEW-ELEMENT" +
                                     "|localName=`" + context.localName + '`' +
                                     "|tag.name=`" + name + '`');
                         }
                         accessor.startElement(
                                 version == null ? 0 : Integer.parseInt(version),
                                 id == null ? 0 : Integer.parseInt(id), name);
                     }
                 })
                 .setEndAction(new XmlFilter.XmlHandler() {
                     @Override
                     public void process(@NonNull final XmlFilter.ElementContext context) {
                         accessor.endElement();
                     }
                 });

        // typed tag starts
        XmlFilter.XmlHandler startTypedTag = new XmlFilter.XmlHandler() {
            @Override
            public void process(@NonNull final XmlFilter.ElementContext context) {
                tag.type = context.localName;
                tag.name = context.attributes.getValue(XmlUtils.ATTR_NAME);
                tag.value = context.attributes.getValue(XmlUtils.ATTR_VALUE);
                if (DEBUG_SWITCHES.XML && BuildConfig.DEBUG) {
                    Logger.info(this, "StartAction" +
                            "|localName=`" + context.localName + '`' +
                            "|tag.name=`" + tag.name + '`' +
                            "|tag.value=`" + tag.value + '`');
                }
                // if we have a value attribute, this tag is done. Handle those here.
                if (tag.value != null) {
                    switch (tag.type) {
                        case XmlUtils.XML_STRING:
                            accessor.putString(tag.name, XmlUtils.decode(tag.value));
                            break;
                        case XmlUtils.XML_BOOLEAN:
                            accessor.putBoolean(tag.name, Boolean.parseBoolean(tag.value));
                            break;
                        case XmlUtils.XML_INT:
                            accessor.putInt(tag.name, Integer.parseInt(tag.value));
                            break;
                        case XmlUtils.XML_LONG:
                            accessor.putLong(tag.name, Long.parseLong(tag.value));
                            break;
                        case XmlUtils.XML_FLOAT:
                            accessor.putFloat(tag.name, Float.parseFloat(tag.value));
                            break;
                        case XmlUtils.XML_DOUBLE:
                            accessor.putDouble(tag.name, Double.parseDouble(tag.value));
                            break;
                    }
                }
            }
        };

        // the end of a typed tag with a body
        XmlFilter.XmlHandler endTypedTag = new XmlFilter.XmlHandler() {
            @Override
            public void process(@NonNull final XmlFilter.ElementContext context) {
                if (DEBUG_SWITCHES.XML && BuildConfig.DEBUG) {
                    Logger.info(this, "EndAction" +
                            "|localName=`" + context.localName + '`' +
                            "|tag.name=`" + tag.name + '`');
                }
                // tags with a value attribute were handled in the startElement call.
                if (tag.value != null) {
                    return;
                }
                // handle tags with bodies.
                try {
                    switch (tag.type) {
                        case XmlUtils.XML_STRING:
                            accessor.putString(tag.name, context.body);
                            break;

                        case XmlUtils.XML_SET:
                        case XmlUtils.XML_LIST:
                            accessor.putStringSet(tag.name, currentStringSet);
                            // cleanup, ready for the next Set
                            currentStringSet.clear();
                            break;

                        case XmlUtils.XML_SERIALIZABLE:
                            accessor.putSerializable(tag.name,
                                                     Base64.decode(context.body, Base64.DEFAULT));
                            break;

                        default:
                            Logger.error("Unknown type: " + tag.type);
                            break;
                    }
                } catch (RuntimeException e) {
                    Logger.error(e);
                    throw new RuntimeException(
                            UNABLE_TO_PROCESS_XML_ENTITY_ERROR + tag.name
                                    + '(' + tag.type + ')', e);
                }
            }
        };


        // typed tags that only use a value attribute
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_BOOLEAN)
                 .setStartAction(startTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_INT)
                 .setStartAction(startTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_LONG)
                 .setStartAction(startTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_FLOAT)
                 .setStartAction(startTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_DOUBLE)
                 .setStartAction(startTypedTag);

        // typed tags that have bodies (or values)
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_STRING)
                 .setStartAction(startTypedTag)
                 .setEndAction(endTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_SET)
                 .setStartAction(startTypedTag)
                 .setEndAction(endTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_LIST)
                 .setStartAction(startTypedTag)
                 .setEndAction(endTypedTag);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_SERIALIZABLE)
                 .setStartAction(startTypedTag)
                 .setEndAction(endTypedTag);

        /*
         * The exporter is generating List/Set tags with String/Int sub tags properly,
         * but importing an Element in a Collection is always done as a String in a Set (for now?)
         */
        XmlFilter.XmlHandler startElementInCollection = new XmlFilter.XmlHandler() {
            @Override
            public void process(@NonNull final XmlFilter.ElementContext context) {
                currentStringSet.add(context.attributes.getValue(XmlUtils.ATTR_VALUE));
            }
        };

        // Set<String>
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement,
                              XmlUtils.XML_SET, XmlUtils.XML_STRING)
                 .setStartAction(startElementInCollection);
        // Set<Integer>
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement,
                              XmlUtils.XML_SET, XmlUtils.XML_INT)
                 .setStartAction(startElementInCollection);

        // List<String>
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement,
                              XmlUtils.XML_LIST, XmlUtils.XML_STRING)
                 .setStartAction(startElementInCollection);
        // List<Integer>
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement,
                              XmlUtils.XML_LIST, XmlUtils.XML_INT)
                 .setStartAction(startElementInCollection);


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
     * Creates an XmlFilter that can read pre-v200 Info and Preferences XML format.
     */
    private void createdPreV200Filter(final XmlFilter rootFilter,
                                      final @NonNull EntityReader<String> accessor,
                                      final TagInfo tag) {

        XmlFilter.buildFilter(rootFilter, "collection", "item")
                 .setStartAction(new XmlFilter.XmlHandler() {
                     @Override
                     public void process(@NonNull final XmlFilter.ElementContext context) {

                         tag.name = context.attributes.getValue("name");
                         tag.type = context.attributes.getValue("type");
                         if (DEBUG_SWITCHES.XML && BuildConfig.DEBUG) {
                             Logger.info(this, "StartAction" +
                                     "|localName=`" + context.localName + '`' +
                                     "|tag.name=`" + tag.name + '`');
                         }
                     }
                 }, null)
                 .setEndAction(new XmlFilter.XmlHandler() {
                     @Override
                     public void process(@NonNull final XmlFilter.ElementContext context) {
                         if (DEBUG_SWITCHES.XML && BuildConfig.DEBUG) {
                             Logger.info(this, "EndAction" +
                                     "|localName=`" + context.localName + '`' +
                                     "|tag.name=`" + tag.name + '`');
                         }
                         try {
                             switch (tag.type) {
                                 case "Int":
                                     accessor.putInt(tag.name, Integer.parseInt(context.body));
                                     break;
                                 case "Long":
                                     accessor.putLong(tag.name, Long.parseLong(context.body));
                                     break;
                                 case "Flt":
                                     accessor.putFloat(tag.name, Float.parseFloat(context.body));
                                     break;
                                 case "Dbl":
                                     accessor.putDouble(tag.name, Double.parseDouble(context.body));
                                     break;
                                 case "Str":
                                     accessor.putString(tag.name, context.body);
                                     break;
                                 case "Bool":
                                     accessor.putBoolean(tag.name,
                                                         Boolean.parseBoolean(context.body));
                                     break;
                                 case "Serial":
                                     accessor.putSerializable(tag.name,
                                                              Base64.decode(context.body,
                                                                            Base64.DEFAULT));
                                     break;

                                 default:
                                     //noinspection ThrowCaughtLocally
                                     throw new RTE.IllegalTypeException(tag.type);
                             }

                         } catch (RuntimeException e) {
                             Logger.error(e);
                             throw new RuntimeException(
                                     UNABLE_TO_PROCESS_XML_ENTITY_ERROR + tag.name +
                                             " (" + tag.type + ')',
                                     e);
                         }
                     }
                 }, null);
    }

    @Override
    public void close()
            throws IOException {
        if (mDb != null) {
            mDb.close();
        }
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
         * @param id      row-id in the database, or 0 if not present
         * @param name    generic name of the element, or null if not present.
         */
        void startElement(final int version,
                          final long id,
                          @Nullable final String name);

        /**
         * Callback at the end of each element in the list.
         */
        void endElement();

        /**
         * Subtag of an element consisting of name/value pairs for each potentially supported type.
         */
        void putString(@NonNull final K key,
                       @NonNull final String value);

        void putBoolean(@NonNull final K key,
                        final boolean value);

        void putInt(@NonNull final K key,
                    final int value);

        void putLong(@NonNull final K key,
                     final long value);

        void putFloat(@NonNull final K key,
                      final float value);

        void putDouble(@NonNull final K key,
                       final double value);

        void putStringSet(@NonNull final K key,
                          @NonNull final Set<String> value);

        void putSerializable(@NonNull final K key,
                             @NonNull final Serializable value);
    }

    /**
     * Record to preserve data while parsing XML input.
     *
     * @author pjw
     */
    static class TagInfo {

        /** attribute with the key into the collection. */
        @NonNull
        String name;
        /**
         * - backward compatibility: the type attribute of a generic 'item' tag.
         * - current use: the type of the element as set by the tag itself.
         */
        @NonNull
        String type;

        /**
         * value attribute (e.g. int,boolean,...),
         * not used when the tag body is used (String,..).
         */
        @Nullable
        String value;
    }

    /**
     * Supports a *single* {@link XmlUtils#XML_INFO} block,
     * enclosed inside a {@link XmlUtils#XML_INFO_LIST}.
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
            return XmlUtils.XML_INFO_LIST;
        }

        @Override
        @NonNull
        public String getElementRoot() {
            return XmlUtils.XML_INFO;
        }

        @Override
        public void startElement(final int version,
                                 final long id,
                                 @Nullable final String name) {
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
            throw new RTE.IllegalTypeException("Collection<String>");
        }

        @Override
        public void putSerializable(@NonNull final String key,
                                    @NonNull final Serializable value) {
            mBundle.putSerializable(key, value);
        }
    }

    /**
     * Supports a *single* {@link XmlUtils#XML_PREFERENCES} block,
     * enclosed inside a {@link XmlUtils#XML_PREFERENCES_LIST}.
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
            return XmlUtils.XML_PREFERENCES_LIST;
        }

        @Override
        @NonNull
        public String getElementRoot() {
            return XmlUtils.XML_PREFERENCES;
        }

        @Override
        public void startElement(final int version,
                                 final long id,
                                 @Nullable final String name) {
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
            throw new RTE.IllegalTypeException(XmlUtils.XML_DOUBLE);
        }

        @Override
        public void putSerializable(@NonNull final String key,
                                    @NonNull final Serializable value) {
            throw new RTE.IllegalTypeException(XmlUtils.XML_SERIALIZABLE);
        }
    }

    /**
     * Supports a *list* of {@link XmlUtils#XML_STYLE} block,
     * enclosed inside a {@link XmlUtils#XML_STYLE_LIST}
     * <p>
     * See {@link XmlExporter} :
     * * Filters and Groups are flattened.
     * * - each filter has a tag
     * * - actual groups are written as a set of id's (kinds)
     * * - each preference in a group has a tag.
     */
    static class StylesReader
            implements EntityReader<String> {

        private final CatalogueDBAdapter mDb;

        private BooklistStyle style;
        // a collection of *ALL* Preferences (including from *all* groups)
        private Map<String, PPref> stylePrefs;

        StylesReader(@NonNull final CatalogueDBAdapter db) {
            mDb = db;
        }

        @Override
        @Nullable
        public String getListRoot() {
            return XmlUtils.XML_STYLE_LIST;
        }

        @NonNull
        @Override
        public String getElementRoot() {
            return XmlUtils.XML_STYLE;
        }

        /**
         * The start of a Style element.
         * <p>
         * Creates a new BooklistStyle, and sets it as the 'current' one ready for writes.
         *
         * @param version of the XML schema for this element, or 0 if not present
         * @param id      row-id in the database, or 0 if not present
         * @param name    the UUID for the Style
         */
        @NonNull
        public void startElement(final int version,
                                 final long id,
                                 @NonNull final String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException();
            }
            // create a new Style object. This will not have any groups assigned to it...
            style = new BooklistStyle(id, name);
            //... and hence, the Style Preferences won't have any group Preferences either.
            stylePrefs = style.getPreferences(true);
            // So loop all groups, and get their Preferences.
            // Do NOT add the group itself to the style at this point as our import
            // might not actually have it.
            for (BooklistGroup group : BooklistGroup.getAllGroups(style)) {
                stylePrefs.putAll(group.getPreferences());
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
            for (BooklistGroup group : style.getGroups()) {
                style.updatePreferences(group.getPreferences());
            }
            // add to the menu of preferred styles if needed.
            if (style.isPreferred()) {
                BooklistStyles.addPreferredStyle(style);
            }

            // the prefs are written on the fly, but we still need the db entry saved.
            style.save(mDb);
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            PBoolean p = (PBoolean) stylePrefs.get(key);
            p.set(value);
        }

        @Override
        public void putInt(@NonNull final String key,
                           final int value) {
            PInt p = (PInt) stylePrefs.get(key);
            p.set(value);
        }

        @Override
        public void putString(@NonNull final String key,
                              @NonNull final String value) {
            PString p = (PString) stylePrefs.get(key);
            p.set(value);
        }

        @Override
        public void putStringSet(@NonNull final String key,
                                 @NonNull final Set<String> value) {
            PCollection p = (PCollection) stylePrefs.get(key);
            p.set(value);
        }

        @Override
        public void putLong(@NonNull final String key,
                            final long value) {
            throw new RTE.IllegalTypeException(XmlUtils.XML_LONG);
        }

        @Override
        public void putFloat(@NonNull final String key,
                             final float value) {
            throw new RTE.IllegalTypeException(XmlUtils.XML_FLOAT);
        }

        @Override
        public void putDouble(@NonNull final String key,
                              final double value) {
            throw new RTE.IllegalTypeException(XmlUtils.XML_DOUBLE);
        }

        @Override
        public void putSerializable(@NonNull final String key,
                                    @NonNull final Serializable value) {
            throw new RTE.IllegalTypeException(XmlUtils.XML_SERIALIZABLE);
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
