package poly.model.evaluation.metric;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import poly.data.NELL;
import poly.data.PolyDataTools;
import poly.data.annotation.HazyFACC1Document;
import poly.data.annotation.HazyFACC1Document.FACC1Annotation;
import poly.data.annotation.LabelsList;
import poly.data.annotation.TokenSpansDatum;
import ark.data.Gazetteer;
import ark.data.annotation.Datum.Tools;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.data.annotation.nlp.TokenSpan;
import ark.data.feature.FeaturizedDataSet;
import ark.model.SupervisedModel;
import ark.model.evaluation.metric.SupervisedModelEvaluation;
import ark.util.Pair;

public class SupervisedModelEvaluationLabelsListFreebase extends SupervisedModelEvaluation<TokenSpansDatum<LabelsList>, LabelsList> {
	public enum EvaluationType {
		F1,
		Precision,
		Recall,
		Accuracy
	}
	
	private double NELLConfidenceThreshold;
	private boolean computeNELLBaseline;
	private EvaluationType evaluationType = EvaluationType.F1;
	
	private String[] parameterNames = { "computeNELLBaseline", "NELLConfidenceThreshold", "evaluationType" };
	
	@Override
	public String getGenericName() {
		return "LabelsListFreebase";
	}

	@Override
	protected double compute(
			SupervisedModel<TokenSpansDatum<LabelsList>, LabelsList> model,
			FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList> data,
			Map<TokenSpansDatum<LabelsList>, LabelsList> predictions) {
		
		double tp = 0.0;
		double fp = 0.0;
		double tn = 0.0;
		double fn = 0.0;
		
		List<String> indicatorLabels = new ArrayList<String>();
		for (LabelIndicator<LabelsList> indicator : data.getDatumTools().getLabelIndicators())
			indicatorLabels.add(indicator.toString());
		
		NELL nell = (this.computeNELLBaseline) ? new NELL((PolyDataTools)data.getDatumTools().getDataTools()) : null;
		Gazetteer freebaseNELLCategoryGazetteer = data.getDatumTools().getDataTools().getGazetteer("FreebaseNELLCategory");
		for (Entry<TokenSpansDatum<LabelsList>, LabelsList> entry : predictions.entrySet()) {
			TokenSpan[] datumTokenSpans = entry.getKey().getTokenSpans();
			for (int i = 0; i < datumTokenSpans.length; i++) {
				HazyFACC1Document document = (HazyFACC1Document)datumTokenSpans[i].getDocument();
				List<Pair<TokenSpan, FACC1Annotation>> facc1Annotations = document.getTokenSpanLabels();
				for (Pair<TokenSpan, FACC1Annotation> facc1Annotation : facc1Annotations) {
					if (facc1Annotation.getFirst().equals(datumTokenSpans[i])) {
						String[] freebaseTypes = facc1Annotation.getSecond().getFreebaseTypes();
						Set<String> actualFreebaseNELLCategories = new HashSet<String>();
						for (String freebaseType : freebaseTypes) {
							if (freebaseNELLCategoryGazetteer.contains(freebaseType))
								actualFreebaseNELLCategories.addAll(freebaseNELLCategoryGazetteer.getIds(freebaseType));
						}
						
						for (String nellCategory : indicatorLabels) {
							boolean predictedTrue = 
									(this.computeNELLBaseline && nell.getNounPhraseNELLCategories(datumTokenSpans[i].toString(), this.NELLConfidenceThreshold).contains(nellCategory))
									||
									(!this.computeNELLBaseline && entry.getValue().contains(nellCategory) && entry.getValue().getLabelWeight(nellCategory) >= 0.5);
							boolean actualTrue = actualFreebaseNELLCategories.contains(nellCategory);
							if (predictedTrue && actualTrue)
								tp++;
							else if (predictedTrue && !actualTrue)
								fp++;
							else if (!predictedTrue && actualTrue)
								fn++;
							else if (!predictedTrue && !actualTrue)
								tn++;
						
							if (this.computeNELLBaseline && ((!predictedTrue && actualTrue) || predictedTrue && !actualTrue)) {
								System.out.println("NELL Incorrect \n" + facc1Annotation.getFirst().toJSON().toString() + "\n" + nellCategory + "\nActual True: " + actualTrue + "\nPredicted true:" + predictedTrue);
							}
						}
					}
				}
			}
			
		}
		
		if (this.evaluationType == EvaluationType.F1) {
			return 2*tp/(2*tp+fn+fp);
		} else if (this.evaluationType == EvaluationType.Precision) {
			return tp/(tp+fp);
		} else if (this.evaluationType == EvaluationType.Recall) {
			return tp/(tp+fn);
		} else if (this.evaluationType == EvaluationType.Accuracy) {
			return (tp+tn)/(tp+tn+fp+fn);
		} else {
			return 0;
		}
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
		else if (parameter.equals("evaluationType"))
			return this.evaluationType.toString();
		
		return null;
	}

	@Override
	public boolean setParameterValue(String parameter, String parameterValue,
			Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools) {
		if (parameter.equals("NELLConfidenceThreshold"))
			this.NELLConfidenceThreshold = Double.valueOf(parameterValue);
		else if (parameter.equals("computeNELLBaseline"))
			this.computeNELLBaseline = Boolean.valueOf(parameterValue);
		else if (parameter.equals("evaluationType"))
			this.evaluationType = EvaluationType.valueOf(parameterValue);
		else
			return false;
		return true;
	}

	@Override
	public SupervisedModelEvaluation<TokenSpansDatum<LabelsList>, LabelsList> makeInstance() {
		return new SupervisedModelEvaluationLabelsListFreebase();
	}
}
