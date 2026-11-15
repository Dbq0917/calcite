/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.runtime;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.config.Lex;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.test.CalciteAssert;
import org.apache.calcite.test.schemata.hr.HrSchema;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

/**
 * Unit tests for rules in {@code org.apache.calcite.rel} and subpackages.
 */
public class RelOptRulesRuntimeTest {

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5952">[CALCITE-5952]
   * SemiJoinJoinTransposeRule should check if JoinType supports pushing predicates
   * into its inputs</a>. */
  @Test void semiJoinLeftJoinTransposeTest() {
    tester(true, new HrSchema())
        .withRel(
            builder -> builder.scan("s", "depts")
                .scan("s", "emps")
                .join(JoinRelType.LEFT,
                    builder.equals(
                        builder.field(2, 0, "deptno"),
                        builder.field(2, 1, "deptno"))
                )
                .scan("s", "dependents")
                .semiJoin(
                    builder.equals(
                    builder.field(2, 0, "empid"),
                    builder.field(2, 1, "empid")))
                .build()
        )
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner ->
            planner.addRule(CoreRules.SEMI_JOIN_JOIN_TRANSPOSE)
        )
        .returnsUnordered();
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5952">[CALCITE-5952]
   * SemiJoinJoinTransposeRule should check if JoinType supports pushing predicates
   * into its inputs</a>. */
  @Test void semiJoinRightJoinTransposeTest() {
    tester(true, new HrSchema())
        .withRel(
            builder -> builder.scan("s", "emps")
                .scan("s", "depts")
                .join(JoinRelType.RIGHT,
                    builder.equals(
                        builder.field(2, 0, "deptno"),
                        builder.field(2, 1, "deptno"))
                )
                .scan("s", "dependents")
                .semiJoin(
                    builder.equals(
                    builder.field(2, 0, "empid"),
                    builder.field(2, 1, "empid")))
                .build()
        )
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner ->
            planner.addRule(CoreRules.SEMI_JOIN_JOIN_TRANSPOSE)
        )
        .returnsUnordered();
  }

  private CalciteAssert.AssertThat tester(boolean forceDecorrelate, Object schema) {
    return CalciteAssert.that()
        .with(CalciteConnectionProperty.LEX, Lex.JAVA)
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, forceDecorrelate)
        .withSchema("s", new ReflectiveSchema(schema));
  }

  /** Verifies that an always-true condition built from a deterministic dynamic
   * function ({@code CURRENT_DATE = CURRENT_DATE}) does not change the query
   * result. The optimizer removes the condition, so the rewritten query must
   * return exactly the same rows as the original query. */
  @Test void deterministicDynamicFunctionFilterIsSemanticallyNeutral() {
    final String[] expected = {"empid=100", "empid=110", "empid=150"};
    tester(true, new HrSchema())
        .query("select empid from s.emps where deptno = 10 order by empid")
        .returnsOrdered(expected);
    tester(true, new HrSchema())
        .query("select empid from s.emps where deptno = 10"
            + " and current_date = current_date order by empid")
        .returnsOrdered(expected);
    // A conjunction that keeps only the always-true predicate returns all rows.
    tester(true, new HrSchema())
        .query("select empid from s.emps where current_date = current_date"
            + " order by empid")
        .returnsOrdered("empid=100", "empid=110", "empid=150", "empid=200");
  }
}
