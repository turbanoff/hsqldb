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


package org.hsqldb;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import org.hsqldb.lib.DataOutputStream;
import org.hsqldb.navigator.RowSetNavigatorClient;
import org.hsqldb.result.Result;
import org.hsqldb.result.ResultConstants;
import org.hsqldb.result.ResultLob;
import org.hsqldb.rowio.RowInputBinaryNet;
import org.hsqldb.rowio.RowOutputBinaryNet;
import org.hsqldb.rowio.RowOutputInterface;
import org.hsqldb.server.HsqlSocketFactory;
import org.hsqldb.store.ValuePool;
import org.hsqldb.types.TimestampData;

/**
 * Base remote session proxy implementation. Uses instances of Result to
 * transmit and recieve data. This implementation utilises the updated HSQL
 * protocol.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 1.9.0
 * @since 1.7.2
 */
public class ClientConnection implements SessionInterface {

    /**
     * Specifies the Compatibility version required for both Servers and
     * network JDBC Clients built with this baseline.  Must remain public
     * for Server to have visibility to it.
     *
     * Update this value only when the current version of HSQLDB does not
     * have inter-compatibility with Server and network JDBC Driver of
     * the previous HSQLDB version.
     *
     * Must specify all 4 version segments (any segment may be the value 0,
     * however).
     */
    public static final String   NETWORK_COMPATIBILITY_VERSION = "1.9.0.0";
    static final int             BUFFER_SIZE                   = 0x1000;
    final byte[]                 mainBuffer = new byte[BUFFER_SIZE];
    private boolean              isClosed;
    private Socket               socket;
    protected DataOutputStream   dataOutput;
    protected DataInputStream    dataInput;
    protected RowOutputInterface rowOut;
    protected RowInputBinaryNet  rowIn;
    private Result               resultOut;
    private long                 sessionID;
    private long                 lobIDSequence;

    //
    private boolean isReadOnlyDefault = false;
    private boolean isAutoCommit      = true;
    private int     zoneSeconds;
    private Scanner scanner;

    //
    String  host;
    int     port;
    String  path;
    String  database;
    boolean isTLS;
    int     databaseID;

    /**
     * Establishes a connection to the server.
     */
    public ClientConnection(String host, int port, String path,
                            String database, boolean isTLS, String user,
                            String password,
                            int timeZoneSeconds) throws HsqlException {

        this.host        = host;
        this.port        = port;
        this.path        = path;
        this.database    = database;
        this.isTLS       = isTLS;
        this.zoneSeconds = timeZoneSeconds;

        initStructures();

        Result login = Result.newConnectionAttemptRequest(user, password,
            database, timeZoneSeconds);

        initConnection(host, port, isTLS);

        Result resultIn = execute(login);

        if (resultIn.isError()) {
            throw Error.error(resultIn);
        }

        sessionID  = resultIn.getSessionId();
        databaseID = resultIn.getDatabaseId();
    }

    /**
     * resultOut is reused to trasmit all remote calls for session management.
     * Here the structure is preset for sending attributes.
     */
    private void initStructures() {

        RowOutputBinaryNet rowOutTemp = new RowOutputBinaryNet(mainBuffer);

        rowOut    = rowOutTemp;
        rowIn     = new RowInputBinaryNet(rowOutTemp);
        resultOut = Result.newSessionAttributesResult();
    }

    protected void initConnection(String host, int port,
                                  boolean isTLS) throws HsqlException {
        openConnection(host, port, isTLS);
    }

    protected void openConnection(String host, int port,
                                  boolean isTLS) throws HsqlException {

        try {
            socket = HsqlSocketFactory.getInstance(isTLS).createSocket(host,
                                                   port);
            dataOutput = new DataOutputStream(socket.getOutputStream());
            dataInput = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()));

