package com.hardbacknutter.nevertomanybooks.entities;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;

import java.util.Locale;

public interface ItemWithTitle {

    /**
     * Get the locale of the actual item; e.g. a book written in Spanish should
     * return an Spanish Locale even if for example the user runs the app in German,
     * and the device in Danish.
     *
     * @return the item Locale
     */
    @NonNull
    Locale getLocale();

    @NonNull
    String getTitle();

    /**
     * Move "The, A, An" etc... to the end of the string.
     *
     * @param context Current context, should be an actual user context,
     *                and not the ApplicationContext.
     *
     * @return formatted title
     */
    default String preprocessTitle(@NonNull final Context context) {
        StringBuilder newTitle = new StringBuilder();
        String[] titleWords = getTitle().split(" ");

        // the resources bundle in the language that the book (item) is written in.
        Resources localeResources = LocaleUtils.getLocalizedResources(context, getLocale());
        String orderPatter = localeResources.getString(R.string.pv_reformat_titles_prefixes);

        try {
            if (titleWords[0].matches(orderPatter)) {
                for (int i = 1; i < titleWords.length; i++) {
                    if (i != 1) {
                        newTitle.append(' ');
                    }
                    newTitle.append(titleWords[i]);
                }
                newTitle.append(", ").append(titleWords[0]);
                return newTitle.toString();
            }
        } catch (@NonNull final RuntimeException ignore) {
            //do nothing. Title stays the same
        }
        return getTitle();
    }

    /**
     * Move "The, A, An" etc... to the end of the string.
     *
     * @param isInsert should be {@code true} for an insert, {@code false} for an update
     *
     * @return formatted title
     */
    default String preprocessTitle(@NonNull final Context context,
                                   final boolean isInsert) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean whenInserting = prefs.getBoolean(Prefs.pk_reformat_titles_on_insert, true);
        boolean whenUpdating = prefs.getBoolean(Prefs.pk_reformat_titles_on_update, true);

        if ((isInsert && whenInserting) || (!isInsert && whenUpdating)) {
            return preprocessTitle(context);
        } else {
            return getTitle();
        }
    }
}
