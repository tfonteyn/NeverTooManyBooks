/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.bnf;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;

public class BnfPreferencesFragment
        extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_bnf_fr, rootKey);

        //noinspection DataFlowIssue
        findPreference(EngineId.Bnf.getPreferenceKey()
                       + AuthorResolverFactory.PK_RESOLVE_AUTHORS
                       + EngineId.Wikidata.getPreferenceKey())
                .setTitle(getString(R.string.pt_fetch_author_info_using_site_x,
                                    getString(R.string.site_wikidata)));
    }
}
