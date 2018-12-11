package com.eleybourn.bookcatalogue.backup.xml;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.csv.Exporter;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

/**
 * Not using {@link com.eleybourn.bookcatalogue.backup.BackupUtils} xml methods are they are one-dimensional.
 *
 * Yes, I thought about using javax.xml.parsers.DocumentBuilderFactory
 * but face it.. overkill and (could) use a lot of memory.
 */
public class XmlExporter implements Exporter {

    private static final int XML_EXPORTER_VERSION = 1;
    private static final int XML_EXPORTER_BOOKSHELVES_VERSION = 1;
    private static final int XML_EXPORTER_AUTHORS_VERSION = 1;
    private static final int XML_EXPORTER_SERIES_VERSION = 1;

    private static final String UTF8 = "utf8";
    private static final int BUFFER_SIZE = 32768;

    // root element, used to recognise 'our' files during import.
    private static final String XML_ROOT = "bc";

    private static final String ATTR_VERSION = "version";
    private static final String ATTR_ID = "id";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_IS_COMPLETE = "complete";

    private static final String XML_BOOKSHELVES = "bookshelves";
    private static final String XML_BOOKSHELF = "bookshelf";

    private static final String XML_AUTHORS = "authors";
    private static final String XML_AUTHOR = "author";
    private static final String XML_AUTHOR_A_GIVEN_NAME = "given_name";

    private static final String XML_SERIES_ALL = "all_series";
    private static final String XML_SERIES = "series";

    @NonNull
    private final CatalogueDBAdapter mDb;
    @NonNull
    private final ExportSettings mSettings;

    private static String id(final long id) {
        return " " + ATTR_ID + "=\"" + id + "\"";
    }
    private static String version(final long id) {
        return " " + ATTR_VERSION + "=\"" + id + "\"";
    }
    private static String attr(final @NonNull String attr, final long value) {
        return " " + attr + "=\"" + value + "\"";
    }
    private static String attr(final @NonNull String attr, final @NonNull String value) {
        return " " + attr + "=\"" + value + "\"";
    }
    private static String attr(final @NonNull String attr, final boolean value) {
        return " " + attr + "=\"" + (value ? "true" : "false") + "\"";
    }

    /**
     * Constructor
     *
     * @param settings {@link ExportSettings#file} is not used, as we must support writing to a stream.
     *
     *                 {@link ExportSettings#EXPORT_SINCE} and {@link ExportSettings#dateFrom}
     *                 are not applicable as we don't export books here
     */
    public XmlExporter(final @NonNull ExportSettings settings) {
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        mSettings = settings;
    }

    @Override
    public boolean doBooks(@NonNull final OutputStream outputStream,
                           @NonNull final ExportListener listener) throws IOException {

        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outputStream, UTF8), BUFFER_SIZE)) {
            out.append("<" + XML_ROOT).append(version(XML_EXPORTER_VERSION)).append("\">\n");
            if (!doBookshelves(out) || listener.isCancelled()) {
                return false;
            }
            if (!doAuthors(out) || listener.isCancelled()) {
                return false;
            }
            if (!doSeries(out) || listener.isCancelled()) {
                return false;
            }
            out.append("</" + XML_ROOT + ">\n");
        }
        return true;
    }

    private boolean doBookshelves(@NonNull final BufferedWriter out) throws IOException {
        List<Bookshelf> list = mDb.getBookshelves();
        out.append("<" + XML_BOOKSHELVES + ATTR_VERSION + "=\"" + XML_EXPORTER_BOOKSHELVES_VERSION + "\">\n");
        for (Bookshelf bookshelf : list) {
            out.append("<" + XML_BOOKSHELF)
                    .append(id(bookshelf.id))
                    .append(attr(ATTR_NAME, bookshelf.name))
                    .append("/>\n");
        }
        out.append("</" + XML_BOOKSHELVES + ">\n");
        return true;
    }

    private boolean doAuthors(@NonNull final BufferedWriter out) throws IOException {
        List<Author> list = mDb.getAuthors();
        out.append("<" + XML_AUTHORS + ATTR_VERSION + "=\"" + XML_EXPORTER_AUTHORS_VERSION + "\">\n");
        for (Author author : list) {
            out.append("<" + XML_AUTHOR)
                    .append(id(author.id))
                    .append(attr(ATTR_NAME, author.familyName))
                    .append(attr(XML_AUTHOR_A_GIVEN_NAME, author.givenNames))
                    .append(attr(ATTR_IS_COMPLETE, author.isComplete))
                    .append("/>\n");
        }
        out.append("</" + XML_AUTHORS + ">\n");
        return true;
    }

    private boolean doSeries(@NonNull final BufferedWriter out) throws IOException {
        List<Series> list = mDb.getSeries();
        out.append("<" + XML_SERIES_ALL + ATTR_VERSION + "=\"" + XML_EXPORTER_SERIES_VERSION + "\">\n");
        for (Series series : list) {
            out.append("<" + XML_SERIES)
                    .append(id(series.id))
                    .append(attr(ATTR_NAME, series.name))
                    .append(attr(ATTR_IS_COMPLETE, series.isComplete))
                    .append("/>\n");
        }
        out.append("</" + XML_SERIES_ALL + ">\n");
        return true;
    }
}
