// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.tensor.Tensor;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class SimpleEvaluator implements Evaluator {

    private final FunctionEvaluator evaluator;

    SimpleEvaluator(FunctionEvaluator prototype) {
        this.evaluator = prototype;
    }

    @Override
    public List<String> allInputs() {
        return List.copyOf(evaluator.function().arguments());
    }

    @Override
    public Evaluator bind(String name, Tensor value) {
        if (value != null) evaluator.bind(name, value);
        return this;
    }

    @Override
    public double evaluateScore() {
        return evaluator.evaluate().asDouble();
    }

    @Override
    public String toString() {
        var buf = new StringBuilder();
        buf.append("SimpleEvaluator(");
        buf.append(evaluator.function().toString());
        buf.append(")[");
        for (String arg : allInputs()) {
            buf.append("{").append(arg).append("}");
        }
        buf.append("]");
        return buf.toString();
    }
}
