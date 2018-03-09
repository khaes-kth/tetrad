package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ConcurrencyUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.multiplyExact;

/**
 * An adaptation of the CStaR algorithm (Steckoven et al., 2012).
 * <p>
 * Stekhoven, D. J., Moraes, I., Sveinbjörnsson, G., Hennig, L., Maathuis, M. H., & Bühlmann, P. (2012). Causal stability ranking. Bioinformatics, 28(21), 2819-2823.
 * <p>
 * Meinshausen, N., & Bühlmann, P. (2010). Stability selection. Journal of the Royal Statistical Society: Series B (Statistical Methodology), 72(4), 417-473.
 * <p>
 * Colombo, D., & Maathuis, M. H. (2014). Order-independent constraint-based causal structure learning. The Journal of Machine Learning Research, 15(1), 3741-3782.
 *
 * @author jdramsey@andrew.cmu.edu
 */
public class CStaSMulti {

    private int maxTrekLength = 15;
    private int numSubsamples = 30;
    private int qFrom = 1;
    private int qTo = 1;
    private int qIncrement = 1;

    private int parallelism = Runtime.getRuntime().availableProcessors() * 10;
    private Graph trueDag = null;
    private IndependenceTest test;

    public int getqFrom() {
        return qFrom;
    }

    public void setqFrom(int qFrom) {
        this.qFrom = qFrom;
    }

    public int getqTo() {
        return qTo;
    }

    public void setqTo(int qTo) {
        this.qTo = qTo;
    }

    public int getqIncrement() {
        return qIncrement;
    }

    public void setqIncrement(int qIncrement) {
        this.qIncrement = qIncrement;
    }

    // A single record in the returned table.
    public static class Record implements TetradSerializable {
        static final long serialVersionUID = 23L;

        private Node predictor;
        private Node target;
        private double pi;
        private double effect;
        private double pcer;
        private double er;
        private double MBEv;
        private boolean ancestor;

        Record(Node predictor, Node target, double pi, double minEffect, double pcer, double ev, double MBev, boolean ancestor) {
            this.predictor = predictor;
            this.target = target;
            this.pi = pi;
            this.effect = minEffect;
            this.pcer = pcer;
            this.er = ev;
            this.MBEv = MBev;
            this.ancestor = ancestor;
        }

        public Node getCause() {
            return predictor;
        }

        public Node getTarget() {
            return target;
        }

        public double getPi() {
            return pi;
        }

        public double getEffect() {
            return effect;
        }

        double getPcer() {
            return pcer;
        }

        double getEr() {
            return er;
        }

        public boolean isAncestor() {
            return ancestor;
        }

        public double getMBEv() {
            return MBEv;
        }
    }

    public CStaSMulti() {
    }

    /**
     * Returns records for a set of variables with expected number of false positives bounded by q.
     *
     * @param dataSet            The full datasets to search over.
     * @param possibleCauses A set of variables in the datasets over which to search.
     * @param targets            The effect variables.
     * @param test               This test is only used to make more tests like it for subsamples.
     */
    public LinkedList<List<Record>> getRecords(DataSet dataSet, List<Node> possibleCauses, List<Node> targets, IndependenceTest test) {
        targets = GraphUtils.replaceNodes(targets, dataSet.getVariables());
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());
        final int tSize = targets.size();

        if (new HashSet<>(possibleCauses).removeAll(new HashSet<>(targets))) {
            throw new IllegalArgumentException("Possible predictors and targets must be disjoint sets.");
        }

        if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof SemBicScore) {
            this.test = test;
        } else if (test instanceof IndTestFisherZ) {
            this.test = test;
        } else if (test instanceof ChiSquare) {
            this.test = test;
        } else if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof ConditionalGaussianScore) {
            this.test = test;
        } else {
            throw new IllegalArgumentException("That test is not configured.");
        }

        List<Node> augmented = new ArrayList<>(targets);
        augmented.addAll(possibleCauses);

        class Tuple {
            private Node cause;
            private Node effect;
            private double pi;

            public Tuple(Node cause, Node effect, double pi) {
                this.cause = cause;
                this.effect = effect;
                this.pi = pi;
            }

            public Node getCause() {
                return cause;
            }

            public Node getEffect() {
                return effect;
            }

            public double getPi() {
                return pi;
            }
        }

        final DataSet selection = dataSet.subsetColumns(augmented);

        final List<Node> variables = selection.getVariables();
        variables.removeAll(targets);

//        final List<Map<Integer, Map<Node, Double>>> minimalEffects = new ArrayList<>();

