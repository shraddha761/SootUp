package sootup.java.bytecode.interceptors;
/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 1997-2020 Raja Vallée-Rai, Christian Brüggemann
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.AbstractBinopExpr;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JFieldRef;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.Body;
import sootup.core.transform.BodyInterceptor;
import sootup.core.views.View;

/*
 * The Jimple Local Aggregator removes some unnecessary copies by combining local variables.
 * Essentially, it finds definitions which have only a single use and, if it is safe to do so,
 * removes the original definition after replacing the use with the definition's right-hand side.
 * At this stage in JimpleBody construction, local aggregation serves largely to remove the copies to and
 * from stack variables which simulate load and store instructions in the original bytecode.
 * */
public class Aggregator implements BodyInterceptor {

  // if this is true, only aggregate variables starting with "$" which are the ones which are *not*
  // referring to a field of a class
  protected boolean dontAggregateFieldLocals;

  public Aggregator() {
    this(false);
  }

  public Aggregator(boolean dontAggregateFieldLocals) {
    this.dontAggregateFieldLocals = dontAggregateFieldLocals;
  }

  /**
   * Traverse the statements in the given body, looking for aggregation possibilities; that is,
   * given a def d and a use u, d has no other uses, u has no other defs, collapse d and u.
   */
  @Override
  public void interceptBody(@Nonnull Body.BodyBuilder builder, @Nonnull View<?> view) {
    StmtGraph<?> graph = builder.getStmtGraph();
    List<Stmt> stmts = builder.getStmts();
    Map<LValue, Collection<Stmt>> usesMap = Body.collectUses(stmts);

    for (Stmt stmt : stmts) {
      if (!(stmt instanceof JAssignStmt)) {
        continue;
      }
      final JAssignStmt assignStmt = (JAssignStmt) stmt;
      Value lhs = assignStmt.getLeftOp();
      if (!(lhs instanceof Local)) {
        continue;
      }
      Local lhsLocal = (Local) lhs;
      if (dontAggregateFieldLocals && !lhsLocal.getName().startsWith("$")) {
        continue;
      }
      for (Value val : assignStmt.getUses()) {
        if (!(val instanceof Local)) {
          continue;
        }
        if (usesMap.get(val).size() > 1) {
          // there are other uses, so it can't be aggregated
          continue;
        }
        List<AbstractDefinitionStmt> defs = ((Local) val).getDefs(stmts);
        if (defs.size() != 1) {
          continue;
        }
        Stmt relevantDef = defs.get(0);
        if (!graph.containsNode(relevantDef) || !graph.containsNode(stmt)) {
          continue;
        }
        List<Stmt> path = graph.getExtendedBasicBlockPathBetween(relevantDef, stmt);
        if (path == null) {
          continue;
        }
        boolean cantAggr = false;
        boolean propagatingInvokeExpr = false;
        boolean propagatingFieldRef = false;
        boolean propagatingArrayRef = false;
        List<JFieldRef> fieldRefList = new ArrayList<>();

        Set<Value> localsUsed = new HashSet<>();
        for (Stmt pathStmt : path) {
          for (Value use : pathStmt.getUses()) {
            if (use instanceof Local) {
              localsUsed.add(use);
            } else if (use instanceof AbstractInstanceInvokeExpr) {
              propagatingInvokeExpr = true;
            } else if (use instanceof JArrayRef) {
              propagatingArrayRef = true;
            } else if (use instanceof JFieldRef) {
              propagatingFieldRef = true;
              fieldRefList.add((JFieldRef) use);
            }
          }
        }

        for (Stmt pathStmt : path) {
          if (pathStmt != stmt && pathStmt != relevantDef) {
            for (Value stmtDef : pathStmt.getDefs()) {
              if (localsUsed.contains(stmtDef)) {
                cantAggr = true;
                break;
              }
              if (propagatingInvokeExpr || propagatingFieldRef || propagatingArrayRef) {
                if (stmtDef instanceof JFieldRef) {
                  if (propagatingInvokeExpr) {
                    cantAggr = true;
                    break;
                  } else if (propagatingFieldRef) {
                    // Can't aggregate a field access if passing a definition of a field
                    // with the same name, because they might be aliased
                    for (JFieldRef fieldRef : fieldRefList) {
                      if (fieldRef.equals((JFieldRef) stmtDef)) {
                        cantAggr = true;
                        break;
                      }
                    }
                  }
                } else if (stmtDef instanceof JArrayRef) {
                  if (propagatingInvokeExpr || propagatingArrayRef) {
                    // Cannot aggregate an invoke expr past an array write and cannot
                    // aggregate an array read past a write
                    cantAggr = true;
                    break;
                  }
                }
              }
            }
          }
          // Check for intervening side effects due to method calls
          if (propagatingInvokeExpr || propagatingFieldRef || propagatingArrayRef) {
            for (final Value value : stmt.getUses()) {
              if (pathStmt == stmt && value == lhs) {
                break;
              }
              if (value instanceof AbstractInstanceInvokeExpr
                  || (propagatingInvokeExpr
                      && (value instanceof JFieldRef || value instanceof JArrayRef))) {
                cantAggr = true;
                break;
              }
            }
          }
        }

        if (cantAggr) {
          continue;
        }

        // cannot aggregate e.g. a JIdentityStmt
        if (!(relevantDef instanceof JAssignStmt)) {
          continue;
        }

        Value aggregatee = ((AbstractDefinitionStmt) relevantDef).getRightOp();
        JAssignStmt newStmt = null;
        if (assignStmt.getRightOp() instanceof AbstractBinopExpr) {
          AbstractBinopExpr rightOp = (AbstractBinopExpr) assignStmt.getRightOp();
          if (rightOp.getOp1() == val) {
            AbstractBinopExpr newBinopExpr = rightOp.withOp1((Immediate) aggregatee);
            newStmt =
                new JAssignStmt(assignStmt.getLeftOp(), newBinopExpr, assignStmt.getPositionInfo());
          } else if (rightOp.getOp2() == val) {
            AbstractBinopExpr newBinopExpr = rightOp.withOp2((Immediate) aggregatee);
            newStmt =
                new JAssignStmt(assignStmt.getLeftOp(), newBinopExpr, assignStmt.getPositionInfo());
          }
        } else {
          newStmt = assignStmt.withRValue(aggregatee);
        }

        if (newStmt != null) {
          builder.replaceStmt(stmt, newStmt);
          if (graph.getStartingStmt() == relevantDef) {
            Stmt newStartingStmt = builder.getStmtGraph().successors(relevantDef).get(0);
            builder.setStartingStmt(newStartingStmt);
          }
          builder.removeStmt(relevantDef);
        }
      }
    }
  }
}
