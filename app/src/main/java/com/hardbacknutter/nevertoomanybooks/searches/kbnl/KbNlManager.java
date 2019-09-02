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
package com.hardbacknutter.nevertoomanybooks.searches.kbnl;

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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

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

    /**
     * RELEASE: Chrome 2019-08-12. Update to latest version.
     * The site does not return full data unless the user agent header is set to a valid browser.
     */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                                             + " AppleWebKit/537.36 (KHTML, like Gecko)"
                                             + " Chrome/76.0.3809.100 Safari/537.36";

    /**
     * <strong>Note:</strong> This is not the same site as the search site itself.
     * We have no indication that this site has an image we want, we just try it.
     * <p>
     * param 1: isbn, param 2: size.
     */
    private static final String BASE_URL_COVERS =
            "https://webservices.bibliotheek.be/index.php?func=cover&ISBN=%1$s&coversize=%2$s";

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_KB";

    /**
     * Response with Dutch labels.
     * <p>
     * param 1: isb
     */
    private static final String BOOK_URL =
            "/DB=1/SET=1/TTL=1/LNG=NE/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=%1$s";
    /* Response with English labels. */
    //private static final String BOOK_URL =
    //      "/DB=1/SET=1/TTL=1/LNG=EN/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=%1$s";

    /**
     * param 1: site specific author id
     */
    private static final String AUTHOR_URL = "http://opc4.kb.nl/DB=1/SET=1/TTL=1/REL?PPN=%1$s";

    public KbNlManager() {
    }

    @NonNull
    public static String getBaseURL() {
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

        String url = getBaseURL() + String.format(BOOK_URL, isbn);

        Bundle bookData = new Bundle();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        KbNlBookHandler handler = new KbNlBookHandler(bookData);

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

        if (fetchThumbnail) {
            getCoverImage(isbn, bookData);
        }
        return bookData;
    }

    /**
     * Ths kb.nl site does not have images, but we try bibliotheek.be.
     * <p>
     * https://webservices.bibliotheek.be/index.php?func=cover&ISBN=9789463731454&coversize=large
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
        // sanity check
        if (!ISBN.isValid(isbn)) {
            return null;
        }

        String sizeParam;
        if (size == null) {
            sizeParam = "large";
        } else {
            switch (size) {
                case Small:
                    sizeParam = "small";
                    break;
                case Medium:
                    sizeParam = "medium";
                    break;
                case Large:
                default:
                    sizeParam = "large";
                    break;
            }
        }

        String url = String.format(BASE_URL_COVERS, isbn, sizeParam);
        String fileSpec = ImageUtils.saveImage(url, isbn, FILENAME_SUFFIX + "_" + sizeParam);
        if (fileSpec != null) {
            return new File(fileSpec);
        }
        return null;
    }

    @Override
    public boolean siteSupportsMultipleSizes() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
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
