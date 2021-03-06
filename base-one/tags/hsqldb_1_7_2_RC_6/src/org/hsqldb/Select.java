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
 * Copyright (c) 2001-2004, The HSQL Development Group
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

import org.hsqldb.HsqlNameManager.HsqlName;
import org.hsqldb.lib.HashMap;
import org.hsqldb.lib.HashSet;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.Iterator;

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
 * The compiled representation of an SQL SELECT.
 *
 * @version 1.7.2
 */
class Select {

    boolean               isDistinctSelect;
    boolean               isAggregated;
    private boolean       isGrouped;
    private HashSet       groupColumnNames;
    TableFilter           tFilter[];
    Expression            limitCondition;
    Expression            queryCondition;       // null means no condition
    Expression            havingCondition;      // null means none
    Expression            exprColumns[];        // 'result', 'group' and 'order' columns
    int                   iResultLen;           // number of columns that are 'result'
    int                   iGroupLen;            // number of columns that are 'group'
    int                   iHavingIndex = -1;    // -1 means no having
    int                   iOrderLen;            // number of columns that are 'order'
    Select                sUnion;               // null means no union select
    HsqlName              sIntoTable;           // null means not select..into
    int                   intoType = Table.MEMORY_TABLE;
    boolean               isIntoTableQuoted;
    int                   iUnionType;
    static final int      NOUNION   = 0,
                          UNION     = 1,
                          UNIONALL  = 2,
                          INTERSECT = 3,
                          EXCEPT    = 4;
    private int           limitStart;           // set only by the LIMIT keyword
    private int           limitCount;           // set only by the LIMIT keyword
    Result.ResultMetaData resultMetaData;

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
            String alias = exprColumns[i].getAlias();

            if (alias != null) {
                aliasMap.put(alias, exprColumns[i]);
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

        resolveTables();
        resolveTypes();
        setFilterConditions();
    }

    /**
     * Method declaration
     *
     *
     * @throws HsqlException
     */
    void resolveTables() throws HsqlException {

        int len = tFilter.length;

        for (int i = 0; i < len; i++) {
            resolveTables(tFilter[i]);
        }
    }

    /**
     * Sets the types of all the expressions that have so far resolved.
     *
     * @throws HsqlException
     */
    void resolveTypes() throws HsqlException {

        int len = exprColumns.length;

        for (int i = 0; i < len; i++) {
            exprColumns[i].resolveTypes();
        }

        if (queryCondition != null) {
            queryCondition.resolveTypes();
        }
    }

    /**
     * Resolves the tables for all the Expression in the Select object
     * if it is possible to do so with the given TableFilter.
     *
     * @param f
     *
     * @throws HsqlException
     */
    void resolveTables(TableFilter f) throws HsqlException {

        int len = exprColumns.length;

        for (int i = 0; i < len; i++) {
            exprColumns[i].resolveTables(f);
        }

        if (queryCondition != null) {
            queryCondition.resolveTables(f);
        }
    }

    private void setFilterConditions() throws HsqlException {

        if (queryCondition == null) {
            return;
        }

        for (int i = 0; i < tFilter.length; i++) {
            tFilter[i].setConditions(queryCondition);
        }
    }

    /**
     * Check all Expression have resolved. Return true or false as a result.
     * Throw if false and check parameter is true.
     *
     * @throws HsqlException
     */
    boolean checkResolved(boolean check) throws HsqlException {

        boolean result = true;
        int     len    = exprColumns.length;

        for (int i = 0; i < len; i++) {
            result = result && exprColumns[i].checkResolved(check);
        }

        if (queryCondition != null) {
            result = result && queryCondition.checkResolved(check);
        }

        return result;
    }

    /**
     * Removes all the TableFilters from the Expressions.
     *
     * @throws HsqlException
     */
/*
    void removeFilters() throws HsqlException {

        int len = eColumn.length;

        for (int i = 0; i < len; i++) {
            eColumn[i].removeFilters();
        }

        if (eCondition != null) {
            eCondition.removeFilters();
        }
    }
*/

