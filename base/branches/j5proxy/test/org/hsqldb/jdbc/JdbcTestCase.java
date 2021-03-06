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

import java.io.InputStream;
import java.io.Reader;

import java.lang.reflect.Array;

import java.math.BigDecimal;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

import java.util.Arrays;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.hsqldb.lib.HashMap;
import org.hsqldb.lib.HashSet;
import org.hsqldb.lib.IntKeyHashMap;
import org.hsqldb.lib.IntKeyIntValueHashMap;
import org.hsqldb.lib.Set;
import org.hsqldb.lib.StringConverter;

/**
 * Abstract JDBC-focused Junit test case. <p>
 *
 * @author  boucherb@users
 * @version 1.7.2
 * @since 1.7.2
 */
public abstract class JdbcTestCase extends TestCase {

    public static final String DEFAULT_DRIVER   = "org.hsqldb.jdbcDriver";
    public static final String DEFAULT_URL      = "jdbc:hsqldb:mem:testcase";
    public static final String DEFAULT_USER     = "SA";
    public static final String DEFAULT_PASSWORD = "";

    // static fields

    // We need a way of confirming compliance with
    // Tables B5 and B6 of JDBC 4.0 spec., outlining
    // the minimum conversions to be supported
    // by JDBC getXXX and setObject methods.

    private static final HashMap               jdbcGetXXXMap;
    private static final IntKeyHashMap         jdbcInverseGetXXXMap;
    private static final HashMap               jdbcSetObjectMap;
    private static final IntKeyHashMap         jdbcInverseSetObjectMap;
    private static final IntKeyIntValueHashMap dataTypeMap;

    private static final int[] tableB5AndB6ColumnDataTypes = new int[] {
        java.sql.Types.TINYINT, // ........................................... 0
        java.sql.Types.SMALLINT,
        java.sql.Types.INTEGER,
        java.sql.Types.BIGINT,
        java.sql.Types.REAL,
        java.sql.Types.FLOAT, // ............................................. 5
        java.sql.Types.DOUBLE,
        java.sql.Types.DECIMAL,
        java.sql.Types.NUMERIC,
        java.sql.Types.BIT,
        java.sql.Types.BOOLEAN, // .......................................... 10
        java.sql.Types.CHAR,
        java.sql.Types.VARCHAR,
        java.sql.Types.LONGVARCHAR,
        java.sql.Types.BINARY,
        java.sql.Types.VARBINARY, // ........................................ 15
        java.sql.Types.LONGVARBINARY,
        java.sql.Types.DATE,
        java.sql.Types.TIME,
        java.sql.Types.TIMESTAMP,
        java.sql.Types.ARRAY, // ............................................ 20
        java.sql.Types.BLOB,
        java.sql.Types.CLOB,
        java.sql.Types.STRUCT,
        java.sql.Types.REF,
        java.sql.Types.DATALINK, // ......................................... 25
        java.sql.Types.JAVA_OBJECT,
        java.sql.Types.ROWID,
        java.sql.Types.NCHAR,
        java.sql.Types.NVARCHAR,
        java.sql.Types.LONGNVARCHAR, // ..................................... 30
        java.sql.Types.NCLOB,
        java.sql.Types.SQLXML,
        java.sql.Types.OTHER
    };

    private static final String[] typeNames = new String[] {
        "TINYINT", // ........................................................ 0
        "SMALLINT",
        "INTEGER",
        "BIGINT",
        "REAL",
        "FLOAT", // .......................................................... 5
        "DOUBLE",
        "DECIMAL",
        "NUMERIC",
        "BIT",
        "BOOLEAN", // ....................................................... 10
        "CHAR",
        "VARCHAR",
        "LONGVARCHAR",
        "BINARY",
        "VARBINARY", // ..................................................... 15
        "LONGVARBINARY",
        "DATE",
        "TIME",
        "TIMESTAMP",
        "ARRAY", // ......................................................... 20
        "BLOB",
        "CLOB",
        "STRUCT",
        "REF",
        "DATALINK", // ...................................................... 25
        "JAVA_OBJECT",
        "ROWID",
        "NCHAR",
        "NVARCHAR",
        "LONGNVARCHAR", // .................................................. 30
        "NCLOB",
        "SQLXML",
        "OTHER"
    };

