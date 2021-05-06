/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Styles;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInt;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PIntList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PString;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * <ul>Supports:
 *      <li>{@link RecordType#MetaData}</li>
 *      <li>{@link RecordType#Styles}</li>
 *      <li>{@link RecordType#Preferences}</li>
 * </ul>
 *
 * <strong>Important</strong>: The sax parser closes streams, which is not good
 * on a Tar archive entry. This class uses a {@link BufferedReaderNoClose} to get around that.
 *
 * @deprecated the main backup to a zip file is moving towards storing all text data in JSON
 * We're keeping this XML reader for a while longer so we're able to read older backups;
 * i.e. {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterAbstract} version 2.
 * See {@link com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderAbstract}
 * class docs for the version descriptions.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public class XmlRecordReader
        implements RecordReader {

    private static final String ERROR_UNABLE_TO_PROCESS_XML_RECORD = "Unable to process XML ";

    @Nullable
    private final Locale mUserLocale;

    /**
     * Stack for popping tags on if we go into one.
     * This is of course overkill, just to handle the list/set set,
     * but it's clean and future proof
     */
    private final Deque<TagInfo> mTagStack = new ArrayDeque<>();
    @NonNull
    private final Set<RecordType> mImportEntriesAllowed;
    /** a simple Holder for the current tag name and attributes. Pushed/pulled on the stack. */
    private TagInfo mCurrentTag;

    /**
     * Constructor.
     *
     * @param context              Current context
     * @param importEntriesAllowed the record types we're allowed to read
     */
    public XmlRecordReader(@NonNull final Context context,
                           @NonNull final Set<RecordType> importEntriesAllowed) {
        mImportEntriesAllowed = importEntriesAllowed;
        mUserLocale = context.getResources().getConfiguration().getLocales().get(0);
    }

    @Override
    @NonNull
    public ArchiveMetaData readMetaData(@NonNull final ArchiveReaderRecord record)
            throws ImportException, IOException {
        final ArchiveMetaData metaData = new ArchiveMetaData();
        fromXml(record, new InfoReader(metaData));
        return metaData;
    }

    @Override
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @NonNull final ImportHelper unused,
                              @NonNull final ProgressListener progressListener)
            throws ImportException, IOException {

        final ImportResults results = new ImportResults();

        if (record.getType().isPresent()) {
            final RecordType recordType = record.getType().get();

            if (mImportEntriesAllowed.contains(recordType)) {
                if (recordType == RecordType.Styles) {
                    final StylesReader stylesReader = new StylesReader(context);
                    fromXml(record, stylesReader);
                    results.styles += stylesReader.getStylesRead();

                } else if (recordType == RecordType.Preferences) {
                    final SharedPreferences.Editor ed = PreferenceManager
                            .getDefaultSharedPreferences(context).edit();
                    fromXml(record, new PreferencesReader(ed));
                    ed.apply();
                    results.preferences++;
                }
            }
        }
        return results;
    }

    /**
     * Internal routine to update the passed EntityAccessor from an XML file.
     *
     * @param record   source to read from
     * @param accessor the EntityReader to convert XML to the object
     *
     * @throws ImportException on a decoding/parsing of data issue
     * @throws IOException     on failure
     */
    private void fromXml(@NonNull final ArchiveReaderRecord record,
                         @NonNull final EntityReader<String> accessor)
            throws ImportException, IOException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final DefaultHandler handler = new XmlFilterHandler(buildFilters(accessor));

        try {
            // Don't close this stream
            final InputStream is = record.getInputStream();
            final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            // The sax parser closes streams, which is not good on a Tar archive entry.
            final BufferedReader reader = new BufferedReaderNoClose(isr, RecordReader.BUFFER_SIZE);
            final InputSource source = new InputSource(reader);

            final SAXParser parser = factory.newSAXParser();
            parser.parse(source, handler);

        } catch (@NonNull final ParserConfigurationException e) {
            throw new IllegalStateException(e);

        } catch (@NonNull final SAXException e) {
            // wrap parser exceptions in an ImportException /
            throw new ImportException(e);
        }
    }

    private XmlFilter buildFilters(@NonNull final EntityReader<String> accessor) {
        final String listRootElement = accessor.getRootTag();
        final String rootElement = accessor.getElementTag();

        // used to read in Set/List data
        final Collection<String> currentStringList = new ArrayList<>();

        final XmlFilter rootFilter = new XmlFilter();
        // A new element under the root
        rootFilter.addFilter(listRootElement, rootElement)
                  .setStartAction(elementContext -> {
                      // use as top-tag
                      mCurrentTag = new TagInfo(elementContext);
                      // we only have a version on the top tag, not on every tag.
                      final String version = elementContext.getAttributes()
                                                           .getValue(XmlUtils.ATTR_VERSION);
                      accessor.startElement(version == null ? 0 : Integer.parseInt(version),
                                            mCurrentTag);
                  })
                  .setEndAction(elementContext -> accessor.endElement());

        // typed tag starts. for both attribute and body based elements.
        final Consumer<ElementContext> startTypedTag = elementContext -> {
            mTagStack.push(mCurrentTag);
            mCurrentTag = new TagInfo(elementContext);

            // if we have a value attribute, this tag is done. Handle here.
            if (mCurrentTag.value != null) {
                switch (mCurrentTag.type) {
                    case XmlUtils.TAG_STRING:
                        // attribute Strings are encoded.
                        accessor.putString(mCurrentTag.name, XmlUtils.decode(mCurrentTag.value));
                        break;

                    case XmlUtils.TAG_BOOLEAN:
                        accessor.putBoolean(mCurrentTag.name, Boolean.parseBoolean(
                                mCurrentTag.value));
                        break;

                    case XmlUtils.TAG_INT:
                        accessor.putInt(mCurrentTag.name, Integer.parseInt(mCurrentTag.value));
                        break;

                    case XmlUtils.TAG_LONG:
                        accessor.putLong(mCurrentTag.name, Long.parseLong(mCurrentTag.value));
                        break;

                    case XmlUtils.TAG_FLOAT:
                        accessor.putFloat(mCurrentTag.name, ParseUtils.parseFloat(
                                mCurrentTag.value, mUserLocale));
                        break;

                    case XmlUtils.TAG_DOUBLE:
                        accessor.putDouble(mCurrentTag.name, ParseUtils.parseDouble(
                                mCurrentTag.value, mUserLocale));
                        break;

                    default:
                        break;
                }
                mCurrentTag = mTagStack.pop();
            }
        };

        // the end of a typed tag with a body
        final Consumer<ElementContext> endTypedTag = elementContext -> {
            try {
                switch (mCurrentTag.type) {
                    case XmlUtils.TAG_STRING:
                        // body Strings use CDATA
                        accessor.putString(mCurrentTag.name, elementContext.getBody());
                        break;

                    case XmlUtils.TAG_SET:
                        accessor.putStringSet(mCurrentTag.name, currentStringList);
                        // cleanup, ready for the next Set
                        currentStringList.clear();
                        break;

                    case XmlUtils.TAG_LIST:
                        accessor.putStringList(mCurrentTag.name, currentStringList);
                        // cleanup, ready for the next List
                        currentStringList.clear();
                        break;

                    case XmlUtils.TAG_SERIALIZABLE:
                        accessor.putSerializable(mCurrentTag.name,
                                                 Base64.decode(elementContext.getBody(),
                                                               Base64.DEFAULT));
                        break;

                    default:
                        break;
                }

                mCurrentTag = mTagStack.pop();

            } catch (@NonNull final RuntimeException e) {
                throw new RuntimeException(ERROR_UNABLE_TO_PROCESS_XML_RECORD + mCurrentTag.name
                                           + '(' + mCurrentTag.type + ')', e);
            }
        };

        // typed tags that only use a value attribute only need action on the start of a tag
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_BOOLEAN)
                  .setStartAction(startTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_INT)
                  .setStartAction(startTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_LONG)
                  .setStartAction(startTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_FLOAT)
                  .setStartAction(startTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_DOUBLE)
                  .setStartAction(startTypedTag);

        // typed tags that have bodies.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_STRING)
                  .setStartAction(startTypedTag)
                  .setEndAction(endTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_SET)
                  .setStartAction(startTypedTag)
                  .setEndAction(endTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_LIST)
                  .setStartAction(startTypedTag)
                  .setEndAction(endTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_SERIALIZABLE)
                  .setStartAction(startTypedTag)
                  .setEndAction(endTypedTag);

        /*
         * The RecordWriter is generating List/Set tags with String/Int sub tags properly,
         * but importing an Element in a Collection is always done as a String in a List (for now?)
         */
        // set/list elements with attributes.
        final Consumer<ElementContext> startElementInCollection = elementContext -> {
            mTagStack.push(mCurrentTag);
            mCurrentTag = new TagInfo(elementContext);

            // if we have a value attribute, this tag is done. Handle here.
            if (mCurrentTag.value != null) {
                // yes, switch is silly here. But let's keep it generic and above all, clear!
                switch (mCurrentTag.type) {
                    case XmlUtils.TAG_BOOLEAN:
                    case XmlUtils.TAG_INT:
                    case XmlUtils.TAG_LONG:
                    case XmlUtils.TAG_FLOAT:
                    case XmlUtils.TAG_DOUBLE:
                        currentStringList.add(mCurrentTag.value);
                        break;

                    default:
                        break;
                }

                mCurrentTag = mTagStack.pop();
            }
        };

        // set/list elements with bodies.
        final Consumer<ElementContext> endElementInCollection = elementContext -> {
            // handle tags with bodies.
            try {
                // yes, switch is silly here. But let's keep it generic and above all, clear!
                switch (mCurrentTag.type) {
                    // No support for list/set inside a list/set (no point)
                    case XmlUtils.TAG_SERIALIZABLE:
                        // serializable is indeed just added as a string...
                        // this 'case' is only here for completeness sake.
                    case XmlUtils.TAG_STRING:
                        // body strings use CDATA
                        currentStringList.add(elementContext.getBody());
                        break;

                    default:
                        break;
                }

                mCurrentTag = mTagStack.pop();

            } catch (@NonNull final RuntimeException e) {
                throw new RuntimeException(ERROR_UNABLE_TO_PROCESS_XML_RECORD + mCurrentTag, e);
            }
        };


        // Set<String>. The String's are body based.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_SET, XmlUtils.TAG_STRING)
                  .setStartAction(startElementInCollection)
                  .setEndAction(endElementInCollection);
        // List<String>. The String's are body based.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_LIST, XmlUtils.TAG_STRING)
                  .setStartAction(startElementInCollection)
                  .setEndAction(endElementInCollection);

        // Set<Integer>. The ints are attribute based.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_SET, XmlUtils.TAG_INT)
                  .setStartAction(startElementInCollection);
        // List<Integer>. The ints are attribute based.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_LIST, XmlUtils.TAG_INT)
                  .setStartAction(startElementInCollection);

        return rootFilter;
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
        String getRootTag();

        /**
         * @return the tag name for an element in the list
         */
        @NonNull
        String getElementTag();

        /**
         * Callback at the start of each element in the list.
         *
         * @param version of the XML schema for this element, or 0 if not present
         * @param tag     the info about the top tag
         */
        default void startElement(final int version,
                                  @NonNull final TagInfo tag) {

        }

        /**
         * Callback at the end of each element in the list.
         */
        default void endElement() {

        }

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
            throw new IllegalArgumentException("Float, key=" + key);
        }

        default void putLong(@NonNull final K key,
                             final long value) {
            throw new IllegalArgumentException("Long, key=" + key);
        }

        default void putDouble(@NonNull final K key,
                               final double value) {
            throw new IllegalArgumentException("Double, key=" + key);
        }

        /**
         * Note the values can be passed in as a List or a Set,
         * but store should be a Set of some type.
         */
        default void putStringSet(@NonNull final K key,
                                  @NonNull final Collection<String> value) {
            throw new IllegalArgumentException("StringSet, key=" + key);
        }

        /**
         * Note the values can be passed in as a List or a Set,
         * but store should be a List of some type.
         */
        default void putStringList(@NonNull final K key,
                                   @NonNull final Collection<String> value) {
            throw new IllegalArgumentException("StringList, key=" + key);
        }

        default void putSerializable(@NonNull final K key,
                                     @NonNull final Serializable value) {
            throw new IllegalArgumentException("Serializable, key=" + key);
        }
    }

    /**
     * Value class to preserve data while parsing XML input.
     */
    public static class TagInfo {

        /**
         * attribute with the key into the collection.
         * For convenience, also part of {@link #attrs}.
         */
        @NonNull
        public final String name;
        /**
         * optional. {@code 0} if none.
         * For convenience, also part of {@link #attrs}.
         */
        public final int id;
        /** The type of the element as set by the tag itself. */
        @NonNull
        final String type;
        /** All attributes on this tag. */
        final Attributes attrs;
        /**
         * optional value attribute (e.g. int,boolean,...),
         * not used when the tag body is used (String,..).
         * <p>
         * <p>
         * For convenience, also part of {@link #attrs}.
         */
        @Nullable
        final String value;

        /**
         * Constructor.
         *
         * @param elementContext of the XML tag
         */
        TagInfo(@NonNull final ElementContext elementContext) {
            type = elementContext.getLocalName();
            attrs = elementContext.getAttributes();

            name = attrs.getValue(XmlUtils.ATTR_NAME);
            value = attrs.getValue(XmlUtils.ATTR_VALUE);

            int tmpId = 0;
            final String idStr = attrs.getValue(XmlUtils.ATTR_ID);
            if (idStr != null) {
                try {
                    tmpId = Integer.parseInt(idStr);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
            id = tmpId;
        }

        @Override
        @NonNull
        public String toString() {
            return "TagInfo{"
                   + "name=`" + name + '`'
                   + ", attrs=" + attrs
                   + ", type=`" + type + '`'
                   + ", id=" + id
                   + ", value=`" + value + '`'
                   + '}';
        }
    }

    /**
     * Supports a *single* {@link RecordType#MetaData} block,
     * enclosed inside a {@link InfoReader#TAG_ROOT}.
     */
    static class InfoReader
            implements EntityReader<String> {

        static final String TAG_ROOT = "info-list";
        @NonNull
        private final Bundle mBundle;

        InfoReader(@NonNull final ArchiveMetaData info) {
            mBundle = info.getBundle();
        }

        @Override
        @NonNull
        public String getRootTag() {
            return TAG_ROOT;
        }

        @Override
        @NonNull
        public String getElementTag() {
            return RecordType.MetaData.getName();
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
     * Supports a *single* {@link RecordType#Preferences} block,
     * enclosed inside a {@link PreferencesReader#TAG_ROOT}.
     */
    public static class PreferencesReader
            implements EntityReader<String> {

        static final String TAG_ROOT = "preferences-list";

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
        public String getRootTag() {
            return TAG_ROOT;
        }

        @Override
        @NonNull
        public String getElementTag() {
            return RecordType.Preferences.getName();
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
                                 @NonNull final Collection<String> value) {
            mEditor.putStringSet(key, new HashSet<>(value));
        }

        @Override
        public void putStringList(@NonNull final String key,
                                  @NonNull final Collection<String> value) {
            mEditor.putString(key, TextUtils.join(",", value));
        }
    }

    /**
     * Supports a *list* of {@link StylesReader#TAG_ELEMENT} block,
     * enclosed inside a {@link RecordType#Styles}
     * <p>
     * See {@link XmlRecordWriter} :
     * * Filters and Groups are flattened.
     * * - each filter has a tag
     * * - actual groups are written as a set of ID's
     * * - each preference in a group has a tag.
     */
    public static class StylesReader
            implements EntityReader<String> {

        static final String TAG_ROOT = "style-list";
        static final String TAG_ELEMENT = "style";

        @NonNull
        private final Context mContext;

        @NonNull
        private final Styles mStyles;

        private ListStyle mStyle;

        /**
         * A collection of all Preferences (including the preferences from *all* groups).
         * Reminder: the map mStylePrefs is NOT a part of the style,
         * but the elements IN this map ARE.
         */
        @Nullable
        private Map<String, PPref<?>> mStylePrefs;

        /** A counter to track how many styles we have read. */
        private int mStylesRead;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        StylesReader(@NonNull final Context context) {
            mContext = context;
            mStyles = ServiceLocator.getInstance().getStyles();
        }

        int getStylesRead() {
            return mStylesRead;
        }

        @Override
        @NonNull
        public String getRootTag() {
            return TAG_ROOT;
        }

        @NonNull
        @Override
        public String getElementTag() {
            return TAG_ELEMENT;
        }

        /**
         * The start of a Style element.
         * <p>
         * Creates a new {@link ListStyle}, and sets it as the 'current' one ready for writes.
         *
         * <br><br>{@inheritDoc}
         */
        @Override
        public void startElement(final int version,
                                 @NonNull final TagInfo tag) {

            String uuid = tag.attrs.getValue(DBKey.KEY_STYLE_UUID);
            if (uuid == null) {
                // backwards compatibility
                uuid = tag.name;
            }

            if (BuiltinStyle.isBuiltin(uuid)) {
                //noinspection ConstantConditions
                mStyle = mStyles.getStyle(mContext, uuid);
                // We do NOT read preferences for known builtin styles
                mStylePrefs = null;

            } else {
                mStyle = UserStyle.createFromImport(mContext, uuid);
                ((UserStyle) mStyle).setName(tag.name);

                // This will not have any groups assigned to it...
                //... and hence, the Style Preferences won't have any group Preferences either.
                // Reminder: the map mStylePrefs is a TEMPORARY map,
                // but the elements IN this map ARE PART OF THE STYLE.
                mStylePrefs = mStyle.getRawPreferences();
                // So loop all groups, and get their Preferences.
                // Do NOT add the group itself to the style at this point as our import
                // might not actually have it.
                for (final BooklistGroup group : BooklistGroup.getAllGroups(mStyle)) {
                    mStylePrefs.putAll(group.getRawPreferences());
                }
            }

            // not bothering with slightly older backups where the 'preferred' flag was a PPref
            boolean isPreferred;
            try {
                isPreferred = ParseUtils.parseBoolean(tag.attrs.getValue(
                        DBKey.BOOL_STYLE_IS_PREFERRED), true);
            } catch (@NonNull final NumberFormatException ignore) {
                isPreferred = false;
            }

            int menuPosition;
            try {
                menuPosition = Integer.parseInt(tag.attrs.getValue(
                        DBKey.KEY_STYLE_MENU_POSITION));
            } catch (@NonNull final NumberFormatException ignore) {
                menuPosition = ListStyle.MENU_POSITION_NOT_PREFERRED;
            }

            mStyle.setPreferred(isPreferred);
            mStyle.setMenuPosition(menuPosition);
        }

        @Override
        public void endElement() {
            if (mStyle instanceof UserStyle) {
                // we now have the groups themselves (one of the 'flat' prefs) set on the style,
                // so transfer their specific Preferences.
                for (final BooklistGroup group : mStyle.getGroups().getGroupList()) {
                    for (final PPref<?> dest : group.getRawPreferences().values()) {
                        // do we have this Preference in the imported data?
                        //noinspection ConstantConditions
                        final PPref<?> source = mStylePrefs.get(dest.getKey());
                        if (source != null) {
                            if (dest instanceof PInt) {
                                ((PInt) dest).set(((PInt) source).getValue());

                            } else if (dest instanceof PBoolean) {
                                ((PBoolean) dest).set(((PBoolean) source).getValue());

                            } else if (dest instanceof PString) {
                                ((PString) dest).set(((PString) source).getValue());

                            } else if (dest instanceof PIntList) {
                                ((PIntList) dest).set(((PIntList) source).getValue());
                            }
                        }
                    }
                }
            }

            mStyles.updateOrInsert(mStyle);

            mStylesRead++;
        }

        @Override
        public void putString(@NonNull final String key,
                              @NonNull final String value) {
            if (mStylePrefs != null) {
                if (UserStyle.PK_STYLE_NAME.equals(key) && mStyle instanceof UserStyle) {
                    // backwards compatibility
                    ((UserStyle) mStyle).setName(value);
                } else {
                    final PPref<String> p = (PString) mStylePrefs.get(key);
                    if (p != null) {
                        p.set(value);
                    }
                }
            }
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            if (mStylePrefs != null) {
                final PPref<Boolean> p = (PBoolean) mStylePrefs.get(key);
                if (p != null) {
                    p.set(value);
                }
            }
        }

        @Override
        public void putInt(@NonNull final String key,
                           final int value) {
            if (mStylePrefs != null) {
                final PInt p = (PInt) mStylePrefs.get(key);
                if (p != null) {
                    p.set(value);
                }
            }
        }

        @Override
        public void putStringSet(@NonNull final String key,
                                 @NonNull final Collection<String> value) {
            if (mStylePrefs != null) {
                final PIntList p = (PIntList) mStylePrefs.get(key);
                if (p != null) {
                    p.setStringCollection(value);
                }
            }
        }

        @Override
        public void putStringList(@NonNull final String key,
                                  @NonNull final Collection<String> value) {
            if (mStylePrefs != null) {
                final PIntList p = (PIntList) mStylePrefs.get(key);
                if (p != null) {
                    p.setStringCollection(value);
                }
            }
        }
    }

    /**
     * The sax parser closes streams, which is not good on a Tar archive entry.
     */
    private static class BufferedReaderNoClose
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
