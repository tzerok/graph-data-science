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
package org.neo4j.gds.similarity.ann;

import org.neo4j.gds.impl.similarity.ApproxNearestNeighborsAlgorithm;
import org.neo4j.gds.impl.similarity.ApproximateNearestNeighborsConfig;
import org.neo4j.gds.impl.similarity.SimilarityAlgorithmResult;
import org.neo4j.gds.impl.similarity.SimilarityInput;
import org.neo4j.gds.pipeline.ComputationResultConsumer;
import org.neo4j.gds.pipeline.GdsCallable;
import org.neo4j.gds.results.SimilarityResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.pipeline.ExecutionMode.STREAM;
import static org.neo4j.gds.similarity.ann.ApproxNearestNeighborsProc.DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

@GdsCallable(name = "gds.alpha.ml.ann.stream", description = DESCRIPTION, executionMode = STREAM)
public class ApproxNearestNeighborsStreamProc extends ApproxNearestNeighborsProc<SimilarityResult> {

    @Procedure(name = "gds.alpha.ml.ann.stream", mode = READ)
    @Description(DESCRIPTION)
    public Stream<SimilarityResult> annStream(
        @Name(value = "configuration") Map<String, Object> configuration
    ) {
        return stream(configuration);
    }

    @Override
    public ComputationResultConsumer<ApproxNearestNeighborsAlgorithm<SimilarityInput>, SimilarityAlgorithmResult, ApproximateNearestNeighborsConfig, Stream<SimilarityResult>> computationResultConsumer() {
        return streamResultConsumer();
    }
}
