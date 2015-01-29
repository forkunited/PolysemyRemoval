package poly.model.evaluation.metric;

import java.util.ArrayList;
import java.util.HashMap;
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
import ark.model.SupervisedModelAreg;
import ark.model.SupervisedModelCompositeBinary;
import ark.model.evaluation.metric.SupervisedModelEvaluation;

public class SupervisedModelEvaluationPolysemy extends SupervisedModelEvaluation<TokenSpansDatum<LabelsList>, LabelsList> {
	private double NELLConfidenceThreshold;
	private boolean computeNELLBaseline;
	
	private String[] parameterNames = { "computeNELLBaseline", "NELLConfidenceThreshold" };
	
	@Override
	public String getGenericName() {
		return "Polysemy";
	}

	@Override
	protected double compute(
			SupervisedModel<TokenSpansDatum<LabelsList>, LabelsList> model,
			FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList> data,
			Map<TokenSpansDatum<LabelsList>, LabelsList> predictions) {
		Map<String, Double> classificationThresholds = getAregIndicatorClassificationThresholds(model, predictions.entrySet().iterator().next().getValue());
		NELL nell = new NELL((PolyDataTools)data.getDatumTools().getDataTools());
		double polysemous = 0.0;
		for (Entry<TokenSpansDatum<LabelsList>, LabelsList> entry : predictions.entrySet()) {
			String np = entry.getKey().getTokenSpans()[0].toString();
			if (this.computeNELLBaseline) {
				polysemous += (nell.isNounPhrasePolysemous(np, this.NELLConfidenceThreshold)) ? 1.0 : 0.0;
			} else {
				List<String> labels = new ArrayList<String>();
				for (String label : entry.getValue().getLabels()) {
					if (entry.getValue().getLabelWeight(label) >= classificationThresholds.get(label))
						labels.add(label);
				}
				polysemous += nell.areCategoriesMutuallyExclusive(labels) ? 1.0 : 0.0;
			}
		}
		
		
		return polysemous / predictions.size();
	}
	
	private Map<String, Double> getAregIndicatorClassificationThresholds(SupervisedModel<TokenSpansDatum<LabelsList>, LabelsList> model, LabelsList indicatorLabels) {
		@SuppressWarnings("unchecked")
		SupervisedModelCompositeBinary<TokenSpansDatum<Boolean>, TokenSpansDatum<LabelsList>, LabelsList> compositeModel = (SupervisedModelCompositeBinary<TokenSpansDatum<Boolean>, TokenSpansDatum<LabelsList>, LabelsList>)model;
		Map<String, Double> classificationThresholds = new HashMap<String, Double>();
		
		for (String indicatorLabel : indicatorLabels.getLabels()) {
			SupervisedModelAreg<TokenSpansDatum<Boolean>, Boolean> aregModel = (SupervisedModelAreg<TokenSpansDatum<Boolean>, Boolean>)compositeModel.getModelForIndicator(indicatorLabel);
			classificationThresholds.put(indicatorLabel, Double.valueOf(aregModel.getParameterValue("classificationThreshold")));
		}
		
		return classificationThresholds;
	}
	
	@Override
	public String[] getParameterNames() {
		return this.parameterNames;
	}

	@Override
	public String getParameterValue(String parameter) {
		if (parameter.equals("NELLConfidenceThreshold"))
			return String.valueOf(this.NELLConfidenceThreshold);
		else if (parameter.equals("computeNELLBaseline"))
			return String.valueOf(this.computeNELLBaseline);
		
		return null;
	}

	@Override
	public boolean setParameterValue(String parameter, String parameterValue,
			Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools) {
		if (parameter.equals("NELLConfidenceThreshold"))
			this.NELLConfidenceThreshold = Double.valueOf(parameterValue);
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
