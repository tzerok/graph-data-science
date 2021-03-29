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
package org.neo4j.graphalgo.beta.pregel;

import org.jetbrains.annotations.NotNull;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.utils.BitUtil;
import org.neo4j.graphalgo.core.utils.paged.HugeAtomicBitSet;
import org.neo4j.graphalgo.core.utils.partition.Partition;
import org.neo4j.graphalgo.core.utils.partition.PartitionUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public class PartitionedComputer<CONFIG extends PregelConfig> implements PregelComputer {

    private final Graph graph;
    private final PregelComputation<CONFIG> computation;
    private final CONFIG config;
    private final NodeValue nodeValues;
    private final Messenger<?> messenger;
    private final HugeAtomicBitSet voteBits;
    private final ExecutorService executorService;
    private final int concurrency;

    private List<ComputeStepTask<CONFIG, ?>> computeSteps;

    PartitionedComputer(
        Graph graph,
        PregelComputation<CONFIG> computation,
        CONFIG config,
        NodeValue nodeValues,
        Messenger<?> messenger,
        HugeAtomicBitSet voteBits,
        int concurrency,
        ExecutorService executorService
    ) {
        this.graph = graph;
        this.computation = computation;
        this.config = config;
        this.nodeValues = nodeValues;
        this.messenger = messenger;
        this.voteBits = voteBits;
        this.executorService = executorService;
        this.concurrency = concurrency;
    }

    @Override
    public void initComputation() {
        this.computeSteps = createComputeSteps(voteBits);
    }

    @Override
    public void initIteration(int iteration) {
        for (var computeStep : computeSteps) {
            computeStep.init(iteration);
        }
    }

    @Override
    public void runIteration() {
        ParallelUtil.runWithConcurrency(concurrency, computeSteps, executorService);
    }

    @Override
    public boolean hasConverged() {
        // No messages have been sent and all nodes voted to halt
        var lastIterationSendMessages = computeSteps
            .stream()
            .anyMatch(ComputeStepTask::hasSendMessage);
        return !lastIterationSendMessages && voteBits.allSet();

    }

    @NotNull
    private List<ComputeStepTask<CONFIG, ?>> createComputeSteps(HugeAtomicBitSet voteBits) {
        Function<Partition, ComputeStepTask<CONFIG, ?>> partitionFunction = partition -> new ComputeStepTask<>(
            graph,
            computation,
            config,
            0,
            partition,
            nodeValues,
            messenger,
            voteBits,
            graph
        );

        switch (config.partitioning()) {
            case RANGE:
                return PartitionUtils.rangePartition(concurrency, graph.nodeCount(), partitionFunction);
            case DEGREE:
                var batchSize = Math.max(
                    ParallelUtil.DEFAULT_BATCH_SIZE,
                    BitUtil.ceilDiv(graph.relationshipCount(), concurrency)
                );
                return PartitionUtils.degreePartition(graph, batchSize, partitionFunction);
            default:
                throw new IllegalArgumentException(formatWithLocale(
                    "Unsupported partitioning `%s`",
                    config.partitioning()
                ));
        }
    }
}
