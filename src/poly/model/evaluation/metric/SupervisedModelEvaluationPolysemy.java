package poly.model.evaluation.metric;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import poly.data.NELL;
import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.TokenSpansDatum;
import ark.data.Context;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.data.feature.FeaturizedDataSet;
import ark.model.SupervisedModel;
import ark.model.evaluation.metric.SupervisedModelEvaluation;
import ark.parse.Obj;

public class SupervisedModelEvaluationPolysemy extends SupervisedModelEvaluation<TokenSpansDatum<LabelsList>, LabelsList> {
	private double NELLConfidenceThreshold;
	private boolean computeNELLBaseline;
	
	private String[] parameterNames = { "computeNELLBaseline", "NELLConfidenceThreshold" };
	
	public SupervisedModelEvaluationPolysemy() {
		
	}
	
	public SupervisedModelEvaluationPolysemy(Context<TokenSpansDatum<LabelsList>, LabelsList> context) {
		this.context = context;
	}
	
	@Override
	public String getGenericName() {
		return "Polysemy";
	}

	@Override
	protected double compute(
			SupervisedModel<TokenSpansDatum<LabelsList>, LabelsList> model,
			FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList> data,
			Map<TokenSpansDatum<LabelsList>, LabelsList> predictions) {
		List<String> indicatorLabels = new ArrayList<String>();
		for (LabelIndicator<LabelsList> indicator : data.getDatumTools().getLabelIndicators())
			indicatorLabels.add(indicator.toString());
	
		NELL nell = new NELL((PolyDataTools)data.getDatumTools().getDataTools());
		double polysemous = 0.0;
		for (Entry<TokenSpansDatum<LabelsList>, LabelsList> entry : predictions.entrySet()) {
			String np = entry.getKey().getTokenSpans()[0].toString();
			if (this.computeNELLBaseline) {
				polysemous += (nell.isNounPhrasePolysemous(np, this.NELLConfidenceThreshold)) ? 1.0 : 0.0;
			} else {
				List<String> labels = new ArrayList<String>();
				for (String label : entry.getValue().getLabels()) {
					if (entry.getValue().getLabelWeight(label) >= 0.5)
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
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("NELLConfidenceThreshold"))
			return Obj.stringValue(String.valueOf(this.NELLConfidenceThreshold));
		else if (parameter.equals("computeNELLBaseline"))
			return Obj.stringValue(String.valueOf(this.computeNELLBaseline));
		
		return null;
	}

	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("NELLConfidenceThreshold"))
			this.NELLConfidenceThreshold = Double.valueOf(this.context.getMatchValue(parameterValue));
		else if (parameter.equals("computeNELLBaseline"))
			this.computeNELLBaseline = Boolean.valueOf(this.context.getMatchValue(parameterValue));
		else
			return false;
		return true;
	}

	@Override
	public SupervisedModelEvaluation<TokenSpansDatum<LabelsList>, LabelsList> makeInstance(Context<TokenSpansDatum<LabelsList>, LabelsList> context) {
		return new SupervisedModelEvaluationPolysemy(context);
	}

}