    /**
     * Retruns a single value result or throws if the result has more than
     * one row with one value.
     *
     * @param type data type
     * @param session context
     * @return the single valued result
     * @throws HsqlException
     */
    Object getValue(int type, Session session) throws HsqlException {

        resolve();

        Result r    = getResult(2, session);    // 2 records are (already) too much
        int    size = r.getSize();
        int    len  = r.getColumnCount();

        if (size == 1 && len == 1) {
            Object o = r.rRoot.data[0];

            return r.metaData.colType[0] == type ? o
                                                 : Column.convertObject(o,
                                                 type);
        }

        HsqlException e = Trace.error(Trace.SINGLE_VALUE_EXPECTED);

        if (size == 0 && len == 1) {
            throw new HsqlInternalException(e);
        }

        throw e;
    }

    /**
     * Prepares rResult having structure compatible with
     * internally building the set of rows returned from getResult().
     */
    void prepareResult() throws HsqlException {

        resolveAll(true);

        if (iGroupLen > 0) {    // has been set in Parser
            isGrouped        = true;
            groupColumnNames = new HashSet();

            for (int i = iResultLen; i < iResultLen + iGroupLen; i++) {
                exprColumns[i].collectColumnName(groupColumnNames);
            }
        }

        int len = exprColumns.length;

        resultMetaData = new Result.ResultMetaData(len);

        Result.ResultMetaData rmd = resultMetaData;

        // tony_lai@users having
        int groupByStart = iResultLen;
        int groupByEnd   = groupByStart + iGroupLen;
        int orderByStart = iHavingIndex >= 0 ? (iHavingIndex + 1)
                                             : groupByEnd;
        int orderByEnd   = orderByStart + iOrderLen;

        for (int i = 0; i < len; i++) {
            Expression e = exprColumns[i];

            rmd.colType[i]  = e.getDataType();
            rmd.colSize[i]  = e.getColumnSize();
            rmd.colScale[i] = e.getColumnScale();

            if (e.isAggregate()) {
                isAggregated = true;
            }

            Trace.check(
                (i < groupByStart) || (i >= groupByEnd)
                || exprColumns[i].canBeInGroupBy(), Trace.INVALID_GROUP_BY,
                    exprColumns[i]);
            Trace.check(
                (i != iHavingIndex) || exprColumns[i].isConditional(),
                Trace.INVALID_HAVING, exprColumns[i]);
            Trace.check(
                (i < orderByStart) || (i >= orderByEnd)
                || exprColumns[i].canBeInOrderBy(), Trace.INVALID_ORDER_BY,
                    exprColumns[i]);

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

        /**
         * @todo - this test is too strict and disallows functions in ORDER BY
         * clause
         */
        if (isDistinctSelect) {
            for (int i = orderByStart; i < orderByEnd; i++) {
                Trace.check(isSimilarIn(exprColumns[i], 0, iResultLen),
                            Trace.INVALID_ORDER_BY_IN_DISTINCT_SELECT,
                            exprColumns[i]);
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
    private int getLimitCount(int maxrows) throws HsqlException {

        limitStart = limitCondition == null ? 0
                                            : ((Integer) limitCondition
                                            .getArg().getValue(null))
                                                .intValue();
        limitCount = limitCondition == null ? 0
                                            : ((Integer) limitCondition
                                            .getArg2().getValue(null))
                                                .intValue();

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
     * Retrieves the result of executing this Select.
     *
     * @param maxrows may be 0 to indicate no limit on the number of rows, or
     *      -1 to indicate 0 size result (used for pre-processing the selects
     *      in view statements. Positive values limit the size of the
     *      result set.
     * @return the result of executing this Select
     * @throws HsqlException if a database access error occurs
     */

// fredt@users 20020130 - patch 471710 by fredt - LIMIT rewritten
// for SELECT LIMIT n m DISTINCT
// fredt@users 20020804 - patch 580347 by dkkopp - view speedup
    Result getResult(int maxrows, Session session) throws HsqlException {

        if (resultMetaData == null) {
            prepareResult();
        }

        Result r = buildResult(getLimitCount(maxrows), session);

        // the result is perhaps wider (due to group and order by)
        // so use the visible columns to remove duplicates
        if (isDistinctSelect) {
            r.removeDuplicates(iResultLen);
        }

        if (sUnion != null) {
            Result x = sUnion.getResult(0, session);

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
                // also whenever a column alias is used
                if (exprColumns[i].orderColumnIndex != -1) {
                    colindex = exprColumns[i].orderColumnIndex;
                }

                order[j] = colindex;
                way[j]   = exprColumns[i].isDescending() ? -1
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
                exprColumns[i].collectInGroupByExpressions(colExps);
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

        if (isGrouped) {
            return isSimilarIn(exp, iResultLen, iResultLen + iGroupLen)
                   || allColumnsAreDefinedIn(exp, groupColumnNames);
        } else if (isAggregated) {
            return exp.canBeInAggregate();
        } else {
            return true;
        }
    }

    /**
     * Check if the given expression is similar to any of the eColumn
     * expressions within the given range.
     */
    private boolean isSimilarIn(Expression exp, int start, int end) {

        for (int i = start; i < end; i++) {
            if (exp.similarTo(exprColumns[i])) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if all the column names used in the given expression are defined
     * in the given defined column names.
     */
    static boolean allColumnsAreDefinedIn(Expression exp,
                                          HashSet definedColumns) {

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
    private Result buildResult(int limitcount,
                               Session session) throws HsqlException {

        GroupedResult gResult     = new GroupedResult(this, resultMetaData);
        final int     len         = exprColumns.length;
        final int     filter      = tFilter.length;
        boolean       first[]     = new boolean[filter];
        boolean       outerused[] = new boolean[filter];
        int           level       = 0;

        while (level >= 0) {

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
            if (queryCondition == null || queryCondition.test(session)) {
                try {
                    Object row[] = new Object[len];

                    // gets the group by column values first.
                    for (int i = gResult.groupBegin; i < gResult.groupEnd;
                            i++) {
                        row[i] = exprColumns[i].getValue(session);
                    }

                    row = gResult.getRow(row);

                    // Get all other values
                    for (int i = 0; i < gResult.groupBegin; i++) {
                        row[i] =
                            isAggregated && exprColumns[i].isAggregate()
                            ? exprColumns[i].updateAggregatingValue(row[i],
                                session)
                            : exprColumns[i].getValue(session);
                    }

                    for (int i = gResult.groupEnd; i < len; i++) {
                        row[i] =
                            isAggregated && exprColumns[i].isAggregate()
                            ? exprColumns[i].updateAggregatingValue(row[i],
                                session)
                            : exprColumns[i].getValue(session);
                    }

                    gResult.addRow(row);

                    if (gResult.size() >= limitcount) {
                        break;
                    }
                } catch (HsqlInternalException e) {
                    continue;
                }
            }
        }

        if (isAggregated &&!isGrouped && gResult.size() == 0) {
            Object row[] = new Object[len];

            for (int i = 0; i < len; i++) {
                row[i] = exprColumns[i].isAggregate() ? null
                                                      : exprColumns[i]
                                                      .getValue(session);
            }

            gResult.addRow(row);
        }

        Iterator it = gResult.iterator();

        while (it.hasNext()) {
            Object[] row = (Object[]) it.next();

            if (isAggregated) {
                for (int i = 0; i < len; i++) {
                    if (exprColumns[i].isAggregate()) {
                        row[i] = exprColumns[i].getAggregatedValue(row[i],
                                session);
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

        return gResult.getResult();
    }

    /**
     * Skeleton under development. Needs a lot of work.
     */
    public StringBuffer getDDL() throws HsqlException {

        StringBuffer sb = new StringBuffer();

        sb.append(Token.T_SELECT).append(' ');

        //limitStart;
        //limitCount;
        for (int i = 0; i < iResultLen; i++) {
            sb.append(exprColumns[i].getDDL());

            if (i < iResultLen - 1) {
                sb.append(',');
            }
        }

        sb.append(Token.T_FROM);

        for (int i = 0; i < tFilter.length; i++) {

            // find out if any expression in any of the filters isInJoin then use this form
            TableFilter filter = tFilter[i];

            // if any expression isInJoin
            if (i != 0) {
                if (filter.isOuterJoin) {
                    sb.append(Token.T_FROM).append(' ');
                    sb.append(Token.T_JOIN).append(' ');
                }

                // eStart and eEnd expressions
            }

            // otherwise use a comma delimited table list
            sb.append(',');
        }

        // if there are any expressions that are not isInJoin
        sb.append(' ').append(Token.T_WHERE).append(' ');

        for (int i = 0; i < tFilter.length; i++) {
            TableFilter filter = tFilter[i];

            // eStart and eEnd expressions that are not isInJoin
        }

        // if has GROUP BY
        sb.append(' ').append(Token.T_GROUP).append(' ');

        for (int i = iResultLen; i < iResultLen + iGroupLen; i++) {
            sb.append(exprColumns[i].getDDL());

            if (i < iResultLen + iGroupLen - 1) {
                sb.append(',');
            }
        }

        // if has HAVING
        sb.append(' ').append(Token.T_HAVING).append(' ');

        for (int i = iHavingIndex; i < iHavingIndex + exprColumns.length;
                i++) {
            sb.append(exprColumns[i].getDDL());

            if (i < iResultLen + iGroupLen - 1) {
                sb.append(',');
            }
        }

        if (sUnion != null) {
            switch (iUnionType) {

                case EXCEPT :
                    sb.append(' ').append(Token.T_EXCEPT).append(' ');
                    break;

                case INTERSECT :
                    sb.append(' ').append(Token.T_INTERSECT).append(' ');
                    break;

                case UNION :
                    sb.append(' ').append(Token.T_UNION).append(' ');
                    break;

                case UNIONALL :
                    sb.append(' ').append(Token.T_UNION).append(' ').append(
                        Token.T_ALL).append(' ');
                    break;
            }
        }

        // if has ORDER BY
        int groupByEnd   = iResultLen + iGroupLen;
        int orderByStart = iHavingIndex >= 0 ? (iHavingIndex + 1)
                                             : groupByEnd;
        int orderByEnd   = orderByStart + iOrderLen;

        sb.append(' ').append(Token.T_ORDER).append(Token.T_BY).append(' ');

        for (int i = orderByStart; i < orderByEnd; i++) {
            sb.append(exprColumns[i].getDDL());

            if (i < iResultLen + iGroupLen - 1) {
                sb.append(',');
            }
        }

        return sb;
    }

    boolean isResolved = false;

    boolean resolveAll(boolean check) throws HsqlException {

        boolean result = true;

        if (isResolved) {
            return true;
        }

        resolve();

        result = result && checkResolved(check);

        if (sUnion != null) {
            if (sUnion.iResultLen != iResultLen) {
                throw Trace.error(Trace.COLUMN_COUNT_DOES_NOT_MATCH);
            }

            sUnion.resolveAll(check);
        }

        isResolved = result;

        return result;
    }

    boolean isResolved() {
        return isResolved;
    }

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

        if (limitCondition != null) {
            sb.append("start=[").append(limitCondition.getArg()).append(
                "]\n");
            sb.append("limit=[").append(limitCondition.getArg2()).append(
                "]\n");
        }

        sb.append("isDistinctSelect=[").append(isDistinctSelect).append(
            "]\n");
        sb.append("isGrouped=[").append(isGrouped).append("]\n");
        sb.append("isAggregated=[").append(isAggregated).append("]\n");
        sb.append("columns=[");

        int columns = exprColumns.length - iOrderLen;

        for (int i = 0; i < columns; i++) {
            sb.append(exprColumns[i]);
        }

        sb.append("\n]\n");
        sb.append("tableFilters=[\n");

        for (int i = 0; i < tFilter.length; i++) {
            sb.append("[\n");
            sb.append(tFilter[i]);
            sb.append("\n]");
        }

        sb.append("]\n");
        sb.append("eCondition=[").append(queryCondition).append("]\n");
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

        try {
            getResult(1, null);
        } catch (HsqlException e) {}
    }

    Result describeResult() {

        Result                r;
        Result.ResultMetaData rmd;
        Expression            e;

        r   = new Result(ResultConstants.DATA, iResultLen);
        rmd = r.metaData;

        for (int i = 0; i < iResultLen; i++) {
            e                    = exprColumns[i];
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
