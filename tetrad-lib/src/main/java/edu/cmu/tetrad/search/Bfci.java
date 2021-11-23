///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

/**
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016.
 *
 * @author Juan Miguel Ogarrio
 * @author ps7z
 * @author jdramsey
 */
public final class Bfci implements GraphSearch {

    // The conditional independence test.
    private final Score score;
    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();
    // The covariance matrix beign searched over. Assumes continuous data.
    ICovarianceMatrix covarianceMatrix;
    // The sample size.
    int sampleSize;
    // The background knowledge.
    private IKnowledge knowledge = new Knowledge2();
    private IndependenceTest test;
    // Flag for complete rule set, true if should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = false;
    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;
    // The maxDegree for the fast adjacency search.
    private int maxDegree = -1;
    // True iff verbose output should be printed.
    private boolean verbose = false;

    // The print stream that output is directed to.
    private PrintStream out = System.out;

    private boolean breakTies;
    private boolean cacheScores;
    private int numStarts;
    private Boss.Method method;
    private TeyssierScorer.ScoreType scoreType;
    private boolean useScore = true;
    private int depth = -1;

    //============================CONSTRUCTORS============================//
    public Bfci(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;

        this.sampleSize = score.getSampleSize();
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        logger.log("info", "Starting FCI algorithm.");
        logger.log("info", "Independence test = " + getTest() + ".");

        // The PAG being constructed.
        Graph graph;

        Boss boss;
        List<Node> variables;

        if (useScore && !(score instanceof GraphScore)) {
            boss = new Boss(score);
            variables = score.getVariables();
        } else {
            boss = new Boss(test);
            variables = test.getVariables();
        }

        boss.setNumStarts(numStarts);
        boss.setCacheScores(cacheScores);
        boss.setScoreType(scoreType);
        boss.setTriangleDepth(depth);
        boss.setVerbose(verbose);
        boss.setKnowledge(knowledge);

        List<Node> perm = boss.bestOrder(variables);
        graph = boss.getGraph(true);
        Graph bossGraph = new EdgeListGraph(graph);

        graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(knowledge, graph, graph.getNodes());

        for (Node b : perm) {
            List<Node> adj = graph.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node a = adj.get(i);
                    Node c = adj.get(j);

                    if (!graph.isAdjacentTo(a, c) && bossGraph.isDefCollider(a, b, c)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }

        TeyssierScorer scorer;

        if (!(score instanceof GraphScore)) {
            scorer = new TeyssierScorer(score);
        } else {
            scorer = new TeyssierScorer(test);
        }

        scorer.evaluate(perm);

        List<Triple> triples = new ArrayList<>();

        for (Node b : perm) {
            Set<Node> into = scorer.getParents(b);

            for (Node a : into) {
                for (Node c : into) {
                    for (Node d : perm) {
                        if (configuration(scorer, a, b, c, d)) {
                            scorer.bookmark();
                            double score = scorer.score();
                            scorer.swap(b, c);

                            if (configuration(scorer, d, c, b, a) && score == scorer.score()) {
                                triples.add(new Triple(b, c, d));
                            }

                            scorer.goToBookmark();
                        }
                    }
                }
            }
        }

        for (Triple triple : triples) {
            Node b = triple.getX();
            Node d = triple.getZ();

            graph.removeEdge(b, d);
        }

        for (Triple triple : triples) {
            Node b = triple.getX();
            Node c = triple.getY();
            Node d = triple.getZ();

            if (graph.isAdjacentTo(b, c) && graph.isAdjacentTo(d, c)) {
                graph.setEndpoint(b, c, Endpoint.ARROW);
                graph.setEndpoint(d, c, Endpoint.ARROW);
            }
        }

        SepsetProducer sepsets = new SepsetsTeyssier(bossGraph, scorer, null, maxDegree);

        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setVerbose(verbose);
        fciOrient.setOut(out);
        fciOrient.skipDiscriminatingPathRule(false);
        fciOrient.setKnowledge(getKnowledge());
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.doFinalOrientation(graph);

        graph.setPag(true);

        graph.removeAttribute("BIC");

        return graph;
    }

    private boolean configuration(TeyssierScorer scorer, Node a, Node b, Node c, Node d) {
        if (!distinct(a, b, c, d)) return false;

        return scorer.adjacent(a, b)
                && scorer.adjacent(b, c)
                && scorer.adjacent(c, d)
                && scorer.adjacent(b, d)
                && !scorer.adjacent(a, c)
                && scorer.collider(a, b, c);
    }

    private boolean distinct(Node a, Node b, Node c, Node d) {
        Set<Node> nodes = new HashSet<>();

        nodes.add(a);
        nodes.add(b);
        nodes.add(c);
        nodes.add(d);

        return nodes.size() == 4;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    /**
     * Returns The maximum indegree of the output graph.
     */
    public int getMaxDegree() {
        return maxDegree;
    }

    /**
     * @param maxDegree The maximum indegree of the output graph.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only
     * R1-R4 (the rule set of the original FCI) should be used. False by
     * default.
     */
    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set
     *                            should be used, false if only R1-R4 (the rule set of the original FCI)
     *                            should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of
     * unlimited.
     */
    public int getMaxPathLength() {
        return maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1
     *                      if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getTest() {
        return test;
    }

    public void setTest(IndependenceTest test) {
        this.test = test;
    }

    public ICovarianceMatrix getCovMatrix() {
        return covarianceMatrix;
    }

    public ICovarianceMatrix getCovarianceMatrix() {
        return covarianceMatrix;
    }

    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    //===========================================PRIVATE METHODS=======================================//

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(IKnowledge knowledge, Graph graph, List<Node> variables) {
        logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        logger.log("info", "Finishing BK Orientation.");
    }

    public void setBreakTies(boolean breakTies) {
        this.breakTies = breakTies;
    }

    public void setCacheScores(boolean cacheScores) {
        this.cacheScores = cacheScores;
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public Boss.Method getMethod() {
        return method;
    }

    public void setMethod(Boss.Method method) {
        this.method = method;
    }

    public TeyssierScorer.ScoreType getScoreType() {
        return scoreType;
    }

    public void setScoreType(TeyssierScorer.ScoreType scoreType) {
        this.scoreType = scoreType;
    }

    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
