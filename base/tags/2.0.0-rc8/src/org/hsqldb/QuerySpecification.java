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

import org.hsqldb.HsqlNameManager.HsqlName;
import org.hsqldb.HsqlNameManager.SimpleName;
import org.hsqldb.ParserDQL.CompileContext;
import org.hsqldb.RangeVariable.RangeIteratorRight;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.index.Index;
import org.hsqldb.lib.ArrayListIdentity;
import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.HashMappedList;
import org.hsqldb.lib.HashSet;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.HsqlList;
import org.hsqldb.lib.IntValueHashMap;
import org.hsqldb.lib.OrderedHashSet;
import org.hsqldb.lib.OrderedIntHashSet;
import org.hsqldb.lib.Set;
import org.hsqldb.navigator.RangeIterator;
import org.hsqldb.navigator.RowSetNavigatorData;
import org.hsqldb.navigator.RowSetNavigatorDataTable;
import org.hsqldb.result.Result;
import org.hsqldb.result.ResultMetaData;
import org.hsqldb.result.ResultProperties;
import org.hsqldb.types.Type;
import org.hsqldb.types.Types;

/**
 * Implementation of an SQL query specification, including SELECT.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 *
 * @version 1.9.0
 * @since 1.9.0
 */
public class QuerySpecification extends QueryExpression {

    private final static int[] defaultLimits = new int[] {
        0, Integer.MAX_VALUE, Integer.MAX_VALUE
    };

    //
    public int            resultRangePosition;
    public boolean        isDistinctSelect;
    public boolean        isAggregated;
    public boolean        isGrouped;
    RangeVariable[]       rangeVariables;
    private HsqlArrayList rangeVariableList;
    Expression            queryCondition;
    Expression            checkQueryCondition;
    private Expression    havingCondition;
    Expression[]          exprColumns;
    private HsqlArrayList exprColumnList;
    public int            indexLimitVisible;
    private int           indexLimitRowId;
    private int           groupByColumnCount;    // columns in 'group by'
    private int           havingColumnCount;     // columns in 'having' (0 or 1)
    private int           indexStartHaving;
    public int            indexStartOrderBy;
    private int           indexStartAggregates;
    private int           indexLimitExpressions;
    private int           indexLimitData;
    private boolean       hasRowId;
    private boolean       hasMemoryRow;

    //
    public boolean  isUniqueResultRows;
    private boolean simpleLimit = true;          // true if maxrows can be uses as is

    //
    Type[]                    columnTypes;
    private ArrayListIdentity aggregateSet;

    //
    private ArrayListIdentity resolvedSubqueryExpressions = null;

    //
    //
    private boolean[] aggregateCheck;

    //
    private OrderedHashSet tempSet = new OrderedHashSet();

    //
    int[]                  columnMap;
    private Table          baseTable;
    private OrderedHashSet conditionTables;      // for view super-view references

    //
    public Index groupIndex;

    //
    QuerySpecification(Session session, Table table,
                       CompileContext compileContext) {

        this(compileContext);

        RangeVariable range = new RangeVariable(table, null, null, null,
            compileContext);

        range.addTableColumns(exprColumnList, 0, null);

        indexLimitVisible = exprColumnList.size();

        addRangeVariable(range);
        resolveReferences(session);
        resolveTypes(session);

        sortAndSlice = SortAndSlice.noSort;
    }

    QuerySpecification(CompileContext compileContext) {

        super(compileContext);

        this.compileContext = compileContext;
        resultRangePosition = compileContext.getNextRangeVarIndex();
        rangeVariableList   = new HsqlArrayList();
        exprColumnList      = new HsqlArrayList();
        sortAndSlice        = SortAndSlice.noSort;
    }

    void addRangeVariable(RangeVariable rangeVar) {
        rangeVariableList.add(rangeVar);
    }

    private void finaliseRangeVariables() {

        if (rangeVariables == null
                || rangeVariables.length < rangeVariableList.size()) {
            rangeVariables = new RangeVariable[rangeVariableList.size()];

            rangeVariableList.toArray(rangeVariables);
        }
    }

    void addSelectColumnExpression(Expression e) {

        if (e.getType() == OpTypes.ROW) {
            throw Error.error(ErrorCode.X_42564);
        }

        exprColumnList.add(e);

        indexLimitVisible++;
    }

    void addQueryCondition(Expression e) {
        queryCondition = e;
    }

    void addGroupByColumnExpression(Expression e) {

        if (e.getType() == OpTypes.ROW) {
            throw Error.error(ErrorCode.X_42564);
        }

        exprColumnList.add(e);

        isGrouped = true;

        groupByColumnCount++;
    }

    void addHavingExpression(Expression e) {

        exprColumnList.add(e);

        havingCondition   = e;
        havingColumnCount = 1;
    }

    void addSortAndSlice(SortAndSlice sortAndSlice) {
        this.sortAndSlice = sortAndSlice;
    }

    public void resolveReferences(Session session) {

        finaliseRangeVariables();
        resolveColumnReferencesForAsterisk();
        finaliseColumns();
        resolveColumnReferences();

        unionColumnTypes = new Type[indexLimitVisible];
        unionColumnMap   = new int[indexLimitVisible];

        ArrayUtil.fillSequence(unionColumnMap);
    }

    /**
     * Resolves all column expressions in the GROUP BY clause and beyond.
     * Replaces any alias column expression in the ORDER BY cluase
     * with the actual select column expression.
     */
    private void resolveColumnReferences() {

        if (isDistinctSelect || isGrouped) {
            acceptsSequences = false;
        }

        for (int i = 0; i < rangeVariables.length; i++) {
            Expression e = rangeVariables[i].getJoinCondition();

            if (e == null) {
                continue;
            }

            resolveColumnReferencesAndAllocate(e, i + 1, false);
        }

        resolveColumnReferencesAndAllocate(queryCondition,
                                           rangeVariables.length, false);

        if (resolvedSubqueryExpressions != null) {

            // subqueries in conditions not to be converted to SIMPLE_COLUMN
            resolvedSubqueryExpressions.setSize(0);
        }

        for (int i = 0; i < indexLimitVisible; i++) {
            resolveColumnReferencesAndAllocate(exprColumns[i],
                                               rangeVariables.length,
                                               acceptsSequences);
        }

        for (int i = indexLimitVisible; i < indexStartHaving; i++) {
            exprColumns[i] = resolveColumnReferencesInGroupBy(exprColumns[i]);
        }

        for (int i = indexStartHaving; i < indexStartOrderBy; i++) {
            resolveColumnReferencesAndAllocate(exprColumns[i],
                                               rangeVariables.length, false);
        }

        resolveColumnRefernecesInOrderBy(sortAndSlice);
    }

