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
import org.hsqldb.lib.HashSet;
import org.hsqldb.lib.HashMap;
import org.hsqldb.lib.Iterator;
import org.hsqldb.HsqlNameManager.HsqlName;

// fredt@users 20010701 - patch 1.6.1 by hybris
// basic implementation of LIMIT n m
// fredt@users 20020221 - patch 513005 by sqlbob@users (RMP)
// type and logging attributes of sIntotable
// fredt@users 20020230 - patch 495938 by johnhobs@users - GROUP BY order
// fred@users 20020522 - patch 1.7.0 - aggregate functions with DISTINCT
// rougier@users 20020522 - patch 552830 - COUNT(DISTINCT)
// tony_lai@users 20021020 - patch 1.7.2 - improved aggregates and HAVING
// boucherb@users 20030811 - patch 1.7.2 - prepared statement support
// fredt@users 20031012 - patch 1.7.2 - better OUTER JOIN implementation
// fredt@users 20031012 - patch 1.7.2 - SQL standard ORDER BY with UNION and other set queries

/**
 * Class declaration
 *
 *
 * @version 1.7.0
 */
class Select {

    boolean                       isPreProcess;
    boolean                       isDistinctSelect;
    boolean                       isAggregated;
    private boolean               isGrouped;
    private HashSet               groupColumnNames;
    private int                   aggregateCount;
    TableFilter                   tFilter[];
    Expression                    eCondition;           // null means no condition
    Expression                    havingCondition;      // null means none
    Expression                    eColumn[];            // 'result', 'group' and 'order' columns
    int                           iResultLen;           // number of columns that are 'result'
    int                           iGroupLen;            // number of columns that are 'group'
    int                           iHavingIndex = -1;    // -1 means no having
    int                           iOrderLen;            // number of columns that are 'order'
    Select                        sUnion;               // null means no union select
    HsqlName                      sIntoTable;           // null means not select..into
    int                           intoType = Table.MEMORY_TABLE;
    boolean                       isIntoTableQuoted;
    int                           iUnionType;
    static final int              NOUNION    = 0,
                                  UNION      = 1,
                                  UNIONALL   = 2,
                                  INTERSECT  = 3,
                                  EXCEPT     = 4;
    int                           limitStart = 0;       // set only by the LIMIT keyword
    int                           limitCount = 0;       // set only by the LIMIT keyword
    private Result.ResultMetaData resultMetaData;

    /**
     * Set to preprocess mode
     *
     */
    void setPreProcess() {
        isPreProcess = true;
    }

    /**
     * Experimental.
     *
     * Map the column aliases to expressions in order to resolve alias names
     * in WHERE clauses
     *
     */
    HashMap getColumnAliases() {

        HashMap aliasMap = new HashMap();

        for (int i = 0; i < iResultLen; i++) {
            String alias = eColumn[i].getAlias();

            if (alias != null) {
                aliasMap.put(alias, eColumn[i]);
            }
        }

        return aliasMap;
    }

    /**
     * Method declaration
     *
     *
     * @throws HsqlException
     */
    void resolve() throws HsqlException {

        int len = tFilter.length;

        for (int i = 0; i < len; i++) {
            resolve(tFilter[i], true);
        }
    }

    /**
     * Method declaration
     *
     *
     * @param f
     * @param ownfilter
     *
     * @throws HsqlException
     */
    void resolve(TableFilter f, boolean ownfilter) throws HsqlException {

        int len = eColumn.length;

        for (int i = 0; i < len; i++) {

//            eColumn[i].resolve(f);
            eColumn[i].resolveTables(f);
            eColumn[i].resolveTypes();
        }

// fredt - moved codition resolution after column resolution
        if (eCondition != null) {

//            eCondition.resolve(f);
            eCondition.resolveTables(f);
            eCondition.resolveTypes();

            if (f != null && ownfilter) {

                // the table filter tries to get as many conditions as
                // possible but only if it belongs to this query
                f.setCondition(eCondition);
            }
        }
    }

