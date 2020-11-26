// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <iostream>

using vespalib::make_string_short::fmt;

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

TensorSpec dense_2d_some_cells(bool square) {
    return TensorSpec("tensor(a[3],d[5])")
        .add({{"a", 1}, {"d", 2}}, square ? 9.0 : 3.0)
        .add({{"a", 2}, {"d", 4}}, square ? 16.0 : 4.0)
        .add({{"a", 1}, {"d", 0}}, square ? 25.0 : 5.0);
}

TensorSpec sparse_2d_some_cells(bool square) {
    return TensorSpec("tensor(c{},e{})")
        .add({{"c", "foo"}, {"e", "foo"}}, square ? 1.0 : 1.0)
        .add({{"c", "foo"}, {"e", "bar"}}, square ? 4.0 : 2.0)
        .add({{"c", "bar"}, {"e", "bar"}}, square ? 9.0 : 3.0)
        .add({{"c", "qux"}, {"e", "foo"}}, square ? 16.0 : 4.0)
        .add({{"c", "qux"}, {"e", "qux"}}, square ? 25.0 : 5.0);
}

TensorSpec mixed_5d_some_cells(bool square) {
    return TensorSpec("tensor(a[3],b[1],c{},d[5],e{})")
        .add({{"a", 1}, {"b", 0}, {"c", "foo"}, {"d", 2}, {"e", "bar"}}, square ? 4.0 : 2.0)
        .add({{"a", 2}, {"b", 0}, {"c", "bar"}, {"d", 3}, {"e", "bar"}}, square ? 9.0 : 3.0)
        .add({{"a", 0}, {"b", 0}, {"c", "foo"}, {"d", 4}, {"e", "foo"}}, square ? 16.0 : 4.0)
        .add({{"a", 1}, {"b", 0}, {"c", "bar"}, {"d", 0}, {"e", "qux"}}, square ? 25.0 : 5.0)
        .add({{"a", 2}, {"b", 0}, {"c", "qux"}, {"d", 1}, {"e", "foo"}}, square ? 36.0 : 6.0);
}

TensorSpec dense_1d_all_two() {
    return TensorSpec("tensor(a[3])")
        .add({{"a", 0}}, 2.0)
        .add({{"a", 1}}, 2.0)
        .add({{"a", 2}}, 2.0);
}

TensorSpec sparse_1d_all_two() {
    return TensorSpec("tensor(c{})")
        .add({{"c", "foo"}}, 2.0)
        .add({{"c", "bar"}}, 2.0)
        .add({{"c", "qux"}}, 2.0);
}

//-----------------------------------------------------------------------------


TEST(ReferenceOperationsTest, concat_gives_expected_results) {
    auto a = TensorSpec("double").add({}, 7.0);
    auto b = TensorSpec("double").add({}, 4.0);
    auto output = ReferenceOperations::concat(a, b, "x");
    auto expect = TensorSpec("tensor(x[2])")
        .add({{"x", 0}}, 7.0)
        .add({{"x", 1}}, 4.0);
    EXPECT_EQ(output, expect);
    a = TensorSpec("tensor(a[3])")
        .add({{"a", 0}}, 1.0)
        .add({{"a", 1}}, 2.0)
        .add({{"a", 2}}, 3.0);
    output = ReferenceOperations::concat(a, b, "a");
    expect = TensorSpec("tensor(a[4])")
        .add({{"a", 0}}, 1.0)
        .add({{"a", 1}}, 2.0)
        .add({{"a", 2}}, 3.0)
        .add({{"a", 3}}, 4.0);
    EXPECT_EQ(output, expect);
    output = ReferenceOperations::concat(b, a, "a");
    expect = TensorSpec("tensor(a[4])")
        .add({{"a", 0}}, 4.0)
        .add({{"a", 1}}, 1.0)
        .add({{"a", 2}}, 2.0)
        .add({{"a", 3}}, 3.0);
    EXPECT_EQ(output, expect);
    a = mixed_5d_some_cells(false);
    b = mixed_5d_some_cells(true);
    output = ReferenceOperations::concat(a, b, "a");
    EXPECT_EQ(output.type(), "tensor(a[6],b[1],c{},d[5],e{})");
    output = ReferenceOperations::concat(a, b, "b");
    EXPECT_EQ(output.type(), "tensor(a[3],b[2],c{},d[5],e{})");
    output = ReferenceOperations::concat(a, b, "x");
    EXPECT_EQ(output.type(), "tensor(a[3],b[1],c{},d[5],e{},x[2])");
    output = ReferenceOperations::concat(a, b, "c");
    EXPECT_EQ(output.type(), "error");
}

