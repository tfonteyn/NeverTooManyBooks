package com.eleybourn.bookcatalogue.backup.xml;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.backup.archivebase.ReaderEntity;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.prefs.PBoolean;
import com.eleybourn.bookcatalogue.booklist.prefs.PInt;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.booklist.prefs.PSet;
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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.eleybourn.bookcatalogue.backup.xml.XmlUtils.BUFFER_SIZE;
import static com.eleybourn.bookcatalogue.backup.xml.XmlUtils.UTF8;

/**
 * For now, only INFO, Preferences and Styles are implemented.
 */
public class XmlImporter
    implements Importer, Closeable {

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
    public void doEntity(@NonNull final ReaderEntity entity)
        throws IOException {
        final BufferedReader in = new XmlUtils.BufferedReaderNoClose(
            new InputStreamReader(entity.getStream(), UTF8), BUFFER_SIZE);

        switch (entity.getType()) {
            case BooklistStyles:
                if ((mSettings.what & ImportSettings.BOOK_LIST_STYLES) != 0) {
                    doStyles(in);
                }
        }
    }

    /**
     * Read the info block from an XML stream.
     */
    @NonNull
    public void doBackupInfoBlock(@NonNull final ReaderEntity entity,
                                  @NonNull final BackupInfo info)
        throws IOException {
        final BufferedReader in = new XmlUtils.BufferedReaderNoClose(
            new InputStreamReader(entity.getStream(), UTF8), BUFFER_SIZE);
        fromXml(in, new InfoReader(info));
    }

    /**
     * Read preferences from an XML stream into a given SharedPreferences object.
     */
    public void doPreferences(@NonNull final ReaderEntity entity,
                              @NonNull final SharedPreferences prefs)
        throws IOException {
        final BufferedReader in = new XmlUtils.BufferedReaderNoClose(
            new InputStreamReader(entity.getStream(), UTF8), BUFFER_SIZE);
        SharedPreferences.Editor editor = prefs.edit();
        fromXml(in, new PreferencesReader(editor));
        editor.apply();
    }

    /**
     * Read styles from an XML stream.
     */
    private void doStyles(@NonNull final BufferedReader in)
        throws IOException {
        List<BooklistStyle> styles = new ArrayList<>();
        // creating the styles takes care of writing their SharedPreference files
        fromXml(in, new StylesReader(styles));
        // but we do need to add them to the database.
        for (BooklistStyle style : styles) {
            style.save(mDb);
        }
    }

    /**
     * Internal routine to update the passed EntityAccessor from an XML file.
     */
    private void fromXml(@NonNull final BufferedReader in,
                         @NonNull final EntityReader<String> accessor)
        throws IOException {

        // we need an uber-root to hang our tree on
        XmlFilter rootFilter = new XmlFilter("");
        // a simple Holder for the current tag name and attributes
        final TagInfo tag = new TagInfo();

        /* used to read in Set data */
        final Set<String> currentStringSet = new HashSet<>();

        // backwards compatibility for pre-v200 Info and Preferences
        XmlFilter.buildFilter(rootFilter, "collection", "item")
                 .setStartAction(new XmlFilter.XmlHandler() {
                     @Override
                     public void process(@NonNull final XmlFilter.ElementContext context) {

                         tag.name = context.attributes.getValue("name");
                         tag.type = context.attributes.getValue("type");

                         Logger.info(this,
                                     "StartAction|localName=`" + context.localName + '`' +
                                         "|tag.name=`" + tag.name + '`');
                     }
                 }, null)
                 .setEndAction(new XmlFilter.XmlHandler() {
                     @Override
                     public void process(@NonNull final XmlFilter.ElementContext context) {
                         Logger.info(this,
                                     "EndAction|localName=`" + context.localName + '`' +
                                         "|tag.name=`" + tag.name + '`');
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
                                     accessor.putSerializable(tag.name, Base64.decode(context.body,
                                                                                      Base64.DEFAULT));
                                     break;

                                 default:
                                     //noinspection ThrowCaughtLocally
                                     throw new RTE.IllegalTypeException(tag.type);
                             }

                         } catch (RuntimeException e) {
                             Logger.error(e);
                             throw new RuntimeException(
                                 "Unable to process XML entity " + tag.name + " (" + tag.type + ')',
                                 e);
                         }
                     }
                 }, null);


        String listRootElement = accessor.getListRoot();
        String rootElement = accessor.getElementRoot();

        // start of a new element
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement)
                 .setStartAction(new XmlFilter.XmlHandler() {
                     @Override
                     public void process(@NonNull final XmlFilter.ElementContext context) {
                         String version = context.attributes.getValue(XmlUtils.ATTR_VERSION);
                         String id = context.attributes.getValue(XmlUtils.ATTR_ID);
                         String name = context.attributes.getValue(XmlUtils.ATTR_NAME);

                         Logger.info(this,
                                     "StartAction|NEW-ELEMENT|localName=`" + context.localName + '`' +
                                         "|tag.name=`" + name + '`');

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
        XmlFilter.XmlHandler startActionHandler = new XmlFilter.XmlHandler() {
            @Override
            public void process(@NonNull final XmlFilter.ElementContext context) {
                tag.type = context.localName;
                tag.name = context.attributes.getValue(XmlUtils.ATTR_NAME);
                tag.value = context.attributes.getValue(XmlUtils.ATTR_VALUE);
                Logger.info(this,
                            "StartAction|localName=`" + context.localName + '`' +
                                "|tag.name=`" + tag.name + '`');
            }
        };

        // the end of a generic (but typed) tag
        XmlFilter.XmlHandler endActionHandler = new XmlFilter.XmlHandler() {
            @Override
            public void process(@NonNull final XmlFilter.ElementContext context) {
                Logger.info(this,
                            "EndAction|localName=`" + context.localName + '`' +
                                "|tag.name=`" + tag.name + '`');

                try {
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
                        case XmlUtils.XML_SET:
                            accessor.putStringSet(tag.name, currentStringSet);
                            // cleanup, ready for the next Set
                            currentStringSet.clear();
                            break;
                        case XmlUtils.XML_SERIALIZABLE:
                            accessor.putSerializable(tag.name,
                                                     Base64.decode(context.body, Base64.DEFAULT));
                            break;
                    }
                } catch (RuntimeException e) {
                    Logger.error(e);
                    throw new RuntimeException(
                        "Unable to process XML entity " + tag.name + '(' + tag.type + ')', e);
                }
            }
        };


        // typed tag of an element
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_STRING)
                 .setStartAction(startActionHandler)
                 .setEndAction(endActionHandler);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_BOOLEAN)
                 .setStartAction(startActionHandler)
                 .setEndAction(endActionHandler);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_INT)
                 .setStartAction(startActionHandler)
                 .setEndAction(endActionHandler);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_LONG)
                 .setStartAction(startActionHandler)
                 .setEndAction(endActionHandler);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_FLOAT)
                 .setStartAction(startActionHandler)
                 .setEndAction(endActionHandler);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_DOUBLE)
                 .setStartAction(startActionHandler)
                 .setEndAction(endActionHandler);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_SERIALIZABLE)
                 .setStartAction(startActionHandler)
                 .setEndAction(endActionHandler);
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_SET)
                 .setStartAction(startActionHandler)
                 .setEndAction(endActionHandler);

        // a String in a Set, add the value as the new Set element
        XmlFilter.buildFilter(rootFilter, listRootElement, rootElement, XmlUtils.XML_SET,
                              XmlUtils.XML_STRING)
                 .setStartAction(new XmlFilter.XmlHandler() {
                     @Override
                     public void process(@NonNull final XmlFilter.ElementContext context) {
                         currentStringSet.add(XmlUtils.decode(context.attributes.getValue(XmlUtils.ATTR_VALUE)));
                     }
                 });

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
         * Callback at the start of each element in the list
         *
         * @param version of the XML schema for this element, or 0 if not present
         * @param id      row-id in the database, or 0 if not present
         * @param name    generic name of the element, or null if not present.
         */
        void startElement(final int version,
                          final long id,
                          @Nullable final String name);

        /**
         * Callback at the end of each element in the list
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
         * - backward compatibility: the type attribute of a generic 'item' tag
         * - current use: the type of the element as set by the tag itself.
         */
        @NonNull
        String type;

        /** value attribute (e.g. int,boolean,..., not used when the tag body is used (String,..) */
        @Nullable
        String value;
    }

    /**
     * Supports a *single* {@link XmlUtils#XML_INFO} block,
     * enclosed inside a {@link XmlUtils#XML_INFO_LIST}
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
            throw new RTE.IllegalTypeException("Set<String>");
        }

        @Override
        public void putSerializable(@NonNull final String key,
                                    @NonNull final Serializable value) {
            mBundle.putSerializable(key, value);
        }
    }

    /**
     * Supports a *single* {@link XmlUtils#XML_PREFERENCES} block,
     * enclosed inside a {@link XmlUtils#XML_PREFERENCES_LIST}
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
        public void putDouble(@NonNull final String key,
                              final double value) {
            throw new RTE.IllegalTypeException(XmlUtils.XML_DOUBLE);
        }

        @Override
        public void putStringSet(@NonNull final String key,
                                 @NonNull final Set<String> value) {
            mEditor.putStringSet(key, value);
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
     *
     * See {@link XmlExporter} :
     *      * Filters and Groups are flattened.
     *      * - each filter has a tag
     *      * - actual groups are written as a set of id's (kinds)
     *      * - each preference in a group has a tag.
     */
    static class StylesReader
        implements EntityReader<String> {

        private final Collection<BooklistStyle> styles;

        private BooklistStyle currentStyle;
        // a collection of *ALL* PPrefs (including from *all* groups)
        private Map<String, PPref> currentPPrefs;

        StylesReader(@NonNull final Collection<BooklistStyle> styles) {
            this.styles = styles;
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
         *
         * Creates a new BooklistStyle, and sets it as the 'current' one ready for writes.
         *
         * @param version of the XML schema for this element, or 0 if not present
         * @param id      row-id in the database, or 0 if not present
         * @param name    generic name of the element, or null if not present.
         */
        @NonNull
        public void startElement(final int version,
                                 final long id,
                                 @Nullable final String name) {
            // create a new group object. This will not have any groups assigned to it...
            currentStyle = new BooklistStyle(id, name);
            styles.add(currentStyle);
            //... and hence, the PPRefs won't have any group PPrefs either.
            currentPPrefs = currentStyle.getPPrefs();
            // So loop all groups, and get their PPrefs. Do NOT add the group itself to the style,
            // at this point as our import might not actually have it.
            for (BooklistGroup group : BooklistGroup.getAllGroups(currentStyle)) {
                currentPPrefs.putAll(group.getStylePPrefs());
            }
        }

        /**
         * The end of a Style element.
         * Update the groups PPrefs.
         */
        @Override
        public void endElement() {
            // we now have the groups themselves set on the style, so transfer their PPrefs.
            for (BooklistGroup group : currentStyle.getGroups()) {
                for (PPref dest : group.getStylePPrefs().values()) {
                    // did we read any prefs for this group ?
                    if (currentPPrefs.containsKey(dest.getKey())) {
                        //noinspection unchecked
                        dest.set(currentStyle.uuid, currentPPrefs.get(dest.getKey()));
                    }
                }
            }
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            PBoolean p = (PBoolean) currentPPrefs.get(key);
            p.set(currentStyle.uuid, value);
        }

        @Override
        public void putInt(@NonNull final String key,
                           final int value) {
            PInt p = (PInt) currentPPrefs.get(key);
            p.set(currentStyle.uuid, value);
        }

        @Override
        public void putString(@NonNull final String key,
                              @NonNull final String value) {
            PString p = (PString) currentPPrefs.get(key);
            p.set(currentStyle.uuid, value);
        }

        @Override
        public void putStringSet(@NonNull final String key,
                                 @NonNull final Set<String> value) {
            PSet p = (PSet) currentPPrefs.get(key);
            p.set(currentStyle.uuid, value);
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
}
