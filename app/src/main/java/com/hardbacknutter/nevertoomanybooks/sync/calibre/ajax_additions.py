Installing the Calibre extension:

=== Windows ===

The standard binary installation of Calibre cannot be modified.
To go ahead with the below modifications, you will need to install a developer version.
See https://manual.calibre-ebook.com/develop.html on how to do this.

Then search your installation for the "ajax.py" file and continue below.

=== Linux ===

Edit  the file "/usr/lib/calibre/calibre/srv/ajax.py", and look for the line

    from calibre.srv.errors import HTTPNotFound, BookNotFound

 modify it:

    from calibre.srv.errors import HTTPNotFound, BookNotFound, HTTPBadRequest

At the end of the file:
- REPLACE the endpoint '/ajax/library-info'
- ADD the endpoint '/ntmb/virtual-libraries-for-books'

Recompile (either with 'python2' or 'python3' depending on platform)
    python2 -m py_compile ajax.py

Restart Calibre server



@endpoint('/ajax/library-info', postprocess=json)
def library_info(ctx, rd):
    """
     Return info about available libraries
    """
    library_map, default_library = ctx.library_info(rd)
    library_details = {}
    for libId in library_map:
        db = get_db(ctx, rd, libId)
        library_details[libId] = {'uuid': db.library_id, 'name': library_map[libId]}
        virtual_libraries = db.pref('virtual_libraries')
        if virtual_libraries is not None:
            library_details[libId]['virtual_libraries'] = db.pref('virtual_libraries')
    return {'library_map':library_map,
            'default_library':default_library,
            'library_details': library_details}


@endpoint('/ntmb/virtual-libraries-for-books/{book_ids}/{library_id=None}', postprocess=json)
def virtual_libraries_for_books(ctx, rd, book_ids, library_id):
    """
     Return the book ids with their virtual libraries
    """
    db = get_db(ctx, rd, library_id)
    with db.safe_read_lock:
        try:
            ids = {int(x) for x in book_ids.split(',')}
        except Exception:
            raise HTTPBadRequest('invalid book_ids: {}'.format(book_ids))
        return db.virtual_libraries_for_books(ids)

