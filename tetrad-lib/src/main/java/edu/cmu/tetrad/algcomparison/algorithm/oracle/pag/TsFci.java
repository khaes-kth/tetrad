package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TsDagToPag;

import java.util.List;

/**
 * tsFCI.
 *
 * @author jdramsey
 * @author dmalinsky
 */
public class TsFci implements Algorithm, TakesInitialGraph, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = null;

    public TsFci(IndependenceWrapper type) {
        this.test = type;
    }

    public TsFci(IndependenceWrapper type, Algorithm initialGraph) {
        this.test = type;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.TsFci search = new edu.cmu.tetrad.search.TsFci(test.getTest(dataSet, parameters));
        search.setDepth(parameters.getInt("depth"));

        IKnowledge _knowledge = getKnowledge() != null ? getKnowledge() : dataSet.getKnowledge();

        search.setKnowledge(_knowledge);
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) { return new TsDagToPag(new EdgeListGraph(graph)).convert(); }

    public String getDescription() {
        return "tsFCI (Time Series Fast Causal Inference) using " + test.getDescription() +
                (initialGraph != null ? " with initial graph from " +
                        initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        return test.getParameters();
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