    private static final int typeCount = tableB5AndB6ColumnDataTypes.length;

    // JDBC 4.0, Table B6, Use of ResultSet getter Methods to Retrieve
    // JDBC Data Types

    // NOTE: Spec is missing for Types.OTHER
    //       We store Serializable, so we should support getXXX where XXX is
    //       serializable or the underlying data is a character or octet
    //       sequence (is inherently streamable)
    private static final String[][] requiredGetXXX = new String[][] {
                              //  S
                              //  M I
                              //T A N
                              //I L T..........................S
                              //N L G..........................Q O
                              //Y L E..........................L T
                              //I I G..........................X H
                              //N N E..........................M E
                              //T T R..........................L R
                              //0123456789012345678901234567890123
        {"getByte",            "1111111111111100000000000001000001"},
        {"getShort",           "1111111111111100000000000000000001"},
        {"getInt",             "1111111111111100000000000000000001"},
        {"getLong",            "1111111111111100000000000000000001"},
        {"getFloat",           "1111111111111100000000000000000001"},
        {"getDouble",          "1111111111111100000000000000000001"},
        {"getBigDecimal",      "1111111111111100000000000000000001"},
        {"getBoolean",         "1111111111111100000000000000000001"},
        {"getString",          "1111111111111111111100001000111001"},
        {"getNString",         "1111111111111111111100001000111001"},
        {"getBytes",           "0000000000000011100000000000000001"},
        {"getDate",            "0000000000011100010100000000000001"},
        {"getTime",            "0000000000011100001100000000000001"},
        {"getTimestamp",       "0000000000011100011100000000000001"},
        {"getAsciiStream",     "0000000000011111100010000000000101"},
        {"getBinaryStream",    "0000000000000011100001000000000011"},
        {"getCharacterStream", "0000000000011111100010000000111111"},
        {"getNCharacterStream","0000000000011111100010000000111111"},
        {"getClob",            "0000000000000000000010000000000101"},
        {"getNClob",           "0000000000000000000010000000000101"},
        {"getBlob",            "0000000000000000000001000000000001"},
        {"getArray",           "0000000000000000000000100000000000"},
        {"getRef",             "0000000000000000000000010000000000"},
        {"getURL",             "0000000000000000000000001000000000"},
        {"getObject",          "1111111111111111111111111111111111"},
        {"getRowId",           "0000000000000000000000000001000000"},
        {"getSQLXML",          "0000000000000000000000000000000010"}
    };

    // JDBC 4.0, Table B5, Conversions Performed by setObject Between
    // Java Object Types and Target JDBC Types

    // NOTE:     Spec is missing for Types.OTHER
    //           We store Serializable, so we should support setObject where
    //           object is serializable or the underlying data is a character
    //           or octet sequence (is inherently streamable)
    private static final Object[][] requiredSetObject = new Object[][] {
                                  //  S
                                  //  M I
                                  //T A N
                                  //I L T..........................S
                                  //N L G..........................Q O
                                  //Y L E..........................L T
                                  //I I G..........................X H
                                  //N N E..........................M E
                                  //T T R..........................L R
                                  //0123456789012345678901234567890123
        {String.class,             "1111111111111111111100000000111001"},
        {BigDecimal.class,         "1111111111111100000000000000000001"},
        {Boolean.class,            "1111111111111100000000000000000001"},
        {Integer.class,            "1111111111111100000000000000000001"},
        {Long.class,               "1111111111111100000000000000000001"},
        {Float.class,              "1111111111111100000000000000000001"},
        {Double.class,             "1111111111111100000000000000000001"},
        {byte[].class,             "0000000000000011100000000000000001"},
        {java.sql.Date.class,      "0000000000011100010100000000000001"},
        {java.sql.Time.class,      "0000000000011100001000000000000001"},
        {java.sql.Timestamp.class, "0000000000011100011100000000000001"},
        {java.sql.Array.class,     "0000000000000000000010000000000000"},
        {java.sql.Blob.class,      "0000000000000000000001000000000001"},
        {java.sql.Clob.class,      "0000000000000000000000100000000001"},
        {java.sql.Struct.class,    "0000000000000000000000010000000000"},
        {java.sql.Ref.class,       "0000000000000000000000001000000000"},
        {java.net.URL.class,       "0000000000000000000000000100000000"},
        {Object.class,             "0000000000000000000000000010000001"},
        {java.sql.RowId.class,     "0000000000000000000000000001000000"},
        {java.sql.NClob.class,     "0000000000000000000000000000000101"},
        {java.sql.SQLXML.class,    "0000000000000000000000000000000010"}
    };

