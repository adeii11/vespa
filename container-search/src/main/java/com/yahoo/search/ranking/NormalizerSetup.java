// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import java.util.function.Supplier;

record NormalizerSetup(String name, Supplier<Normalizer> supplier, FunEvalSpec inputEvalSpec) {}
