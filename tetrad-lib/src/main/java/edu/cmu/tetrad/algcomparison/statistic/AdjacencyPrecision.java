package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class AdjacencyPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AP";
    }

    @Override
    public String getDescription() {
        return "Adjacency Precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getAdjTp();
        int adjFp = adjConfusion.getAdjFp();
        return adjTp / (double) (adjTp + adjFp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
