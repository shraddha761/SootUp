package de.upb.soot.core;

import static org.junit.Assert.assertEquals;

import de.upb.soot.jimple.Jimple;
import de.upb.soot.jimple.basic.LocalGenerator;
import de.upb.soot.jimple.common.type.RefType;
import de.upb.soot.jimple.common.type.Type;
import de.upb.soot.jimple.common.type.VoidType;
import de.upb.soot.util.printer.Printer;
import de.upb.soot.views.IView;
import de.upb.soot.views.JavaView;

import java.io.PrintWriter;
import java.util.Arrays;

import org.junit.experimental.categories.Category;

import categories.Java8Test;

@Category(Java8Test.class)
public class SootMethodTest {

  @Ignore
  public void testCreateMethod() {
    IView view = new JavaView(null);
    view.addRefType(new RefType(view, "java.lang.String"));
    RefType type = RefType.getInstance("java.lang.String");
    SootMethod dummyMainMethod = new SootMethod(view, "main", Arrays.asList(new Type[] { type }), VoidType.getInstance(),
        Modifier.PUBLIC | Modifier.STATIC);

    SootClass mainClass = new SootClass(view, "MainClass");
    mainClass.addMethod(dummyMainMethod);
    assertEquals("<MainClass: void main(java.lang.String)>", dummyMainMethod.getSignature());

    Body body = Jimple.newBody(dummyMainMethod);

    LocalGenerator generator = new LocalGenerator(body);
    body.addStmt(Jimple.newIdentityStmt(generator.generateLocal(type), Jimple.newParameterRef(type, 0)));
    body.addStmt(Jimple.newAssignStmt(generator.generateLocal(type), Jimple.newNewExpr(type)));

    dummyMainMethod.setActiveBody(body);


    PrintWriter writer=new PrintWriter(System.out);
    Printer printer = new Printer();
    printer.printTo(mainClass, writer);
    writer.println();
  }
}
