/* Copyrights and Licenses
 *
 * This product includes Hypersonic SQL.
 * Originally developed by Thomas Mueller and the Hypersonic SQL Group. 
 *
 * Copyright (c) 1995-2000 by the Hypersonic SQL Group. All rights reserved. 
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met: 
 *     -  Redistributions of source code must retain the above copyright notice, this list of conditions
 *         and the following disclaimer. 
 *     -  Redistributions in binary form must reproduce the above copyright notice, this list of
 *         conditions and the following disclaimer in the documentation and/or other materials
 *         provided with the distribution. 
 *     -  All advertising materials mentioning features or use of this software must display the
 *        following acknowledgment: "This product includes Hypersonic SQL." 
 *     -  Products derived from this software may not be called "Hypersonic SQL" nor may
 *        "Hypersonic SQL" appear in their names without prior written permission of the
 *         Hypersonic SQL Group. 
 *     -  Redistributions of any form whatsoever must retain the following acknowledgment: "This
 *          product includes Hypersonic SQL." 
 * This software is provided "as is" and any expressed or implied warranties, including, but
 * not limited to, the implied warranties of merchantability and fitness for a particular purpose are
 * disclaimed. In no event shall the Hypersonic SQL Group or its contributors be liable for any
 * direct, indirect, incidental, special, exemplary, or consequential damages (including, but
 * not limited to, procurement of substitute goods or services; loss of use, data, or profits;
 * or business interruption). However caused any on any theory of liability, whether in contract,
 * strict liability, or tort (including negligence or otherwise) arising in any way out of the use of this
 * software, even if advised of the possibility of such damage. 
 * This software consists of voluntary contributions made by many individuals on behalf of the
 * Hypersonic SQL Group.
 *
 *
 * For work added by the HSQL Development Group:
 *
 * Copyright (c) 2001-2002, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer, including earlier
 * license statements (above) and comply with all above license conditions.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution, including earlier
 * license statements (above) and comply with all above license conditions.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG, 
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb;

import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.HsqlHashMap;
import org.hsqldb.lib.HsqlHashSet;
import org.hsqldb.lib.HsqlStringBuffer;
import java.sql.SQLException;
import java.util.Enumeration;

// fredt@users 20020130 - patch 497872 by Nitin Chauhan - loop optimisation
// fredt@users 20020320 - doc 1.7.0 - update
// fredt@users 20021103 - patch 1.7.2 - allow for drop table, etc.

/**
 * Contains a set of User objects, and supports operations for
 * creating, finding, modifying and deleting User objects for a Database.
 * @version  1.7.2
 * @see  User
 */
class UserManager {

    /** Flag required to SELECT from a table. */
    static final int SELECT = 1 << 0;

    /** Flag required to DELETE from a table. */
    static final int DELETE = 1 << 1;

    /** flag required to INSERT into a table. */
    static final int INSERT = 1 << 2;

    /** Flag required to UPDATE a table. */
    static final int UPDATE = 1 << 3;

    /** Combined flag permitting any action. */
    static final int     ALL         = SELECT | DELETE | INSERT | UPDATE;
    static final Integer INTEGER_ALL = new Integer(ALL);

    /**
     * This object's set of User objects. <p>
     *
     * Note: The special SYS User object
     * is not included in this list but the special PUBLIC
     * User object is.
     */
    private HsqlArrayList uUser;

    /**
     * The special PUBLIC User object. <p>
     *
     * Note: All User objects except the special
     * SYS and PUBLIC User objects contain a reference to this object
     */
    private User uPublic;

    /**
     * Construction happens once for each Database object. The PUBLIC user is
     * created
     */
    UserManager() throws SQLException {

        uUser   = new HsqlArrayList();
        uPublic = createUser("PUBLIC", null, false);

        uPublic.grant("java.lang.Math", UserManager.ALL);
        uPublic.grant("org.hsqldb.Library", UserManager.ALL);
    }

    /**
     * Translate a string representation or right(s) into its numeric form.
     */
    static int getRight(String right) {

        if (right.equals("ALL")) {
            return ALL;
        } else if (right.equals("SELECT")) {
            return SELECT;
        } else if (right.equals("UPDATE")) {
            return UPDATE;
        } else if (right.equals("DELETE")) {
            return DELETE;
        } else if (right.equals("INSERT")) {
            return INSERT;
        }

        return 0;
    }

    /**
     * Returns a comma separated list of right names corresponding to the
     * right flags set in the right argument. <p>
     */
    static String getRight(int right) {

//        checkValidFlags(right);
        if (right == ALL) {
            return "ALL";
        } else if (right == 0) {
            return null;
        }

        HsqlStringBuffer b = new HsqlStringBuffer();

        if ((right & SELECT) != 0) {
            b.append("SELECT,");
        }

        if ((right & UPDATE) != 0) {
            b.append("UPDATE,");
        }

        if ((right & DELETE) != 0) {
            b.append("DELETE,");
        }

        if ((right & INSERT) != 0) {
            b.append("INSERT,");
        }

        b.setLength(b.length() - 1);

        return b.toString();
    }