    /**
     * Method declaration
     *
     * @throws HsqlException
     */
    void checkResolved() throws HsqlException {

        int len = eColumn.length;

        for (int i = 0; i < len; i++) {
            eColumn[i].checkResolved();
        }

        if (eCondition != null) {
            eCondition.checkResolved();
        }
    }

    /**
     * Method declaration
     * @param type
     * @return
     * @throws HsqlException
     */
    Object getValue(int type) throws HsqlException {

        resolve();

        Result r    = getResult(2);    // 2 records are (already) too much
        int    size = r.getSize();
        int    len  = r.getColumnCount();

        Trace.check(size == 1 && len == 1, Trace.SINGLE_VALUE_EXPECTED);

        Object o = r.rRoot.data[0];

        return r.metaData.colType[0] == type ? o
                                             : Column.convertObject(o, type);
    }

    /**
     * Prepares rResult having structure compatible with
     * internally building the set of rows returned from getResult().
     */
    void prepareResult() throws HsqlException {

        resolveAll();

        if (iGroupLen > 0) {    // has been set in Parser
            isGrouped        = true;
            groupColumnNames = new HashSet();

            for (int i = iResultLen; i < iResultLen + iGroupLen; i++) {
                eColumn[i].collectColumnName(groupColumnNames);
            }
        }

        int len = eColumn.length;

        resultMetaData = new Result.ResultMetaData(len);

        Result.ResultMetaData rmd = resultMetaData;

        // tony_lai@users having
        int groupByStart = iResultLen;
        int groupByEnd   = groupByStart + iGroupLen;
        int orderByStart = iHavingIndex >= 0 ? (iHavingIndex + 1)
                                             : groupByEnd;
        int orderByEnd   = orderByStart + iOrderLen;

        for (int i = 0; i < len; i++) {
            Expression e = eColumn[i];

            rmd.colType[i]  = e.getDataType();
            rmd.colSize[i]  = e.getColumnSize();
            rmd.colScale[i] = e.getColumnScale();

            if (e.isAggregate()) {
                isAggregated = true;
            }

            Trace.check(
                (i < groupByStart) || (i >= groupByEnd)
                || eColumn[i].canBeInGroupBy(), Trace.INVALID_GROUP_BY,
                                                eColumn[i]);
            Trace.check((i != iHavingIndex) || eColumn[i].isConditional(),
                        Trace.INVALID_HAVING, eColumn[i]);
            Trace.check(
                (i < orderByStart) || (i >= orderByEnd)
                || eColumn[i].canBeInOrderBy(), Trace.INVALID_ORDER_BY,
                                                eColumn[i]);

            if (i < iResultLen) {
                rmd.sLabel[i]        = e.getAlias();
                rmd.isLabelQuoted[i] = e.isAliasQuoted();
                rmd.sTable[i]        = e.getTableName();
                rmd.sName[i]         = e.getColumnName();

                if (rmd.isTableColumn(i)) {
                    rmd.nullability[i] = e.nullability;
                    rmd.isIdentity[i]  = e.isIdentity;
                    rmd.isWritable[i]  = e.isWritable;
                }

                rmd.sClassName[i] = e.getValueClassName();
            }
        }

        checkAggregateOrGroupByColumns(0, iResultLen);

        if (iHavingIndex >= 0) {
            checkAggregateOrGroupByColumns(iHavingIndex, iHavingIndex + 1);
        }

        checkAggregateOrGroupByColumns(orderByStart, orderByEnd);

        if (isDistinctSelect) {
            for (int i = orderByStart; i < orderByEnd; i++) {
                Trace.check(isSimilarIn(eColumn[i], 0, iResultLen),
                            Trace.INVALID_ORDER_BY_IN_DISTINCT_SELECT,
                            eColumn[i]);
            }
        }
    }

// fredt@users 20020130 - patch 471710 by fredt - LIMIT rewritten

/**
 * For SELECT LIMIT n m ....
 * find cases where the result does not have to be fully built and
 * set issimplemaxrows and adjust maxrows with LIMIT params.
 * Chnages made to apply LIMIT only to the containing SELECT
 * so they can be used as part of UNION and other set operations
 */
    private int getLimitCount(int maxrows) {

        if (maxrows == 0) {
            maxrows = limitCount;
        } else if (limitCount == 0) {
            limitCount = maxrows;
        } else {
            maxrows = limitCount = (maxrows > limitCount) ? limitCount
                                                          : maxrows;
        }

        boolean issimplemaxrows = false;

        if (maxrows != 0 && isDistinctSelect == false && isGrouped == false
                && sUnion == null && iOrderLen == 0) {
            issimplemaxrows = true;
        }

        return issimplemaxrows ? limitStart + maxrows
                               : Integer.MAX_VALUE;
    }

