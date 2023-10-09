// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.tensor.Tensor;

import java.util.List;

interface Evaluator {
    List<String> allInputs();

    Evaluator bind(String name, Tensor value);

    double evaluateScore();
}
