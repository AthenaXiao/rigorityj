package main.rule.base;

import main.analyzer.backward.Analysis;
import main.analyzer.backward.ParamFakeUnitContainer;
import main.analyzer.backward.PropertyFakeUnitContainer;
import main.analyzer.backward.UnitContainer;
import main.util.Utils;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.RValueBox;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by krishnokoli on 11/26/17.
 */
public abstract class PredictableSourceRuleChecker extends BaseRuleChecker {

    public static final List<String> PREDICTABLE_SOURCES = new ArrayList<>();

    static {
        PREDICTABLE_SOURCES.add("<java.lang.System: long nanoTime()>");
        PREDICTABLE_SOURCES.add("<java.lang.System: long currentTimeMillis()>");
        PREDICTABLE_SOURCES.add("<java.util.Date: java.util.Date <init>");
    }

    // Todo: Add a field to keep track of all the predictable sources ...

    private Map<UnitContainer, List<String>> predictableSourcMap = new HashMap<>();
    private Map<UnitContainer, List<String>> othersSourceMap = new HashMap<>();

    @Override
    public void analyzeSlice(Analysis analysis) {

        if (analysis.getAnalysisResult().isEmpty()) {
            return;
        }

        for (int index = 0; index < analysis.getAnalysisResult().size(); index++) {

            UnitContainer e = analysis.getAnalysisResult().get(index);

            boolean found = false;

            for (String predictableSource : PREDICTABLE_SOURCES) {
                if (e.getUnit().toString().contains(predictableSource)) {

                    List<UnitContainer> outSet = new ArrayList<>();
                    outSet.add(e);

                    if (MajorHeuristics.isArgumentOfInvoke(analysis, index, outSet)) {
                        putIntoMap(othersSourceMap, e, e.getUnit().toString());
                    } else if (MajorHeuristics.isArgumentOfByteArrayCreation(analysis, index, outSet)) {
                        putIntoMap(othersSourceMap, e, e.getUnit().toString());
                    } else {
                        putIntoMap(predictableSourcMap, e, e.getUnit().toString());
                    }

                    found = true;
                    break;
                }
            }

            if (found) {
                continue;
            }

            for (ValueBox usebox : e.getUnit().getUseBoxes()) {
                if (usebox.getValue() instanceof Constant) {

                    List<UnitContainer> outSet = new ArrayList<>();

                    if (e.getUnit() instanceof JAssignStmt && usebox.getValue().getType() instanceof IntegerType) {

                        List<ValueBox> defBoxes = e.getUnit().getDefBoxes();

                        if (defBoxes != null && !defBoxes.isEmpty()) {
                            if (usebox instanceof RValueBox && defBoxes.get(0).getValue().getType() instanceof ByteType) {
                                outSet.add(e);
                            } else {
                                putIntoMap(othersSourceMap, e, usebox.getValue().toString());
                            }
                        }
                    } else if (e.getUnit() instanceof AssignStmt) {
                        if (((AssignStmt) e.getUnit()).containsInvokeExpr() && !e.getUnit().toString().contains(" void <init>")) {
                            InvokeExpr invokeExpr = ((AssignStmt) e.getUnit()).getInvokeExpr();
                            List<Value> args = invokeExpr.getArgs();
                            for (Value arg : args) {
                                if (arg.equivTo(usebox.getValue())) {
                                    putIntoMap(othersSourceMap, e, usebox.getValue().toString());
                                    break;
                                }
                            }
                        } else {
                            outSet.add(e);
                        }

                    } else {
                        putIntoMap(othersSourceMap, e, usebox.getValue().toString());
                    }

                    if (outSet.isEmpty()) {
                        continue;
                    }

                    if (MajorHeuristics.isArgumentOfInvoke(analysis, index, outSet)) {
                        putIntoMap(othersSourceMap, e, usebox.getValue().toString());
                    } else if (MajorHeuristics.isArgumentOfByteArrayCreation(analysis, index, outSet)) {
                        putIntoMap(othersSourceMap, e, usebox.getValue().toString());
                    } else {
                        if (!usebox.getValue().toString().equals("null") &&
                                !usebox.getValue().toString().equals("\"\"")) {
                            putIntoMap(predictableSourcMap, e, usebox.getValue().toString());
                        } else {
                            putIntoMap(othersSourceMap, e, usebox.getValue().toString());
                        }
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
        List<UnitContainer> othersSourceInst = new ArrayList<>();
        List<String> others = new ArrayList<>();

        for (List<String> values : predictableSourcMap.values()) {
            predictableSources.addAll(values);
        }
        predictableSourceInst.addAll(predictableSourcMap.keySet());

        for (List<String> values : othersSourceMap.values()) {
            others.addAll(values);
        }
        othersSourceInst.addAll(othersSourceMap.keySet());

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

    abstract public String getRuleId();
}
