package com.eleybourn.bookcatalogue.entities;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

import java.util.HashMap;
import java.util.Map;

/**
 * System wide book format representation.
 */
public class Format {

    /** map to translate site book format' terminology with our own. */
    private static final Map<String, Integer> MAPPER = new HashMap<>();

    //ENHANCE: move the mapping to resource files (2 string-arrays ?).
    static {
        MAPPER.put("pb", R.string.book_format_paperback);
        MAPPER.put("tp", R.string.book_format_trade_paperback);
        MAPPER.put("hc", R.string.book_format_hardcover);
        MAPPER.put("ebook", R.string.book_format_ebook);
        MAPPER.put("digest", R.string.book_format_digest);
        MAPPER.put("audio cassette", R.string.book_format_audiobook);
        MAPPER.put("audio CD", R.string.book_format_audiobook);
        MAPPER.put("unknown", R.string.unknown);
    }

    public Format() {
    }

    /**
     * Tries to map website terminology to our own localised.
     *
     * @param source string to map
     *
     * @return localized equivalent, or the source if no mapping exists.
     */
    public String map(@NonNull final String source) {
        Integer resId = MAPPER.get(source);
        return resId != null ? BookCatalogueApp.getResString(resId)
                             : source;
    }
}
