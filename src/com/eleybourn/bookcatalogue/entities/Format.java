package com.eleybourn.bookcatalogue.entities;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * System wide book format representation.
 * <p>
 * ENHANCE: make a separate table for the format.
 * <p>
 * {@link DBDefinitions#DOM_BOOK_FORMAT}
 * <p>
 * Good description:  http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Format
 */
public final class Format {

    /** map to translate site book format' terminology with our own. */
    private static final Map<String, Integer> MAPPER = new HashMap<>();

    // use all lowercase keys!
    static {
        // mass market paperback
        MAPPER.put("mmpb", R.string.book_format_paperback);
        MAPPER.put("pb", R.string.book_format_paperback);
        MAPPER.put("tp", R.string.book_format_trade_paperback);
        MAPPER.put("hc", R.string.book_format_hardcover);
        MAPPER.put("ebook", R.string.book_format_ebook);
        MAPPER.put("digest", R.string.book_format_digest);
        MAPPER.put("audio cassette", R.string.book_format_audiobook);
        MAPPER.put("audio cd", R.string.book_format_audiobook);
        MAPPER.put("unknown", R.string.unknown);
    }

    private Format() {
    }

    /**
     * Tries to map website terminology to our own localised.
     *
     * @param context caller context
     * @param source  string to map
     *
     * @return localized equivalent, or the source if no mapping exists.
     */
    public static String map(@NonNull final Context context,
                             @NonNull final String source) {

        Integer resId = MAPPER.get(source.toLowerCase(LocaleUtils.getSystemLocale()));
        return resId != null ? context.getString(resId)
                             : source;
    }
}
