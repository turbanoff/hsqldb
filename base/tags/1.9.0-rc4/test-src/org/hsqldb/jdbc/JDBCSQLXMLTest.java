/* Copyright (c) 2001-2009, The HSQL Development Group
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.sql.SQLXML;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.hsqldb.lib.StringConverter;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.hsqldb.jdbc.testbase.BaseTestCase;

/**
 *
 * @author boucherb@users
 */
public class JDBCSQLXMLTest extends BaseTestCase {

    public JDBCSQLXMLTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected java.net.URL getResource(String path) throws Exception {
        return getClass().getResource(path);
    }

    protected InputStream getZipEntryInputStream(String zipPath,
            String entryPath) throws Exception {
        URL         zipUrl    =  getResource(zipPath);
        String      spec     = "jar:" + zipUrl + "!/" + entryPath;
        URL         entryUrl = new URL(spec);
        InputStream is        = entryUrl.openStream();

        return is;
    }

    protected String zipEntryToString(String zip, String entry)
    throws Exception {
        InputStream is = getZipEntryInputStream(zip, entry);
        String      s  = StringConverter.inputStreamToString(is, "US-ASCII");

        return s;
    }

    protected SQLXML newMyDoc() throws Exception {
        InputStream is = getZipEntryInputStream("resources/xml/MyDoc.xml.zip", "MyDoc.xml");
        StreamSource ss = new StreamSource();

        ss.setInputStream(is);
        ss.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        return new JDBCSQLXML(ss);
    }

    protected String newMyDocString() throws Exception {
        return zipEntryToString("resources/xml/MyDoc.xml.zip", "MyDoc.xml");
    }

    protected void assertXmlEquals(Source expectedSource, Source actualSource) throws Exception
    {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();

        DOMResult expectedResult = new DOMResult();
        DOMResult actualResult = new DOMResult();

        transformer.transform(expectedSource, expectedResult);
        transformer.transform(actualSource, actualResult);

        Node expectedRoot = expectedResult.getNode();
        Node actualRoot = actualResult.getNode();

        assertNodeEquals(expectedRoot, actualRoot);
    }

    protected void assertNodeEquals(Node n1, Node n2) {
        short nt1 = n1.getNodeType();
        short nt2 = n2.getNodeType();

        assertEquals("Node Types", nt1, nt2);

        String nn1 = n1.getNodeName();
        String nn2 = n2.getNodeName();

        assertEquals("Node Names", nn1, nn2);

        NamedNodeMap n1Attrs = n1.getAttributes();
        NamedNodeMap n2Attrs = n2.getAttributes();

        if (n1Attrs != null && n2Attrs != null) {

            int n1AttrsLength = n1Attrs.getLength();
            int n2AttrsLength = n2Attrs.getLength();

            assertEquals("Attributes Length", n1AttrsLength, n2AttrsLength);

            for(int i = 0; i < n1AttrsLength; i++) {
                assertNodeEquals(n1Attrs.item(i), n2Attrs.item(i));
            }
        }
        else if (n1Attrs == null)
        {
            assertNull("n2Attrs", n2Attrs);
        }
        else if (n2Attrs == null)
        {
            assertNull("n1Attrs", n1Attrs);
        }

        NodeList n1Children = n1.getChildNodes();
        NodeList n2Children = n2.getChildNodes();

        if (n1Children != null && n2Children != null) {
            int n1ChildrenLength = n1Children.getLength();
            int n2ChildrenLength = n2Children.getLength();

            assertEquals("Child Count", n1ChildrenLength, n2ChildrenLength);

            for(int i = 0; i < n1ChildrenLength; i++)
            {
                assertNodeEquals(n1Children.item(i), n2Children.item(i));
            }
        }
        else if (n1Children == null)
        {
            assertNull("n2Children", n2Children);
        }
        else if (n2Children == null)
        {
            assertNull("n1Children", n1Children);
        }
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(JDBCSQLXMLTest.class);

        return suite;
    }

