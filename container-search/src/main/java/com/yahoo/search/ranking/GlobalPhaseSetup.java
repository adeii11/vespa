// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.vespa.config.search.RankProfilesConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Supplier;

class GlobalPhaseSetup {

    final GlobalFunEvalCtx globalPhaseEvalCtx;
    final int rerankCount;
    final Collection<String> matchFeaturesToHide;
    final List<NormalizerSetup> normalizers;

    GlobalPhaseSetup(GlobalFunEvalCtx globalPhase,
                     final int rerankCount,
                     Collection<String> matchFeaturesToHide,
                     List<NormalizerSetup> normalizers)
    {
	this.globalPhaseEvalCtx = globalPhase;
	this.rerankCount = rerankCount;
	this.matchFeaturesToHide = matchFeaturesToHide;
         this.normalizers = normalizers;
    }

    static GlobalPhaseSetup maybeMakeSetup(RankProfilesConfig.Rankprofile rp, RankProfilesEvaluator modelEvaluator) {
        Map<String, RankProfilesConfig.Rankprofile.Normalizer> availableNormalizers = new HashMap<>();
        for (var n : rp.normalizer()) {
            availableNormalizers.put(n.name(), n);
        }
        Supplier<Evaluator> functionEvaluatorSource = null;
        int rerankCount = -1;
        Set<String> namesToHide = new HashSet<>();
        for (var prop : rp.fef().property()) {
            if (prop.name().equals("vespa.globalphase.rerankcount")) {
                rerankCount = Integer.valueOf(prop.value());
            }
            if (prop.name().equals("vespa.rank.globalphase")) {
                functionEvaluatorSource = modelEvaluator.getSupplier(rp.name(), "globalphase");
            }
            if (prop.name().equals("vespa.hidden.matchfeature")) {
                namesToHide.add(prop.value());
            }
        }
        if (rerankCount < 0) {
            rerankCount = 100;
        }
        if (functionEvaluatorSource != null) {
            var evaluator = functionEvaluatorSource.get();
            var needInputs = evaluator.allInputs();
            List<String> fromQuery = new ArrayList<>();
            List<String> fromMF = new ArrayList<>();
            List<NormalizerSetup> normalizers = new ArrayList<>();
            for (var input : needInputs) {
                String queryFeatureName = asQueryFeature(input);
                if (queryFeatureName != null) {
                    fromQuery.add(queryFeatureName);
                } else if (availableNormalizers.containsKey(input)) {
                    var cfg = availableNormalizers.get(input);
                    // var normSource = modelEvaluator.getSupplier(rp.name(), cfg.input());
                    Supplier<Evaluator> normSource = () -> new DummyEvaluator(cfg.input());
                    normalizers.add(makeNormalizerSetup(cfg, normSource, rerankCount));
                } else {
                    fromMF.add(input);
                }
            }
            var gfun = new GlobalFunEvalCtx(functionEvaluatorSource, fromQuery, fromMF);
            return new GlobalPhaseSetup(gfun, rerankCount, namesToHide, normalizers);
        }
        return null;
    }

    private static NormalizerSetup makeNormalizerSetup(RankProfilesConfig.Rankprofile.Normalizer cfg,
                                                       Supplier<Evaluator> evalSupplier,
                                                       int rerankCount)
    {
        List<String> fromQuery = new ArrayList<>();
        List<String> fromMF = new ArrayList<>();
        for (var input : evalSupplier.get().allInputs()) {
            String queryFeatureName = asQueryFeature(input);
            if (queryFeatureName != null) {
                fromQuery.add(queryFeatureName);
            } else {
                fromMF.add(input);
            }
        }
        var fun = new GlobalFunEvalCtx(evalSupplier, fromQuery, fromMF);
        return new NormalizerSetup(cfg.name(), makeNormalizerSupplier(cfg, rerankCount), fun);
    }

    private static Supplier<Normalizer> makeNormalizerSupplier(RankProfilesConfig.Rankprofile.Normalizer cfg, int rerankCount) {
        switch (cfg.algo()) {
            case LINEAR:
                return () -> new LinearNormalizer(rerankCount);
            case RRANK:
                return () -> new ReciprocalRankNormalizer(rerankCount, cfg.kparam());
        }
        throw new IllegalArgumentException("bad algo config: " + cfg.algo());
    }

    static String asQueryFeature(String input) {
        var optRef = com.yahoo.searchlib.rankingexpression.Reference.simple(input);
        if (optRef.isPresent()) {
            var ref = optRef.get();
            if (ref.isSimple() && ref.name().equals("query")) {
                return ref.simpleArgument().get();
            }
        }
        return null;
    }
}