    /**
     * maxrow may be 0 to indicate no limit on the number of rows, or -1
     * to indicate 0 size result (used for pre-processing the selects in
     * view statements. positive values limit the size of the result set.
     *
     * @param maxrows
     * @return
     * @throws HsqlException
     */

// fredt@users 20020130 - patch 471710 by fredt - LIMIT rewritten
// for SELECT LIMIT n m DISTINCT
// fredt@users 20020804 - patch 580347 by dkkopp - view speedup
    Result getResult(int maxrows) throws HsqlException {

        if (resultMetaData == null) {
            prepareResult();
        }

        Result r = new Result(resultMetaData);

        buildResult(r, getLimitCount(maxrows));

        // the result is perhaps wider (due to group and order by)
        // so use the visible columns to remove duplicates
        if (isDistinctSelect) {
            r.removeDuplicates(iResultLen);
        }

        if (sUnion != null) {
            Result x = sUnion.getResult(0);

            switch (iUnionType) {

                case UNION :
                    r.append(x);
                    r.removeDuplicates(iResultLen);
                    break;

                case UNIONALL :
                    r.append(x);
                    break;

                case INTERSECT :
                    r.removeDifferent(x, iResultLen);
                    break;

                case EXCEPT :
                    r.removeSecond(x, iResultLen);
                    break;
            }
        }

        sortResult(r);

        // fredt - now there is no need for the sort and group columns
        r.setColumnCount(iResultLen);
        r.trimResult(limitStart, limitCount);

        return r;
    }

    private void sortResult(Result r) throws HsqlException {

        if (iOrderLen != 0) {
            int order[] = new int[iOrderLen];
            int way[]   = new int[iOrderLen];

            for (int i = iResultLen + (isGrouped ? iGroupLen
                                                 : 0), j = 0; j < iOrderLen;
                    i++, j++) {
                int colindex = i;

                // fredt - when a union, use the visible select columns for sort comparison
                if (sUnion != null) {
                    colindex = eColumn[i].orderColumnIndex;
                }

                order[j] = colindex;
                way[j]   = eColumn[i].isDescending() ? -1
                                                     : 1;
            }

            r.sortResult(order, way);
        }
    }

    /**
     * Check result columns for aggregate or group by violation.
     * <p>
     * If any result column is aggregated, then all result columns need to be
     * aggregated, unless it is included in the group by clause.
     */
    private void checkAggregateOrGroupByColumns(int start,
            int end) throws HsqlException {

        if (start < end) {
            HsqlArrayList colExps = new HsqlArrayList();

            for (int i = start; i < end; i++) {
                eColumn[i].collectInGroupByExpressions(colExps);
            }

            for (int i = 0, size = colExps.size(); i < size; i++) {
                Expression exp = (Expression) colExps.get(i);

                Trace.check(inAggregateOrGroupByClause(exp),
                            Trace.NOT_IN_AGGREGATE_OR_GROUP_BY, exp);
            }
        }
    }

    /**
     * Check if the given expression is acceptable in a select that may
     * include aggregate function and/or group by clause.
     * <p>
     * The expression is acceptable if:
     * <UL>
     * <LI>The select does not containt any aggregate function;
     * <LI>The expression itself can be included in an aggregate select;
     * <LI>The expression is defined in the group by clause;
     * <LI>All the columns in the expression are defined in the group by clause;
     * </UL)
     */
    private boolean inAggregateOrGroupByClause(Expression exp) {

        if ((!isAggregated) || exp.canBeInAggregate()) {
            return true;
        }

        if (!isGrouped) {
            return false;
        }

        return isSimilarIn(exp, iResultLen, iResultLen + iGroupLen)
               || allColumnsAreDefinedIn(exp, groupColumnNames);
    }