    void resolveColumnRefernecesInOrderBy(SortAndSlice sortAndSlice) {

        // replace the aliases with expressions
        // replace column names with expressions and resolve the table columns
        int orderCount = sortAndSlice.getOrderLength();

        for (int i = 0; i < orderCount; i++) {
            ExpressionOrderBy e =
                (ExpressionOrderBy) sortAndSlice.exprList.get(i);

            replaceColumnIndexInOrderBy(e);

            if (e.getLeftNode().queryTableColumnIndex != -1) {
                continue;
            }

            if (sortAndSlice.sortUnion) {
                if (e.getLeftNode().getType() != OpTypes.COLUMN) {
                    throw Error.error(ErrorCode.X_42576);
                }
            }

            e.replaceAliasInOrderBy(exprColumns, indexLimitVisible);
            resolveColumnReferencesAndAllocate(e, rangeVariables.length,
                                               false);
        }

        sortAndSlice.prepare(this);
    }

    private boolean resolveColumnReferences(Expression e, int rangeCount,
            boolean withSequences) {

        if (e == null) {
            return true;
        }

        int oldSize = unresolvedExpressions == null ? 0
                                                    : unresolvedExpressions
                                                        .size();

        unresolvedExpressions = e.resolveColumnReferences(rangeVariables,
                rangeCount, unresolvedExpressions, withSequences);

        int newSize = unresolvedExpressions == null ? 0
                                                    : unresolvedExpressions
                                                        .size();

        return oldSize == newSize;
    }

    private void resolveColumnReferencesForAsterisk() {

        for (int pos = 0; pos < indexLimitVisible; ) {
            Expression e = (Expression) (exprColumnList.get(pos));

            if (e.getType() == OpTypes.MULTICOLUMN) {
                exprColumnList.remove(pos);

                String tablename = ((ExpressionColumn) e).getTableName();

                if (tablename == null) {
                    addAllJoinedColumns(e);
                } else {
                    int rangeIndex =
                        e.findMatchingRangeVariableIndex(rangeVariables);

                    if (rangeIndex == -1) {
                        throw Error.error(ErrorCode.X_42501, tablename);
                    }

                    RangeVariable range   = rangeVariables[rangeIndex];
                    HashSet       exclude = getAllNamedJoinColumns();

                    range.addTableColumns(e, exclude);
                }

                for (int i = 0; i < e.nodes.length; i++) {
                    exprColumnList.add(pos, e.nodes[i]);

                    pos++;
                }

                indexLimitVisible += e.nodes.length - 1;
            } else {
                pos++;
            }
        }
    }

