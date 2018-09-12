package com.eleybourn.bookcatalogue.utils;

import android.support.v4.content.FileProvider;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

public class GenericFileProvider extends FileProvider {
    public final static String AUTHORITY =  BookCatalogueApp.getAppContext().getPackageName() + ".GenericFileProvider";
}
