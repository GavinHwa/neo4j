/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.kernel.api.index;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.collection.primitive.PrimitiveIntIterator;
import org.neo4j.kernel.api.properties.DefinedProperty;
import org.neo4j.kernel.api.properties.Property;
import org.neo4j.kernel.api.schema_new.LabelSchemaDescriptor;
import org.neo4j.kernel.api.schema_new.SchemaDescriptorFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.junit.Assert.fail;

public class NodeUpdatesTest
{
    private static final long nodeId = 0;
    private static final int labelId = 0;
    private static final int propertyKeyId1 = 0;
    private static final int propertyKeyId2 = 1;
    private static final long[] labels = new long[]{labelId};
    private static final long[] empty = new long[]{};

    private static final LabelSchemaDescriptor index1 = SchemaDescriptorFactory.forLabel( labelId, propertyKeyId1 );
    private static final LabelSchemaDescriptor index2 = SchemaDescriptorFactory.forLabel( labelId, propertyKeyId2 );
    private static final LabelSchemaDescriptor index12 = SchemaDescriptorFactory.forLabel( labelId, propertyKeyId1, propertyKeyId2 );
    private static final List<LabelSchemaDescriptor> indexes = Arrays.asList( index1, index2, index12 );

    private static final DefinedProperty property1 = Property.stringProperty( propertyKeyId1, "Neo" );
    private static final DefinedProperty property2 = Property.longProperty( propertyKeyId2, 100L );
    private static final Object[] values12 = new Object[]{property1.value(), property2.value()};