TEST(ReferenceOperationsTest, create_gives_expected_results) {
    auto a = TensorSpec("double").add({}, 1.5);
    auto b = TensorSpec("tensor(z[2])").add({{"z",0}}, 2.0).add({{"z",1}}, 3.0);
    auto c = TensorSpec("tensor()").add({{}}, 4.0);
    ReferenceOperations::CreateSpec spec;
    spec.emplace(TensorSpec::Address{{"x",1},{"y","foo"}}, 0);
    spec.emplace(TensorSpec::Address{{"x",0},{"y","bar"}}, 1);
    spec.emplace(TensorSpec::Address{{"x",1},{"y","bar"}}, 2);
    auto output = ReferenceOperations::create("tensor(x[2],y{})", spec, {a,b,c});
    auto expect = TensorSpec("tensor(x[2],y{})")
        .add({{"x",1},{"y","foo"}}, 1.5)
        .add({{"x",0},{"y","bar"}}, 5.0)
        .add({{"x",1},{"y","bar"}}, 4.0);
    EXPECT_EQ(output, expect);
}

TEST(ReferenceOperationsTest, join_gives_expected_results) {
    auto a = TensorSpec("tensor()").add({}, 7.0);
    auto b = TensorSpec("tensor()").add({}, 4.0);
    auto output = ReferenceOperations::join(a, b, operation::Sub::f);
    EXPECT_EQ(output, TensorSpec("double").add({}, 3.0));

    const auto expect_sq = mixed_5d_some_cells(true);
    a = mixed_5d_some_cells(false);
    b = TensorSpec("double").add({}, 2.0);
    output = ReferenceOperations::join(a, b, operation::Pow::f);
    EXPECT_EQ(output, expect_sq);
    output = ReferenceOperations::join(a, a, operation::Mul::f);        
    EXPECT_EQ(output, expect_sq);
    auto c = ReferenceOperations::join(output, a, operation::Div::f);
    EXPECT_EQ(c, a);
    b = dense_1d_all_two();
    output = ReferenceOperations::join(a, b, operation::Pow::f);
    EXPECT_EQ(output, expect_sq);
    b = sparse_1d_all_two();
    output = ReferenceOperations::join(a, b, operation::Pow::f);
    EXPECT_EQ(output, expect_sq);
}

TEST(ReferenceOperationsTest, map_gives_expected_results) {
    auto input = TensorSpec("tensor()").add({}, 0.0);
    auto output = ReferenceOperations::map(input, operation::Exp::f);
    EXPECT_EQ(output, TensorSpec("double").add({}, 1.0));
    auto out2 = ReferenceOperations::map(output, operation::Neg::f);
    EXPECT_EQ(out2, TensorSpec("double").add({}, -1.0));

    input = dense_2d_some_cells(false);
    output = ReferenceOperations::map(input, operation::Square::f);
    EXPECT_EQ(output, dense_2d_some_cells(true));
        
    input = sparse_2d_some_cells(false);
    output = ReferenceOperations::map(input, operation::Square::f);
    EXPECT_EQ(output, sparse_2d_some_cells(true));
        
    input = mixed_5d_some_cells(false);
    output = ReferenceOperations::map(input, operation::Square::f);
    EXPECT_EQ(output, mixed_5d_some_cells(true));
}


TEST(ReferenceOperationsTest, merge_gives_expected_results) {
    auto a = mixed_5d_some_cells(false);
    auto b = TensorSpec("tensor(a[3],b[1],c{},d[5],e{})")
        .add({{"a", 0}, {"b", 0}, {"c", "foo"}, {"d", 4}, {"e", "foo"}}, 0.0)
        .add({{"a", 1}, {"b", 0}, {"c", "bar"}, {"d", 0}, {"e", "qux"}}, 42.0)
        .add({{"a", 0}, {"b", 0}, {"c", "new"}, {"d", 0}, {"e", "new"}}, 1.0);
    auto output = ReferenceOperations::merge(a, b, operation::Max::f);
    auto expect = TensorSpec("tensor(a[3],b[1],c{},d[5],e{})")
        .add({{"a", 1}, {"b", 0}, {"c", "foo"}, {"d", 2}, {"e", "bar"}}, 2.0)
        .add({{"a", 2}, {"b", 0}, {"c", "bar"}, {"d", 3}, {"e", "bar"}}, 3.0)
        .add({{"a", 0}, {"b", 0}, {"c", "foo"}, {"d", 4}, {"e", "foo"}}, 4.0)
        .add({{"a", 1}, {"b", 0}, {"c", "bar"}, {"d", 0}, {"e", "qux"}}, 42.0)
        .add({{"a", 2}, {"b", 0}, {"c", "qux"}, {"d", 1}, {"e", "foo"}}, 6.0)
        .add({{"a", 0}, {"b", 0}, {"c", "new"}, {"d", 0}, {"e", "new"}}, 1.0);
    EXPECT_EQ(output, expect);
}