//        for (int t = 0; t < tSize; t++) {
//            minimalEffects.add(new ConcurrentHashMap<>());
//
//            for (int b = 0; b < getNumSubsamples(); b++) {
//                final Map<Node, Double> map = new ConcurrentHashMap<>();
//                for (Node node : possibleCauses) map.put(node, 0.0);
//                minimalEffects.get(t).put(b, map);
//            }
//        }

        final List<List<Ida.NodeEffects>> effects = new ArrayList<>();
        final List<Node> _targets = new ArrayList<>(targets);

        for (int t = 0; t < tSize; t++) {
            effects.add(new ArrayList<>());
        }

        final List<Node> _possiblePredictors = new ArrayList<>(possibleCauses);
        final List<Integer> edgeCounts = new ArrayList<>();

        class Task implements Callable<Boolean> {
            private Task() {
            }

            public Boolean call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    sampler.setWithoutReplacements(false);
                    DataSet sample = sampler.sample(dataSet, dataSet.getNumRows());
                    Graph pattern = getPatternFges(sample);
                    Ida ida = new Ida(sample, pattern, _possiblePredictors);

                    for (int t = 0; t < _targets.size(); t++) {
                        effects.get(t).add(ida.getSortedMinEffects(_targets.get(t)));
                    }

                    edgeCounts.add(pattern.getNumEdges());

                    System.out.println("R");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int b = 0; b < getNumSubsamples(); b++) {
            tasks.add(new Task());
        }

        ConcurrencyUtils.runCallables(tasks, getParallelism());

        int totalEdges = 0;

        for (int count : edgeCounts) {
            totalEdges += count;
        }

        int p = dataSet.getNumColumns();

        double avgEdges = totalEdges / getNumSubsamples();
        final double avgDegree = 2.0 * avgEdges / possibleCauses.size();

        System.out.println();
        LinkedList<List<Record>> allRecords = new LinkedList<>();

        for (int q = getqFrom(); q <= getqTo(); q += getqIncrement()) {
            System.out.println("Examining q = " + q);

            final List<Map<Node, Integer>> counts = new ArrayList<>();

            for (int t = 0; t < tSize; t++) {
                counts.add(new HashMap<>());
                for (Node node : possibleCauses) counts.get(t).put(node, 0);
            }

            for (int w = 1; w <= q; w++) {
                for (int t = 0; t < tSize; t++) {
                    final Map<Node, Integer> _counts = counts.get(t);

                    for (Ida.NodeEffects _effects : effects.get(t)) {
                        final List<Node> nodes = _effects.getNodes();

                        if (w - 1 < possibleCauses.size()) {
                            final Node key = nodes.get(w - 1);
                            _counts.put(key, _counts.get(key) + 1);
                        }
                    }
                }
            }

//            for (int t = 0; t < tSize; t++) {
//                for (int b = 0; b < effects.get(t).size(); b++) {
//                    Ida.NodeEffects _effects = effects.get(t).get(b);
//                    final List<Node> nodes = _effects.getNodes();
//                    final LinkedList<Double> effects1 = _effects.getEffects();
//                    final Map<Node, Double> _minimalEffects = minimalEffects.get(t).get(b);
//
//                    for (int r = 0; r < possibleCauses.size(); r++) {
//                        Node n = nodes.get(r);
//                        Double e = effects1.get(r);
//                        _minimalEffects.put(n, e);
//                    }
//                }
//            }

            LinkedList<Tuple> tuples = new LinkedList<>();

            double minPi = Double.POSITIVE_INFINITY;

            for (int t = 0; t < tSize; t++) {
                for (Node v : possibleCauses) {
                    final Integer count = counts.get(t).get(v);
                    final double pi = count / ((double) getNumSubsamples());

                    if (tuples.size() < q) {
                        tuples.add(new Tuple(v, targets.get(t), pi));
                        if (pi < minPi) minPi = pi;
                    } else {
                        if (pi > minPi) {
                            tuples.add(new Tuple(v, targets.get(t), pi));
                        }

                        if (tuples.size() > q) {
                            int index = -1;
                            double _min = Double.POSITIVE_INFINITY;

                            for (int s = 0; s < tuples.size(); s++) {
                                if (tuples.get(s).getPi() < _min) {
                                    index = s;
                                    minPi = _min;
                                    _min = tuples.get(s).getPi();
                                }
                            }

                            tuples.remove(index);
                        }
                    }
                }
            }

            double sum = 0.0;

            for (int g = 0; g < q; g++) {
                sum += tuples.get(g).getPi();
            }

            double pi_thr = Double.NaN;

            for (int g = 0; g < q; g++) {
                if (Double.isNaN(pi_thr) || tuples.get(g).getPi() < pi_thr) pi_thr = tuples.get(g).getPi();
            }

            trueDag = GraphUtils.replaceNodes(trueDag, possibleCauses);
            trueDag = GraphUtils.replaceNodes(trueDag, targets);

            List<Record> records = new ArrayList<>();

            for (Tuple tuple : tuples) {
                List<Double> e = new ArrayList<>();

                for (int b = 0; b < getNumSubsamples(); b++) {
                    Ida.NodeEffects _effects = effects.get(targets.indexOf(tuple.getEffect())).get(b);
                    final LinkedList<Double> effects1 = _effects.getEffects();

                    for (int r = 0; r < possibleCauses.size(); r++) {
                        e.add(effects1.get(r));
                    }
                }

                double[] _e = new double[e.size()];
                for (int t = 0; t < e.size(); t++) _e[t] = e.get(t);
                double avg = StatUtils.mean(_e);
                boolean ancestor = false;

                if (trueDag != null) {
                    ancestor = trueDag.isAncestorOf(tuple.getCause(), tuple.getEffect());
                }

                double ev = q - sum;
                double mbev = er(pi_thr, q, p);

                records.add(new Record(tuple.getCause(), tuple.getEffect(), tuple.getPi(), avg, ev, ev, mbev, ancestor));
            }

            records.sort((o1, o2) -> {
                if (o1.getPi() == o2.getPi()) {
                    return Double.compare(o2.getEffect(), o1.getEffect());
                } else {
                    return Double.compare(o2.getPi(), o1.getPi());
                }
            });

            allRecords.add(records);
        }


        return allRecords;
    }

    /**
     * Returns a text table from the given records
     */
    public String makeTable(List<Record> records) {
        TextTable table = new TextTable(records.size() + 1, 8);
        NumberFormat nf = new DecimalFormat("0.0000");
        int col = 0;

        table.setToken(0, col++, "Index");
        table.setToken(0, col++, "Predictor");
        table.setToken(0, col++, "Target");
        table.setToken(0, col++, "Type");
        table.setToken(0, col++, "A");
        table.setToken(0, col++, "PI");
        table.setToken(0, col++, "Average Effect");
        table.setToken(0, col++, "PCER");
//        table.setToken(0, 8, "ER");

        int fp = 0;

        for (int i = 0; i < records.size(); i++) {
            final Node predictor = records.get(i).getCause();
            final Node target = records.get(i).getTarget();
            final boolean ancestor = records.get(i).isAncestor();
            if (!(ancestor)) fp++;
            col = 0;

            table.setToken(i + 1, col++, "" + (i + 1));
            table.setToken(i + 1, col++, predictor.getName());
            table.setToken(i + 1, col++, target.getName());
            table.setToken(i + 1, col++, predictor instanceof DiscreteVariable ? "D" : "C");
            table.setToken(i + 1, col++, ancestor ? "A" : "");
            table.setToken(i + 1, col++, nf.format(records.get(i).getPi()));
            table.setToken(i + 1, col++, nf.format(records.get(i).getEffect()));
            table.setToken(i + 1, col++, nf.format(records.get(i).getPcer()));
        }

        final double er = !records.isEmpty() ? records.get(0).getEr() : Double.NaN;
        final double mbEv = !records.isEmpty() ? records.get(0).getMBEv() : Double.NaN;

        return "\n" + table + "\n" + "# FP = " + fp + " E(V) = " + nf.format(er) + " MB-E(V) = " + nf.format(mbEv) +
                "\n\nT = exists a trek of length no more than " + maxTrekLength + " to the effect" +
                "\nA = ancestor of the effect" +
                "\nType: C = continuous, D = discrete\n";
    }

    /**
     * Makes a graph of the estimated predictors to the effect.
     */
    public Graph makeGraph(Node y, List<Record> records) {
        List<Node> outNodes = new ArrayList<>();
        for (Record record : records) outNodes.add(record.getCause());

        Graph graph = new EdgeListGraph(outNodes);
        graph.addNode(y);

        for (int i = 0; i < new ArrayList<>(outNodes).size(); i++) {
            graph.addDirectedEdge(outNodes.get(i), y);
        }

        return graph;
    }

    public void setNumSubsamples(int numSubsamples) {
        this.numSubsamples = numSubsamples;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public void setTrueDag(Graph trueDag) {
        this.trueDag = trueDag;
    }

    //=============================PRIVATE==============================//

    private int getNumSubsamples() {
        return numSubsamples;
    }

    private int getParallelism() {
        return parallelism;
    }

    private Graph getPatternPcStable(DataSet sample) {
        IndependenceTest test = getIndependenceTest(sample, this.test);
        PcAll pc = new PcAll(test, null);
        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
        pc.setConflictRule(PcAll.ConflictRule.OVERWRITE);
        pc.setColliderDiscovery(PcAll.ColliderDiscovery.FAS_SEPSETS);
        return pc.search();
    }

    private Graph getPatternFges(DataSet sample) {
        Score score = new ScoredIndTest(getIndependenceTest(sample, this.test));
        Fges fges = new Fges(score);
        fges.setParallelism(1);
        return fges.search();
    }

    private Graph getPatternFgesMb(DataSet sample, Node target) {
        Score score = new ScoredIndTest(getIndependenceTest(sample, this.test));
        FgesMb fges = new FgesMb(score);
        return fges.search(target);
    }

    private IndependenceTest getIndependenceTest(DataSet sample, IndependenceTest test) {
        if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof SemBicScore) {
            SemBicScore score = new SemBicScore(new CorrelationMatrixOnTheFly(sample));
            score.setPenaltyDiscount(((SemBicScore) ((IndTestScore) test).getWrappedScore()).getPenaltyDiscount());
            return new IndTestScore(score);
        } else if (test instanceof IndTestFisherZ) {
            double alpha = test.getAlpha();
            return new IndTestFisherZ(new CorrelationMatrixOnTheFly(sample), alpha);
        } else if (test instanceof ChiSquare) {
            double alpha = test.getAlpha();
            return new IndTestFisherZ(sample, alpha);
        } else if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof ConditionalGaussianScore) {
            ConditionalGaussianScore score = (ConditionalGaussianScore) ((IndTestScore) test).getWrappedScore();
            double penaltyDiscount = score.getPenaltyDiscount();
            ConditionalGaussianScore _score = new ConditionalGaussianScore(sample, 1, false);
            _score.setPenaltyDiscount(penaltyDiscount);
            return new IndTestScore(_score);
        } else {
            throw new IllegalArgumentException("That test is not configured: " + test);
        }
    }

    // E(|V|) bound
    private static double er(double pi, double q, double p) {
        double v = q * q / (p * (2 * pi - 1));
        if (v < 0 || v > q) v = q;
        return v;
    }

    // Per comparison error rate.
    private static double pcer(double pi, double q, double p) {
        double v = (q * q) / (p * p * (2 * pi - 1));
        if (v < 0 || v > 1) v = 1;
        return v;
    }

    public List<Node> selectVariables(DataSet dataSet, List<Node> targets, double alpha, int parallelism) {
        IndependenceTest test = new IndTestFisherZ(dataSet, alpha);
        List<Node> selection = new CopyOnWriteArrayList<>();

        final List<Node> variables = dataSet.getVariables();
        targets = GraphUtils.replaceNodes(targets, test.getVariables());

        final List<Node> y = new ArrayList<>(targets);

        {
            class Task implements Callable<Boolean> {
                private int from;
                private int to;
                private List<Node> y;

                private Task(int from, int to, List<Node> y) {
                    this.from = from;
                    this.to = to;
                    this.y = y;
                }

                @Override
                public Boolean call() {
                    for (int n = from; n < to; n++) {
                        final Node node = variables.get(n);
                        if (!y.contains(node)) {
                            for (Node target : y) {
                                if (!test.isIndependent(node, target)) {
                                    if (!selection.contains(node)) {
                                        selection.add(node);
                                    }
                                }
                            }
                        }
                    }

                    return true;
                }
            }

            final int chunk = 50;
            List<Callable<Boolean>> tasks;

            {
                tasks = new ArrayList<>();

                for (int from = 0; from < variables.size(); from += chunk) {
                    final int to = Math.min(variables.size(), from + chunk);
                    tasks.add(new Task(from, to, y));
                }

                ConcurrencyUtils.runCallables(tasks, parallelism);
            }

//            test.setAlpha(test.getAlpha() / 20);
//
//            {
//                tasks = new ArrayList<>();
//
//                for (int from = 0; from < variables.size(); from += chunk) {
//                    final int to = Math.min(variables.size(), from + chunk);
//                    tasks.add(new Task(from, to, new ArrayList<>(selection)));
//                }
//
//                ConcurrencyUtils.runCallables(tasks, parallelism);
//            }
        }

        selection.removeAll(targets);

        System.out.println("# selected variables = " + selection.size());

        return selection;
    }
}