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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;

/**
 * Implementation of XML-specific reader functions.
 */
public class XmlArchiveReader
        extends ArchiveReaderAbstract {

    /**
     * The data stream for the archive.
     * Do <strong>NOT</strong> use this directly, see {@link #getXmlPullParser()}.
     */
    @Nullable
    private XmlPullParser mXmlPullParser;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    public XmlArchiveReader(@NonNull final Context context,
                            @NonNull final ImportHelper helper) {
        super(context, helper);
    }

    @Nullable
    @Override
    protected ArchiveReaderRecord seek(@NonNull final ArchiveReaderRecord.Type type)
            throws InvalidArchiveException, IOException {

        final XmlPullParser parser = getXmlPullParser();
        try {
            int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && type == tagToType(parser.getName())) {
                    return new XmlArchiveRecord(type, parser.getName(), getInnerXml(parser));
                }
                eventType = parser.next();
            }
        } catch (@NonNull final XmlPullParserException e) {
            throw new InvalidArchiveException(e);
        }

        return null;
    }

    @Nullable
    @Override
    protected ArchiveReaderRecord next()
            throws InvalidArchiveException, IOException {

        final XmlPullParser parser = getXmlPullParser();
        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    final ArchiveReaderRecord.Type type = tagToType(parser.getName());
                    if (type != null) {
                        return new XmlArchiveRecord(type, parser.getName(), getInnerXml(parser));
                    }
                    return null;
                }
                eventType = parser.next();
            }
        } catch (@NonNull final XmlPullParserException e) {
            throw new InvalidArchiveException(e);
        }

        return null;
    }

    @NonNull
    private XmlPullParser getXmlPullParser()
            throws IOException {
        if (mXmlPullParser == null) {
            try {
                mXmlPullParser = XmlPullParserFactory.newInstance().newPullParser();
                mXmlPullParser.setInput(openInputStream(), null);

            } catch (@NonNull final XmlPullParserException e) {
                throw new IllegalStateException(e);
            }
        }

        return mXmlPullParser;
    }

    @Override
    protected void closeInputStream() {
        mXmlPullParser = null;
    }


    @Nullable
    private ArchiveReaderRecord.Type tagToType(final String tagName) {
        switch (tagName) {
            case XmlTags.TAG_INFO_LIST:
                return ArchiveReaderRecord.Type.InfoHeader;
            case XmlTags.TAG_STYLE_LIST:
                return ArchiveReaderRecord.Type.Styles;
            case XmlTags.TAG_PREFERENCES_LIST:
                return ArchiveReaderRecord.Type.Preferences;
            case XmlTags.TAG_BOOK_LIST:
                return ArchiveReaderRecord.Type.Books;
            default:
                return null;
        }
    }

    // Awkward code, but it seems this is the only thing we can do to get the string as-is
    private String getInnerXml(@NonNull final XmlPullParser parser)
            throws XmlPullParserException, IOException {

        final String rootTag = parser.getName();
        final StringBuilder rootAttrs = new StringBuilder();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            rootAttrs.append(parser.getAttributeName(i)).append("=\"")
                     .append(parser.getAttributeValue(i)).append("\" ");
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("<").append(rootTag).append(" ").append(rootAttrs).append(">");

        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG: {
                    depth--;
                    if (depth > 0) {
                        sb.append("</").append(parser.getName()).append(">");
                    }
                    break;
                }
                case XmlPullParser.START_TAG: {
                    depth++;
                    final StringBuilder attrs = new StringBuilder();
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        attrs.append(parser.getAttributeName(i)).append("=\"")
                             .append(parser.getAttributeValue(i)).append("\" ");
                    }
                    sb.append("<").append(parser.getName()).append(" ").append(attrs).append(">");
                    break;
                }
                default:
                    sb.append(parser.getText());
                    break;
            }
        }

        sb.append("</").append(rootTag).append(">");
        return sb.toString();
    }

    private static class XmlArchiveRecord
            implements ArchiveReaderRecord {

        @NonNull
        private final Type mType;
        @NonNull
        private final String mName;
        @NonNull
        private final String mTagContent;

        /**
         * Constructor.
         */
        XmlArchiveRecord(@NonNull final Type type,
                         @NonNull final String name,
                         @NonNull final String tagContent) {
            mType = type;
            mName = name;
            mTagContent = tagContent;
        }

        @NonNull
        @Override
        public Type getType() {
            return mType;
        }

        @NonNull
        @Override
        public Encoding getEncoding() {
            return Encoding.Xml;
        }

        @NonNull
        @Override
        public String getName() {
            return mName;
        }

        @Override
        public long getLastModifiedEpochMilli() {
            // just pretend
            return Instant.now().toEpochMilli();
        }

        @NonNull
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(mTagContent.getBytes(StandardCharsets.UTF_8));
        }
    }
}
