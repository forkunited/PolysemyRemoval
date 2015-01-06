package poly.model.evaluation.metric;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import poly.data.annotation.TokenSpansDatum;
import ark.data.feature.FeaturizedDataSet;
import ark.model.SupervisedModel;
import ark.model.evaluation.metric.SupervisedModelEvaluation;
import ark.model.evaluation.metric.SupervisedModelEvaluationAccuracy;

public class SupervisedModelEvaluationPolyAccuracy<L> extends SupervisedModelEvaluationAccuracy<TokenSpansDatum<L>, L> {
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
		return "PolyAccuracy";
	}

	@Override
	public SupervisedModelEvaluation<TokenSpansDatum<L>, L> makeInstance() {
		return new SupervisedModelEvaluationPolyAccuracy<L>();
	}
}
