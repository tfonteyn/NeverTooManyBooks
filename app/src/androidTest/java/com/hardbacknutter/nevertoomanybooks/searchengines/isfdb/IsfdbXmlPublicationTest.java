/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertTrue;

/**
 * ENHANCE: this is experimental code. Parsing works, but reporting EOF is dodgy
 */
@SuppressWarnings("MissingJavadoc")
public class IsfdbXmlPublicationTest
        extends BaseDBTest {

    private static final String TAG = "IsfdbXmlPublicationTest";

    private IsfdbSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

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
    public void singleByExtId()
            throws ParserConfigurationException, IOException, SAXException {
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_425189;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final IsfdbPublicationListHandler listHandler =
                new IsfdbPublicationListHandler(context, searchEngine,
                                                new boolean[]{false, false}, 1);

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final SAXParser parser = factory.newSAXParser();
        // getContext(): we want the "androidTest" context which is where our test resources live
        try (InputStream is = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId)) {
            // The ISFDB site returns xml with the encoding "iso-8859-1"
            // i.e. with <?xml version="1.0" encoding="iso-8859-1" ?>
            // but for Android seems to ignore this and defaults to UTF-8
            // So wrap in InputSource and manually set the encoding.
            final InputSource inputSource = new InputSource(is);
            inputSource.setEncoding("iso-8859-1");

            // The parser is EXPECTED to throw a new SAXException(new EOFException())
            // when it is done.
            // For a reason not understood, this test will always throw it,
            // instead of letting the catch swallow the EOFException and we get
            // E TestRunner: failed: singleByExtId(com.hardbacknutter.nevertoomanybooks
            //                       .searchengines.isfdb.IsfdbXmlPublicationTest)
            parser.parse(inputSource, listHandler);

        } catch (@NonNull final SAXException | EOFException e) {
            if (!(e.getCause() instanceof EOFException)) {
                throw e;
            }
        }

        Log.d(TAG, listHandler.getResult().toString());
    }
}
