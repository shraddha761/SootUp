package de.upb.soot.util.printer;

import de.upb.soot.core.Body;
import de.upb.soot.core.SootField;
import de.upb.soot.core.SootMethod;
import de.upb.soot.jimple.common.ref.IdentityRef;
import de.upb.soot.jimple.common.ref.JCaughtExceptionRef;
import de.upb.soot.jimple.common.ref.JParameterRef;
import de.upb.soot.jimple.common.ref.JThisRef;
import de.upb.soot.jimple.common.type.Type;

/**
 * IStmtPrinter implementation for normal (full) Jimple
 */
public class NormalStmtPrinter extends LabeledStmtPrinter {

  public NormalStmtPrinter(Body b) {
    super(b);
  }


  @Override
  public void type(Type t) {
    handleIndent();
    String s = t == null ? "<null>" : t.toQuotedString();
    output.append(s);
  }

  @Override
  public void method(SootMethod m) {
    handleIndent();
    output.append(m.getSignature());
  }

  @Override
  public void field(SootField f) {
    handleIndent();
    output.append(f.getSignature());
  }

  @Override
  public void identityRef(IdentityRef r) {
    handleIndent();
    if (r instanceof JThisRef) {
      literal("@this: ");
      type(r.getType());
    } else if (r instanceof JParameterRef) {
      JParameterRef pr = (JParameterRef) r;
      literal("@parameter" + pr.getIndex() + ": ");
      type(r.getType());
    } else if (r instanceof JCaughtExceptionRef) {
      literal("@caughtexception");
    } else {
      throw new RuntimeException();
    }
  }

  @Override
  public void literal(String s) {
    handleIndent();
    output.append(s);
  }
}