//-----------------------------------------------------------------------------

TEST(ReferenceOperationsTest, peek_verbatim_labels) {
    auto input = sparse_2d_some_cells(true);
    ReferenceOperations::PeekSpec spec;
    spec.emplace("c", "qux");
    // peek 1 mapped dimension, verbatim label
    auto output = ReferenceOperations::peek(input, spec, {});
    auto expect = TensorSpec("tensor(e{})")
        .add({{"e","foo"}}, 16.0)
        .add({{"e","qux"}}, 25.0);
    EXPECT_EQ(output, expect);
    spec.emplace("e", "foo");
    // peek all mapped dimensions, verbatim labels
    output = ReferenceOperations::peek(input, spec, {});    
    expect = TensorSpec("double").add({}, 16.0);
    EXPECT_EQ(output, expect);    

    spec.clear();
    spec.emplace("c", "nomatch");
    // peek 1 mapped dimension, non-matching verbatim label
    output = ReferenceOperations::peek(input, spec, {});
    expect = TensorSpec("tensor(e{})");
    EXPECT_EQ(output, expect);    
    spec.emplace("e", "nomatch");
    // peek all mapped dimensions, non-matching verbatim labels
    output = ReferenceOperations::peek(input, spec, {});
    expect = TensorSpec("double");
    EXPECT_EQ(output, expect);    

    input = dense_2d_some_cells(false);
    spec.clear();
    spec.emplace("a", TensorSpec::Label(1));
    // peek 1 indexed dimension, verbatim label
    output = ReferenceOperations::peek(input, spec, {});
    expect = TensorSpec("tensor(d[5])")
        .add({{"d", 2}}, 3.0)
        .add({{"d", 0}}, 5.0);
    EXPECT_EQ(output, expect);
    spec.emplace("d", TensorSpec::Label(2));
    // peek all indexed dimensions, verbatim labels
    output = ReferenceOperations::peek(input, spec, {});    
    expect = TensorSpec("double").add({}, 3.0);
    EXPECT_EQ(output, expect);    
}    

TEST(ReferenceOperationsTest, peek_labels_from_children) {
    auto pos_ch = TensorSpec("double").add({}, 1.0);
    auto zero_ch = TensorSpec("double").add({}, 0.0);
    auto neg_ch = TensorSpec("double").add({}, -2.0);
    auto too_big_ch = TensorSpec("double").add({}, 42.0);
    std::vector<TensorSpec> children = {too_big_ch, too_big_ch, zero_ch, pos_ch, neg_ch, too_big_ch};

    auto input = dense_2d_some_cells(false);
    ReferenceOperations::PeekSpec spec;
    spec.emplace("a", size_t(3));
    // peek 1 indexed dimension, child (evaluating to 1.0)
    auto output = ReferenceOperations::peek(input, spec, children);
    auto expect = TensorSpec("tensor(d[5])")
        .add({{"d", 2}}, 3.0)
        .add({{"d", 0}}, 5.0);
    EXPECT_EQ(output, expect);
    spec.emplace("d", size_t(2));
    // peek 2 indexed dimensions (both children)
    output = ReferenceOperations::peek(input, spec, children);
    expect = TensorSpec("double").add({}, 5.0);
    EXPECT_EQ(output, expect);
    spec.clear();
    spec.emplace("a", size_t(0));
    // peek 1 indexed dimension, child (evaluating to 42.0)
    output = ReferenceOperations::peek(input, spec, children);
    expect = TensorSpec("tensor(d[5])");
    EXPECT_EQ(output, expect);
    spec.clear();
    spec.emplace("a", size_t(4));
    // peek 1 indexed dimension, child (evaluating to -2.0)
    output = ReferenceOperations::peek(input, spec, children);
    expect = TensorSpec("tensor(d[5])");
    EXPECT_EQ(output, expect);

    input = TensorSpec("tensor(c{},e{})")
        .add({{"c", "0"},  {"e", "0"}}, 2.0)
        .add({{"c", "1"},  {"e", "1"}}, 3.0)
        .add({{"c", "1"},  {"e", "0"}}, 4.0)
        .add({{"c", "-2"}, {"e", "1"}}, 5.0)
        .add({{"c", "-2"}, {"e", "-2"}}, 6.0);
    spec.clear();
    spec.emplace("c", size_t(3));
    // peek 1 mapped dimension, child (evaluating to 1.0)
    output = ReferenceOperations::peek(input, spec, children);
    expect = TensorSpec("tensor(e{})")
        .add({{"e", "1"}}, 3.0)
        .add({{"e", "0"}}, 4.0);
    EXPECT_EQ(output, expect);
    spec.emplace("e", size_t(2));
    // peek 2 mapped dimensions (both children)
    output = ReferenceOperations::peek(input, spec, children);
    expect = TensorSpec("double").add({}, 4.0);
    EXPECT_EQ(output, expect);

    spec.clear();
    spec.emplace("c", size_t(4));
    // peek 1 mapped dimension, child (evaluating to -2.0)
    output = ReferenceOperations::peek(input, spec, children);
    expect = TensorSpec("tensor(e{})")
        .add({{"e", "1"}}, 5.0)
        .add({{"e", "-2"}}, 6.0);
    EXPECT_EQ(output, expect);

    spec.clear();
    spec.emplace("c", size_t(0));
    // peek 1 indexed dimension, child (evaluating to 42.0)
    output = ReferenceOperations::peek(input, spec, children);
    expect = TensorSpec("tensor(e{})");
    EXPECT_EQ(output, expect);
}

