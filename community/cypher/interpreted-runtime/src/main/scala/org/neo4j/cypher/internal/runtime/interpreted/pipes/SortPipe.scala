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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import java.util.Comparator

import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.ReadableRow
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.memory.HeapEstimator

case class SortPipe(source: Pipe, comparator: Comparator[ReadableRow])
                   (val id: Id = Id.INVALID_ID)
  extends PipeWithSource(source) {

  protected def internalCreateResults(input: Iterator[CypherRow], state: QueryState): Iterator[CypherRow] = {
    val array: Array[ReadableRow] = state.memoryTracker.memoryTrackingIterator(input, id.x).toArray
    state.memoryTracker.memoryTrackerForOperator(id.x).allocateHeap(HeapEstimator.shallowSizeOfObjectArray(array.length))
    java.util.Arrays.sort(array, comparator)
    array.toIterator.asInstanceOf[Iterator[CypherRow]]
  }
}