    static {
        jdbcGetXXXMap           = new HashMap();
        jdbcInverseGetXXXMap    = new IntKeyHashMap();
        jdbcSetObjectMap        = new HashMap();
        jdbcInverseSetObjectMap = new IntKeyHashMap();
        dataTypeMap             = new IntKeyIntValueHashMap();

        for (int i = 0; i < typeCount; i++) {
            dataTypeMap.put(tableB5AndB6ColumnDataTypes[i], i);
        }

        for (int i = (requiredGetXXX.length - 1); i >= 0; i--) {

            Object   key         = requiredGetXXX[i][0];
            String   bits        = requiredGetXXX[i][1];
            String[] requiredGet = new String[typeCount];

            jdbcGetXXXMap.put(key, requiredGet);

            for (int j = (typeCount - 1); j >= 0; j--) {

                if (bits.charAt(j) == '1') {

                    requiredGet[j] = typeNames[j];

                    int dataType = tableB5AndB6ColumnDataTypes[j];
                    Set set      = (Set) jdbcInverseGetXXXMap.get(dataType);

                    if (set == null) {
                        set = new HashSet();

                        jdbcInverseGetXXXMap.put(dataType, set);
                    }

                    set.add(key);
                }
            }
        }

        for (int i = requiredSetObject.length - 1; i >= 0; i--) {

            Object   key         = requiredSetObject[i][0];
            String   bits        = (String) requiredSetObject[i][1];
            String[] requiredSet = new String[typeCount];

            jdbcSetObjectMap.put(key, requiredSet);

            for (int j = (typeCount - 1); j >= 0; j--) {
                if (bits.charAt(j) == '1') {

                    requiredSet[j] = typeNames[j];

                    int dataType = tableB5AndB6ColumnDataTypes[j];
                    Set set      = (Set) jdbcInverseSetObjectMap.get(dataType);

                    if (set == null) {
                        set = new HashSet();

                        jdbcInverseSetObjectMap.put(dataType, set);
                    }

                    set.add(key);
                }
            }
        }
    }

    /**
     * Retrieves whether the given JDBC 4 getter method is required to perform
     * a conversion from the given java.sql.Types SQL data type.
     *
     * @param methodName a jdbc getXXX method name.
     * @param dataType a java.sql.Types data type code
     * @return true the given JDBC getter method is required to perform
     * a conversion from the given java.sql.Types data type, else false
     */
    protected static boolean isRequiredGetXXX(String methodName, int dataType) {
        String[] requiredGet = (String[]) jdbcGetXXXMap.get(methodName);
        int      pos         = dataTypeMap.get(dataType, -1);

        return (pos >= 0) && (requiredGet != null) && (requiredGet[pos] != null);
    }

    /**
     * Retrieves a Set containing the names of the JDBC 4 getter methods that
     * are required to perform a conversion from the given java.sql.Types
     * data type.
     *
     * @param dataType a java.sql.Types data type code
     * @return a set containing the names of the JDBC 4 getter methods that
     * are required to perform a conversion from the given java.sql.Types
     * data type.
     */
    protected static Set getRequiredGetXXX(int dataType) {
        return (Set) jdbcInverseGetXXXMap.get(dataType);
    }

    /**
     * Retrieves whether the JDBC 4 PreparedStatement setObject method is
     * required to perform a conversion from the given class to the given
     * java.sql.Types SQL data type.
     *
     * @param clazz a candidate Class object
     * @param dataType a java.sql.Types data type code
     * @return true if the JDBC 4 PreparedStatement setObject method is
     * required to perform a conversion from the given class to the given
     * java.sql.Types SQL data type
     */
    protected static boolean isRequiredSetObject(Class clazz, int dataType) {
        String[] requiredSet = (String[]) jdbcSetObjectMap.get(clazz);
        int      pos         = dataTypeMap.get(dataType);

        return (pos >= 0) && (requiredSet != null) && (requiredSet[pos] != null);
    }

