package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.DagToPag2;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * GFCI.
 *
 * @author jdramsey
 */
public class FaskGfci implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public FaskGfci(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.FaskGfci search = new edu.cmu.tetrad.search.FaskGfci(test.getTest(dataSet, parameters),
                (DataSet) dataSet);
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag2(graph).convert();
    }

    @Override
    public String getDescription() {
        return "FASKGFCI (Greedy Fast Causal Inference with FASK knowledge) using " + test.getDescription() +
                " and " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.addAll(test.getParameters());
        parameters.add("faithfulnessAssumed");
        parameters.add("maxDegree");
        parameters.add("maxPathLength");
        parameters.add("completeRuleSetUsed");
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

}