    private void resolveColumnReferencesAndAllocate(Expression expression,
            int count, boolean withSequences) {

        if (expression == null) {
            return;
        }

        HsqlList list = expression.resolveColumnReferences(rangeVariables,
            count, null, withSequences);

        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Expression e = (Expression) list.get(i);
                boolean    resolved;

                if (e.isSelfAggregate()) {
                    resolved = resolveColumnReferences(e.getLeftNode(), count,
                                                       false);
                } else {
                    resolved = resolveColumnReferences(e, count,
                                                       withSequences);
                }

                if (resolved) {
                    if (e.isSelfAggregate()) {
                        if (aggregateSet == null) {
                            aggregateSet = new ArrayListIdentity();
                        }

                        aggregateSet.add(e);

                        isAggregated           = true;
                        expression.isAggregate = true;
                    }

                    if (resolvedSubqueryExpressions == null) {
                        resolvedSubqueryExpressions = new ArrayListIdentity();
                    }

                    resolvedSubqueryExpressions.add(e);
                } else {
                    if (unresolvedExpressions == null) {
                        unresolvedExpressions = new ArrayListIdentity();
                    }

                    unresolvedExpressions.add(e);
                }
            }
        }
    }

    private Expression resolveColumnReferencesInGroupBy(
            Expression expression) {

        if (expression == null) {
            return null;
        }

        HsqlList list = expression.resolveColumnReferences(rangeVariables,
            rangeVariables.length, null, false);

        if (list != null) {

            // if not resolved, resolve as simple alias
            if (expression.getType() == OpTypes.COLUMN) {
                Expression resolved =
                    expression.replaceAliasInOrderBy(exprColumns,
                                                     indexLimitVisible);

                if (resolved != expression) {
                    return resolved;
                }
            }

            // resolve and allocate to throw exception
            resolveColumnReferencesAndAllocate(expression,
                                               rangeVariables.length, false);
        }

        return expression;
    }

    private HashSet getAllNamedJoinColumns() {

        HashSet set = null;

        for (int i = 0; i < rangeVariableList.size(); i++) {
            RangeVariable range = (RangeVariable) rangeVariableList.get(i);

            if (range.namedJoinColumns != null) {
                if (set == null) {
                    set = new HashSet();
                }

                set.addAll(range.namedJoinColumns);
            }
        }

        return set;
    }

    public Expression getEquiJoinExpressions(OrderedHashSet nameSet,
            RangeVariable rightRange, boolean fullList) {

        HashSet        set             = new HashSet();
        Expression     result          = null;
        OrderedHashSet joinColumnNames = new OrderedHashSet();

        for (int i = 0; i < rangeVariableList.size(); i++) {
            RangeVariable  range = (RangeVariable) rangeVariableList.get(i);
            HashMappedList columnList = range.rangeTable.columnList;

            for (int j = 0; j < columnList.size(); j++) {
                ColumnSchema column       = (ColumnSchema) columnList.get(j);
                String       name         = range.getColumnAlias(j);
                boolean      columnInList = nameSet.contains(name);
                boolean namedJoin = range.namedJoinColumns != null
                                    && range.namedJoinColumns.contains(name);
                boolean repeated = !namedJoin && !set.add(name);

                if (repeated && (!fullList || columnInList)) {
                    throw Error.error(ErrorCode.X_42578, name);
                }

                if (!columnInList) {
                    continue;
                }

                joinColumnNames.add(name);

                int leftPosition =
                    range.rangeTable.getColumnIndex(column.getNameString());
                int rightPosition = rightRange.rangeTable.getColumnIndex(name);
                Expression e = new ExpressionLogical(range, leftPosition,
                                                     rightRange,
                                                     rightPosition);

                result = ExpressionLogical.andExpressions(result, e);

                ExpressionColumn col = range.getColumnExpression(name);

                if (col == null) {
                    col = new ExpressionColumn(new Expression[] {
                        e.getLeftNode(), e.getRightNode()
                    }, name);

                    range.addNamedJoinColumnExpression(name, col);
                } else {
                    col.nodes = (Expression[]) ArrayUtil.resizeArray(col.nodes,
                            col.nodes.length + 1);
                    col.nodes[col.nodes.length - 1] = e.getRightNode();
                }

                rightRange.addNamedJoinColumnExpression(name, col);
            }
        }

        if (fullList && !joinColumnNames.containsAll(nameSet)) {
            throw Error.error(ErrorCode.X_42501);
        }

        rightRange.addNamedJoinColumns(joinColumnNames);

        return result;
    }

    private void addAllJoinedColumns(Expression e) {

        HsqlArrayList list = new HsqlArrayList();

        for (int i = 0; i < rangeVariables.length; i++) {
            rangeVariables[i].addTableColumns(list);
        }

        Expression[] nodes = new Expression[list.size()];

        list.toArray(nodes);

        e.nodes = nodes;
    }

    private void finaliseColumns() {

        indexLimitRowId   = indexLimitVisible;
        indexStartHaving  = indexLimitRowId + groupByColumnCount;
        indexStartOrderBy = indexStartHaving + havingColumnCount;
        indexStartAggregates = indexStartOrderBy
                               + sortAndSlice.getOrderLength();
        indexLimitData = indexLimitExpressions = indexStartAggregates;
        exprColumns    = new Expression[indexLimitExpressions];

        exprColumnList.toArray(exprColumns);

        for (int i = 0; i < indexLimitVisible; i++) {
            exprColumns[i].queryTableColumnIndex = i;
        }

        if (sortAndSlice.hasOrder()) {
            for (int i = 0; i < sortAndSlice.getOrderLength(); i++) {
                exprColumns[indexStartOrderBy + i] =
                    (Expression) sortAndSlice.exprList.get(i);
            }
        }
    }

    private void replaceColumnIndexInOrderBy(Expression orderBy) {

        Expression e = orderBy.getLeftNode();

        if (e.getType() != OpTypes.VALUE) {
            return;
        }

        if (e.getDataType().typeCode == Types.SQL_INTEGER) {
            int i = ((Integer) e.getValue(null)).intValue();

            if (0 < i && i <= indexLimitVisible) {
                orderBy.setLeftNode(exprColumns[i - 1]);

                return;
            }
        }

        throw Error.error(ErrorCode.X_42576);
    }

    void collectRangeVariables(RangeVariable[] rangeVars, Set set) {

        for (int i = 0; i < indexStartAggregates; i++) {
            exprColumns[i].collectRangeVariables(rangeVars, set);
        }

        if (queryCondition != null) {
            queryCondition.collectRangeVariables(rangeVars, set);
        }

        if (havingCondition != null) {
            havingCondition.collectRangeVariables(rangeVars, set);
        }
    }

    public boolean hasReference(RangeVariable range) {

        if (unresolvedExpressions == null) {
            return false;
        }

        for (int i = 0; i < unresolvedExpressions.size(); i++) {
            if (((Expression) unresolvedExpressions.get(i)).hasReference(
                    range)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sets the types of all the expressions used in this SELECT list.
     */
    public void resolveExpressionTypes(Session session) {

        for (int i = 0; i < indexStartAggregates; i++) {
            Expression e = exprColumns[i];

            e.resolveTypes(session, null);

            if (e.getType() == OpTypes.ROW) {
                throw Error.error(ErrorCode.X_42564);
            }
        }

        for (int i = 0, len = rangeVariables.length; i < len; i++) {
            Expression e = rangeVariables[i].getJoinCondition();

            if (e != null) {
                e.resolveTypes(session, null);

                if (e.getDataType() != Type.SQL_BOOLEAN) {
                    throw Error.error(ErrorCode.X_42568);
                }
            }
        }

        if (queryCondition != null) {
            queryCondition.resolveTypes(session, null);

            if (queryCondition.getDataType() != Type.SQL_BOOLEAN) {
                throw Error.error(ErrorCode.X_42568);
            }
        }

        if (havingCondition != null) {
            havingCondition.resolveTypes(session, null);

            if (havingCondition.getDataType() != Type.SQL_BOOLEAN) {
                throw Error.error(ErrorCode.X_42568);
            }
        }
    }

    private void resolveAggregates() {

        tempSet.clear();

        if (isAggregated) {
            aggregateCheck = new boolean[indexStartAggregates];

            tempSet.addAll(aggregateSet);

            indexLimitData = indexLimitExpressions = exprColumns.length
                    + tempSet.size();
            exprColumns = (Expression[]) ArrayUtil.resizeArray(exprColumns,
                    indexLimitExpressions);

            for (int i = indexStartAggregates, j = 0;
                    i < indexLimitExpressions; i++, j++) {
                ExpressionAggregate e = (ExpressionAggregate) tempSet.get(j);

                exprColumns[i]          = new ExpressionAggregate(e);
                exprColumns[i].dataType = e.dataType;
            }

            tempSet.clear();
        }
    }

    public boolean areColumnsResolved() {
        return unresolvedExpressions == null
               || unresolvedExpressions.isEmpty();
    }

    private void setRangeVariableConditions() {

        RangeVariableResolver rangeResolver =
            new RangeVariableResolver(rangeVariables, queryCondition,
                                      compileContext);

        rangeResolver.processConditions();

        rangeVariables = rangeResolver.rangeVariables;

//        queryCondition = null;
    }

    public void resolveTypes(Session session) {

        if (isResolved) {
            return;
        }

        resolveTypesPartOne(session);
        resolveTypesPartTwo(session);
        ArrayUtil.copyArray(resultTable.colTypes, unionColumnTypes,
                            unionColumnTypes.length);

        for (int i = 0; i < indexStartHaving; i++) {
            if (exprColumns[i].dataType == null) {
                throw Error.error(ErrorCode.X_42567);
            }
        }

        isResolved = true;

        return;
    }

    void resolveTypesPartOne(Session session) {

        resolveExpressionTypes(session);
        setRangeVariableConditions();
        resolveAggregates();

        for (int i = 0; i < unionColumnMap.length; i++) {
            unionColumnTypes[i] = Type.getAggregateType(unionColumnTypes[i],
                    exprColumns[i].getDataType());
        }
    }

    void resolveTypesPartTwo(Session session) {

        resolveGroups();

        for (int i = 0; i < unionColumnMap.length; i++) {
            Type type = unionColumnTypes[unionColumnMap[i]];

            if (type == null) {
                throw Error.error(ErrorCode.X_42567);
            }

            exprColumns[unionColumnMap[i]].setDataType(session, type);
        }

        for (int i = 0; i < indexStartHaving; i++) {
            if (exprColumns[i].dataType == null) {
                throw Error.error(ErrorCode.X_42567);
            }
        }

        checkLobUsage();
        setReferenceableColumns();
        setUpdatability();
        createResultMetaData();
        createTable(session);

        if (isUpdatable) {
            getMergedSelect();
        }

        sortAndSlice.setSortRange(this);
    }

    void checkLobUsage() {

        if (!isDistinctSelect && !isGrouped) {
            return;
        }

        for (int i = 0; i < indexStartHaving; i++) {
            if (exprColumns[i].dataType.isLobType()) {
                throw Error.error(ErrorCode.X_42534);
            }
        }
    }

    private void resolveGroups() {

        // - 1.9.0 is standard compliant but has more extended support for
        //   referencing columns
        // - check there is no direct aggregate expression in group by
        // - check each expression in select list can be
        //   decomposed into the expressions in group by or any aggregates
        //   this allows direct function of group by expressions, but
        //   doesn't allow indirect functions. e.g.
        //     select 2*abs(cola) , sum(colb) from t group by abs(cola) // ok
        //     select 2*(cola + 10) , sum(colb) from t group by cola + 10 // ok
        //     select abs(cola) , sum(colb) from t group by cola // ok
        //     select 2*cola + 20 , sum(colb) from t group by cola + 10 // not allowed although correct
        //     select cola , sum(colb) from t group by abs(cola) // not allowed because incorrect
        // - group by can introduce invisible, derived columns into the query table
        // - check the having expression can be decomposed into
        //   select list expresions plus group by expressions
        // - having cannot introduce additional, derived columns
        // - having cannot reference columns not in the select or group by list
        // - if there is any aggregate in select list but no group by, no
        //   non-aggregates is allowed
        // - check order by columns
        // - if distinct select, order by must be composed of the select list columns
        // - if grouped by, then order by should be decomposed into the
        //   select list plus group by list
        // - references to column aliases are allowed only in order by (Standard
        //   compliance) and take precendence over references to non-alias
        //   column names.
        // - references to table / correlation and column list in correlation
        //   names are handled according to the Standard
        //  fredt@users
        tempSet.clear();

        if (isGrouped) {
            for (int i = indexLimitVisible;
                    i < indexLimitVisible + groupByColumnCount; i++) {
                Expression.collectAllExpressions(
                    tempSet, exprColumns[i], Expression.aggregateFunctionSet,
                    Expression.subqueryExpressionSet);

                if (!tempSet.isEmpty()) {
                    throw Error.error(ErrorCode.X_42572,
                                      ((Expression) tempSet.get(0)).getSQL());
                }
            }

            for (int i = 0; i < indexLimitVisible; i++) {
                if (!exprColumns[i].isComposedOf(
                        exprColumns, indexLimitVisible,
                        indexLimitVisible + groupByColumnCount,
                        Expression.subqueryAggregateExpressionSet)) {
                    tempSet.add(exprColumns[i]);
                }
            }

            if (!tempSet.isEmpty() && !resolveForGroupBy(tempSet)) {
                throw Error.error(ErrorCode.X_42574,
                                  ((Expression) tempSet.get(0)).getSQL());
            }
        } else if (isAggregated) {
            for (int i = 0; i < indexLimitVisible; i++) {
                Expression.collectAllExpressions(
                    tempSet, exprColumns[i], Expression.columnExpressionSet,
                    Expression.aggregateFunctionSet);

                if (!tempSet.isEmpty()) {
                    throw Error.error(ErrorCode.X_42574,
                                      ((Expression) tempSet.get(0)).getSQL());
                }
            }
        }

        tempSet.clear();

        if (havingCondition != null) {
            if (unresolvedExpressions != null) {
                tempSet.addAll(unresolvedExpressions);
            }

            for (int i = indexLimitVisible;
                    i < indexLimitVisible + groupByColumnCount; i++) {
                tempSet.add(exprColumns[i]);
            }

            if (!havingCondition.isComposedOf(
                    tempSet, Expression.subqueryAggregateExpressionSet)) {
                throw Error.error(ErrorCode.X_42573);
            }

            tempSet.clear();
        }

        if (isDistinctSelect) {
            int orderCount = sortAndSlice.getOrderLength();

            for (int i = 0; i < orderCount; i++) {
                Expression e = (Expression) sortAndSlice.exprList.get(i);

                if (e.queryTableColumnIndex != -1) {
                    continue;
                }

                if (!e.isComposedOf(exprColumns, 0, indexLimitVisible,
                                    Expression.emptyExpressionSet)) {
                    throw Error.error(ErrorCode.X_42576);
                }
            }
        }

        if (isGrouped) {
            int orderCount = sortAndSlice.getOrderLength();

            for (int i = 0; i < orderCount; i++) {
                Expression e = (Expression) sortAndSlice.exprList.get(i);

                if (e.queryTableColumnIndex != -1) {
                    continue;
                }

                if (!e.isAggregate
                        && !e.isComposedOf(
                            exprColumns, 0,
                            indexLimitVisible + groupByColumnCount,
                            Expression.emptyExpressionSet)) {
                    throw Error.error(ErrorCode.X_42576);
                }
            }
        }

        if (isDistinctSelect || isGrouped) {
            simpleLimit = false;
        }

        if (!isAggregated) {
            return;
        }

        OrderedHashSet expressions       = new OrderedHashSet();
        OrderedHashSet columnExpressions = new OrderedHashSet();

        for (int i = indexStartAggregates; i < indexLimitExpressions; i++) {
            Expression e = exprColumns[i];
            Expression c = new ExpressionColumn(e, i, resultRangePosition);

            expressions.add(e);
            columnExpressions.add(c);
        }

        for (int i = 0; i < indexStartHaving; i++) {
            if (exprColumns[i].isAggregate) {
                continue;
            }

            Expression e = exprColumns[i];

            if (expressions.add(e)) {
                Expression c = new ExpressionColumn(e, i, resultRangePosition);

                columnExpressions.add(c);
            }
        }

        // order by with aggregate
        int orderCount = sortAndSlice.getOrderLength();

        for (int i = 0; i < orderCount; i++) {
            Expression e = (Expression) sortAndSlice.exprList.get(i);

            if (e.getLeftNode().isAggregate) {
                e.isAggregate = true;
            }
        }

        for (int i = indexStartOrderBy; i < indexStartAggregates; i++) {
            if (exprColumns[i].getLeftNode().isAggregate) {
                exprColumns[i].isAggregate = true;
            }
        }

        for (int i = 0; i < indexStartAggregates; i++) {
            Expression e = exprColumns[i];

            if (!e.isAggregate) {
                continue;
            }

            aggregateCheck[i] = true;

            e.convertToSimpleColumn(expressions, columnExpressions);
        }

        for (int i = 0; i < aggregateSet.size(); i++) {
            Expression e = (Expression) aggregateSet.get(i);

            e.convertToSimpleColumn(expressions, columnExpressions);
        }

        if (resolvedSubqueryExpressions != null) {
            for (int i = 0; i < resolvedSubqueryExpressions.size(); i++) {
                Expression e = (Expression) resolvedSubqueryExpressions.get(i);

                e.convertToSimpleColumn(expressions, columnExpressions);
            }
        }
    }

    boolean resolveForGroupBy(HsqlList unresolvedSet) {

        for (int i = indexLimitVisible;
                i < indexLimitVisible + groupByColumnCount; i++) {
            Expression e = exprColumns[i];

            if (e.getType() == OpTypes.COLUMN) {
                RangeVariable range    = e.getRangeVariable();
                int           colIndex = e.getColumnIndex();

                range.columnsInGroupBy[colIndex] = true;
            }
        }

        for (int i = 0; i < rangeVariables.length; i++) {
            RangeVariable range = rangeVariables[i];

            range.hasKeyedColumnInGroupBy =
                range.rangeTable.getUniqueNotNullColumnGroup(
                    range.columnsInGroupBy) != null;
        }

        OrderedHashSet set = null;

        for (int i = 0; i < unresolvedSet.size(); i++) {
            Expression e = (Expression) unresolvedSet.get(i);

            set = e.getUnkeyedColumns(set);
        }

        return set == null;
    }

    private int getLimitStart(Session session) {

        if (sortAndSlice.limitCondition != null) {
            Integer limit =
                (Integer) sortAndSlice.limitCondition.getLeftNode().getValue(
                    session);

            if (limit == null || limit.intValue() < 0) {
                throw Error.error(ErrorCode.X_2201X);
            }

            return limit.intValue();
        }

        return 0;
    }

    int[] getLimits(Session session, int maxRows) {

        int     skipRows   = 0;
        int     limitRows  = Integer.MAX_VALUE;
        int     limitFetch = Integer.MAX_VALUE;
        boolean hasLimits  = false;

        if (sortAndSlice.hasLimit()) {
            Integer value =
                (Integer) sortAndSlice.limitCondition.getLeftNode().getValue(
                    session);

            if (value == null || value.intValue() < 0) {
                throw Error.error(ErrorCode.X_2201X);
            }

            skipRows  = value.intValue();
            hasLimits = skipRows != 0;

            if (sortAndSlice.limitCondition.getRightNode() != null) {
                value =
                    (Integer) sortAndSlice.limitCondition.getRightNode().getValue(
                        session);

                if (value == null || value.intValue() <= 0) {
                    throw Error.error(ErrorCode.X_2201W);
                }

                if (value.intValue() == 0) {
                    limitRows = Integer.MAX_VALUE;
                }
                else {
                    limitRows = value.intValue();
                    hasLimits = true;
                }
            }
        }

        if (maxRows != 0) {
            if (maxRows < limitRows) {
                limitRows = maxRows;
            }

            hasLimits = true;
        }

        if (hasLimits && simpleLimit
                && (!sortAndSlice.hasOrder() || sortAndSlice.skipSort)
                && (!sortAndSlice.hasLimit() || sortAndSlice.skipFullResult)) {
            if (limitFetch - skipRows > limitRows) {
                limitFetch = skipRows + limitRows;
            }
        }

        return hasLimits ? new int[] {
            skipRows, limitRows, limitFetch
        }
                         : defaultLimits;
    }

    /**
     * Returns the result of executing this Select.
     *
     * @param maxrows may be 0 to indicate no limit on the number of rows.
     * Positive values limit the size of the result set.
     * @return the result of executing this Select
     */
    Result getResult(Session session, int maxrows) {

        Result r = getSingleResult(session, maxrows);

        // fredt - now there is no need for the sort and group columns
//        r.setColumnCount(indexLimitVisible);
        r.getNavigator().reset();

        return r;
    }

    private Result getSingleResult(Session session, int maxRows) {

        int[]               limits    = getLimits(session, maxRows);
        Result              r         = buildResult(session, limits[2]);
        RowSetNavigatorData navigator = (RowSetNavigatorData) r.getNavigator();

        if (isDistinctSelect) {
            navigator.removeDuplicates();
        }

        if (sortAndSlice.hasOrder()) {
            navigator.sortOrder();
        }

        if (limits != defaultLimits) {
            navigator.trim(limits[0], limits[1]);
        }

        return r;
    }

    private Result buildResult(Session session, int limitcount) {

        RowSetNavigatorData navigator = new RowSetNavigatorData(session,
            (QuerySpecification) this);
        Result result = Result.newResult(navigator);

        result.metaData = resultMetaData;

        if (isUpdatable) {
            result.rsProperties = ResultProperties.updatablePropsValue;
        }

        int fullJoinIndex = 0;
        RangeIterator[] rangeIterators =
            new RangeIterator[rangeVariables.length];

        for (int i = 0; i < rangeVariables.length; i++) {
            rangeIterators[i] = rangeVariables[i].getIterator(session);
        }

        for (int currentIndex = 0; ; ) {
            if (currentIndex < fullJoinIndex) {
                boolean end = true;

                for (int i = fullJoinIndex + 1; i < rangeVariables.length;
                        i++) {
                    if (rangeVariables[i].isRightJoin) {
                        fullJoinIndex = i;
                        currentIndex  = i;
                        end           = false;

                        ((RangeIteratorRight) rangeIterators[i])
                            .setOnOuterRows();

                        break;
                    }
                }

                if (end) {
                    break;
                }
            }

            RangeIterator it = rangeIterators[currentIndex];

            if (it.next()) {
                if (currentIndex < rangeVariables.length - 1) {
                    currentIndex++;

                    continue;
                }
            } else {
                it.reset();

                currentIndex--;

                continue;
            }

            session.sessionData.startRowProcessing();

            Object[] data = new Object[indexLimitData];

            for (int i = 0; i < indexStartAggregates; i++) {
                if (isAggregated && aggregateCheck[i]) {
                    continue;
                } else {
                    data[i] = exprColumns[i].getValue(session);
                }
            }

            for (int i = indexLimitVisible; i < indexLimitRowId; i++) {
                if (i == indexLimitVisible) {
                    data[i] = it.getRowidObject();
                } else {
                    data[i] = it.getCurrentRow();
                }
            }

            Object[] groupData = null;

            if (isAggregated || isGrouped) {
                groupData = navigator.getGroupData(data);

                if (groupData != null) {
                    data = groupData;
                }
            }

            for (int i = indexStartAggregates; i < indexLimitExpressions;
                    i++) {
                data[i] =
                    ((ExpressionAggregate) exprColumns[i])
                        .updateAggregatingValue(session, data[i]);
            }

            if (groupData == null) {
                navigator.add(data);
            } else if (isAggregated) {
                navigator.update(groupData, data);
            }

            int rowCount = navigator.getSize();

            if (rowCount == session.resultMaxMemoryRows && !isAggregated
                    && !hasMemoryRow) {
                navigator = new RowSetNavigatorDataTable(session, this,
                        navigator);

                result.setNavigator(navigator);
            }

            if (isAggregated || isGrouped) {
                continue;
            }

            if (rowCount >= limitcount) {
                break;
            }
        }

        navigator.reset();

        if (!isGrouped && !isAggregated) {
            return result;
        }

        if (isAggregated) {
            if (!isGrouped && navigator.getSize() == 0) {
                Object[] data = new Object[exprColumns.length];

                navigator.add(data);
            }

            navigator.reset();
            session.sessionContext.setRangeIterator(navigator);

            while (navigator.next()) {
                Object[] data = navigator.getCurrent();

                for (int i = indexStartAggregates; i < indexLimitExpressions;
                        i++) {
                    ExpressionAggregate aggregate =
                        (ExpressionAggregate) exprColumns[i];

                    data[i] = aggregate.getAggregatedValue(session, data[i]);
                }

                for (int i = 0; i < indexStartAggregates; i++) {
                    if (aggregateCheck[i]) {
                        data[i] = exprColumns[i].getValue(session);
                    }
                }
            }
        }

        navigator.reset();

        if (havingCondition != null) {
            while (navigator.hasNext()) {
                Object[] data = (Object[]) navigator.getNext();

                if (!Boolean.TRUE.equals(
                        data[indexLimitVisible + groupByColumnCount])) {
                    navigator.remove();
                }
            }

            navigator.reset();
        }

        return result;
    }

    void setReferenceableColumns() {

        accessibleColumns = new boolean[indexLimitVisible];

        IntValueHashMap aliases = new IntValueHashMap();

        for (int i = 0; i < indexLimitVisible; i++) {
            Expression expression = exprColumns[i];
            String     alias      = expression.getAlias();

            if (alias.length() == 0) {
                SimpleName name = HsqlNameManager.getAutoColumnName(i);

                expression.setAlias(name);

                continue;
            }

            int index = aliases.get(alias, -1);

            if (index == -1) {
                aliases.put(alias, i);

                accessibleColumns[i] = true;
            } else {
                accessibleColumns[index] = false;
            }
        }
    }

    private void createResultMetaData() {

        columnTypes = new Type[indexLimitData];

        for (int i = 0; i < indexStartAggregates; i++) {
            Expression e = exprColumns[i];

            columnTypes[i] = e.getDataType();
        }

        for (int i = indexLimitVisible; i < indexLimitRowId; i++) {
            if (i == indexLimitVisible) {
                columnTypes[i] = Type.SQL_BIGINT;
            } else {
                columnTypes[i] = Type.SQL_ALL_TYPES;
            }
        }

        for (int i = indexLimitRowId; i < indexLimitData; i++) {
            Expression e = exprColumns[i];

            columnTypes[i] = e.getDataType();
        }

        resultMetaData = ResultMetaData.newResultMetaData(columnTypes,
                columnMap, indexLimitVisible, indexLimitRowId);

        for (int i = 0; i < indexLimitVisible; i++) {
            Expression e = exprColumns[i];

            resultMetaData.columnTypes[i] = e.getDataType();

            if (i < indexLimitVisible) {
                ColumnBase column = e.getColumn();

                if (column != null) {
                    resultMetaData.columns[i]      = column;
                    resultMetaData.columnLabels[i] = e.getAlias();

                    continue;
                }

                column = new ColumnBase();

                column.setType(e.getDataType());

                resultMetaData.columns[i]      = column;
                resultMetaData.columnLabels[i] = e.getAlias();
            }
        }
    }

    void createTable(Session session) {

        createResultTable(session);

        mainIndex = resultTable.getPrimaryIndex();

        if (sortAndSlice.hasOrder() && !sortAndSlice.skipSort) {
            orderIndex = resultTable.createAndAddIndexStructure(null,
                    sortAndSlice.sortOrder, sortAndSlice.sortDescending,
                    sortAndSlice.sortNullsLast, false, false, false);
        }

        if (isDistinctSelect || isFullOrder) {
            int[] fullCols = new int[indexLimitVisible];

            ArrayUtil.fillSequence(fullCols);

            fullIndex = resultTable.createAndAddIndexStructure(null, fullCols,
                    null, null, false, false, false);
            resultTable.fullIndex = fullIndex;
        }

        if (isGrouped) {
            int[] groupCols = new int[groupByColumnCount];

            for (int i = 0; i < groupByColumnCount; i++) {
                groupCols[i] = indexLimitVisible + i;
            }

            groupIndex = resultTable.createAndAddIndexStructure(null,
                    groupCols, null, null, false, false, false);
        } else if (isAggregated) {
            groupIndex = mainIndex;
        }

        if (isUpdatable && view == null) {
            int[] idCols = new int[]{ indexLimitVisible };

            idIndex = resultTable.createAndAddIndexStructure(null, idCols,
                    null, null, false, false, false);
        }
    }

    void createResultTable(Session session) {

        HsqlName       tableName;
        HashMappedList columnList;
        int            tableType;

        tableName = session.database.nameManager.getSubqueryTableName();
        tableType = persistenceScope == TableBase.SCOPE_STATEMENT
                    ? TableBase.SYSTEM_SUBQUERY
                    : TableBase.RESULT_TABLE;
        columnList = new HashMappedList();

        for (int i = 0; i < indexLimitVisible; i++) {
            Expression e          = exprColumns[i];
            SimpleName simpleName = e.getSimpleName();
            String     nameString = simpleName.name;
            HsqlName name =
                session.database.nameManager.newColumnSchemaHsqlName(tableName,
                    simpleName);

            if (!accessibleColumns[i]) {
                nameString = HsqlNameManager.getAutoNoNameColumnString(i);
            }

            ColumnSchema column = new ColumnSchema(name, e.dataType, true,
                                                   false, null);

            columnList.add(nameString, column);
        }

        try {
            resultTable = new TableDerived(session.database, tableName,
                                           tableType, columnTypes, columnList,
                                           null);
        } catch (Exception e) {}
    }

    public String getSQL() {

        StringBuffer sb = new StringBuffer();
        int          limit;

        sb.append(Tokens.T_SELECT).append(' ');

        limit = indexLimitVisible;

        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(',');
            }

            sb.append(exprColumns[i].getSQL());
        }

        sb.append(Tokens.T_FROM);

        limit = rangeVariables.length;

        for (int i = 0; i < limit; i++) {
            RangeVariable rangeVar = rangeVariables[i];

            if (i > 0) {
                if (rangeVar.isLeftJoin && rangeVar.isRightJoin) {
                    sb.append(Tokens.T_FULL).append(' ');
                } else if (rangeVar.isLeftJoin) {
                    sb.append(Tokens.T_LEFT).append(' ');
                } else if (rangeVar.isRightJoin) {
                    sb.append(Tokens.T_RIGHT).append(' ');
                }

                sb.append(Tokens.T_JOIN).append(' ');
            }

            sb.append(rangeVar.getTable().getName().statementName);
        }

        if (isGrouped) {
            sb.append(' ').append(Tokens.T_GROUP).append(' ').append(
                Tokens.T_BY);

            limit = indexLimitVisible + groupByColumnCount;

            for (int i = indexLimitVisible; i < limit; i++) {
                sb.append(exprColumns[i].getSQL());

                if (i < limit - 1) {
                    sb.append(',');
                }
            }
        }

        if (havingCondition != null) {
            sb.append(' ').append(Tokens.T_HAVING).append(' ');
            sb.append(havingCondition.getSQL());
        }

        if (sortAndSlice.hasOrder()) {
            limit = indexStartOrderBy + sortAndSlice.getOrderLength();

            sb.append(' ').append(Tokens.T_ORDER).append(Tokens.T_BY).append(
                ' ');

            for (int i = indexStartOrderBy; i < limit; i++) {
                sb.append(exprColumns[i].getSQL());

                if (i < limit - 1) {
                    sb.append(',');
                }
            }
        }

        if (sortAndSlice.hasLimit()) {
            sb.append(sortAndSlice.limitCondition.getLeftNode().getSQL());
        }

        return sb.toString();
    }

    public ResultMetaData getMetaData() {
        return resultMetaData;
    }

    public String describe(Session session) {

        StringBuffer sb;
        String       temp;

/*
        // temporary :  it is currently unclear whether this may affect
        // later attempts to retrieve an actual result (calls getResult(1)
        // in preProcess mode).  Thus, toString() probably should not be called
        // on Select objects that will actually be used to retrieve results,
        // only on Select objects used by EXPLAIN PLAN FOR
        try {
            getResult(session, 1);
        } catch (HsqlException e) {}
*/
        sb = new StringBuffer();

        sb.append(super.toString()).append("[\n");

        if (sortAndSlice.limitCondition != null) {
            sb.append("offset=[").append(
                sortAndSlice.limitCondition.getLeftNode().describe(
                    session)).append("]\n");
            sb.append("limit=[").append(
                sortAndSlice.limitCondition.getRightNode().describe(
                    session)).append("]\n");
        }

        sb.append("isDistinctSelect=[").append(isDistinctSelect).append("]\n");
        sb.append("isGrouped=[").append(isGrouped).append("]\n");
        sb.append("isAggregated=[").append(isAggregated).append("]\n");
        sb.append("columns=[");

        for (int i = 0; i < indexLimitVisible; i++) {
            int index = i;

            if (exprColumns[i].getType() == OpTypes.SIMPLE_COLUMN) {
                index = exprColumns[i].columnIndex;
            }

            sb.append(exprColumns[index].describe(session));
        }

        sb.append("\n]\n");
        sb.append("range variables=[\n");

        for (int i = 0; i < rangeVariables.length; i++) {
            sb.append("[\n");
            sb.append(rangeVariables[i].describe(session));
            sb.append("\n]");
        }

        sb.append("]\n");

        temp = queryCondition == null ? "null"
                                      : queryCondition.describe(session);

        sb.append("groupColumns=[");

        for (int i = indexLimitRowId; i < indexLimitRowId + groupByColumnCount;
                i++) {
            int index = i;

            if (exprColumns[i].getType() == OpTypes.SIMPLE_COLUMN) {
                index = exprColumns[i].columnIndex;
            }

            sb.append(exprColumns[index].describe(session));
        }

        sb.append("]\n");

        temp = havingCondition == null ? "null"
                                       : havingCondition.describe(session);

        sb.append("havingCondition=[").append(temp).append("]\n");

        return sb.toString();
    }

    void setUpdatability() {

        if (!isUpdatable) {
            return;
        }

        isUpdatable = false;

        if (isAggregated || isGrouped || isDistinctSelect || !isTopLevel) {
            return;
        }

        if (sortAndSlice.hasLimit() || sortAndSlice.hasOrder()) {
            return;
        }

        if (rangeVariables.length != 1) {
            return;
        }

        RangeVariable rangeVar  = rangeVariables[0];
        Table         table     = rangeVar.getTable();
        Table         baseTable = table.getBaseTable();

        isInsertable = table.isInsertable();
        isUpdatable  = table.isUpdatable();

        if (!isInsertable && !isUpdatable) {
            return;
        }

        IntValueHashMap columns = new IntValueHashMap();
        boolean[]       checkList;
        int[]           baseColumnMap = table.getBaseTableColumnMap();
        int[]           columnMap     = new int[indexLimitVisible];

        for (int i = 0; i < indexLimitVisible; i++) {
            Expression expression = exprColumns[i];

            if (expression.getType() == OpTypes.COLUMN) {
                String name = expression.getColumn().getName().name;

                if (columns.containsKey(name)) {
                    columns.put(name, 1);

                    continue;
                }

                columns.put(name, 0);
            }
        }

        isUpdatable = false;

        for (int i = 0; i < indexLimitVisible; i++) {
            if (accessibleColumns[i]) {
                Expression expression = exprColumns[i];

                if (expression.getType() == OpTypes.COLUMN) {
                    String name = expression.getColumn().getName().name;

                    if (columns.get(name) == 0) {
                        int index = table.findColumn(name);

                        columnMap[i] = baseColumnMap[index];

                        if (columnMap[i] != -1) {
                            isUpdatable = true;
                        }

                        continue;
                    }
                }
            }

            columnMap[i] = -1;
            isInsertable = false;
        }

        if (isInsertable) {
            checkList = baseTable.getColumnCheckList(columnMap);

            for (int i = 0; i < checkList.length; i++) {
                if (checkList[i]) {
                    continue;
                }

                ColumnSchema column = baseTable.getColumn(i);

                if (column.isIdentity() || column.isGenerated()
                        || column.hasDefault() || column.isNullable()) {}
                else {
                    isInsertable = false;

                    break;
                }
            }
        }

        if (!isUpdatable) {
            isInsertable = false;
        }

        if (isUpdatable) {
            this.columnMap = columnMap;
            this.baseTable = baseTable;

            if (view != null) {
                return;
            }

            indexLimitRowId++;

            hasRowId = true;

            if (!baseTable.isFileBased()) {
                indexLimitRowId++;

                hasMemoryRow = true;
            }

            indexLimitData = indexLimitRowId;
        }
    }

    public Table getBaseTable() {
        return baseTable;
    }

    public void collectAllExpressions(HsqlList set, OrderedIntHashSet typeSet,
                                      OrderedIntHashSet stopAtTypeSet) {

        for (int i = 0; i < indexStartAggregates; i++) {
            Expression.collectAllExpressions(set, exprColumns[i], typeSet,
                                             stopAtTypeSet);
        }

        Expression.collectAllExpressions(set, queryCondition, typeSet,
                                         stopAtTypeSet);
        Expression.collectAllExpressions(set, havingCondition, typeSet,
                                         stopAtTypeSet);
    }

    public void collectObjectNames(Set set) {

        for (int i = 0; i < indexStartAggregates; i++) {
            exprColumns[i].collectObjectNames(set);
        }

        if (queryCondition != null) {
            queryCondition.collectObjectNames(set);
        }

        if (havingCondition != null) {
            havingCondition.collectObjectNames(set);
        }

        for (int i = 0, len = rangeVariables.length; i < len; i++) {
            HsqlName name = rangeVariables[i].getTable().getName();

            set.add(name);
        }
    }

    void getMergedSelect() {

        RangeVariable rangeVar            = rangeVariables[0];
        Table         table               = rangeVar.getTable();
        Expression    localQueryCondition = queryCondition;
        Expression    baseQueryCondition  = null;

        if (table instanceof TableDerived) {
            QuerySpecification baseSelect =
                ((TableDerived) table).queryExpression.getMainSelect();
            RangeVariable baseRangeVariable = baseSelect.rangeVariables[0];

            rangeVariables    = new RangeVariable[1];
            rangeVariables[0] = new RangeVariable(baseRangeVariable);

            Expression[] newExprColumns = new Expression[indexLimitRowId];

            for (int i = 0; i < indexLimitVisible; i++) {
                Expression e = exprColumns[i];

                newExprColumns[i] = e.replaceColumnReferences(rangeVar,
                        baseSelect.exprColumns);
            }

            exprColumns = newExprColumns;

            if (localQueryCondition != null) {
                localQueryCondition =
                    localQueryCondition.replaceColumnReferences(rangeVar,
                        baseSelect.exprColumns);
            }

            baseQueryCondition  = baseSelect.queryCondition;
            checkQueryCondition = baseSelect.checkQueryCondition;
        }

        queryCondition = ExpressionLogical.andExpressions(baseQueryCondition,
                localQueryCondition);

        if (queryCondition != null && (isUpdatable || isInsertable)) {
            tempSet.clear();
            Expression.collectAllExpressions(tempSet, queryCondition,
                                             Expression.subqueryExpressionSet,
                                             Expression.emptyExpressionSet);

            int size = tempSet.size();

            for (int i = 0; i < size; i++) {
                Expression e = (Expression) tempSet.get(i);

                e.collectObjectNames(tempSet);
            }

            if (tempSet.contains(baseTable.getName())) {
                isUpdatable  = false;
                isInsertable = false;
            }
        }

        if (view != null) {
            switch (view.getCheckOption()) {

                case SchemaObject.ViewCheckModes.CHECK_LOCAL :
                    if (!isUpdatable) {
                        throw Error.error(ErrorCode.X_42537);
                    }

                    checkQueryCondition = localQueryCondition;
                    break;

                case SchemaObject.ViewCheckModes.CHECK_CASCADE :
                    if (!isUpdatable) {
                        throw Error.error(ErrorCode.X_42537);
                    }

                    checkQueryCondition = queryCondition;
                    break;
            }
        }

        if (table instanceof TableDerived) {
            setRangeVariableConditions();
        }
    }

    /**
     * Not for views. Only used on root node.
     */
    public void setReturningResult() {

        setReturningResultSet();

        acceptsSequences = true;
        isTopLevel       = true;
    }

    void setReturningResultSet() {
        persistenceScope = TableBase.SCOPE_SESSION;
    }

    public boolean isSingleColumn() {
        return indexLimitVisible == 1;
    }

    public String[] getColumnNames() {

        String[] names = new String[indexLimitVisible];

        for (int i = 0; i < indexLimitVisible; i++) {
            names[i] = exprColumns[i].getAlias();
        }

        return names;
    }

    public Type[] getColumnTypes() {

        if (columnTypes.length == indexLimitVisible) {
            return columnTypes;
        }

        Type[] types = new Type[indexLimitVisible];

        ArrayUtil.copyArray(columnTypes, types, types.length);

        return types;
    }

    public int getColumnCount() {
        return indexLimitVisible;
    }

    public int[] getBaseTableColumnMap() {
        return columnMap;
    }

    public Expression getCheckCondition() {
        return queryCondition;
    }

    void getBaseTableNames(OrderedHashSet set) {

        for (int i = 0; i < rangeVariables.length; i++) {
            Table    rangeTable = rangeVariables[i].rangeTable;
            HsqlName name       = rangeTable.getName();

            if (rangeTable.isReadOnly() || rangeTable.isTemp()) {
                continue;
            }

            if (name.schema == SqlInvariants.SYSTEM_SCHEMA_HSQLNAME) {
                continue;
            }

            set.add(name);
        }
    }
}