    /**
     * Creates a new User object under management of this object. <p>
     *
     *  A set of constraints regarding user creation is imposed: <p>
     *
     *  <OL>
     *    <LI>If the specified name is null, then an
     *        ASSERTION_FAILED exception is thrown stating that
     *        the name is null.
     *
     *    <LI>If the specified name equals the reserved SYS user
     *        name, then an exception is thrown stating that the user already
     *        exits.
     *
     *    <LI>If this object's collection already contains an element whose
     *        name attribute equals the name argument, then
     *        a USER_ALREADY_EXISTS exception is thrown.
     *  </OL>
     */
    User createUser(String name, String password,
                    boolean admin) throws SQLException {

        // boucherb@users 20020815 - patch assert nn name
        Trace.doAssert(name != null, "null is name");

        // TODO:
        // checkComplexity(password);
        // requires special: createSAUser(), createPublicUser()
        // boucherb@users 20020815 - disallow user-land creation of SYS user
        if (SYS_USER_NAME.equals(name)) {
            throw Trace.error(Trace.USER_ALREADY_EXISTS, name);
        }

        // -------------------------------------------------------
        int i = uUser.size();

        while (i-- > 0) {
            User u = (User) uUser.get(i);

            if (u != null && u.getName().equals(name)) {
                throw Trace.error(Trace.USER_ALREADY_EXISTS, name);
            }
        }

        User u = new User(name, password, admin, uPublic);

        uUser.add(u);

        return u;
    }

    /**
     * Attempts to drop a User object with the specified name
     *  from this object's set. <p>
     *
     *  A successful drop action consists of: <p>
     *
     *  <UL>
     *
     *    <LI>removing the User object with the specified name
     *        from the set.
     *
     *    <LI>revoking all rights from the removed object<br>
     *        (this ensures that in case there are still references to the
     *        just dropped User object, those references
     *        cannot be used to erronously access database objects).
     *
     *  </UL> <p>
     *
     */
    void dropUser(String name) throws SQLException {

        Trace.check(!name.equals("PUBLIC"), Trace.ACCESS_IS_DENIED);

        int i = uUser.size();

        while (i-- > 0) {
            User u = (User) uUser.get(i);

            if (u.getName().equals(name)) {
                /*
                // todo: find a better way. Problem: removeElementAt would not
                // work correctly while others are connected
                uUser.set(i, null);
                */
                uUser.remove(i);
                u.revokeAll();    // in case the user is referenced in a Session

                return;
            }
        }

        throw Trace.error(Trace.USER_NOT_FOUND, name);
    }

    /**
     * Returns the User object with the specified name and
     * password from this object's set.
     */
    User getUser(String name, String password) throws SQLException {

        Trace.check(!name.equals("PUBLIC"), Trace.ACCESS_IS_DENIED);

        if (name == null) {
            name = "";
        }

        if (password == null) {
            password = "";
        }

        User u = get(name);

        Trace.check(u != null, Trace.USER_NOT_FOUND);
        u.checkPassword(password);

        return u;
    }

    /**
     * Retrieves this object's set of User objects as
     *  an HsqlArrayList. <p>
     */
    HsqlArrayList getUsers() {
        return uUser;
    }

    /**
     * Grants the rights represented by the rights argument on
     * the database object identified by the dbobject argument
     * to the User object identified by name
     * argument.<p>
     *
     *  Note: For the dbobject argument, Java Class objects are identified
     *  using a String object whose value is the fully qualified name
     *  of the Class, while Table objects are
     *  identified by an HsqlName object.  A Table
     *  object identifier must be precisely the one obtained by calling
     *  table.getName(); if a different HsqlName
     *  object with an identical name attribute is specified, then
     *  rights checks and tests will fail, since the HsqlName
     *  class implements its {@link HsqlName#hashCode hashCode} and
     *  {@link HsqlName#equals equals} methods based on pure object
     *  identity, rather than on attribute values. <p>
     */
    void grant(String name, Object dbobject, int rights) throws SQLException {
        get(name).grant(dbobject, rights);
    }

    /**
     * Revokes the rights represented by the rights argument on
     * the database object identified by the dbobject argument
     * from the User object identified by the name
     * argument.<p>
     * @see #grant
     */
    void revoke(String name, Object dbobject,
                int rights) throws SQLException {
        get(name).revoke(dbobject, rights);
    }

    /**
     * Returns the User object identified by the
     * name argument. <p>
     */
    private User get(String name) throws SQLException {

        int i = uUser.size();

        while (i-- > 0) {
            User u = (User) uUser.get(i);

            if (u != null && u.getName().equals(name)) {
                return u;
            }
        }

        throw Trace.error(Trace.USER_NOT_FOUND, name);
    }

    /**
     * Removes all rights mappings for the database object identified by
     * the dbobject argument from all User objects in the set.
     */
    void removeDbObject(Object dbobject) {

        int i = uUser.size();

        while (i-- > 0) {
            User u = (User) uUser.get(i);

            if (u != null) {
                u.revokeDbObject(dbobject);
            }
        }
    }

    /** The user name reserved for the special SYS user. */
    static final String SYS_USER_NAME = "SYS";

