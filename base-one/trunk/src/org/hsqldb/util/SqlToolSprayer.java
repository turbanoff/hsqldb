package org.hsqldb.util;

import java.util.Date;
import java.util.ArrayList;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.File;

/* $Id: SqlToolSprayer.java,v 1.2 2004/05/14 20:29:26 unsaved Exp $ */

/**
 * Sql Tool Sprayer.
 * Invokes SqlTool.main() multiple times with the same SQL.
 * Invokes for multiple urlids and/or retries.
 *
 * See JavaDocs for the main method for syntax of how to run.
 *
 * System properties used if set:
 * <UL>
 *      <LI>sqltoolsprayer.period (in ms.)</LI>
 *      <LI>sqltoolsprayer.maxtime (in ms.)</LI>
 *      <LI>sqltoolsprayer.rcfile (filepath)</LI>
 * </UL>
 *
 * @see @main()
 * @version $Revision: 1.2 $
 * @author Blaine Simpson
 */
public class SqlToolSprayer {
    static private final String SYNTAX_MSG =
        "SYNTAX:  java [-D...] SqlToolSprayer 'SQL;' [urlid1 urlid2...]\n"
        + "System properties you may use [default values]:\n"
        + "    sqltoolsprayer.period (in ms.) [500]\n"
        + "    sqltoolsprayer.maxtime (in ms.) [0]\n"
        + "    sqltoolsprayer.monfile (filepath) [none]\n"
      + "    sqltoolsprayer.rcfile (filepath) [none.  SqlTool default used.]\n"
        + "    sqltoolsprayer.propfile (filepath) [none]";

    static public void main(String[] sa) {
        if (sa.length < 1) {
            System.err.println(SYNTAX_MSG);
            System.exit(4);
        }
        System.setProperty("sqltool.noexit", "true");
        long period = ((System.getProperty("sqltoolsprayer.period") == null)
                ? 500 :
                Integer.parseInt(System.getProperty("sqltoolsprayer.period")));
        long maxtime = ((System.getProperty("sqltoolsprayer.maxtime") == null)
                ? 0 :
                Integer.parseInt(System.getProperty("sqltoolsprayer.maxtime")));
        String rcFile = System.getProperty("sqltoolsprayer.rcfile");
        String propfile = System.getProperty("sqltoolsprayer.propfile");
        File monitorFile =
                (System.getProperty("sqltoolsprayer.monfile") == null)
                ? null
                : new File(System.getProperty("sqltoolsprayer.monfile"));
        ArrayList urlids = new ArrayList();
        if (propfile != null) try {
            getUrlsFromPropFile(propfile, urlids);
        } catch (Exception e) {
            System.err.println("Failed to load property file '" + propfile
                    + "':  " + e);
            System.exit(3);
        }
        for (int i = 1; i < sa.length; i++) urlids.add(sa[i]);
        boolean status[] = new boolean[urlids.size()];
        for (int i = 0; i < status.length; i++) {
            status[i] = false;
        }
        String[] withRcArgs = {
            "--noinput",
            "--sql",
            sa[0],
            "--rcfile",
            rcFile,
            null
        };
        String[] withoutRcArgs = {
            "--noinput",
            "--sql",
            sa[0],
            null
        };
        String[] sqlToolArgs = (rcFile == null) ? withoutRcArgs : withRcArgs;
        boolean onefailed = false;
        long startTime = (new Date()).getTime();
        while (true) {
            if (monitorFile != null && !monitorFile.exists()) {
                System.err.println("Required file is gone:  " + monitorFile);
                System.exit(2);
            }
            onefailed = false;
            for (int i = 0; i < status.length; i++) {
                if (status[i]) continue;
                sqlToolArgs[sqlToolArgs.length - 1] = (String) urlids.get(i);
                try {
                    SqlTool.main(sqlToolArgs);
                    status[i] = true;
                    System.err.println("Success for instance '" + urlids.get(i)
                            + "'");
                } catch (Exception e) {
                    onefailed = true;
                }
            }
            if (!onefailed) break;
            if (maxtime == 0 || (new Date()).getTime() > startTime + maxtime)
                break;
            try {
                Thread.sleep(period);
            } catch (InterruptedException ie) {}
        }
        ArrayList failedUrlids = new ArrayList();
        // If all statuses true, then System.exit(0);
        for (int i = 0; i < status.length; i++)
            if (status[i] != true) failedUrlids.add((String) urlids.get(i));
        if (failedUrlids.size() > 0) {
            System.err.println("Failed instances:   " + failedUrlids);
            System.exit(1);
        }
        System.exit(0);
    }

    static private void getUrlsFromPropFile(String fileName, ArrayList al)
    throws Exception {
        Properties p = new Properties();
        p.load(new FileInputStream(fileName));
        int i = -1;
        String val;
        while (true) {
            i++;
            val = p.getProperty("server.urlid." + i);
            if (val == null) return;
            al.add(val);
        }
    }
}
