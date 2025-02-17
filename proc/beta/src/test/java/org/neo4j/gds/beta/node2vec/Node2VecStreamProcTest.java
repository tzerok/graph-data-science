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
package org.neo4j.gds.beta.node2vec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.AlgoBaseProcTest;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.catalog.GraphProjectProc;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.embeddings.node2vec.Node2Vec;
import org.neo4j.gds.embeddings.node2vec.Node2VecModel;
import org.neo4j.gds.embeddings.node2vec.Node2VecStreamConfig;
import org.neo4j.gds.extension.Neo4jGraph;
import org.neo4j.graphdb.GraphDatabaseService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

class Node2VecStreamProcTest extends BaseProcTest implements AlgoBaseProcTest<Node2Vec, Node2VecStreamConfig, Node2VecModel.Result> {

    @Neo4jGraph
    public static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node1)" +
        ", (b:Node1)" +
        ", (c:Node2)" +
        ", (d:Isolated)" +
        ", (e:Isolated)" +
        ", (a)-[:REL]->(b)" +
        ", (b)-[:REL]->(a)" +
        ", (a)-[:REL]->(c)" +
        ", (c)-[:REL]->(a)" +
        ", (b)-[:REL]->(c)" +
        ", (c)-[:REL]->(b)";


    @BeforeEach
    void setUp() throws Exception {
        registerProcedures(
            Node2VecStreamProc.class,
            GraphProjectProc.class
        );
    }

    @Test
    void embeddingsShouldHaveTheConfiguredDimension() {
        loadGraph(DEFAULT_GRAPH_NAME);
        int dimensions = 42;
        var query = GdsCypher.call(DEFAULT_GRAPH_NAME)
            .algo("gds.beta.node2vec")
            .streamMode()
            .addParameter("embeddingDimension", 42)
            .yields();

        runQueryWithRowConsumer(query, row -> {
            assertThat(row.get("embedding"))
                .asList()
                .hasSize(dimensions);
        });
    }

    @Override
    public Class<Node2VecStreamProc> getProcedureClazz() {
        return Node2VecStreamProc.class;
    }

    @Override
    public Node2VecStreamConfig createConfig(CypherMapWrapper userInput) {
        return Node2VecStreamConfig.of(userInput);
    }

    @Test
    void shouldThrowIfRunningWouldOverflow() {
        long nodeCount = runQuery("MATCH (n) RETURN count(n) AS count", result ->
            result.<Long>columnAs("count").stream().findFirst().orElse(-1L)
        );
        loadGraph(DEFAULT_GRAPH_NAME);
        var query = GdsCypher.call(DEFAULT_GRAPH_NAME)
            .algo("gds.beta.node2vec")
            .streamMode()
            .addParameter("walksPerNode", Integer.MAX_VALUE)
            .addParameter("walkLength", Integer.MAX_VALUE)
            .addParameter("sudo", true)
            .yields();

        String expectedMessage = formatWithLocale(
            "Aborting execution, running with the configured parameters is likely to overflow: node count: %d, walks per node: %d, walkLength: %d." +
            " Try reducing these parameters or run on a smaller graph.",
            nodeCount,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE
        );
        assertThatThrownBy(() -> runQuery(query))
            .rootCause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(expectedMessage);
    }

    @Override
    public GraphDatabaseService graphDb() {
        return db;
    }

}
