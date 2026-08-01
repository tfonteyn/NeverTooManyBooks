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
package com.hardbacknutter.nevertoomanybooks.settings.searchsites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * Shared between ALL tabs (fragments) and the hosting Activity.
 */
@SuppressWarnings("WeakerAccess")
public class SearchAdminViewModel
        extends ViewModel {

    /** Ordered list. */
    private final Map<Site.Type, List<Site>> typeAndSites = new LinkedHashMap<>();

    private final MutableLiveData<Site.Type> siteListUpdated = new MutableLiveData<>();

    @NonNull
    LiveData<Site.Type> onSiteListUpdated() {
        return siteListUpdated;
    }

    /**
     * Pseudo constructor.
     *
     * @param siteList single list/type, when {@code null} or empty,
     *                 the system/user preferred lists will be used.
     */
    public void init(@Nullable final List<Site> siteList) {
        if (typeAndSites.isEmpty()) {
            if (siteList != null && !siteList.isEmpty()) {
                // all sites have the same type, just grab it from the first one.
                typeAndSites.put(siteList.get(0).getType(), siteList);
            }

            if (typeAndSites.isEmpty()) {
                typeAndSites.put(Site.Type.Data,
                                 Site.Type.Data.getSites());
                typeAndSites.put(Site.Type.Covers,
                                 Site.Type.Covers.getSites());
                typeAndSites.put(Site.Type.AltEditions,
                                 Site.Type.AltEditions.getSites());
            }
        }
    }

    /**
     * Clear all selections in the site list for the given type.
     *
     * @param type to clear
     */
    public void clear(@NonNull final Site.Type type) {
        getList(type).forEach(site -> site.setActive(false));
        siteListUpdated.setValue(type);
    }

    /**
     * Reset the site list for the given type back to the default.
     *
     * @param context Current context
     * @param type    to reset
     */
    public void reset(@NonNull final Context context,
                      @NonNull final Site.Type type) {
        final Languages languages = ServiceLocator.getInstance().getLanguages();
        type.resetList(context, languages);
        // and replace the content of the local list with the (new) defaults.
        final List<Site> sites = getList(type);
        sites.clear();
        sites.addAll(type.getSites());

        siteListUpdated.setValue(type);
    }

    /**
     * Get the list of types we're handling.
     *
     * @return new List
     */
    @NonNull
    public List<Site.Type> getTypes() {
        return new ArrayList<>(typeAndSites.keySet());
    }

    /**
     * Get the list for the given type.
     *
     * @param type type of list
     *
     * @return the list of sites
     *
     * @throws IllegalStateException (debug) for unknown types
     */
    @NonNull
    List<Site> getList(@NonNull final Site.Type type) {
        final List<Site> list = typeAndSites.get(type);
        if (list == null) {
            throw new IllegalStateException("type not found: " + type);
        }
        return list;
    }

    /**
     * Validate if each list handled has at least one site enabled.
     * <p>
     * Can be called in single-list, AND in all-lists mode.
     *
     * @return {@code true} if each list handled has at least one site enabled.
     */
    public boolean validate() {
        for (final List<Site> list : typeAndSites.values()) {
            if (list.stream().noneMatch(Site::isActive)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Persist ALL lists.
     */
    void persist() {
        typeAndSites.forEach(Site.Type::setSiteList);
    }
}