    /**
     * Check if the given expression is similar to any of the eColumn
     * expressions within the given range.
     */
    private boolean isSimilarIn(Expression exp, int start, int end) {

        for (int i = start; i < end; i++) {
            if (exp.similarTo(eColumn[i])) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if all the column names used in the given expression are defined
     * in the given defined column names.
     */
    boolean allColumnsAreDefinedIn(Expression exp, HashSet definedColumns) {

        HashSet colNames = new HashSet();

        exp.collectAllColumnNames(colNames);

        if ((colNames.size() > 0) && (definedColumns == null)) {
            return false;
        }

        Iterator i = colNames.iterator();

        while (i.hasNext()) {
            if (!definedColumns.contains(i.next())) {
                return false;
            }
        }

        return true;
    }

// fredt@users 20030810 - patch 1.7.2 - OUTER JOIN rewrite
    private void buildResult(Result r, int limitcount) throws HsqlException {

        GroupedResult gResult     = new GroupedResult(this, r);
        final int     len         = eColumn.length;
        final int     filter      = tFilter.length;
        boolean       first[]     = new boolean[filter];
        boolean       outerused[] = new boolean[filter];
        int           level       = 0;

        while (level >= 0 &&!isPreProcess) {

            // perform a join
            TableFilter t = tFilter[level];
            boolean     found;
            boolean     outerfound;

            if (!first[level]) {
                found = t.findFirst();

                // if outer join, and no inner result, get next outer row
                // nonJoinIsNull disallows getting the next outer row in some circumstances
                outerused[level] = outerfound = t.isOuterJoin &&!found
                                                &&!outerused[level]
                                                &&!t.nonJoinIsNull
                                                && t.nextOuter();
                first[level] = found;
            } else {
                found = t.next();
                outerused[level] = outerfound = t.isOuterJoin &&!found
                                                &&!first[level]
                                                &&!outerused[level]
                                                &&!t.nonJoinIsNull
                                                && t.nextOuter();
                first[level] = found;
            }

            if (!found &&!outerfound) {
                level--;

                continue;
            }

            if (level < filter - 1) {
                level++;

                continue;
            } else {
                while (outerused[level]) {
                    outerused[level--] = false;
                }
            }

            // apply condition
            if (eCondition == null || eCondition.test()) {
                Object row[] = new Object[len];

                // gets the group by column values first.
                for (int i = gResult.groupBegin; i < gResult.groupEnd; i++) {
                    row[i] = eColumn[i].getValue();
                }

                row = gResult.addRow(row);

                // Get all other values
                for (int i = 0; i < gResult.groupBegin; i++) {
                    row[i] = isAggregated && eColumn[i].isAggregate()
                             ? eColumn[i].getAggregatingValue(row[i])
                             : eColumn[i].getValue();
                }

                for (int i = gResult.groupEnd; i < len; i++) {
                    row[i] = isAggregated && eColumn[i].isAggregate()
                             ? eColumn[i].getAggregatingValue(row[i])
                             : eColumn[i].getValue();
                }

                if (gResult.size() >= limitcount) {
                    break;
                }
            }
        }

        if (isAggregated &&!isGrouped && gResult.size() == 0) {
            gResult.addRow(new Object[len]);
        }

        Iterator it = gResult.iterator();

        while (it.hasNext()) {
            Object[] row = (Object[]) it.next();

            if (isAggregated) {
                for (int i = 0; i < len; i++) {
                    if (eColumn[i].isAggregate()) {
                        row[i] = eColumn[i].getAggregatedValue(row[i]);
                    }
                }
            }

            if (iHavingIndex >= 0) {

                // The test value, either aggregate or not, is set already.
                // Removes the row that does not satisfy the HAVING
                // condition.
                if (!((Boolean) row[iHavingIndex]).booleanValue()) {
                    it.remove();
                }
            }
        }
    }

// boucherb@users 20030418 - patch 1.7.2 - fast execution for compiled statements
// -----------------------------------------------------------------------------
    boolean isResolved = false;

    void resolveAll() throws HsqlException {

        if (isResolved) {
            return;
        }

        resolve();
        checkResolved();

        if (sUnion != null) {
            if (sUnion.iResultLen != iResultLen) {
                throw Trace.error(Trace.COLUMN_COUNT_DOES_NOT_MATCH);
            }

            sUnion.resolveAll();
        }

        isResolved = true;
    }

    boolean isResolved() {
        return isResolved;
    }

// --
// boucherb@users 20030418 - patch 1.7.2 - explain plan support
// -----------------------------------------------------------------------------
    public String toString() {

        StringBuffer sb;

        // temporary :  it is currently unclear whether this may affect
        // later attempts to retrieve an actual result (calls getResult(1)
        // in preProcess mode).  Thus, toString() probably should not be called
        // on Select objects that will actually be used to retrieve results,
        // only on Select objects used by EXPLAIN PLAN FOR
        preProcess();

        sb = new StringBuffer();

        sb.append(super.toString()).append("[\n");

        if (sIntoTable != null) {
            sb.append("into table=[").append(sIntoTable.name).append("]\n");
        }

        sb.append("start=[").append(limitStart).append("]\n");
        sb.append("limit=[").append(limitCount).append("]\n");
        sb.append("isDistinctSelect=[").append(isDistinctSelect).append(
            "]\n");
        sb.append("isGrouped=[").append(isGrouped).append("]\n");
        sb.append("isAggregated=[").append(isAggregated).append("]\n");
        sb.append("columns=[");

        int columns = eColumn.length - iOrderLen;

        for (int i = 0; i < columns; i++) {
            sb.append(eColumn[i]);
        }

        sb.append("\n]\n");
        sb.append("tableFilters=[\n");

        for (int i = 0; i < tFilter.length; i++) {
            sb.append("[\n");
            sb.append(tFilter[i]);
            sb.append("\n]");
        }

        sb.append("]\n");
        sb.append("eCondition=[").append(eCondition).append("]\n");
        sb.append("havingCondition=[").append(havingCondition).append("]\n");
        sb.append("groupColumns=[").append(groupColumnNames).append("]\n");

        if (sUnion != null) {
            switch (iUnionType) {

                case EXCEPT :
                    sb.append(" EXCEPT ");
                    break;

                case INTERSECT :
                    sb.append(" INTERSECT ");
                    break;

                case UNION :
                    sb.append(" UNION ");
                    break;

                case UNIONALL :
                    sb.append(" UNION ALL ");
                    break;

                default :
                    sb.append(" UNKNOWN SET OPERATION ");
            }

            sb.append("[\n").append(sUnion).append("]\n");
        }

        return sb.toString();
    }

    // Used only by toString()
    private void preProcess() {

        boolean oldPreProcess;

        oldPreProcess = isPreProcess;
        isPreProcess  = true;

        try {
            getResult(1);
        } catch (HsqlException e) {}

        isPreProcess = oldPreProcess;
    }

    Result describeResult() {

        Result                r;
        Result.ResultMetaData rmd;
        Expression            e;

        r   = new Result(ResultConstants.DATA, iResultLen);
        rmd = r.metaData;

        for (int i = 0; i < iResultLen; i++) {
            e                    = eColumn[i];
            rmd.colType[i]       = e.getDataType();
            rmd.colSize[i]       = e.getColumnSize();
            rmd.colScale[i]      = e.getColumnScale();
            rmd.sLabel[i]        = e.getAlias();
            rmd.isLabelQuoted[i] = e.isAliasQuoted();
            rmd.sTable[i]        = e.getTableName();
            rmd.sName[i]         = e.getColumnName();

            if (rmd.isTableColumn(i)) {
                rmd.nullability[i] = e.nullability;
                rmd.isIdentity[i]  = e.isIdentity;
                rmd.isWritable[i]  = e.isWritable;
            }
        }

        return r;
    }
}
