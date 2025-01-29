/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

/**
 * Collects all {@link Identifier}s present
 * and builds/displays a menu suitable for the given {@link Book}.
 * <p>
 * We hide the entire submenu if there are none.
 */
class ViewBookOnSiteMenuHandler
        implements MenuHandler {

    private final Map<Integer, String> menuIds = new HashMap<>();

    @NonNull
    private static List<Identifier.Value> getExternalIds(@NonNull final DataHolder rowData) {
        final List<Identifier.Value> ivs = DataHolderUtils.getExternalIds(rowData);

        if (ivs.stream().map(Identifier.Value::getKey).noneMatch(Identifier.SID_ASIN::equals)) {
            //URGENT: is this a good idea? The browser/amazon gives a 404 if the isbn is not found
            // When looking for the Amazon ASIN, fallback on an Isbn code if possible
            if (rowData.contains(DBKey.BOOK_ISBN)) {
                final String isbnStr = rowData.getString(DBKey.BOOK_ISBN);
                final ISBN isbn = new ISBN(isbnStr, true);
                if (isbn.isValid(true) && isbn.isIsbn10Compat()) {
                    ivs.add(new Identifier.Value(Identifier.SID_ASIN,
                                                 isbn.asText(ISBN.Type.Isbn10)));
                }
            }
        }
        return ivs;
    }

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater,
                             @NonNull final DataHolder rowData) {
        // Sanity check
        MenuItem menuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
        if (menuItem == null) {
            inflater.inflate(R.menu.sm_view_on_site, menu);
            menuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
            menuIds.clear();

            final SubMenu subMenu = menuItem.getSubMenu();

            // add to the menu if the Identifier has a valid bookUrl
            getExternalIds(rowData)
                    .stream()
                    .map(Identifier.Value::getKey)
                    .map(key -> ServiceLocator
                            .getInstance().getIdentifierDao().findByKey(key))
                    .flatMap(Optional::stream)
                    .forEach(identifier -> {
                                 if (identifier.getBookUrl(context) != null) {
                                     // generate a random id, and map it to the key
                                     final int menuItemId = View.generateViewId();
                                     menuIds.put(menuItemId, identifier.getKey());

                                     //noinspection DataFlowIssue
                                     subMenu.add(R.id.MENU_GROUP_BOOK, menuItemId, 0,
                                                 identifier.getName())
                                            .setIcon(R.drawable.link_24px);
                                 }
                             }
                    );
        }
    }

    @Override
    public void onPrepareMenu(
            @NonNull final Context context,
            @NonNull final Menu menu,
            @NonNull final DataHolder rowData) {

        final MenuItem subMenuItem = menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE);
        if (subMenuItem == null) {
            // Not ours to handle
            return;
        }

        //noinspection DataFlowIssue
        subMenuItem.setVisible(subMenuItem.getSubMenu().size() > 0);
    }

    @Override
    public boolean onMenuItemSelected(
            @NonNull final Context context,
            @IdRes final int menuItemId,
            @NonNull final DataHolder rowData) {

        final String key = menuIds.get(menuItemId);
        if (key == null) {
            // Not ours to handle
            return false;
        }

        final Optional<String> oBookUrl = ServiceLocator.getInstance()
                                                        .getIdentifierDao()
                                                        .findByKey(key)
                                                        .map(identifier -> identifier.getBookUrl(
                                                                context));
        // Sanity check, it should be there!
        if (oBookUrl.isEmpty()) {
            return false;
        }

        final Optional<String> oSid = DataHolderUtils.getExternalId(rowData, key);
        // Sanity check, it should be there!
        if (oSid.isEmpty()) {
            return false;
        }

        final String url = String.format(oBookUrl.get(), oSid.get());
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        return true;
    }
}