    /**
     * Test of free method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testFree() throws Exception {
        println("free");

        SQLXML instance = newMyDoc();

        instance.free();
    }

    /**
     * Test of getBinaryStream method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testGetBinaryStream() throws Exception {
        println("getBinaryStream");

        SQLXML instance = newMyDoc();

        Source actualSource = new StreamSource(instance.getBinaryStream());

        actualSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        Source expectedSource = new StreamSource(new StringReader(newMyDocString()));

        expectedSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        assertXmlEquals(expectedSource, actualSource);
    }

    /**
     * Test of setBinaryStream method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testSetBinaryStream() throws Exception {
        println("setBinaryStream");

        SQLXML       instance = new JDBCSQLXML();
        OutputStream os       = instance.setBinaryStream();
        OutputStreamWriter writer = new OutputStreamWriter(os);

        String expected = newMyDocString();

        writer.write(expected);
        writer.flush();

        try {
            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        try {
            os.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        Source actualSource = new StreamSource(instance.getBinaryStream());

        actualSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        Source expectedSource = new StreamSource(new StringReader(expected));

        expectedSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        assertXmlEquals(expectedSource, actualSource);
    }

    /**
     * Test of getCharacterStream method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testGetCharacterStream() throws Exception {
        println("getCharacterStream");

        SQLXML instance = newMyDoc();

        Source actualSource = new StreamSource(instance.getCharacterStream());

        actualSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        Source expectedSource = new StreamSource(new StringReader(newMyDocString()));

        expectedSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        assertXmlEquals(expectedSource, actualSource);
    }

    /**
     * Test of setCharacterStream method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testSetCharacterStream() throws Exception {
        println("setCharacterStream");

        SQLXML instance = new JDBCSQLXML();
        Writer writer   = instance.setCharacterStream();
        String expected = newMyDocString();

        writer.write(expected);
        writer.flush();
        writer.close();

        String actual = instance.getString();

        Source actualSource = new StreamSource(new StringReader(actual));

        actualSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        Source expectedSource = new StreamSource(new StringReader(expected));

        expectedSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        assertXmlEquals(expectedSource, actualSource);
    }

    /**
     * Test of getString method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testGetString() throws Exception {
        println("getString");

        SQLXML instance = newMyDoc();

        String actual = instance.getString();
        String expected = newMyDocString();


        Source actualSource = new StreamSource(new StringReader(actual));

        actualSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        Source expectedSource = new StreamSource(new StringReader(expected));

        expectedSource.setSystemId(getResource("resources/xml/MyDoc.xml.zip").toExternalForm());

        assertXmlEquals(expectedSource, actualSource);
    }

    /**
     * Test of setString method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testSetString() throws Exception {
        println("setString");

        String expected = "<kid id='1'><stuff id='2'>Is fun</stuff></kid>";

        JDBCSQLXML instance = new JDBCSQLXML();

        instance.setString(expected);

        String actual = instance.getString();
        Source actualSource = new StreamSource(new StringReader(actual));
        Source expectedSource = new StreamSource(new StringReader(expected));

        assertXmlEquals(expectedSource, actualSource);
    }

    /**
     * Test of getSource method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testGetDOMSource() throws Exception {
        println("getDOMSource");

        fail("TODO: testGetDOMSource()");
    }

    /**
     * Test of getSource method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testGetSAXSource() throws Exception {
        println("getSAXSource");

        fail("TODO: testGetSAXSource()");
    }

    /**
     * Test of getSource method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testGetStAXSource() throws Exception {
        println("getStAXSource");

        fail("TODO: testGetStAXSource()");
    }

    /**
     * Test of getSource method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testGetStreamSource() throws Exception {
        println("getStreamSource");

         fail("TODO: testGetStreamSource()");
    }

    /**
     * Test of setResult method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testSetDOMResult() throws Exception {
        println("setDOMResult");

        fail("TODO: testSetDOMResult()");
    }

    /**
     * Test of setResult method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testSetSAXResult() throws Exception {
        println("setSAXResult");

        fail("TODO: testSetSAXResult()");
    }

    /**
     * Test of setResult method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testSetStAXResult() throws Exception {
        println("setStAXResult");

        fail("TODO: testSetStAXResult()");
    }

    /**
     * Test of setResult method, of class org.hsqldb.jdbc.JDBCSQLXML.
     */
    public void testSetStreamResult() throws Exception {
        println("setStreamResult");

        fail("TODO: testSetStreamResult()");
    }

    public static void main(java.lang.String[] argList) {

        junit.textui.TestRunner.run(suite());
    }

}
