package poly.model.evaluation.metric;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import poly.data.NELL;
import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.TokenSpansDatum;
import ark.data.annotation.Datum.Tools;
import ark.data.feature.FeaturizedDataSet;
import ark.model.SupervisedModel;
import ark.model.evaluation.metric.SupervisedModelEvaluation;

public class SupervisedModelEvaluationPolysemy extends SupervisedModelEvaluation<TokenSpansDatum<LabelsList>, LabelsList> {
	private double confidenceThreshold;
	private boolean computeNELLBaseline;
	
	private String[] parameterNames = { "computeNELLBaseline", "confidenceThreshold" };
	
	@Override
	public String getGenericName() {
		return "Polysemy";
	}

	@Override
	protected double compute(
			SupervisedModel<TokenSpansDatum<LabelsList>, LabelsList> model,
			FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList> data,
			Map<TokenSpansDatum<LabelsList>, LabelsList> predictions) {
		
		NELL nell = (this.computeNELLBaseline) ? new NELL((PolyDataTools)data.getDatumTools().getDataTools()) : null;
		double polysemous = 0.0;
		for (Entry<TokenSpansDatum<LabelsList>, LabelsList> entry : predictions.entrySet()) {
			String np = entry.getKey().getTokenSpans()[0].toString();
			if (this.computeNELLBaseline) {
				polysemous += (nell.isNounPhrasePolysemous(np, this.confidenceThreshold)) ? 1.0 : 0.0;
			} else {
				List<String> labels = new ArrayList<String>();
				for (String label : entry.getValue().getLabels()) {
					if (entry.getValue().getLabelWeight(label) >= this.confidenceThreshold)
						labels.add(label);
				}
				polysemous += nell.areCategoriesMutuallyExclusive(labels) ? 1.0 : 0.0;
			}
		}
		
		return polysemous / predictions.size();
	}
	
	@Override
	public String[] getParameterNames() {
		return this.parameterNames;
	}

	@Override
	public String getParameterValue(String parameter) {
		if (parameter.equals("confidenceThreshold"))
			return String.valueOf(this.confidenceThreshold);
		else if (parameter.equals("computeNELLBaseline"))
			return String.valueOf(this.computeNELLBaseline);
		
		return null;
	}

	@Override
	public boolean setParameterValue(String parameter, String parameterValue,
			Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools) {
		if (parameter.equals("confidenceThreshold"))
			this.confidenceThreshold = Double.valueOf(parameterValue);
		else if (parameter.equals("computeNELLBaseline"))
			this.computeNELLBaseline = Boolean.valueOf(parameterValue);
		else
			return false;
		return true;
	}

	@Override
	public SupervisedModelEvaluation<TokenSpansDatum<LabelsList>, LabelsList> makeInstance() {
		return new SupervisedModelEvaluationPolysemy();
	}

}
