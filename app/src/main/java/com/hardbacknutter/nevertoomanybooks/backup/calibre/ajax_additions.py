
Add to the top:

from calibre.srv.errors import HTTPBadRequest


Add to the end (keeping 2 empty lines between the last original line and the next new line)


@endpoint('/ntmb/virtual-library-info/{library_id=None}', postprocess=json)
def virtual_library_info(ctx, rd, library_id):
    """
     Return info about available virtual libraries
    """
    db = get_db(ctx, rd, library_id)
    with db.safe_read_lock:
        return {'virtual_libraries': db.pref('virtual_libraries', {})}


@endpoint('/ntmb/virtual-libraries-for-books/{library_id=None}', postprocess=json)
def virtual_libraries_for_books(ctx, rd, library_id):
    """
     Return the book ids with their virtual libraries
     Mandatory Query parameters: ?ids=1,2,3,...
    """
    db = get_db(ctx, rd, library_id)
    with db.safe_read_lock:
        ids = rd.query.get('ids')
        if ids is None:
            raise HTTPBadRequest('ids must be passed in')
        ids = ids.split(',')
        ids = [int(i) for i in ids]
        return db.virtual_libraries_for_books(ids)
