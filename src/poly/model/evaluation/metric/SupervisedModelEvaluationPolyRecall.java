package poly.model.evaluation.metric;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import poly.data.annotation.TokenSpansDatum;
import ark.data.Context;
import ark.data.feature.FeaturizedDataSet;
import ark.model.SupervisedModel;
import ark.model.evaluation.metric.SupervisedModelEvaluation;
import ark.model.evaluation.metric.SupervisedModelEvaluationRecall;

public class SupervisedModelEvaluationPolyRecall<L> extends SupervisedModelEvaluationRecall<TokenSpansDatum<L>, L> {
	public SupervisedModelEvaluationPolyRecall() {
		super();
	}
	
	public SupervisedModelEvaluationPolyRecall(Context<TokenSpansDatum<L>, L> context) {
		super(context);
	}
	
	@Override
	protected double compute(SupervisedModel<TokenSpansDatum<L>, L> model, FeaturizedDataSet<TokenSpansDatum<L>, L> data, Map<TokenSpansDatum<L>, L> predictions) {
		Map<TokenSpansDatum<L>, L> polysemousPredictions = new HashMap<TokenSpansDatum<L>, L>();
		
		for (Entry<TokenSpansDatum<L>, L> prediction : predictions.entrySet())
			if (prediction.getKey().isPolysemous())
				polysemousPredictions.put(prediction.getKey(), prediction.getValue());
			
		return super.compute(model, data, polysemousPredictions);
	}
	
	@Override
	public String getGenericName() {
		return "PolyRecall";
	}

	@Override
	public SupervisedModelEvaluation<TokenSpansDatum<L>, L> makeInstance(Context<TokenSpansDatum<L>, L> context) {
		return new SupervisedModelEvaluationPolyRecall<L>(context);
	}

}