            handshake();
        } catch (Exception e) {
            throw Error.error(ErrorCode.X_08001, e.getMessage());

            // The details from "e" should not be thrown away here.  This is
            // very useful info for end users to diagnose the runtime problem.
        }
    }

    protected void closeConnection() {

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {}

        socket = null;
    }

    public synchronized Result execute(Result r) throws HsqlException {

        try {
            r.setSessionId(sessionID);
            r.setDatabaseId(databaseID);
            write(r);

            return read();
        } catch (Throwable e) {
            throw Error.error(ErrorCode.X_08006, e.toString());
        }
    }

    public RowSetNavigatorClient getRows(long navigatorId, int offset,
                                         int size) throws HsqlException {

        try {
            resultOut.setResultType(ResultConstants.REQUESTDATA);
            resultOut.setResultId(navigatorId);
            resultOut.setUpdateCount(offset);
            resultOut.setFetchSize(size);

            Result result = execute(resultOut);

            return (RowSetNavigatorClient) result.getNavigator();
        } catch (Throwable e) {
            throw Error.error(ErrorCode.X_08006, e.toString());
        }
    }

    public void closeNavigator(long navigatorId) {

        try {
            resultOut.setResultType(ResultConstants.CLOSE_RESULT);
            resultOut.setResultId(navigatorId);
            execute(resultOut);
        } catch (Throwable e) {}
    }

    public synchronized void close() {

        if (isClosed) {
            return;
        }

        isClosed = true;

        try {
            resultOut.setResultType(ResultConstants.DISCONNECT);
            execute(resultOut);
        } catch (Exception e) {}

        try {
            closeConnection();
        } catch (Exception e) {}
    }

    public Object getAttribute(int id) throws HsqlException {

        resultOut.setResultType(ResultConstants.GETSESSIONATTR);
        resultOut.setStatementType(id);

        Result in = execute(resultOut);

        if (in.isError()) {
            throw Error.error(in);
        }

        Object[] data = in.getSingleRowData();

        switch (id) {

            case SessionInterface.INFO_AUTOCOMMIT :
                return data[SessionInterface.INFO_BOOLEAN];

            case SessionInterface.INFO_CONNECTION_READONLY :
                return data[SessionInterface.INFO_BOOLEAN];

            case SessionInterface.INFO_ISOLATION :
                return data[SessionInterface.INFO_INTEGER];

            case SessionInterface.INFO_CATALOG :
                return data[SessionInterface.INFO_VARCHAR];
        }

        return null;
    }

    public void setAttribute(int id, Object value) throws HsqlException {

        resultOut.setResultType(ResultConstants.SETSESSIONATTR);

        Object[] data = resultOut.getSingleRowData();

        data[SessionInterface.INFO_ID] = ValuePool.getInt(id);

        switch (id) {

            case SessionInterface.INFO_AUTOCOMMIT :
            case SessionInterface.INFO_CONNECTION_READONLY :
                data[SessionInterface.INFO_BOOLEAN] = value;
                break;

            case SessionInterface.INFO_ISOLATION :
                data[SessionInterface.INFO_INTEGER] = value;
                break;

            case SessionInterface.INFO_CATALOG :
                data[SessionInterface.INFO_VARCHAR] = value;
                break;
        }

        Result resultIn = execute(resultOut);

        if (resultIn.isError()) {
            throw Error.error(resultIn);
        }
    }

    public synchronized boolean isReadOnlyDefault() throws HsqlException {

        Object info = getAttribute(SessionInterface.INFO_CONNECTION_READONLY);

        isReadOnlyDefault = ((Boolean) info).booleanValue();

        return isReadOnlyDefault;
    }

    public synchronized void setReadOnlyDefault(boolean mode)
    throws HsqlException {

        if (mode != isReadOnlyDefault) {
            setAttribute(SessionInterface.INFO_CONNECTION_READONLY,
                         mode ? Boolean.TRUE
                              : Boolean.FALSE);

            isReadOnlyDefault = mode;
        }
    }

    public synchronized boolean isAutoCommit() throws HsqlException {

        Object info = getAttribute(SessionInterface.INFO_AUTOCOMMIT);

        isAutoCommit = ((Boolean) info).booleanValue();

        return isAutoCommit;
    }

    public synchronized void setAutoCommit(boolean mode) throws HsqlException {

        if (mode != isAutoCommit) {
            setAttribute(SessionInterface.INFO_AUTOCOMMIT, mode ? Boolean.TRUE
                                                                : Boolean
                                                                .FALSE);

            isAutoCommit = mode;
        }
    }

    public synchronized void setIsolationDefault(int level) throws HsqlException {
        setAttribute(SessionInterface.INFO_ISOLATION, ValuePool.getInt(level));
    }

    public synchronized int getIsolation() throws HsqlException {

        Object info = getAttribute(SessionInterface.INFO_ISOLATION);

        return ((Integer) info).intValue();
    }

    public synchronized boolean isClosed() {
        return isClosed;
    }

    public Session getSession() {
        return null;
    }

    public synchronized void startPhasedTransaction() throws HsqlException {}

    public synchronized void prepareCommit() throws HsqlException {

        resultOut.setAsTransactionEndRequest(ResultConstants.PREPARECOMMIT,
                                             null);

        Result in = execute(resultOut);

        if (in.isError()) {
            throw Error.error(in);
        }
    }

    public synchronized void commit(boolean chain) throws HsqlException {

        resultOut.setAsTransactionEndRequest(ResultConstants.TX_COMMIT, null);

        Result in = execute(resultOut);

        if (in.isError()) {
            throw Error.error(in);
        }
    }

    public synchronized void rollback(boolean chain) throws HsqlException {

        resultOut.setAsTransactionEndRequest(ResultConstants.TX_ROLLBACK,
                                             null);

        Result in = execute(resultOut);

        if (in.isError()) {
            throw Error.error(in);
        }
    }

    public synchronized void rollbackToSavepoint(String name)
    throws HsqlException {

        resultOut.setAsTransactionEndRequest(
            ResultConstants.TX_SAVEPOINT_NAME_ROLLBACK, name);

        Result in = execute(resultOut);

        if (in.isError()) {
            throw Error.error(in);
        }
    }

    public synchronized void savepoint(String name) throws HsqlException {

        Result result = Result.newSetSavepointRequest(name);
        Result in     = execute(result);

        if (in.isError()) {
            throw Error.error(in);
        }
    }

    public synchronized void releaseSavepoint(String name)
    throws HsqlException {

        resultOut.setAsTransactionEndRequest(
            ResultConstants.TX_SAVEPOINT_NAME_RELEASE, name);

        Result in = execute(resultOut);

        if (in.isError()) {
            throw Error.error(in);
        }
    }

    public void addWarning(HsqlException warning) {}

    public synchronized long getId() {
        return sessionID;
    }

    /**
     * Used by pooled connections to reset the server-side session to a new
     * one. In case of failure, the connection is closed.
     *
     * When the Connection.close() method is called, a pooled connection calls
     * this method instead of HSQLClientConnection.close(). It can then
     * reuse the HSQLClientConnection object with no further initialisation.
     *
     */
    public synchronized void resetSession() throws HsqlException {

        Result login    = Result.newResetSessionRequest();
        Result resultIn = execute(login);

        if (resultIn.isError()) {
            isClosed = true;

            closeConnection();

            throw Error.error(resultIn);
        }

        sessionID  = resultIn.getSessionId();
        databaseID = resultIn.getDatabaseId();
    }

    protected void write(Result r) throws IOException, HsqlException {
        r.write(dataOutput, rowOut);
    }

    protected Result read() throws IOException, HsqlException {

        Result result = Result.newResult(dataInput, rowIn);

        result.readAdditionalResults(this, dataInput, rowIn);
        rowOut.setBuffer(mainBuffer);
        rowIn.resetRow(mainBuffer.length);

        return result;
    }

    /**
     * Never called on this class
     */
    public String getInternalConnectionURL() {
        return null;
    }

    public synchronized long getLobId() {
        return lobIDSequence++;
    }

    /**
     * Does nothing here
     */
    public void allocateResultLob(ResultLob resultLob, DataInput dataInput) {}

    public Scanner getScanner() {

        if (scanner == null) {
            scanner = new Scanner();
        }

        return scanner;
    }

    public TimestampData getCurrentDate() {

        long currentMillis = System.currentTimeMillis();
        long seconds = HsqlDateTime.getCurrentDateMillis(currentMillis) / 1000;

        return new TimestampData(seconds);
    }

    public int getZoneSeconds() {
        return zoneSeconds;
    }

    /**
     * Converts specified encoded integer to a Network Compatibility Version
     * String.
     */
    static public String toNcvString(int i) {

        StringBuffer sb = new StringBuffer();

        i *= -1;

        sb.append(i / 1000000);

        i %= 1000000;

        sb.append('.');
        sb.append(i / 10000);

        i %= 10000;

        sb.append('.');
        sb.append(i / 100);

        i %= 100;

        sb.append('.');
        sb.append(i);

        return sb.toString();
    }

    /**
     * Encodes the specified Network Compatibility Version String to an int.
     *
     * The negation is applied to distinguish all generated codes from any
     * possible text input bytes (and from the many applications which send
     * text values over the TCP/IP pipe).
     *
     * @throws NumberFormatException
     *             A numerical segement of input NCV String malformatted
     * @throws IllegalArgumentException  Malformatted input NCV String
     */
    static public int toNcvInt(String s) {

        // This would be much more concise with Java 1.4's java.util.regex
        // or StringBuffer.indexOf().
        int totalLen = s.length();
        int value    = 0;
        int offset   = 0;
        int nextDot  = -1;

        // SEG 1
        nextDot = s.indexOf('.', offset + 1);

        if (nextDot < 1) {
            throw new IllegalArgumentException();
        }

        value  += Integer.parseInt(s.substring(offset, nextDot));
        offset = nextDot + 1;

        if (offset == totalLen) {
            throw new IllegalArgumentException();
        }

        value *= 100;

        // SEG 2
        nextDot = s.indexOf('.', offset + 1);

        if (nextDot < 1) {
            throw new IllegalArgumentException();
        }

        value  += Integer.parseInt(s.substring(offset, nextDot));
        offset = nextDot + 1;

        if (offset == totalLen) {
            throw new IllegalArgumentException();
        }

        value *= 100;

        // SEG 3
        nextDot = s.indexOf('.', offset + 1);

        if (nextDot < 1) {
            throw new IllegalArgumentException();
        }

        value  += Integer.parseInt(s.substring(offset, nextDot));
        offset = nextDot + 1;

        if (offset == totalLen) {
            throw new IllegalArgumentException();
        }

        value *= 100;

        // SEG 4
        nextDot = s.indexOf('.', offset + 1);

        if (nextDot > -1) {
            throw new IllegalArgumentException();
        }

        return -1 * (value + Integer.parseInt(s.substring(offset)));
    }

    protected void handshake() throws IOException {
        dataOutput.writeInt(toNcvInt(NETWORK_COMPATIBILITY_VERSION));
        dataOutput.flush();
    }
}
