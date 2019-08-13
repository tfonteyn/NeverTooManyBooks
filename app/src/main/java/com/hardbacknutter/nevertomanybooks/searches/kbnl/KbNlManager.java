/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.searches.kbnl;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertomanybooks.utils.ISBN;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 */
public class KbNlManager
        implements SearchEngine {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "KBNL.";

    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";
    /** RELEASE: Chrome 2019-08-12. Update to latest version. */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                                             + " AppleWebKit/537.36 (KHTML, like Gecko)"
                                             + " Chrome/76.0.3809.100 Safari/537.36";

    public KbNlManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return SearchEngine.getPref().getString(PREFS_HOST_URL, "http://opc4.kb.nl");
    }

    /**
     * ENHANCE: implement non-isbn searches.
     */
    @NonNull
    @Override
    public Bundle search(@Nullable final String isbn,
                         @Nullable final /* not supported */ String author,
                         @Nullable final /* not supported */ String title,
                         @Nullable final /* not supported */ String publisher,
                         final boolean fetchThumbnail)
            throws IOException {

        if (isbn == null || isbn.isEmpty() || !ISBN.isValid(isbn)) {
            return new Bundle();
        }

        String url;
        // Response with English labels.
//      url = getBaseURL() + "/DB=1/SET=1/TTL=1/LNG=EN/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=" + isbn;
        // Response with Dutch labels.
        url = getBaseURL() + "/DB=1/SET=1/TTL=1/LNG=NE/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=" + isbn;

        Bundle bookData = new Bundle();

        SAXParserFactory factory = SAXParserFactory.newInstance();

        DefaultHandler handler = new KbNlHandler(bookData);

        try (TerminatorConnection terminatorConnection = new TerminatorConnection(url)) {
            HttpURLConnection con = terminatorConnection.getHttpURLConnection();

            // needed so we get the XML
            con.setInstanceFollowRedirects(false);
            // the site does not return full data unless this is set to a valid browser.
            con.setRequestProperty("User-Agent", USER_AGENT);
            terminatorConnection.open();

            SAXParser parser = factory.newSAXParser();
            parser.parse(terminatorConnection.inputStream, handler);

            // wrap parser exceptions in an IOException
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e, url);
            }
            throw new IOException(e);
        }

        return bookData;
    }

    /**
     * Site does not have images.
     *
     * @param isbn to search for
     * @param size of image to get.
     *
     * @return {@code null}
     */
    @Nullable
    @Override
    public File getCoverImage(@NonNull final String isbn,
                              @Nullable final ImageSize size) {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isIsbnOnly() {
        return true;
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.KBNL;
    }
}
