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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * Dialog to edit an <strong>in-line in Books table</strong> Language.
 * <p>
 * Will hardly ever be needed as we use ISO code.
 * However, if a language was misspelled, auto-translation will fail,
 * and a manual edit <strong>will</strong> be needed.
 */
class EditLanguageDelegate
        extends EditStringDelegate {

    EditLanguageDelegate(@NonNull final DialogFragment owner,
                         @NonNull final Bundle args) {
        super(owner, args, R.string.lbl_language, R.string.lbl_language,
              ServiceLocator.getInstance()::getLanguageDao
        );
    }

    @Override
    @NonNull
    List<String> getList(@NonNull final Context context) {
        final Languages languages = ServiceLocator.getInstance().getLanguages();

        // Convert the list of ISO codes to user readable strings.
        // We do NOT need a distinction here between different countries.
        // The codes are always unique, but code to name conversion can create duplicates
        // (e.g. en_GB and en_US both result in "English"); eliminate them using distinct()
        return super.getList(context)
                    .stream()
                    .filter(code -> code != null && !code.isEmpty())
                    .map(code -> languages.getDisplayNameFromISO3(context, code))
                    .distinct()
                    .collect(Collectors.toList());
    }

    @Override
    @NonNull
    String onSave(@NonNull final Context context,
                  @NonNull final String originalText,
                  @NonNull final String currentText) {

        final Languages languages = ServiceLocator.getInstance().getLanguages();
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        final String fromIso = languages.getISO3FromDisplayName(
                context, userLocale, originalText);
        final String toIso = languages.getISO3FromDisplayName(
                context, userLocale, currentText);

        super.onSave(context, fromIso, toIso);
        return toIso;
    }
}
