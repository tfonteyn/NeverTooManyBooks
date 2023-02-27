/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.EOFException;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.MoneyParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertTrue;


class IsfdbXmlPublicationTest
        extends Base {

    private static final String TAG = "IsfdbXmlPublicationTest";

    private IsfdbSearchEngine searchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        searchEngine = (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        // Override the default 'false'
        preferences.edit().putBoolean(IsfdbSearchEngine.PK_SERIES_FROM_TOC, true).apply();
        final boolean b = preferences.getBoolean(IsfdbSearchEngine.PK_SERIES_FROM_TOC, false);
        assertTrue(b);
    }

    @Test
    void singleByExtId()
            throws ParserConfigurationException, IOException, SAXException {
        setLocale(searchEngine.getLocale(context));
        final String filename = "/isfdb/425189.xml";

        final RealNumberParser realNumberParser = new RealNumberParser(locales);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);

        final IsfdbPublicationListHandler listHandler =
                new IsfdbPublicationListHandler(searchEngine,
                                                new boolean[]{false, false},
                                                1, moneyParser);

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final SAXParser parser = factory.newSAXParser();
        try {
            parser.parse(this.getClass().getResourceAsStream(filename), listHandler);
        } catch (@NonNull final SAXException e) {
            if (!(e.getCause() instanceof EOFException)) {
                throw e;
            }
        }

        System.out.println(listHandler.getResult());
    }
}
