/* Copyright (c) 2001-2006, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
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


package org.hsqldb.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Test of of interface java.sql.Savepoint.
 *
 * @author boucherb@users
 */
public class jdbcSavepointTest extends JdbcTestCase {
    
    public jdbcSavepointTest(String testName) {
        super(testName);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(jdbcSavepointTest.class);
        
        return suite;
    }

    /**
     * Test of getSavepointId method, of interface java.sql.Savepoint.
     */
    public void testGetSavepointId() throws Exception {
        System.out.println("getSavepointId");
        
        Connection conn = super.newConnection();        
        Savepoint sp = null;
        int id;
        
        assertEquals(true, conn.getAutoCommit());
        
        try {            
            sp = conn.setSavepoint();
            id = sp.getSavepointId();
            fail("Allowed setSavepoint()/getSavepointId() while autocommit == true");
        } catch (SQLException ex) {
            // ex.printStackTrace();
        }
        
        conn.setAutoCommit(false);

        try {    
            sp = conn.setSavepoint();
            id = sp.getSavepointId();
            
            System.out.println("savepoint id: " + id);
        } catch (SQLException ex) {
            fail(ex.getMessage());
        }        
    }

    /**
     * Test of getSavepointName method, of interface java.sql.Savepoint.
     */
    public void testGetSavepointName() throws Exception {
        System.out.println("getSavepointName");
        
        Connection conn = super.newConnection();        
        Savepoint sp = null;
        String expResult = "sp1";
        String result;
        
        assertEquals(true, conn.getAutoCommit());
        
        try {            
            sp = conn.setSavepoint(expResult);
            result = sp.getSavepointName();
            
            fail("Allowed setSavepoint(String)/getSavepointName() while autocommit == true");
        } catch (SQLException ex) {
            // ex.printStackTrace();
        }
        
        conn.setAutoCommit(false);        
        
        sp = conn.setSavepoint(expResult);        
        result = sp.getSavepointName();
        
        assertEquals(expResult, result);
    }

    public static void main(java.lang.String[] argList) {

        junit.textui.TestRunner.run(suite());
    }   
}