    /**
     * Retrieves a Set containing the names of the JDBC 4 getter methods that
     * are required tp perform a conversion from the given java.sql.Types
     * data type.
     * @param dataType
     * @return
     */
    protected static Set getRequiredSetObject(int dataType) {
        return (Set) jdbcInverseSetObjectMap.get(dataType);
    }

    public JdbcTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();

        org.hsqldb.DatabaseManager.closeDatabases(-1);
    }

    /**
     * Retrieves a new Connection with the dirver, url, user and password
     * specifed by the corresponding protected members of this class,
     *
     * @throws java.lang.Exception
     * @return
     */
    protected Connection newConnection() throws Exception {
        final String driver = getDriver();

        // not actually needed under JDBC4, as long as jar has META-INF service entry
        Class.forName(driver);

        final String url      = getUrl();
        final String user     = getUser();
        final String password = getPassword();

        return DriverManager.getConnection(url, user, password);
    }

    /**
     *
     * @param script
     * @throws java.lang.Exception
     */
    protected void executeScript(String script) throws Exception {
        java.net.URL   url  = this.getClass().getResource(script);
        ScriptIterator it   = new ScriptIterator(url);
        Connection     conn = newConnection();
        Statement      stmt = conn.createStatement();

        while(it.hasNext()) {
            String sql = (String) it.next();
            //System.out.println("sql: " + sql);
            stmt.execute(sql);
        }

        conn.commit();
        stmt.close();
        conn.close();
    }

    /**
     * Retrieves a character code corresponding to the given object's component
     * type. <p>
     *
     * For null, we return 'X' (unknown). <p>
     *
     * For 1D arrays, we return: <p>
     *
     * <pre>
     * BaseType Character 	Type            Interpretation
     *
     * B                        byte            signed byte
     * C                        char            Unicode character
     * D                        double          double-precision floating-point value
     * F                        float           single-precision floating-point value
     * I                        int             integer
     * J                        long            long integer
     * L                        reference 	an instance of class
     * S                        short           signed short
     * Z                        boolean 	true or false
     * </pre>
     *
     * for multi-arrays, we return 'N' (n-dimensional). <p>
     *
     * for (non-null, non-array) object ref, we return 'O' (Object).
     * @param o for which to retrieve the component type.
     * @return the component type.
     */
    protected static char getComponentDescriptor(Object o) {

        if (o == null) {
            return 'X'; // the unknown value
        }

        Class cls = o.getClass();

        if (cls.isArray()) {
            Class comp = cls.getComponentType();

            String className = cls.getName();

            int count = 0;

            for(;className.charAt(count) == '['; count++);

            if (count > 1) {
                return 'N';
            } else if (comp.isPrimitive()) {
                return className.charAt(1);
            } else {
                return 'L';
            }
        } else {
            // L...; is JMV field descriptor
            return 'O';
        }
    }
    
    /**
     * Computes a shallow string representation of the given array.
     *
     * @param array the array
     * @return a shallow string representation.
     */
    protected static String arrayToString(Object array)
    {
        if (array == null)
        {
            return "null";            
        }
        
        int length = Array.getLength(array);
        
        StringBuffer sb = new StringBuffer();
        
        sb.append('[');
        
        for(int i = 0; i < length; i++)
        {
            if (i > 0)
            {
                sb.append(',');
            }
            
            sb.append(Array.get(array,i));
        }
        
        sb.append(']');
        
        return sb.toString();
    }
    
    /**
     * Computes an arrays not equal failure message
     * for the given arrays.
     *
     * @param expected array
     * @param actual array
     * @return failure message
     */
    protected static String arraysNotEqualMessage(Object expected, Object actual)
    {
         return "expected:<" 
                 + arrayToString(expected) 
                 + "> but was:<" 
                 + arrayToString(actual) 
                 + ">";    
    }

    /**
     * Assert that the given objects are equal.
     *
     * Provides special handling for Java array objects.
     *
     * @param expected object
     * @param actual object
     * @throws java.lang.Exception
     */
    protected static void assertJavaArrayEquals(Object expected, Object actual) throws Exception {
        
        switch(getComponentDescriptor(expected)) {
            case 'X' : {
                if (actual != null) {
                    fail(arraysNotEqualMessage(expected, actual));
                }
                break;
            }
            case 'B' : {
                if (!Arrays.equals((byte[]) expected, (byte[]) actual)) {
                    fail(arraysNotEqualMessage(expected, actual));
                }
               break;
            }
            case 'C' : {
                if (!Arrays.equals((char[]) expected, (char[]) actual)) {
                    fail(arraysNotEqualMessage(expected, actual));
                }
                break;
            }
            case 'D' : {
                if (!Arrays.equals((double[])expected, (double[])actual)) {
                    fail(arraysNotEqualMessage(expected, actual));
                }
                break;
            }
            case 'F' : {
                if (!Arrays.equals((float[])expected, (float[])actual)) {
                     fail(arraysNotEqualMessage(expected, actual));
                }            
                break;
            }
            case 'I' : {
                if(!Arrays.equals((int[])expected, (int[])actual)) {
                     fail(arraysNotEqualMessage(expected, actual));
                }
                break;
            }
            case 'J' : {
                if (!Arrays.equals((long[])expected, (long[])actual)) {
                     fail(arraysNotEqualMessage(expected, actual));
                }
                break;
            }
            case 'L' : {
                if (!Arrays.deepEquals((Object[])expected, (Object[])actual)) {
                    fail(arraysNotEqualMessage(expected, actual));
                }
                break;
            }
            case 'S' : {
                if (!Arrays.equals((short[])expected, (short[])actual)) {
                     fail(arraysNotEqualMessage(expected, actual));
                }
                break;
            }
            case 'Z' : {
                if (!Arrays.equals((boolean[])expected, (boolean[])actual)) {
                    fail(arraysNotEqualMessage(expected, actual));
                }
                break;
            }
            case 'N' : {
                if (actual == null)
                {
                    fail("expected:<" 
                       + arrayToString(expected) 
                       + "> but was:<null>");
                }
                else if(Object[].class.isAssignableFrom(expected.getClass()) 
                     && Object[].class.isAssignableFrom(actual.getClass())) 
                {
                    if (!Arrays.deepEquals(
                            (Object[])expected,
                            (Object[])actual)) {
                         fail(arraysNotEqualMessage(expected, actual));
                    }
                }
                else
                {
                    assertEquals(
                            "Array Class",                                                        
                            expected.getClass(),
                            actual.getClass());
                    assertEquals(
                            "Array Length",
                            Array.getLength(expected),
                            Array.getLength(actual));
                    
                    int len = Array.getLength(expected);
                    
                    for (int i = 0; i < len; i++)
                    {
                        assertJavaArrayEquals(
                                Array.get(expected, i), 
                                Array.get(actual, i));
                    }
                }
                break;
            }
            case '0' :
            default : {
                assertEquals(expected, actual);
                break;
            }
        }
    }

    /**
     * Assert that the given InputStream objects produce the same octet
     * sequence.
     *
     * @param expected the expected octet sequence, as an InputStream
     * @param actual the actual octet sequence, as an InputStream
     * @throws java.lang.Exception if an I/0 error occurs.
     */
    protected void assertStreamEquals(final InputStream expected,
                                      final InputStream actual) throws Exception {
        if (expected == actual) {
            return;
        }

        assertTrue("expected != null", expected != null);
        assertTrue("actual != null", actual != null);

        int count = 0;

        String sexp = expected.getClass().getName()
                    + Integer.toHexString(System.identityHashCode(expected));
        String sact = actual.getClass().getName()
                    + Integer.toHexString(System.identityHashCode(actual));

        while (true) {

            int expByte = expected.read();
            int actByte = actual.read();

            // More efficient than generating the message for every
            // stream element.
            if (expByte != actByte) {
                String msg = sexp + "(" + count + ") == " +
                             sact + "(" + count + ")";
                assertEquals(msg, expByte, actByte);
            }

            if (expByte == -1) {
                // Assert that the actual stream is also at the end
                assertEquals("End of expected stream", -1, actual.read());
                break;
            }

            count++;
        }
    }

    /**
     *
     * @param expected
     * @param actual
     * @throws java.lang.Exception
     */
    protected void assertReaderEquals(Reader expected, Reader actual) throws Exception {
        if (expected == actual) {
            return;
        }

        assertTrue("expected != null", expected != null);
        assertTrue("actual != null", actual != null);

        int count = 0;

        String sexp = expected.getClass().getName()
                    + Integer.toHexString(System.identityHashCode(expected));
        String sact = actual.getClass().getName()
                    + Integer.toHexString(System.identityHashCode(actual));

        while (true) {

            int expChar = expected.read();
            int actChar = actual.read();

            // More efficient than generating the message for every
            // stream element.
            if (expChar != actChar) {
                String msg = sexp + "(" + count + ") == " +
                             sact + "(" + count + ")";
                assertEquals(msg, expChar, actChar);
            }

            if (expChar == -1) {
                assertEquals("End of expected sequence", -1, actual.read());
                break;
            }

            count++;
        }
    }

    /**
     *
     * @param expected
     * @param actual
     * @throws java.lang.Exception
     */
    protected void assertResultSetEquals(ResultSet expected, ResultSet actual) throws Exception {
        if (expected == actual) {
            return;
        }

        assertTrue("expected != null", expected != null);
        assertTrue("actual != null", actual != null);

        ResultSetMetaData expRsmd = expected.getMetaData();
        ResultSetMetaData actRsmd = actual.getMetaData();

        assertEquals("expRsmd.getColumnCount() == actRsmd.getColumnCount()",
                     expRsmd.getColumnCount(),
                     actRsmd.getColumnCount());

        int columnCount = actRsmd.getColumnCount();
        int rowCount    = 0;

        while(expected.next()) {
            rowCount++;

            assertTrue("actual.next() [row: " + rowCount + "]", actual.next());

            for (int i = 1; i <= columnCount; i++) {
                Object expObject = expected.getObject(i);
                Object actObject = actual.getObject(i);

                if (expObject != null && expObject.getClass().isArray()) {
                    assertJavaArrayEquals(expObject, actObject);
                } else if (expObject instanceof Blob) {
                    Blob expBlob = (Blob) expObject;
                    Blob actBlob = (Blob) actObject;

                    assertEquals("expBlob.length(), actBlob.length()", expBlob.length(), actBlob.length());
                    assertEquals("expBlob.position(actBlob, 1L), 1L", expBlob.position(actBlob, 1L), 1L);

                } else if (expObject instanceof Clob) {
                    Clob expClob = (Clob) expObject;
                    Clob actClob = (Clob) actObject;

                    assertEquals("expClob.length(), actClob.length()", expClob.length(), actClob.length());
                    assertEquals("expClob.position(actClob, 1L), 1L", expClob.position(actClob, 1L), 1L);
                } else {
                    String msg = "expected.getObject(" + i + "), actual.getObject(" + i + ")";
                    assertEquals(msg, expObject, actObject);
                }
            }
        }
    }

    /**
     * 
     * @param key 
     * @return 
     */
    protected String translatePropertyKey(final String key) {
        return "hsqldb.test.suite." + key;
    }

    /**
     * 
     * @param key 
     * @param defaultValue 
     * @return 
     */
    public String getProperty(final String key, final String defaultValue) {        
        try {                        
             return System.getProperty(translatePropertyKey(key), defaultValue);
        } catch(SecurityException se) {
            return defaultValue;
        }
    }
    
    public int getIntProperty(final String key, final int defaultValue)
    {
        try
        {
            return Integer.getInteger(translatePropertyKey(key), defaultValue);
        } catch(SecurityException se) {
            return defaultValue;
        }
    }
    
    public boolean getBooleanProperty(final String key, final boolean defaultValue)
    {
        try
        {
           String value = getProperty(key, String.valueOf(defaultValue));
           
           if (
              value.equalsIgnoreCase("true") 
           || value.equalsIgnoreCase("on") 
           || value.equals("1"))
           {
               return true;
           }
           else if (
               value.equalsIgnoreCase("false") 
            || value.equalsIgnoreCase("off")
            || value.equals("0"))
           {
               return false;
           }
           else
           {
               return defaultValue;
           }           
        } catch(SecurityException se) {
            return defaultValue;
        }        
    }

    /**
     * 
     * @return 
     */
    public String getDriver() {
        return getProperty("driver", DEFAULT_DRIVER);
    }


    /**
     * 
     * @return 
     */
    public String getUrl() {
        return getProperty("url", DEFAULT_URL);
    }


    /**
     * 
     * @return 
     */
    public String getUser() {
        return getProperty("user", DEFAULT_USER);
    }


    /**
     * 
     * @return 
     */
    public String getPassword() {
        return getProperty("password", DEFAULT_PASSWORD);
    }
}
