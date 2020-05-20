package de.upb.swt.soot.test.java.bytecode.inputlocation;

/*-
 * #%L
 * Soot
 * %%
 * Copyright (C) 06.06.2018 Manuel Benz
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

import categories.Java8Test;
import de.upb.swt.soot.core.types.ClassType;
import de.upb.swt.soot.java.bytecode.inputlocation.PathBasedAnalysisInputLocation;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Manuel Benz created on 06.06.18
 * @author Kaustubh Kelkar update on 16.04.2020
 */
@Category(Java8Test.class)
public class PathBasedAnalysisInputLocationTest extends AnalysisInputLocationTest {

  @Test
  public void testJar() {
    PathBasedAnalysisInputLocation pathBasedNamespace =
        PathBasedAnalysisInputLocation.createForClassContainer(war);
    System.err.println(war.toFile().getAbsolutePath());

    /* TODO adding class paths for jar and classes from the war file location
    String baseClassPath = war+"WEB-INF/classes";
    String libClassPath = war+"WEB-INF/lib";

    AnalysisInputLocation cpBased = new JavaClassPathAnalysisInputLocation(libClassPath);
    Project p = JavaProject.builder(new JavaLanguage(8)).addClassPath(cpBased).build();

    // 1. simple case
    View fullView = p.createFullView();
    fullView.

     */
    final ClassType warClass1 =
        getIdentifierFactory().getClassType("SimpleWarRead", "WEB-INF/classes");
    testClassReceival(pathBasedNamespace, warClass1, 0);

    final ClassType class1 = getIdentifierFactory().getClassType("Employee", "ds");
    final ClassType mainClass = getIdentifierFactory().getClassType("MiniApp");
    testClassReceival(pathBasedNamespace, class1, 0);
    testClassReceival(pathBasedNamespace, mainClass, 0);
  }
}