    /**
     * An empty list returned from {@link #listRightNames
     * listRightNames} when the <code>rights</code> argument
     * is zero.
     *
     */
    static final HsqlArrayList emptyRightsList = new HsqlArrayList();

    /**
     * MAP:  Integer => HsqlArrayList. <p>
     *
     * This map caches the lists of <code>String</code> objects naming the rights
     * corresponding to each valid set of rights flags, as returned by
     * {@link #listRightNames listRightNames}
     *
     */
    static final HsqlHashMap hRightsLists = new HsqlHashMap();

    /**
     * Retrieves the list of right names represented by the right flags
     * set in the specified <code>Integer</code> object's <code>int</code>
     * value. <p>
     *
     * <b>Note:</b> In the interests of memory and CPU efficiency, this method
     * uses a lazy cache algorithm, so special care must be taken not to modify
     * the retrieved list objects.  If you do need to manipulate the returned
     * lists, please first make a copy and then and perform the manipulation
     * on the copy. <p>
     *
     * @param rights An Integer representing a set of right flags
     * @return an empty list if the specified <code>Integer</code> object is
     *        null, else a list of rights, as <code>String</code> objects,
     *        represented by the rights flag bits set in the specified
     *        <code>Integer</code> object's int value.
     *
     */
    static HsqlArrayList listRightNames(Integer rights) {

        if (rights == null) {
            return emptyRightsList;
        }

        HsqlArrayList list = (HsqlArrayList) hRightsLists.get(rights);

        if (list != null) {
            return list;
        }

        int rightFlags = rights.intValue();

        list = new HsqlArrayList();

        if ((rightFlags & SELECT) != 0) {
            list.add("SELECT");
        }

        if ((rightFlags & UPDATE) != 0) {
            list.add("UPDATE");
        }

        if ((rightFlags & DELETE) != 0) {
            list.add("DELETE");
        }

        if ((rightFlags & INSERT) != 0) {
            list.add("INSERT");
        }

        hRightsLists.put(rights, list);

        return list;
    }

    /**
     * Retrieves the <code>User</code> objects representing the database
     * users that are visible to the <code>User</code> object
     * represented by the <code>session</code> argument. <p>
     *
     * If the <code>session</code> argument's <code>User</code> object
     * attribute has the database administrator role, then all of the non-null
     * <code>User</code> objects in this collection are considered visible.
     * Otherwise, only this object's special <code>PUBLIC</code>
     * <code>User</code> object attribute and the session <code>User</code>
     * object, if it exists in this collection, are considered visible. <p>
     *
     * @param session The <code>Session</code> object used to determine
     *          visibility
     * @param andPublicUser whether to include the special <code>PUBLIC</code>
     *          <code>User</code> object in the retrieved list
     * @return a list of <code>User</code> objects visible to
     *          the <code>User</code> object contained by the
     *         <code>session</code> argument.
     *
     */
    HsqlArrayList listVisibleUsers(Session session, boolean andPublicUser) {

        HsqlArrayList list;
        User          user;
        boolean       isAdmin;
        String        sessName;
        String        userName;

        list     = new HsqlArrayList();
        isAdmin  = session.isAdmin();
        sessName = session.getUsername();

        if (uUser == null || uUser.size() == 0) {
            return list;
        }

        for (int i = 0; i < uUser.size(); i++) {
            user = (User) uUser.get(i);

            if (user == null) {
                continue;
            }

            userName = user.getName();

            if ("PUBLIC".equals(userName)) {
                if (andPublicUser) {
                    list.add(user);
                }
            } else if (isAdmin) {
                list.add(user);
            } else if (sessName.equals(userName)) {
                list.add(user);
            }
        }

        return list;
    }

    /**
     * Retrieves the set of distinct, fully qualified Java <code>Class</code>
     * names upon which any grants currently exist to elements in
     * this collection. <p>
     * @return the set of distinct, fully qualified Java Class names, as
     *        <code>String</code> objects, upon which grants currently exist
     *        to the elements of this collection
     *
     */
    HsqlHashSet getGrantedClassNames() {

        int         size;
        User        user;
        HsqlHashSet out;
        Enumeration e;

        size = uUser.size();
        out  = new HsqlHashSet();

        for (int i = 0; i < size; i++) {
            user = (User) uUser.get(i);

            if (user == null) {
                continue;
            }

            e = user.getGrantedClassNames(false).elements();

            while (e.hasMoreElements()) {
                out.add(e.nextElement());
            }
        }

        return out;
    }

    /**
     * Constructs a new <code>SYS</code> <code>User</code> object for the
     * specified <code>Database</code> object. <p>
     *
     * @param database the <code>Database</code> object for which to
     *          construct a new
     * <code>SYS</code> <code>User</code> object
     * @throws SQLException - if the specified <code>Database</code>
     *          object already has a non-null <code>SYS</code>
     *          <code>User</code> attribute
     * @return a new <code>SYS</code> <code>User</code> object
     *
     */
    static User createSysUser(Database database) throws SQLException {
        return new User(SYS_USER_NAME, null, true, null);
    }
}
