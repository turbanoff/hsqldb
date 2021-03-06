/* Copyright (c) 2001-2005, The HSQL Development Group
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


package org.hsqldb.scriptio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.hsqldb.Database;
import org.hsqldb.HsqlException;
import org.hsqldb.Result;
import org.hsqldb.ResultConstants;
import org.hsqldb.Session;
import org.hsqldb.Trace;
import org.hsqldb.lib.StringConverter;
import org.hsqldb.rowio.RowInputTextLog;

import java.io.BufferedInputStream;

import org.hsqldb.store.ValuePool;

import java.io.DataInputStream;
import java.io.InputStream;

/**
 * Handles operations involving reading back a script or log file written
 * out by ScriptWriterText. This implementation
 * corresponds to ScriptWriterText.
 *
 *  @author fredt@users
 *  @version 1.8.0
 *  @since 1.7.2
 */
public class ScriptReaderText extends ScriptReaderBase {

    // this is used only to enable reading one logged line at a time
    BufferedReader  dataStreamIn;
    RowInputTextLog rowIn;
    boolean         isInsert;

    ScriptReaderText(Database db,
                     String file) throws HsqlException, IOException {

        super(db, file);

        rowIn = new RowInputTextLog();
    }

    protected void openFile() throws IOException {

        InputStream d = db.isFilesInJar()
                        ? getClass().getResourceAsStream(fileName)
                        : db.getFileAccess().openInputStreamElement(fileName);

        dataStreamIn = new BufferedReader(
            new InputStreamReader(new BufferedInputStream(d)));
    }

    protected void readDDL(Session session)
    throws IOException, HsqlException {

        for (; readLoggedStatement(); ) {
            if (rowIn.getStatementType() == INSERT_STATEMENT) {
                isInsert = true;

                break;
            }

            Result result = session.sqlExecuteDirectNoPreChecks(statement);

            if (result != null && result.mode == ResultConstants.ERROR) {
                db.logger.appLog.logContext(result.getException());

                throw Trace.error(Trace.ERROR_IN_SCRIPT_FILE,
                                  Trace.DatabaseScriptReader_readDDL,
                                  new Object[] {
                    new Integer(lineCount), result.getMainString()
                });
            }
        }
    }

    protected void readExistingData(Session session)
    throws IOException, HsqlException {

        try {
            String tablename = null;

            // fredt - needed for forward referencing FK constraints
            db.setReferentialIntegrity(false);

            for (; isInsert || readLoggedStatement(); isInsert = false) {
                if (!rowIn.getTableName().equals(tablename)) {
                    tablename    = rowIn.getTableName();
                    currentTable = db.getUserTable(null, tablename);
                }

                currentTable.insertFromScript(rowData);
            }

            db.setReferentialIntegrity(true);
        } catch (Exception e) {
            db.logger.appLog.logContext(e);

            throw Trace.error(Trace.ERROR_IN_SCRIPT_FILE,
                              Trace.DatabaseScriptReader_readExistingData,
                              new Object[] {
                new Integer(lineCount), e.getMessage()
            });
        }
    }

    public boolean readLoggedStatement() throws IOException {

        //fredt temporary solution - should read bytes directly from buffer
        String s = dataStreamIn.readLine();

        lineCount++;

        statement = StringConverter.asciiToUnicode(s);

        if (statement == null) {
            return false;
        }

        processStatement();

        return true;
    }

    private void processStatement() throws IOException {

        try {
            if (statement.startsWith("/*C")) {
                int endid = statement.indexOf('*', 4);

                sessionNumber = Integer.parseInt(statement.substring(3,
                        endid));
                statement = statement.substring(endid + 2);
            }

            rowIn.setSource(statement);

            statementType = rowIn.getStatementType();

            if (statementType == ANY_STATEMENT) {
                rowData      = null;
                currentTable = null;

                return;
            } else if (statementType == COMMIT_STATEMENT) {
                rowData      = null;
                currentTable = null;

                return;
            }

            String name = rowIn.getTableName();

            currentTable = db.getUserTable(null, name);

            int[] colTypes;

            if (statementType == INSERT_STATEMENT) {
                colTypes = currentTable.getColumnTypes();
                rowData =
                    rowIn.readData(colTypes,
                                   currentTable.getInvisibleColumnCount());
            } else if (currentTable.hasPrimaryKey()) {
                colTypes = currentTable.getPrimaryKeyTypes();
                rowData  = rowIn.readData(colTypes, colTypes.length);
            } else {
                colTypes = currentTable.getColumnTypes();
                rowData = rowIn.readData(colTypes,
                                         currentTable.getColumnCount());
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public void close() {

        try {
            dataStreamIn.close();
        } catch (Exception e) {}
    }
}
