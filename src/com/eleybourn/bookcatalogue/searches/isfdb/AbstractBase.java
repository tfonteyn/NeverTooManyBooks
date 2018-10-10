package com.eleybourn.bookcatalogue.searches.isfdb;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.EditBookAnthologyFragment;
import com.eleybourn.bookcatalogue.debug.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

abstract class AbstractBase {

    /**
     * Trim extraneous punctuation and whitespace from the titles and authors
     *
     * Original code in {@link EditBookAnthologyFragment} had:
     * CLEANUP_REGEX = "[\\,\\.\\'\\:\\;\\`\\~\\@\\#\\$\\%\\^\\&\\*\\(\\)\\-\\=\\_\\+]*$";
     *
     * Android Studio:
     * Reports character escapes that are replaceable with the unescaped character without a
     * change in meaning. Note that inside the square brackets of a character class, many
     * escapes are unnecessary that would be necessary outside of a character class.
     * For example the regex [\.] is identical to [.]
     *
     * So that became:
     * private static final String CLEANUP_REGEX = "[,.':;`~@#$%^&*()\\-=_+]*$";
     *
     * But given a title like "Introduction (The Father-Thing)"
     * you loose the ")" at the end, so remove that from the regex, see below
     */
    private static final String CLEANUP_TITLE_REGEX = "[,.':;`~@#$%^&*(\\-=_+]*$";

    String mPath;
    Document mDoc;

    boolean loadPage() {
        if (mDoc == null) {
            try {
                mDoc = Jsoup.connect(mPath)
                        .userAgent(BCPreferences.JSOUP_USER_AGENT)
                        .followRedirects(true)
                        .get();
            } catch (IOException e) {
                Logger.error(e, mPath);
                return false;
            }
        }
        return true;
    }

    @NonNull
    String cleanUpName(@NonNull final String s) {
        return s.trim()
                .replace("\n", " ")
                .replaceAll(CLEANUP_TITLE_REGEX, "")
                .trim();
    }

    long stripNumber(@NonNull final String url) {
        return Long.parseLong(url.split("\\?")[1]);
    }
}