TEST(ReferenceOperationsTest, peek_mixed) {
    auto pos_ch = TensorSpec("double").add({}, 1.0);
    auto zero_ch = TensorSpec("double").add({}, 0.0);
    auto neg_ch = TensorSpec("double").add({}, -2.0);
    auto too_big_ch = TensorSpec("double").add({}, 42.0);
    std::vector<TensorSpec> children = {too_big_ch, too_big_ch, zero_ch, pos_ch, neg_ch, too_big_ch};
    auto input = TensorSpec("tensor(a[3],b[1],c{},d[5],e{})")
        .add({{"a", 0}, {"b", 0}, {"c", "-2"}, {"d", 1}, {"e", "foo"}},  1.0)
        .add({{"a", 0}, {"b", 0}, {"c", "1"}, {"d", 4}, {"e", "foo"}},   2.0) 
        .add({{"a", 1}, {"b", 0}, {"c", "-1"}, {"d", 4}, {"e", "foo"}},  3.0)
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 0}, {"e", "qux"}},  4.0) 
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 1}, {"e", "bar"}},  5.0)
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 1}, {"e", "foo"}},  6.0) //
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 2}, {"e", "bar"}},  7.0) 
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 2}, {"e", "foo"}},  8.0) //
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 2}, {"e", "qux"}},  9.0)
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 3}, {"e", "foo"}}, 10.0) //
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 0}, {"e", "foo"}}, 11.0) //
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 3}, {"e", "nop"}}, 12.0)
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 4}, {"e", "bar"}}, 13.0) 
        .add({{"a", 1}, {"b", 0}, {"c", "-2"}, {"d", 4}, {"e", "foo"}}, 14.0) //
        .add({{"a", 1}, {"b", 0}, {"c", "0"}, {"d", 1}, {"e", "foo"}},  15.0)
        .add({{"a", 1}, {"b", 0}, {"c", "1"}, {"d", 2}, {"e", "foo"}},  16.0)
        .add({{"a", 1}, {"b", 0}, {"c", "2"}, {"d", 3}, {"e", "foo"}},  17.0)
        .add({{"a", 2}, {"b", 0}, {"c", "-2"}, {"d", 2}, {"e", "foo"}}, 18.0)
        .add({{"a", 2}, {"b", 0}, {"c", "0"}, {"d", 3}, {"e", "bar"}},  19.0) 
        .add({{"a", 2}, {"b", 0}, {"c", "1"}, {"d", 1}, {"e", "foo"}},  20.0);
    ReferenceOperations::PeekSpec spec;
    spec.emplace("a", size_t(3));
    spec.emplace("b", size_t(2));
    spec.emplace("c", size_t(4));
    spec.emplace("e", "foo");
    auto output = ReferenceOperations::peek(input, spec, children);
    auto expect = TensorSpec("tensor(d[5])")
        .add({{"d", 1}}, 6.0)
        .add({{"d", 2}}, 8.0)
        .add({{"d", 3}}, 10.0)
        .add({{"d", 0}}, 11.0)
        .add({{"d", 4}}, 14.0);
    EXPECT_EQ(output, expect);
}

