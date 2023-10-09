// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.FeatureNames;
import com.yahoo.searchlib.rankingexpression.evaluation.BooleanValue;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.IfNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.tensor.functions.Generate;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * Transforms X into Y
 * @author arnej
 */
public class NormalizerFunctionExpander extends ExpressionTransformer<RankProfileTransformContext> {

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof ReferenceNode r) {
            node = transformReference(r, context);
        }
        if (node instanceof CompositeNode composite) {
            node = transformChildren(composite, context);
        }
        return node;
    }

    private ExpressionNode transformReference(ReferenceNode node, RankProfileTransformContext context) {
        Reference ref = node.reference();
        String name = ref.name();
        if (ref.output() != null) {
            return node;
        }
        var f = context.rankProfile().getFunctions().get(name);
        if (f != null) {
            // never transform declared functions
            return node;
        }
        if (name.equals("reciprocal_rank_fusion")) {
            var args = ref.arguments();
            if (args.size() < 2) {
                throw new IllegalArgumentException("must have at least 2 arguments: " + node);
            }
            List<ExpressionNode> children = new ArrayList<>();
            List<Operator> operators = new ArrayList<>();
            for (var arg : args.expressions()) {
                if (! children.isEmpty()) operators.add(Operator.plus);
                children.add(new ReferenceNode("reciprocal_rank", List.of(arg), null));
            }
            return transform(new OperationNode(children, operators), context);
        }
        if (name.equals("normalize_linear")) {
            long num = ref.toString().hashCode() + 0x100000000L;
            // String inputName = "normalize@" + num + "@input";
            String normName = "normalize@" + num + "@linear";
            var args = ref.arguments();
            if (args.size() != 1) {
                throw new IllegalArgumentException("must have exactly 1 argument: " + node);
            }
            var input = args.expressions().get(0);
            if (input instanceof ReferenceNode inputRefNode) {
                var inputRef = inputRefNode.reference();
                System.err.println("got input ref: " + inputRef);
            } else {
                throw new IllegalArgumentException("the first argument must be a simple feature: " + ref + " => " + input.getClass());
            }
            context.rankProfile().addGlobalPhaseNormalizer(normName, input.toString(), "LINEAR");
            var newRef = Reference.fromIdentifier(normName);
            return new ReferenceNode(newRef);
        }
        if (name.equals("reciprocal_rank")) {
            long num = ref.toString().hashCode() + 0x100000000L;
            String normName = "normalize@" + num + "@rrank";
            var args = ref.arguments();
            if (args.size() < 1 || args.size() > 2) {
                throw new IllegalArgumentException("must have 1 or 2 arguments: " + ref);
            }
            double k = 60.0;
            if (args.size() == 2) {
                var kArg = args.expressions().get(1);
                if (kArg instanceof ConstantNode kNode) {
                    k = kNode.getValue().asDouble();
                    System.err.println("got k: " + k);
                } else {
                    throw new IllegalArgumentException("the second argument (k) must be a constant in: " + ref);
                }
            }
            var input = args.expressions().get(0);
            if (input instanceof ReferenceNode inputRefNode) {
                var inputRef = inputRefNode.reference();
                System.err.println("got input ref: " + inputRef);
            } else {
                throw new IllegalArgumentException("the first argument must be a simple feature: " + ref);
            }
            context.rankProfile().addGlobalPhaseNormalizer(normName, input.toString(), "RRANK");
            var newRef = Reference.fromIdentifier(normName);
            return new ReferenceNode(newRef);
        }
        return node;
    }
}
