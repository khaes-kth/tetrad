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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author Joseph Ramsey (this version).
 */
public final class PcAll implements GraphSearch {
    public enum FasType {REGULAR, STABLE}

    public enum Concurrent {YES, NO}

    private FasType fasType = FasType.REGULAR;
    private Concurrent concurrent = Concurrent.YES;
    private IndependenceTest test;
    private OrientColliders.ColliderMethod colliderDiscovery = OrientColliders.ColliderMethod.SEPSETS;
    private OrientColliders.ConflictRule conflictRule = OrientColliders.ConflictRule.OVERWRITE;
    private OrientColliders.IndependenceDetectionMethod independenceMethod = OrientColliders.IndependenceDetectionMethod.ALPHA;
    private boolean doMarkovLoop = false;
    private IKnowledge knowledge = new Knowledge2();
    private int depth = 1000;
    private double orientationAlpha = 0.;
    private Graph initialGraph;
    private boolean aggressivelyPreventCycles = false;
    private TetradLogger logger = TetradLogger.getInstance();
    private SepsetMap sepsets;
    private long elapsedTime;
    private boolean verbose = false;
    private int maxPathLength;
    private PrintStream out = System.out;

    private Graph G;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public PcAll(IndependenceTest independenceTest, Graph initialGraph) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.test = independenceTest;
        this.initialGraph = initialGraph;
    }

    //==============================PUBLIC METHODS========================//

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public void setFasType(FasType fasType) {
        this.fasType = fasType;
    }

    public void setConcurrent(Concurrent concurrent) {
        this.concurrent = concurrent;
    }

    public void setOrientationAlpha(double orientationAlpha) {
        this.orientationAlpha = orientationAlpha;
    }

    /**
     * @return true just in case edges will not be added if they would create cycles.
     */
    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    /**
     * Sets to true just in case edges will not be added if they would create cycles.
     */
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public void setColliderDiscovery(OrientColliders.ColliderMethod colliderMethod) {
        this.colliderDiscovery = colliderMethod;
    }

    public void setConflictRule(OrientColliders.ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    public void setIndependenceMethod(OrientColliders.IndependenceDetectionMethod independenceMethod) {
        this.independenceMethod = independenceMethod;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
     */
    public final void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Depth must not be Integer.MAX_VALUE, " +
                    "due to a known bug.");
        }

        this.depth = depth;
    }

    /**
     * @return the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public final long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     */
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * @return the independence test used in the search, set in the constructor. This is not returning a copy, for fear
     * of duplicating the data set!
     */
    public IndependenceTest getTest() {
        return test;
    }

    /**
     * @return the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @return the set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAmbiguousTriples() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        throw new UnsupportedOperationException();
    }

    public void setDoMarkovLoop(boolean doMarkovLoop) {
        this.doMarkovLoop = doMarkovLoop;
    }

    /**
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        throw new UnsupportedOperationException();
    }

    public Set<Edge> getAdjacencies() {
        return new HashSet<>(G.getEdges());
    }

    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(G);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(G);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    public Graph search() {
        return search(getTest().getVariables());
    }

    public Graph search(List<Node> nodes) {
        this.logger.log("info", "Starting CPC algorithm");
        this.logger.log("info", "Independence test = " + getTest() + ".");

        test.setVerbose(verbose);

        if (getTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        long start = System.currentTimeMillis();

        if (colliderDiscovery == OrientColliders.ColliderMethod.MPC) {
            G = kpartial(test);
        } else {
            findAdjacencies();
            G = E(G);
        }

        System.out.println("doMarkovLoop = " + doMarkovLoop);

        if (doMarkovLoop) {
            if (colliderDiscovery == OrientColliders.ColliderMethod.SEPSETS) {
                throw new IllegalArgumentException("Cannot do the Markov loop with the Sepset method of collider discovery.");
            }

            doMarkovLoop(nodes);
        }

        printNonMarkovCounts(nodes);

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + G);

        long end = System.currentTimeMillis();

        this.elapsedTime = end - start;

        TetradLogger.getInstance().

                log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().

                log("info", "Finishing CPC algorithm.");

        TetradLogger.getInstance().flush();
        return G;
    }

    private void doMarkovLoop(List<Node> nodes) {

        int round = 0;
        boolean changed = true;

        while (changed && round < 10) {
            System.out.println("Round = " + ++round);

            changed = false;

            for (Node y : nodes) {
                for (Node x : nodes) {
                    if (x == y) continue;
                    if (G.isAdjacentTo(x, y)) continue;

                    if (nonMarkovContains(y, G, x)) {
                        Graph H = new EdgeListGraph(G);
                        H.addUndirectedEdge(x, y);
                        G = E(H);
                        changed = true;
                    }
                }
            }

            for (Edge edge : G.getEdges()) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                Graph H = new EdgeListGraph(G);
                H.removeEdge(x, y);

                List<Node> adj = H.getAdjacentNodes(x);
                adj.retainAll(H.getAdjacentNodes(y));

                for (Node z : adj) {
                    if (!H.isDefCollider(x, z, y) && isCollider(x, z, y, H)) {
                        H.removeEdge(x, z);
                        H.removeEdge(y, z);
                        H.addDirectedEdge(x, z);
                        H.addDirectedEdge(y, z);
                    }
                }

                if (nonMarkovEmpty(y, H) && nonMarkovEmpty(x, H)) {
                    G = E(H);
                    changed = true;
                }
            }
        }
    }

    private boolean isCollider(Node x, Node z, Node y, Graph h) {
        OrientColliders orientColliders;

        if (colliderDiscovery == OrientColliders.ColliderMethod.SEPSETS) {
            orientColliders = new OrientColliders(test, sepsets);
        } else {
            orientColliders = new OrientColliders(test, colliderDiscovery);
        }

        orientColliders.setConflictRule(conflictRule);
        orientColliders.setIndependenceDetectionMethod(independenceMethod);
        orientColliders.setDepth(depth);
        orientColliders.setOrientationQ(orientationAlpha);
        orientColliders.setVerbose(verbose);
        orientColliders.setOut(out);
        return orientColliders.orientTriple(h, x, z, y) == SearchGraphUtils.CpcTripleType.COLLIDER;
    }

    private Graph E(Graph H) {
        H = removeOrientations(H);
        applyBackgroundKnowledge(H);
        orientTriples(H);
        applyMeekRules(H);
        removeUnnecessaryMarks(H);
        return H;
    }

    private void applyBackgroundKnowledge(Graph G) {
        SearchGraphUtils.pcOrientbk(knowledge, G, G.getNodes());
    }

    private Graph removeOrientations(Graph G) {
        return GraphUtils.undirectedGraph(G);
    }

    public static List<Edge> asList(int[] indices, List<Edge> nodes) {
        List<Edge> list = new LinkedList<>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private void printNonMarkovCounts(List<Node> nodes) {
        nodes = new ArrayList<>(nodes);
        Collections.sort(nodes);
        List<List<Node>> extra = new ArrayList<>();

        for (Node node : nodes) {
            extra.add(nonMarkov(node, G));
        }

        for (int i = 0; i < nodes.size(); i++) {
            System.out.println("Count for " + nodes.get(i) + " = " + extra.get(i).size()
                    + " boundary = " + boundary(nodes.get(i), G)
                    + " non-Markov = " + extra.get(i));
        }
    }

    private List<Node> boundary(Node x, Graph G) {
        Set<Node> b = new HashSet<>();

        for (Node y : G.getAdjacentNodes(x)) {
            if (x == y) continue;

            if (Edges.isUndirectedEdge(G.getEdge(x, y))) {
                b.add(y);
            }

            if (G.isParentOf(y, x)) {
                b.add(y);
            }
        }

        return new ArrayList<>(b);
    }

    private List<Node> nonMarkov(Node y, Graph G) {
        List<Node> boundary = boundary(y, G);
        List<Node> nodes = new ArrayList<>();

        for (Node x : G.getNodes()) {
            if (y == x) continue;
            if (G.isDescendentOf(x, y)) continue;
            if (boundary.contains(x)) continue;
            if (!test.isIndependent(y, x, boundary)) {
                nodes.add(x);
            }
        }

        return nodes;
    }

    private boolean nonMarkovEmpty(Node y, Graph G) {
        List<Node> boundary = boundary(y, G);

        for (Node x : G.getNodes()) {
            if (y == x) continue;
            if (G.isDescendentOf(x, y)) continue;
            if (boundary.contains(x)) continue;
            if (!test.isIndependent(y, x, boundary)) {
                return false;
            }
        }

        return true;
    }

    // Returns true if the set of non-Markov variables to y contains x.
    private boolean nonMarkovContains(Node y, Graph G, Node x) {
        List<Node> boundary = boundary(y, G);
        if (y == x) return false;
        if (G.isDescendentOf(x, y)) return false;
        if (boundary.contains(x)) return false;
        return !test.isIndependent(y, x, boundary);
    }

    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public SepsetMap getSepsets() {
        return sepsets;
    }

    /**
     * The graph that's constructed during the search.
     */
    public Graph getG() {
        return G;
    }

    public void setG(Graph g) {
        this.G = g;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public Graph getInitialGraph() {
        return initialGraph;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(Object from, Object to,
                                              IKnowledge knowledge) {
        if (knowledge == null) {
            return true;

        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }
//==========================PRIVATE METHODS===========================//

    private void findAdjacencies() {
        IFas fas;

        if (G != null) {
            initialGraph = G;
        }

        if (fasType == FasType.REGULAR) {
            if (concurrent == Concurrent.NO) {
                fas = new Fas(initialGraph, getTest());
            } else {
                fas = new FasConcurrent(initialGraph, getTest());
                ((FasConcurrent) fas).setStable(false);
            }
        } else {
            if (concurrent == Concurrent.NO) {
                fas = new FasStable(initialGraph, getTest());
            } else {
                fas = new FasConcurrent(initialGraph, getTest());
                ((FasConcurrent) fas).setStable(true);
            }
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.G = fas.search();
        sepsets = fas.getSepsets();
    }

    private void orientTriples(Graph G) {
        OrientColliders orientColliders;

        if (colliderDiscovery == OrientColliders.ColliderMethod.SEPSETS) {
            orientColliders = new OrientColliders(test, sepsets);
        } else {
            orientColliders = new OrientColliders(test, colliderDiscovery);
        }

        orientColliders.setConflictRule(conflictRule);
        orientColliders.setIndependenceDetectionMethod(independenceMethod);
        orientColliders.setDepth(depth);
        orientColliders.setOrientationQ(orientationAlpha);
        orientColliders.setVerbose(verbose);
        orientColliders.setOut(out);
        orientColliders.orientTriples(G);
    }

    private void applyMeekRules(Graph G) {
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());
        meekRules.setAggressivelyPreventCycles(true);
        meekRules.setUndirectUnforcedEdges(true);
        meekRules.orientImplied(G);
    }

    private void removeUnnecessaryMarks(Graph G) {

        // Remove unnecessary marks.
        for (Triple triple : G.getUnderLines()) {
            G.removeUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }

        for (Triple triple : G.getAmbiguousTriples()) {
            if (G.getEdge(triple.getX(), triple.getY()).pointsTowards(triple.getX())) {
                G.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (G.getEdge(triple.getZ(), triple.getY()).pointsTowards(triple.getZ())) {
                G.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (G.getEdge(triple.getX(), triple.getY()).pointsTowards(triple.getY())
                    && G.getEdge(triple.getZ(), triple.getY()).pointsTowards(triple.getY())) {
                G.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }
        }
    }

    private Graph kpartial(IndependenceTest test) {
        colliderDiscovery = OrientColliders.ColliderMethod.CPC;

        int k = depth;

        findAdjacencies();

        G = E(G);

        List<Node> nodes = test.getVariables();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i);
                Node b = nodes.get(j);
                List<Node> _nodes = new ArrayList<>(nodes);
                _nodes.remove(a);
                _nodes.remove(b);

                DepthChoiceGenerator gen = new DepthChoiceGenerator(_nodes.size(), k);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> Z = GraphUtils.asList(choice, _nodes);

                    if (test.isIndependent(a, b, Z)) {
                        List<Node> C = G.getAdjacentNodes(a);
                        C.retainAll(G.getAdjacentNodes(b));

                        for (Node c : C) {
                            if (Z.contains(c)) continue;
                            if (!G.getEdges(a, c).contains(Edges.directedEdge(c, a))
                                    || !G.getEdges(b, c).contains(Edges.directedEdge(c, b))) continue;

                            if (test.isDependent(a, c, Z) && test.isDependent(c, b, Z)) {
                                System.out.println("Removing " + a + "<-" + c + "->" + b);

                                kpartialRemoveEdge(G, c, a);
                                kpartialRemoveEdge(G, c, b);

                                System.out.println("a--c edges = " + G.getEdges(a, c));
                                System.out.println("b--c edges = " + G.getEdges(b, c));
                            }
                        }
                    }
                }
            }
        }

        applyMeekRules(G);

        return G;
    }

    private void kpartialRemoveEdge(Graph g, Node c, Node a) {
        if (g.getEdge(a, c) == null) throw new IllegalArgumentException("No edge to remove");
        if (g.getEdge(c, a).pointsTowards(a)) g.removeEdge(c, a);
        else if (g.isUndirectedFromTo(c, a)) {
            g.removeEdge(c, a);
            g.addDirectedEdge(a, c);
        }
    }


}

