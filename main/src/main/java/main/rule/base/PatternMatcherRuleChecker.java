package main.rule.base;

import main.analyzer.backward.Analysis;
import main.analyzer.backward.UnitContainer;
import soot.ValueBox;
import soot.jimple.Constant;

import java.util.*;
import java.util.regex.Pattern;

public abstract class PatternMatcherRuleChecker extends BaseRuleChecker {

    //Todo: Add a field to keep track of all the found patterns ...

    private Map<UnitContainer, List<String>> predictableSourcMap = new HashMap<>();
    private Map<UnitContainer, List<String>> othersSourceMap = new HashMap<>();

    @Override
    public void analyzeSlice(Analysis analysis) {
        if (analysis.getAnalysisResult().isEmpty()) {
            return;
        }

        for (UnitContainer e : analysis.getAnalysisResult()) {
            for (ValueBox usebox : e.getUnit().getUseBoxes()) {
                if (usebox.getValue() instanceof Constant) {
                    boolean found = false;

                    for (String regex : getPatternsToMatch()) {
                        if (usebox.getValue().toString().matches(regex)) {
                            putIntoMap(predictableSourcMap, e, usebox.getValue().toString());
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        putIntoMap(othersSourceMap, e, usebox.getValue().toString());
                    }
                }
            }
        }
    }

    public void printAnalysisOutput(Map<String, String> configFiles) {

        String rule = getRuleId();
        String ruleDesc = RULE_VS_DESCRIPTION.get(rule);

        List<String> predictableSources = new ArrayList<>();
        List<UnitContainer> predictableSourceInst = new ArrayList<>();
        List<String> others = new ArrayList<>();

        for(List<String> values : predictableSourcMap.values()) {
            predictableSources.addAll(values);
        }
        predictableSourceInst.addAll(predictableSourcMap.keySet());

        for(List<String> values : othersSourceMap.values()) {
            others.addAll(values);
        }

        if (!predictableSources.isEmpty()) {
            System.out.println("=======================================");
            String output = getPrintableMsg(predictableSourcMap, rule, ruleDesc);
            System.out.println(output);

            System.out.println("=======================================");
        }
    }

    private String getPrintableMsg(Map<UnitContainer, List<String>> predictableSourcMap, String rule, String ruleDesc) {
        String output = "***Violated Rule " +
                rule + ": " +
                ruleDesc;

        for (UnitContainer unit : predictableSourcMap.keySet()) {

            output += "\n***Found: " + predictableSourcMap.get(unit);
            if (unit.getUnit().getJavaSourceStartLineNumber() >= 0) {
                output += " in Line " + unit.getUnit().getJavaSourceStartLineNumber();
            }

            output += " in Method: " + unit.getMethod();
        }

        return output;
    }

    abstract public List<String> getPatternsToMatch();

    abstract public String getRuleId();
}