    @Test
    public void shouldNotGenerateUpdatesForEmptyNodeUpdates()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId ).build();

        // Then
        assertThat( updates.forIndexes( indexes, assertNoLoading() ), emptyIterable() );
    }

    @Test
    public void shouldNotGenerateUpdateForMultipleExistingPropertiesAndLabels()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, labels )
                .buildWithExistingProperties( property1, property2 );

        // Then
        assertThat( updates.forIndexes( indexes, assertNoLoading() ), emptyIterable() );
    }

    @Test
    public void shouldNotGenerateUpdatesForLabelAdditionWithNoProperties()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, empty, labels ).build();

        // Then
        assertThat( updates.forIndexes( indexes, propertyLoader() ), emptyIterable() );
    }

    @Test
    public void shouldGenerateUpdateForLabelAdditionWithExistingProperty()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, empty, labels ).build();

        // Then
        assertThat(
                updates.forIndexes( indexes, propertyLoader( property1 ) ),
                containsInAnyOrder(
                    IndexEntryUpdate.add( nodeId, index1, property1.value() )
                ) );
    }

    @Test
    public void shouldGenerateUpdatesForLabelAdditionWithExistingProperties()
    {
        // When
        NodeUpdates updates =
                NodeUpdates.forNode( nodeId, empty, labels ).buildWithExistingProperties( property1, property2 );

        // Then
        assertThat(
                updates.forIndexes( indexes, propertyLoader( property1, property2 ) ),
                containsInAnyOrder(
                        IndexEntryUpdate.add( nodeId, index1, property1.value() ),
                        IndexEntryUpdate.add( nodeId, index2, property2.value() ),
                        IndexEntryUpdate.add( nodeId, index12, values12 )
                ) );
    }

    @Test
    public void shouldNotGenerateUpdatesForLabelRemovalWithNoProperties()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, labels, empty ).build();

        // Then
        assertThat( updates.forIndexes( indexes, propertyLoader() ), emptyIterable() );
    }

    @Test
    public void shouldGenerateUpdateForLabelRemovalWithExistingProperty()
    {
        // When
        NodeUpdates updates =
                NodeUpdates.forNode( nodeId, labels, empty ).build();

        // Then
        assertThat(
                updates.forIndexes( indexes, propertyLoader( property1 ) ),
                containsInAnyOrder(
                        IndexEntryUpdate.remove( nodeId, index1, property1.value() )
                ) );
    }

    @Test
    public void shouldGenerateUpdatesForLabelRemovalWithExistingProperties()
    {
        // When
        NodeUpdates updates =
                NodeUpdates.forNode( nodeId, labels, empty ).build();

        // Then
        assertThat(
                updates.forIndexes( indexes, propertyLoader( property1, property2 ) ),
                containsInAnyOrder(
                        IndexEntryUpdate.remove( nodeId, index1, property1.value() ),
                        IndexEntryUpdate.remove( nodeId, index2, property2.value() ),
                        IndexEntryUpdate.remove( nodeId, index12, values12 )
                ) );
    }

    @Test
    public void shouldNotGenerateUpdatesForPropertyAdditionWithNoLabels()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId )
                .added( property1.propertyKeyId(), property1.value() )
                .build();

        // Then
        assertThat( updates.forIndexes( indexes, assertNoLoading() ), emptyIterable() );
    }

    @Test
    public void shouldGenerateUpdatesForSinglePropertyAdditionWithLabels()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, labels )
                .added( property1.propertyKeyId(), property1.value() )
                .build();

        // Then
        assertThat(
                updates.forIndexes( indexes, propertyLoader() ),
                containsInAnyOrder(
                        IndexEntryUpdate.add( nodeId, index1, property1.value() )
                ) );
    }

    @Test
    public void shouldGenerateUpdatesForMultiplePropertyAdditionWithLabels()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, labels )
                .added( property1.propertyKeyId(), property1.value() )
                .added( property2.propertyKeyId(), property2.value() )
                .build();

        // Then
        assertThat(
                updates.forIndexes( indexes, propertyLoader( property1, property2 ) ),
                containsInAnyOrder(
                        IndexEntryUpdate.add( nodeId, index1, property1.value() ),
                        IndexEntryUpdate.add( nodeId, index2, property2.value() ),
                        IndexEntryUpdate.add( nodeId, index12, values12 )
                ) );
    }

    @Test
    public void shouldNotGenerateUpdatesForLabelAddAndPropertyRemove()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, empty, labels )
                .removed( property1.propertyKeyId(), property1.value() )
                .removed( property2.propertyKeyId(), property2.value() )
                .build();

        // Then
        assertThat( updates.forIndexes( indexes, assertNoLoading() ), emptyIterable() );
    }

    @Test
    public void shouldNotGenerateUpdatesForLabelRemoveAndPropertyAdd()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, labels, empty )
                .added( property1.propertyKeyId(), property1.value() )
                .added( property2.propertyKeyId(), property2.value() )
                .build();

        // Then
        assertThat( updates.forIndexes( indexes, assertNoLoading() ), emptyIterable() );
    }

    @Test
    public void shouldNotLoadPropertyForLabelsAndNoPropertyChanges()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, labels ).build();

        // Then
        assertThat(
                updates.forIndexes( Collections.singleton( index1 ), assertNoLoading() ),
                emptyIterable() );
    }

    @Test
    public void shouldNotLoadPropertyForNoLabelsAndButPropertyAddition()
    {
        // When
        NodeUpdates updates = NodeUpdates.forNode( nodeId, empty )
                .added( property1.propertyKeyId(), property1.value() )
                .build();

        // Then
        assertThat(
                updates.forIndexes( Collections.singleton( index1 ), assertNoLoading() ),
                emptyIterable() );
    }

    private PropertyLoader propertyLoader( DefinedProperty... properties )
    {
        Map<Integer, Object> propertyMap = new HashMap<>( );
        for ( DefinedProperty p : properties )
        {
            propertyMap.put( p.propertyKeyId(), p.value() );
        }
        return ( nodeId1, propertyIds, sink ) -> {
            PrimitiveIntIterator iterator = propertyIds.iterator();
            while ( iterator.hasNext() )
            {
                int propertyId = iterator.next();
                if ( propertyMap.containsKey( propertyId ) )
                {
                    sink.onProperty( propertyId, propertyMap.get( propertyId ) );
                    propertyIds.remove( propertyId );
                }
            }
        };
    }

    private PropertyLoader assertNoLoading()
    {
        return ( nodeId1, propertyIds, sink ) -> {
            fail( "Should never attempt to load properties!" );
        };
    }
}
