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
import java.util.function.BiFunction;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

abstract class ViewOnSiteMenuHandler
        implements MenuHandler {

    private final Map<Integer, String> menuIds = new HashMap<>();

    @IdRes
    private final int subMenuResId;
    @IdRes
    private final int menuGroupResId;
    @NonNull
    private final BiFunction<Context, Identifier, Optional<String>> uriProvider;

    ViewOnSiteMenuHandler(
            @IdRes final int subMenuResId,
            @IdRes final int menuGroupResId,
            @NonNull final BiFunction<Context, Identifier, Optional<String>> uriProvider) {
        this.subMenuResId = subMenuResId;
        this.menuGroupResId = menuGroupResId;
        this.uriProvider = uriProvider;
    }

    @NonNull
    abstract List<Identifier.Value> getSids(@NonNull DataHolder rowData);

    @NonNull
    abstract Optional<String> getSid(@NonNull DataHolder rowData,
                                     @NonNull String key);

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final Menu menu,
                             @NonNull final MenuInflater inflater,
                             @NonNull final DataHolder rowData) {
        final MenuItem item = menu.findItem(subMenuResId);
        if (item == null) {
            final SubMenu parent = menu
                    .addSubMenu(menuGroupResId, subMenuResId,
                                context.getResources()
                                       .getInteger(R.integer.MENU_ORDER_VIEW_ON_SITE),
                                R.string.option_view_book_at)
                    .setIcon(R.drawable.link_24px);

            menuIds.clear();

            // add to the menu if the Identifier has a valid url
            final IdentifierDao dao = ServiceLocator.getInstance().getIdentifierDao();
            getSids(rowData)
                    .stream()
                    .map(Identifier.Value::getKey)
                    .map(dao::findByKey)
                    .flatMap(Optional::stream)
                    .filter(identifier -> uriProvider.apply(context, identifier).isPresent())
                    .forEach(identifier -> {
                                 // generate a random id, and map it to the key
                                 final int menuItemId = View.generateViewId();
                                 menuIds.put(menuItemId, identifier.getKey());

                        parent.add(menuGroupResId, menuItemId, 0,
                                             identifier.getName())
                                        .setIcon(R.drawable.link_24px);
                             }
                    );
        }
    }

    @Override
    public void onPrepareMenu(@NonNull final Context context,
                              @NonNull final Menu menu,
                              @NonNull final DataHolder rowData) {

        final MenuItem subMenuItem = menu.findItem(subMenuResId);
        // Sanity check
        if (subMenuItem == null) {
            return;
        }

        //noinspection DataFlowIssue
        subMenuItem.setVisible(subMenuItem.getSubMenu().size() > 0);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @IdRes final int menuItemId,
                                      @NonNull final DataHolder rowData) {

        final String key = menuIds.get(menuItemId);
        if (key == null) {
            // Not ours to handle
            return false;
        }

        final Optional<String> oUri = ServiceLocator
                .getInstance()
                .getIdentifierDao()
                .findByKey(key)
                .flatMap(identifier -> uriProvider.apply(context, identifier));

        // Sanity check, it should be there!
        if (oUri.isPresent()) {
            final Optional<String> oSid = getSid(rowData, key);
            // Sanity check, it should be there!
            if (oSid.isEmpty()) {
                return false;
            }

            final Uri uri = Uri.parse(String.format(oUri.get(), oSid.get()));
            context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        } else {
            return false;
        }
    }
}
