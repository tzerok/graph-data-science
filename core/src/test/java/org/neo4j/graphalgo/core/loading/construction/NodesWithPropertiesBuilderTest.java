/*
 * Copyright (c) "Neo4j"
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
package org.neo4j.graphalgo.core.loading.construction;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.nodeproperties.ValueType;
import org.neo4j.graphalgo.api.schema.NodeSchema;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.concurrency.Pools;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.extension.GdlExtension;
import org.neo4j.graphalgo.extension.GdlGraph;
import org.neo4j.graphalgo.extension.IdFunction;
import org.neo4j.graphalgo.extension.Inject;
import org.neo4j.values.storable.Values;

import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@GdlExtension
class NodesWithPropertiesBuilderTest {

    @GdlGraph
    static final String DB_CYPHER = "CREATE" +
                                    "  (a:A {prop1: 42, prop2: 1337})" +
                                    ", (b:A {prop1: 43, prop2: 1338})";

    @Inject
    Graph graph;

    @Inject
    IdFunction idFunction;

    @Test
    void shouldBuildNodesWithProperties() {
        NodesWithPropertiesBuilder nodesBuilder = NodesWithPropertiesBuilder.fromSchema(
            2,
            graph.nodeCount(),
            1,
            graph.schema().nodeSchema(),
            AllocationTracker.empty()
        );

        nodesBuilder.addNode(idFunction.of("a"), Map.of("prop1", Values.longValue(42), "prop2", Values.longValue(1337)), NodeLabel.of("A"));
        nodesBuilder.addNode(idFunction.of("b"), Map.of("prop1", Values.longValue(43), "prop2", Values.longValue(1338)), NodeLabel.of("A"));

        NodesWithPropertiesBuilder.NodeMappingWithProperties nodeMappingWithProperties = nodesBuilder.build();
        var nodeMapping = nodeMappingWithProperties.nodeMapping();
        var nodeProperties = nodeMappingWithProperties.nodeProperties();

        assertThat(graph.nodeCount()).isEqualTo(nodeMapping.nodeCount());
        assertThat(graph.availableNodeLabels()).isEqualTo(nodeMapping.availableNodeLabels());
        graph.forEachNode(nodeId -> {
            assertThat(nodeMapping.toOriginalNodeId(nodeId)).isEqualTo(graph.toOriginalNodeId(nodeId));
            assertThat(nodeProperties.get("prop1").longValue(nodeId)).isEqualTo(graph.nodeProperties("prop1").longValue(nodeId));
            assertThat(nodeProperties.get("prop2").longValue(nodeId)).isEqualTo(graph.nodeProperties("prop2").longValue(nodeId));
            return true;
        });
    }

    @Test
    void shouldBuildNodesWithPropertiesInParallel() {
        int nodeCount = 10000;
        var nodeLabel = NodeLabel.of("A");
        var nodeSchema = NodeSchema.builder()
            .addProperty(nodeLabel, "prop1", ValueType.LONG)
            .addProperty(nodeLabel, "prop2", ValueType.LONG)
            .build();
        int concurrency = 4;
        var nodesBuilder = NodesWithPropertiesBuilder.fromSchema(
            nodeCount,
            nodeCount,
            concurrency,
            nodeSchema,
            AllocationTracker.empty()
        );

        var tasks = ParallelUtil.tasks(
            concurrency,
            (index) -> () -> IntStream.range(index * (nodeCount / concurrency), (index + 1) * (nodeCount / concurrency))
                .forEach(originalNodeId -> nodesBuilder.addNode(
                    originalNodeId,
                    Map.of("prop1", Values.longValue(originalNodeId), "prop2", Values.longValue(nodeCount - originalNodeId)),
                    nodeLabel
                ))
        );

        ParallelUtil.run(tasks, Pools.DEFAULT);

        var nodeMappingWithProperties = nodesBuilder.build();
        var nodeMapping = nodeMappingWithProperties.nodeMapping();
        var nodeProperties = nodeMappingWithProperties.nodeProperties();

        assertThat(nodeMapping.nodeCount()).isEqualTo(nodeCount);
        assertThat(nodeMapping.availableNodeLabels()).containsExactly(nodeLabel);

        nodeMapping.forEachNode(nodeId -> {
            long originalNodeId = nodeMapping.toOriginalNodeId(nodeId);
            assertThat(originalNodeId).isEqualTo(nodeProperties.get("prop1").longValue(nodeId));
            assertThat(nodeCount - originalNodeId).isEqualTo(nodeProperties.get("prop2").longValue(nodeId));
            return true;
        });
    }
}
