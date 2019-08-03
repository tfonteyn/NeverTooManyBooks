package com.hardbacknutter.nevertomanybooks.entities;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;

/**
 * System wide book format representation.
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
        // ISFDB
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

        // Goodreads, not already listed above.
        MAPPER.put("mass market paperback", R.string.book_format_paperback);
        MAPPER.put("paperback", R.string.book_format_paperback);
        MAPPER.put("hardcover", R.string.book_format_hardcover);

    }

    private Format() {
    }

    /**
     * Try to map website terminology to our own localised.
     *
     * @param source string to map
     *
     * @return localized equivalent, or the source if no mapping exists.
     */
    public static String map(@NonNull final Context context,
                             @NonNull final String source) {

        //FIXME: the context we get is not always a 'userContext' so try to get correct resources
        Resources resources = LocaleUtils.getLocalizedResources(context,
                LocaleUtils.getPreferredLocale(context));
        Locale locale = resources.getConfiguration().locale;
        if (locale == null) {
            locale = Locale.ENGLISH;
        }
        Integer resId = MAPPER.get(source.toLowerCase(locale));
        return resId != null ? resources.getString(resId)
                             : source;
    }
}
