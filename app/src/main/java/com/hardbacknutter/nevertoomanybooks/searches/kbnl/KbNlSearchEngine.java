/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 * <p>
 * 2020-01-04: "http://opc4.kb.nl" is not available on https.
 * see "res/xml/network_security_config.xml"
 */
public class KbNlSearchEngine
        implements SearchEngine,
                   SearchEngine.ByIsbn,
                   SearchEngine.CoverByIsbn {

    private static final Locale SITE_LOCALE = new Locale("nl", "NL");

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "kbnl.";
    /** Type: {@code String}. */
    @VisibleForTesting
    public static final String PREFS_HOST_URL = PREF_PREFIX + "host.url";

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
    /* param 1: site specific author id. */
//    private static final String AUTHOR_URL = getBaseURL(context)
//    + "/DB=1/SET=1/TTL=1/REL?PPN=%1$s";

    @NonNull
    public static String getBaseURL(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(PREFS_HOST_URL, "http://opc4.kb.nl");
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        return SITE_LOCALE;
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        String url = getBaseURL(context) + String.format(BOOK_URL, validIsbn);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        KbNlBookHandler handler = new KbNlBookHandler(new Bundle());

        try (TerminatorConnection terminatorConnection =
                     new TerminatorConnection(context, url)) {
            HttpURLConnection con = terminatorConnection.getHttpURLConnection();

            // needed so we get the XML instead of the rendered page
            con.setInstanceFollowRedirects(false);
            terminatorConnection.open();

            SAXParser parser = factory.newSAXParser();
            parser.parse(terminatorConnection.getInputStream(), handler);

            // wrap parser exceptions in an IOException
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            throw new IOException(e);
        }

        Bundle bookData = handler.getResult();
        if (fetchThumbnail[0]) {
            getCoverImage(context, validIsbn, 0, bookData);
        }
        return bookData;
    }

    /**
     * Ths kb.nl site does not have images, but we try bibliotheek.be.
     * <p>
     * https://webservices.bibliotheek.be/index.php?func=cover&ISBN=9789463731454&coversize=large
     *
     * <br><br>{@inheritDoc}
     */
    @Nullable
    @Override
    public String getCoverImage(@NonNull final Context context,
                                @NonNull final String isbn,
                                @IntRange(from = 0) final int cIdx,
                                @Nullable final ImageSize size) {
        String sizeSuffix;
        if (size == null) {
            sizeSuffix = "large";
        } else {
            switch (size) {
                case Small:
                    sizeSuffix = "small";
                    break;
                case Medium:
                    sizeSuffix = "medium";
                    break;
                case Large:
                default:
                    sizeSuffix = "large";
                    break;
            }
        }

        // ignore cIdx, site has only one image.
        String url = String.format(BASE_URL_COVERS, isbn, sizeSuffix);
        String name = isbn + FILENAME_SUFFIX + "_" + sizeSuffix;
        return ImageUtils.saveImage(context, url, name, null);
    }

    @Override
    public boolean supportsMultipleSizes() {
        return true;
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context context) {
        return getBaseURL(context);
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.site_KBNL;
    }
}