//-----------------------------------------------------------------------------

TEST(ReferenceOperationsTest, reduce_gives_expected_results) {
    auto input = TensorSpec("tensor(a[3],b[1],c{},d[5],e{})")
        .add({{"a", 0}, {"b", 0}, {"c", "bar"}, {"d", 1}, {"e", "foo"}},  5.0)
        .add({{"a", 0}, {"b", 0}, {"c", "bar"}, {"d", 4}, {"e", "foo"}},  3.0)
        .add({{"a", 0}, {"b", 0}, {"c", "foo"}, {"d", 1}, {"e", "foo"}},  4.0)
        .add({{"a", 0}, {"b", 0}, {"c", "foo"}, {"d", 2}, {"e", "foo"}},  6.0)
        .add({{"a", 0}, {"b", 0}, {"c", "foo"}, {"d", 4}, {"e", "foo"}},  2.0)
        .add({{"a", 1}, {"b", 0}, {"c", "bar"}, {"d", 0}, {"e", "qux"}},  7.0)
        .add({{"a", 1}, {"b", 0}, {"c", "bar"}, {"d", 2}, {"e", "qux"}},  9.0)
        .add({{"a", 1}, {"b", 0}, {"c", "foo"}, {"d", 1}, {"e", "qux"}},  8.0)
        .add({{"a", 1}, {"b", 0}, {"c", "foo"}, {"d", 2}, {"e", "bar"}},  10.0)
        .add({{"a", 2}, {"b", 0}, {"c", "bar"}, {"d", 2}, {"e", "bar"}},  13.0)
        .add({{"a", 2}, {"b", 0}, {"c", "bar"}, {"d", 3}, {"e", "bar"}},  12.0)
        .add({{"a", 2}, {"b", 0}, {"c", "foo"}, {"d", 3}, {"e", "foo"}},  11.0)
        .add({{"a", 2}, {"b", 0}, {"c", "qux"}, {"d", 1}, {"e", "foo"}},  14.0);

    auto output = ReferenceOperations::reduce(input, {"a"}, Aggr::SUM);
    auto expect = TensorSpec("tensor(b[1],c{},d[5],e{})")
        .add({{"b", 0}, {"c", "bar"}, {"d", 0}, {"e", "qux"}},  7.0)
        .add({{"b", 0}, {"c", "bar"}, {"d", 1}, {"e", "foo"}},  5.0)
        .add({{"b", 0}, {"c", "bar"}, {"d", 2}, {"e", "bar"}},  13.0)
        .add({{"b", 0}, {"c", "bar"}, {"d", 2}, {"e", "qux"}},  9.0)
        .add({{"b", 0}, {"c", "bar"}, {"d", 3}, {"e", "bar"}},  12.0)
        .add({{"b", 0}, {"c", "bar"}, {"d", 4}, {"e", "foo"}},  3.0)
        .add({{"b", 0}, {"c", "foo"}, {"d", 1}, {"e", "foo"}},  4.0)
        .add({{"b", 0}, {"c", "foo"}, {"d", 1}, {"e", "qux"}},  8.0)
        .add({{"b", 0}, {"c", "foo"}, {"d", 2}, {"e", "bar"}},  10.0)
        .add({{"b", 0}, {"c", "foo"}, {"d", 2}, {"e", "foo"}},  6.0)
        .add({{"b", 0}, {"c", "foo"}, {"d", 3}, {"e", "foo"}},  11.0)
        .add({{"b", 0}, {"c", "foo"}, {"d", 4}, {"e", "foo"}},  2.0)
        .add({{"b", 0}, {"c", "qux"}, {"d", 1}, {"e", "foo"}},  14.0);
    EXPECT_EQ(output, expect);

    output = ReferenceOperations::reduce(input, {"a", "b", "d"}, Aggr::SUM);
    expect = TensorSpec("tensor(c{},e{})")
        .add({{"c", "bar"}, {"e", "bar"}},  25.0)
        .add({{"c", "bar"}, {"e", "foo"}},   8.0)
        .add({{"c", "bar"}, {"e", "qux"}},  16.0)
        .add({{"c", "foo"}, {"e", "bar"}},  10.0)
        .add({{"c", "foo"}, {"e", "foo"}},  23.0)
        .add({{"c", "foo"}, {"e", "qux"}},   8.0)
        .add({{"c", "qux"}, {"e", "foo"}},  14.0);
    EXPECT_EQ(output, expect);

    output = ReferenceOperations::reduce(input, {"c"}, Aggr::SUM);
    expect = TensorSpec("tensor(a[3],b[1],d[5],e{})")
        .add({{"a", 0}, {"b", 0}, {"d", 1}, {"e", "foo"}},  9.0)
        .add({{"a", 0}, {"b", 0}, {"d", 2}, {"e", "foo"}},  6.0)
        .add({{"a", 0}, {"b", 0}, {"d", 4}, {"e", "foo"}},  5.0)
        .add({{"a", 1}, {"b", 0}, {"d", 0}, {"e", "qux"}},  7.0)
        .add({{"a", 1}, {"b", 0}, {"d", 1}, {"e", "qux"}},  8.0)
        .add({{"a", 1}, {"b", 0}, {"d", 2}, {"e", "bar"}},  10.0)
        .add({{"a", 1}, {"b", 0}, {"d", 2}, {"e", "qux"}},  9.0)
        .add({{"a", 2}, {"b", 0}, {"d", 1}, {"e", "foo"}},  14.0)
        .add({{"a", 2}, {"b", 0}, {"d", 2}, {"e", "bar"}},  13.0)
        .add({{"a", 2}, {"b", 0}, {"d", 3}, {"e", "bar"}},  12.0)
        .add({{"a", 2}, {"b", 0}, {"d", 3}, {"e", "foo"}},  11.0);
    EXPECT_EQ(output, expect);
    
    output = ReferenceOperations::reduce(input, {"a", "c"}, Aggr::SUM);
    expect = TensorSpec("tensor(b[1],d[5],e{})")
        .add({{"b", 0}, {"d", 0}, {"e", "qux"}},  7.0)
        .add({{"b", 0}, {"d", 1}, {"e", "foo"}},  23.0)
        .add({{"b", 0}, {"d", 1}, {"e", "qux"}},  8.0)
        .add({{"b", 0}, {"d", 2}, {"e", "bar"}},  23.0)
        .add({{"b", 0}, {"d", 2}, {"e", "foo"}},  6.0)
        .add({{"b", 0}, {"d", 2}, {"e", "qux"}},  9.0)
        .add({{"b", 0}, {"d", 3}, {"e", "bar"}},  12.0)
        .add({{"b", 0}, {"d", 3}, {"e", "foo"}},  11.0)
        .add({{"b", 0}, {"d", 4}, {"e", "foo"}},  5.0);
    EXPECT_EQ(output, expect);

    output = ReferenceOperations::reduce(input, {"a", "c", "d"}, Aggr::SUM);
    expect = TensorSpec("tensor(b[1],e{})")
        .add({{"b", 0}, {"e", "bar"}},  35.0)
        .add({{"b", 0}, {"e", "foo"}},  45.0)
        .add({{"b", 0}, {"e", "qux"}},  24.0);
    EXPECT_EQ(output, expect);

    output = ReferenceOperations::reduce(input, {"a", "b", "c", "d", "e"}, Aggr::SUM);
    expect = TensorSpec("double").add({}, 104);
    EXPECT_EQ(output, expect);
    output = ReferenceOperations::reduce(input, {}, Aggr::SUM);
    EXPECT_EQ(output, expect);
}


TEST(ReferenceOperationsTest, rename_gives_expected_results) {
    auto input = mixed_5d_some_cells(false);
    auto output = ReferenceOperations::rename(input,
                                              {"a","b","c","e"},
                                              {"e","x","b","a"});
    auto expect = TensorSpec("tensor(a{},b{},d[5],e[3],x[1])")
        .add({{"e", 1}, {"x", 0}, {"b", "foo"}, {"d", 2}, {"a", "bar"}}, 2.0)
        .add({{"e", 2}, {"x", 0}, {"b", "bar"}, {"d", 3}, {"a", "bar"}}, 3.0)
        .add({{"e", 0}, {"x", 0}, {"b", "foo"}, {"d", 4}, {"a", "foo"}}, 4.0)
        .add({{"e", 1}, {"x", 0}, {"b", "bar"}, {"d", 0}, {"a", "qux"}}, 5.0)
        .add({{"e", 2}, {"x", 0}, {"b", "qux"}, {"d", 1}, {"a", "foo"}}, 6.0);
    EXPECT_EQ(output, expect);
}

GTEST_MAIN_RUN_ALL_TESTS()

