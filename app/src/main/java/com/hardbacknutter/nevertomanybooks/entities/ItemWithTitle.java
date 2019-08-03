package com.hardbacknutter.nevertomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;

import java.util.Locale;

public interface ItemWithTitle {

    @NonNull
    Locale getLocale();

    @NonNull
    String getTitle();

    @NonNull
    default String getTitleReorderPattern(@NonNull final Context userContext) {
        //FIXME: the context we get is not always a 'userContext' so try to get correct resources
        return LocaleUtils.getLocalizedResources(userContext, getLocale())
                .getString(R.string.pv_reformat_titles_prefixes);
    }
}
