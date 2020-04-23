/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.interpreted.pipes.aggregation

import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.pipes.AggregationPipe
import org.neo4j.cypher.internal.runtime.interpreted.pipes.AggregationPipe.AggregatingCol
import org.neo4j.cypher.internal.runtime.interpreted.pipes.AggregationPipe.AggregationTable
import org.neo4j.cypher.internal.runtime.interpreted.pipes.AggregationPipe.AggregationTableFactory
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DistinctPipe.GroupingCol
import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExecutionContextFactory
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.values.AnyValue

/**
 * This table must be used when we have grouping columns, and there is no provided order for at least one grouping column.
 *
 * @param groupingColumns  all grouping columns
 * @param groupingFunction a precomputed function to calculate the grouping key of a row
 * @param aggregations     all aggregation columns
 */
class GroupingAggTable(groupingColumns: Array[GroupingCol],
                       groupingFunction: (CypherRow, QueryState) => AnyValue,
                       aggregations: Array[AggregatingCol],
                       state: QueryState,
                       executionContextFactory: ExecutionContextFactory,
                       operatorId: Id) extends AggregationTable {

  protected var resultMap: java.util.LinkedHashMap[AnyValue, Array[AggregationFunction]] = _
  protected val addKeys: (CypherRow, AnyValue) => Unit = AggregationPipe.computeAddKeysToResultRowFunction(groupingColumns)

  override def clear(): Unit = {
    // TODO: Use a heap tracking collection or ScopedMemoryTracker instead
    if (resultMap != null) {
      resultMap.forEach { (key, functions) =>
        state.memoryTracker.deallocated(key, operatorId.x)
        functions.foreach(_.recordMemoryDeallocation())
      }
    }
    resultMap = new java.util.LinkedHashMap[AnyValue, Array[AggregationFunction]]()
  }

  override def processRow(row: CypherRow): Unit = {
    val groupingValue: AnyValue = groupingFunction(row, state)
    val aggregationFunctions = resultMap.computeIfAbsent(groupingValue, _ => {
      state.memoryTracker.allocated(groupingValue, operatorId.x)
      val functions = new Array[AggregationFunction](aggregations.length)
      var i = 0
      while (i < aggregations.length) {
        functions(i) = aggregations(i).expression.createAggregationFunction(operatorId)
        i += 1
      }
      functions
    })
    var i = 0
    while (i < aggregationFunctions.length) {
      aggregationFunctions(i)(row, state)
      i += 1
    }
  }

  override def result(): Iterator[CypherRow] = {
    val innerIterator = resultMap.entrySet().iterator()
    new Iterator[CypherRow] {
      override def hasNext: Boolean = innerIterator.hasNext

      override def next(): CypherRow = {
        val entry = innerIterator.next()
        val unorderedGroupingValue = entry.getKey
        val aggregateFunctions = entry.getValue
        val row = state.newExecutionContext(executionContextFactory)
        addKeys(row, unorderedGroupingValue)
        var i = 0
        while (i < aggregateFunctions.length) {
          row.set(aggregations(i).key, aggregateFunctions(i).result(state))
          i += 1
        }
        row

      }
    }
  }

}

object GroupingAggTable {

  case class Factory(groupingColumns: Array[GroupingCol],
                     groupingFunction: (CypherRow, QueryState) => AnyValue,
                     aggregations: Array[AggregatingCol]) extends AggregationTableFactory {
    override def table(state: QueryState, executionContextFactory: ExecutionContextFactory, operatorId: Id): AggregationTable =
      new GroupingAggTable(groupingColumns, groupingFunction, aggregations, state, executionContextFactory, operatorId)
  }

}
