package de.upb.soot.frontends.java;

import com.ibm.wala.cast.ir.ssa.AssignInstruction;
import com.ibm.wala.cast.ir.ssa.AstAssertInstruction;
import com.ibm.wala.cast.ir.ssa.AstLexicalAccess.Access;
import com.ibm.wala.cast.ir.ssa.AstLexicalRead;
import com.ibm.wala.cast.ir.ssa.AstLexicalWrite;
import com.ibm.wala.cast.ir.ssa.CAstBinaryOp;
import com.ibm.wala.cast.java.ssa.AstJavaInvokeInstruction;
import com.ibm.wala.cast.java.ssa.EnclosingObjectReference;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.AstMethod.DebuggingInformation;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction.IOperator;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction.Operator;
import com.ibm.wala.shrikeBT.IShiftInstruction;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import de.upb.soot.core.Modifier;
import de.upb.soot.core.SootField;
import de.upb.soot.jimple.Jimple;
import de.upb.soot.jimple.basic.JStmtBox;
import de.upb.soot.jimple.basic.Local;
import de.upb.soot.jimple.basic.LocalGenerator;
import de.upb.soot.jimple.basic.PositionInfo;
import de.upb.soot.jimple.basic.Value;
import de.upb.soot.jimple.common.constant.ClassConstant;
import de.upb.soot.jimple.common.constant.Constant;
import de.upb.soot.jimple.common.constant.DoubleConstant;
import de.upb.soot.jimple.common.constant.FloatConstant;
import de.upb.soot.jimple.common.constant.IntConstant;
import de.upb.soot.jimple.common.constant.LongConstant;
import de.upb.soot.jimple.common.constant.NullConstant;
import de.upb.soot.jimple.common.constant.StringConstant;
import de.upb.soot.jimple.common.expr.AbstractBinopExpr;
import de.upb.soot.jimple.common.expr.AbstractConditionExpr;
import de.upb.soot.jimple.common.expr.JCastExpr;
import de.upb.soot.jimple.common.expr.JEqExpr;
import de.upb.soot.jimple.common.expr.JInstanceOfExpr;
import de.upb.soot.jimple.common.expr.JNegExpr;
import de.upb.soot.jimple.common.expr.JNewExpr;
import de.upb.soot.jimple.common.expr.JSpecialInvokeExpr;
import de.upb.soot.jimple.common.ref.JArrayRef;
import de.upb.soot.jimple.common.ref.JCaughtExceptionRef;
import de.upb.soot.jimple.common.ref.JInstanceFieldRef;
import de.upb.soot.jimple.common.ref.JStaticFieldRef;
import de.upb.soot.jimple.common.stmt.IStmt;
import de.upb.soot.jimple.common.stmt.JAssignStmt;
import de.upb.soot.jimple.common.stmt.JGotoStmt;
import de.upb.soot.jimple.common.stmt.JIfStmt;
import de.upb.soot.jimple.common.stmt.JInvokeStmt;
import de.upb.soot.jimple.common.stmt.JNopStmt;
import de.upb.soot.jimple.common.stmt.JThrowStmt;
import de.upb.soot.jimple.javabytecode.stmt.JLookupSwitchStmt;
import de.upb.soot.signatures.ArrayTypeSignature;
import de.upb.soot.signatures.DefaultSignatureFactory;
import de.upb.soot.signatures.FieldSignature;
import de.upb.soot.signatures.JavaClassSignature;
import de.upb.soot.signatures.MethodSignature;
import de.upb.soot.signatures.PrimitiveTypeSignature;
import de.upb.soot.signatures.ReferenceTypeSignature;
import de.upb.soot.signatures.SignatureFactory;
import de.upb.soot.signatures.TypeSignature;
import de.upb.soot.signatures.UnknownTypeSignature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import scala.Char;

/**
 * This class converts wala instruction to jimple statement.
 *
 * @author Linghui Luo
 */
public class InstructionConverter {

  private WalaIRToJimpleConverter converter;
  private MethodSignature methodSignature;
  private AstMethod walaMethod;
  private SymbolTable symbolTable;
  private LocalGenerator localGenerator;
  // <ifStmt, iindex>
  protected Map<JIfStmt, Integer> targetsOfIfStmts;
  protected Map<JGotoStmt, Integer> targetsOfGotoStmts;
  protected Map<JLookupSwitchStmt, List<Integer>> targetsOfLookUpSwitchStmts;
  protected Map<JLookupSwitchStmt, Integer> defaultOfLookUpSwitchStmts;
  protected Map<JLookupSwitchStmt, List<IStmt>> targetStmtsOfLookUpSwitchStmts;
  private Map<Integer, Local> locals;
  private SignatureFactory sigFactory;

  public InstructionConverter(
      WalaIRToJimpleConverter converter,
      MethodSignature methodSignature,
      AstMethod walaMethod,
      LocalGenerator localGenerator) {
    this.converter = converter;
    this.methodSignature = methodSignature;
    this.walaMethod = walaMethod;
    this.symbolTable = walaMethod.symbolTable();
    this.localGenerator = localGenerator;
    this.targetsOfIfStmts = new HashMap<>();
    this.targetsOfGotoStmts = new HashMap<>();
    this.targetsOfLookUpSwitchStmts = new HashMap<>();
    this.defaultOfLookUpSwitchStmts = new HashMap<>();
    this.locals = new HashMap<>();
    this.sigFactory = converter.view.getSignatureFactory();
  }

  public List<IStmt> convertInstruction(DebuggingInformation debugInfo, SSAInstruction inst) {
    List<IStmt> stmts = new ArrayList<>();
    // System.out.println(sootMethod.getSignature());
    // System.out.println(inst);
    if (inst instanceof SSAConditionalBranchInstruction) {
      stmts.add(this.convertBranchInstruction(debugInfo, (SSAConditionalBranchInstruction) inst));
    } else if (inst instanceof SSAGotoInstruction) {
      stmts.add(this.convertGoToInstruction(debugInfo, (SSAGotoInstruction) inst));
    } else if (inst instanceof SSAReturnInstruction) {
      stmts.add(this.convertReturnInstruction(debugInfo, (SSAReturnInstruction) inst));
    } else if (inst instanceof AstJavaInvokeInstruction) {
      stmts.add(this.convertInvokeInstruction(debugInfo, (AstJavaInvokeInstruction) inst));
    } else if (inst instanceof SSAFieldAccessInstruction) {
      if (inst instanceof SSAGetInstruction) {
        stmts.add(this.convertGetInstruction(debugInfo, (SSAGetInstruction) inst)); // field read
      } else if (inst instanceof SSAPutInstruction) {
        stmts.add(this.convertPutInstruction(debugInfo, (SSAPutInstruction) inst)); // field write
      } else {
        throw new RuntimeException("Unsupported instruction type: " + inst.getClass().toString());
      }
    } else if (inst instanceof SSANewInstruction) {
      stmts.add(convertNewInstruction(debugInfo, (SSANewInstruction) inst));
    } else if (inst instanceof SSAConversionInstruction) {
      stmts.add(convertConversionInstruction(debugInfo, (SSAConversionInstruction) inst));
    } else if (inst instanceof SSAInstanceofInstruction) {
      stmts.add(convertInstanceofInstruction(debugInfo, (SSAInstanceofInstruction) inst));
    } else if (inst instanceof SSABinaryOpInstruction) {
      stmts.add(this.convertBinaryOpInstruction(debugInfo, (SSABinaryOpInstruction) inst));
    } else if (inst instanceof SSAUnaryOpInstruction) {
      stmts.add(this.convertUnaryOpInstruction(debugInfo, (SSAUnaryOpInstruction) inst));
    } else if (inst instanceof SSAThrowInstruction) {
      stmts.add(this.convertThrowInstruction(debugInfo, (SSAThrowInstruction) inst));
    } else if (inst instanceof SSASwitchInstruction) {
      stmts.add(this.convertSwitchInstruction(debugInfo, (SSASwitchInstruction) inst));
    } else if (inst instanceof SSALoadMetadataInstruction) {
      stmts.add(this.convertLoadMetadataInstruction(debugInfo, (SSALoadMetadataInstruction) inst));
    } else if (inst instanceof EnclosingObjectReference) {
      stmts.add(this.convertEnclosingObjectReference(debugInfo, (EnclosingObjectReference) inst));
    } else if (inst instanceof AstLexicalRead) {
      stmts = (this.convertAstLexicalRead(debugInfo, (AstLexicalRead) inst));
    } else if (inst instanceof AstLexicalWrite) {
      stmts = (this.convertAstLexicalWrite(debugInfo, (AstLexicalWrite) inst));
    } else if (inst instanceof AstAssertInstruction) {
      stmts = this.convertAssertInstruction(debugInfo, (AstAssertInstruction) inst);
    } else if (inst instanceof SSACheckCastInstruction) {
      stmts.add(this.convertCheckCastInstruction(debugInfo, (SSACheckCastInstruction) inst));
    } else if (inst instanceof SSAMonitorInstruction) {
      stmts.add(
          this.convertMonitorInstruction(
              debugInfo, (SSAMonitorInstruction) inst)); // for synchronized statement
    } else if (inst instanceof SSAGetCaughtExceptionInstruction) {
      stmts.add(
          this.convertGetCaughtExceptionInstruction(
              debugInfo, (SSAGetCaughtExceptionInstruction) inst));
    } else if (inst instanceof SSAArrayLengthInstruction) {
      stmts.add(this.convertArrayLengthInstruction(debugInfo, (SSAArrayLengthInstruction) inst));
    } else if (inst instanceof SSAArrayReferenceInstruction) {
      if (inst instanceof SSAArrayLoadInstruction) {
        stmts.add(this.convertArrayLoadInstruction(debugInfo, (SSAArrayLoadInstruction) inst));
      } else if (inst instanceof SSAArrayStoreInstruction) {
        stmts.add(this.convertArrayStoreInstruction(debugInfo, (SSAArrayStoreInstruction) inst));
      } else {
        throw new RuntimeException("Unsupported instruction type: " + inst.getClass().toString());
      }
    } else {
      throw new RuntimeException("Unsupported instruction type: " + inst.getClass().toString());
    }
    return stmts;
  }

  private IStmt convertArrayStoreInstruction(
      DebuggingInformation debugInfo, SSAArrayStoreInstruction inst) {
    Local base = getLocal(UnknownTypeSignature.getInstance(), inst.getArrayRef());
    int i = inst.getIndex();
    Value index = null;
    if (symbolTable.isConstant(i)) {
      index = getConstant(i);
    } else {
      index = getLocal(PrimitiveTypeSignature.getIntSignature(), i);
    }
    JArrayRef arrayRef = Jimple.newArrayRef(base, index);
    Value rvalue = null;
    int value = inst.getValue();
    if (symbolTable.isConstant(value)) {
      rvalue = getConstant(value);
    } else {
      rvalue = getLocal(base.getSignature(), value);
    }
    return Jimple.newAssignStmt(
        arrayRef, rvalue, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertArrayLoadInstruction(
      DebuggingInformation debugInfo, SSAArrayLoadInstruction inst) {
    Local base = getLocal(UnknownTypeSignature.getInstance(), inst.getArrayRef());
    int i = inst.getIndex();
    Value index = null;
    if (symbolTable.isConstant(i)) {
      index = getConstant(i);
    } else {
      index = getLocal(PrimitiveTypeSignature.getIntSignature(), i);
    }
    JArrayRef arrayRef = Jimple.newArrayRef(base, index);
    Value left = null;
    int def = inst.getDef();
    left = getLocal(base.getSignature(), def);
    return Jimple.newAssignStmt(
        left, arrayRef, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertArrayLengthInstruction(
      DebuggingInformation debugInfo, SSAArrayLengthInstruction inst) {
    int result = inst.getDef();
    Local left = getLocal(PrimitiveTypeSignature.getIntSignature(), result);
    int arrayRef = inst.getArrayRef();
    Local arrayLocal = getLocal(UnknownTypeSignature.getInstance(), arrayRef);
    Value right = Jimple.newLengthExpr(arrayLocal);
    return Jimple.newAssignStmt(
        left, right, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertGetCaughtExceptionInstruction(
      DebuggingInformation debugInfo, SSAGetCaughtExceptionInstruction inst) {
    int exceptionValue = inst.getException();
    Local local =
        getLocal(
            DefaultSignatureFactory.getInstance().getClassSignature("java.lang.Throwable"),
            exceptionValue);
    JCaughtExceptionRef caught = Jimple.newCaughtExceptionRef();
    return Jimple.newIdentityStmt(
        local, caught, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertMonitorInstruction(
      DebuggingInformation debugInfo, SSAMonitorInstruction inst) {
    Value op = getLocal(UnknownTypeSignature.getInstance(), inst.getRef());
    if (inst.isMonitorEnter()) {
      return Jimple.newEnterMonitorStmt(
          op, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    } else {
      return Jimple.newExitMonitorStmt(
          op, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    }
  }

  private List<IStmt> convertAssertInstruction(
      DebuggingInformation debugInfo, AstAssertInstruction inst) {
    List<IStmt> stmts = new ArrayList<>();
    // create a static field for checking if assertion is disabled.
    JavaClassSignature cSig = methodSignature.getDeclClassSignature();
    FieldSignature fieldSig = sigFactory.getFieldSignature("$assertionsDisabled", cSig, "boolean");
    SootField assertionsDisabled =
        new SootField(fieldSig, EnumSet.of(Modifier.FINAL, Modifier.STATIC));

    converter.addSootField(assertionsDisabled);
    Local testLocal = localGenerator.generateLocal(PrimitiveTypeSignature.getBooleanSignature());
    JStaticFieldRef assertFieldRef = Jimple.newStaticFieldRef(fieldSig);
    JAssignStmt assignStmt =
        Jimple.newAssignStmt(
            testLocal,
            assertFieldRef,
            new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    stmts.add(assignStmt);

    // add ifStmt for testing assertion is disabled.
    JEqExpr condition = Jimple.newEqExpr(testLocal, IntConstant.getInstance(1));
    JNopStmt nopStmt =
        Jimple.newNopStmt(new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    JIfStmt ifStmt =
        Jimple.newIfStmt(
            condition,
            nopStmt,
            new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    stmts.add(ifStmt);

    // create ifStmt for the actual assertion.
    Local assertLocal = getLocal(PrimitiveTypeSignature.getBooleanSignature(), inst.getUse(0));
    JEqExpr assertionExpr = Jimple.newEqExpr(assertLocal, IntConstant.getInstance(1));
    JIfStmt assertIfStmt =
        Jimple.newIfStmt(
            assertionExpr,
            nopStmt,
            new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    stmts.add(assertIfStmt);
    // create failed assertion code.

    ReferenceTypeSignature assertionErrorType =
        DefaultSignatureFactory.getInstance().getClassSignature("java.lang.AssertionError");
    Local failureLocal = localGenerator.generateLocal(assertionErrorType);
    JNewExpr newExpr = Jimple.newNewExpr(assertionErrorType);
    JAssignStmt newAssignStmt =
        Jimple.newAssignStmt(
            failureLocal,
            newExpr,
            new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    stmts.add(newAssignStmt);
    MethodSignature methodSig =
        sigFactory.getMethodSignature(
            "<init>", "java.lang.AssertionError", "void", Collections.emptyList());
    JSpecialInvokeExpr invoke = Jimple.newSpecialInvokeExpr(failureLocal, methodSig);
    JInvokeStmt invokeStmt =
        Jimple.newInvokeStmt(
            invoke, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    stmts.add(invokeStmt);
    JThrowStmt throwStmt =
        Jimple.newThrowStmt(
            failureLocal, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    stmts.add(throwStmt);

    // add nop in the end
    stmts.add(nopStmt); // TODO. This should be removed later
    return stmts;
  }

  private List<IStmt> convertAstLexicalWrite(DebuggingInformation debugInfo, AstLexicalWrite inst) {
    List<IStmt> stmts = new ArrayList<>();
    for (int i = 0; i < inst.getAccessCount(); i++) {
      Access access = inst.getAccess(i);
      TypeSignature type = converter.convertType(access.type);
      Value right = null;
      if (symbolTable.isConstant(access.valueNumber)) {
        right = getConstant(access.valueNumber);
      } else {
        right = getLocal(type, access.valueNumber);
      }
      JavaClassSignature cSig = methodSignature.getDeclClassSignature();
      // TODO check modifier
      Value left = null;
      if (!walaMethod.isStatic()) {
        FieldSignature fieldSig =
            sigFactory.getFieldSignature("val$" + access.variableName, cSig, type.toString());
        SootField field = new SootField(fieldSig, EnumSet.of(Modifier.FINAL));
        left = Jimple.newInstanceFieldRef(localGenerator.getThisLocal(), fieldSig);
        converter.addSootField(field); // add this field to class
        // TODO in old jimple this is not supported
      } else {
        left = localGenerator.generateLocal(type);
      }
      stmts.add(
          Jimple.newAssignStmt(
              left, right, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null)));
    }
    return stmts;
  }

  private List<IStmt> convertAstLexicalRead(DebuggingInformation debugInfo, AstLexicalRead inst) {
    List<IStmt> stmts = new ArrayList<>();
    for (int i = 0; i < inst.getAccessCount(); i++) {
      Access access = inst.getAccess(i);
      TypeSignature type = converter.convertType(access.type);
      Local left = getLocal(type, access.valueNumber);
      JavaClassSignature cSig = methodSignature.getDeclClassSignature();
      // TODO check modifier
      Value rvalue = null;
      if (!walaMethod.isStatic()) {
        FieldSignature fieldSig =
            sigFactory.getFieldSignature("val$" + access.variableName, cSig, type.toString());
        SootField field = new SootField(fieldSig, EnumSet.of(Modifier.FINAL));
        rvalue = Jimple.newInstanceFieldRef(localGenerator.getThisLocal(), fieldSig);
        converter.addSootField(field); // add this field to class
      } else {
        rvalue = localGenerator.generateLocal(type);
      }
      stmts.add(
          Jimple.newAssignStmt(
              left, rvalue, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null)));
    }
    return stmts;
  }

  private IStmt convertEnclosingObjectReference(
      DebuggingInformation debugInfo, EnclosingObjectReference inst) {
    TypeSignature enclosingType = converter.convertType(inst.getEnclosingType());
    Value variable = getLocal(enclosingType, inst.getDef());
    JavaClassSignature cSig = methodSignature.getDeclClassSignature();

    // TODO check modifier
    FieldSignature fieldSig =
        sigFactory.getFieldSignature("this$0", cSig, enclosingType.toString());

    // FIXME: [JMP] The `enclosingObject` is never used
    //    SootField enclosingObject = new SootField(fieldSig, EnumSet.of(Modifier.FINAL));

    JInstanceFieldRef rvalue = Jimple.newInstanceFieldRef(localGenerator.getThisLocal(), fieldSig);
    return Jimple.newAssignStmt(
        variable, rvalue, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertCheckCastInstruction(
      DebuggingInformation debugInfo, SSACheckCastInstruction inst) {
    TypeReference[] types = inst.getDeclaredResultTypes();
    Local result = getLocal(converter.convertType(types[0]), inst.getResult());
    Value rvalue = null;
    int val = inst.getVal();
    if (symbolTable.isConstant(val)) {
      rvalue = getConstant(val);
    } else {
      rvalue = getLocal(converter.convertType(types[0]), val);
    }
    // TODO declaredResultType is wrong
    JCastExpr castExpr = Jimple.newCastExpr(rvalue, converter.convertType(types[0]));
    return Jimple.newAssignStmt(
        result, castExpr, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertLoadMetadataInstruction(
      DebuggingInformation debugInfo, SSALoadMetadataInstruction inst) {
    Local lval = getLocal(converter.convertType(inst.getType()), inst.getDef());
    TypeReference token = (TypeReference) inst.getToken();
    ClassConstant c = ClassConstant.getInstance(token.getName().toString());
    return Jimple.newAssignStmt(
        lval, c, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertSwitchInstruction(
      DebuggingInformation debugInfo, SSASwitchInstruction inst) {
    int val = inst.getUse(0);
    Local local = getLocal(UnknownTypeSignature.getInstance(), val);
    int[] cases = inst.getCasesAndLabels();
    int defaultCase = inst.getDefault();
    List<IntConstant> lookupValues = new ArrayList<>();
    List<Integer> targetsList = new ArrayList<>();
    List<? extends IStmt> targets = new ArrayList<>();
    for (int i = 0; i < cases.length; i++) {
      int c = cases[i];
      if (i % 2 == 0) {
        IntConstant cValue = IntConstant.getInstance(c);
        lookupValues.add(cValue);
      } else {
        targetsList.add(c);
        targets.add(null); // add null as placeholder for targets
      }
    }
    IStmt defaultTarget = null;
    JLookupSwitchStmt stmt =
        Jimple.newLookupSwitchStmt(
            local,
            lookupValues,
            targets,
            defaultTarget,
            new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    this.targetsOfLookUpSwitchStmts.put(stmt, targetsList);
    this.defaultOfLookUpSwitchStmts.put(stmt, defaultCase);
    return stmt;
  }

  private IStmt convertThrowInstruction(DebuggingInformation debugInfo, SSAThrowInstruction inst) {
    int exception = inst.getException();
    Local local = getLocal(UnknownTypeSignature.getInstance(), exception);
    return Jimple.newThrowStmt(
        local, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertUnaryOpInstruction(
      DebuggingInformation debugInfo, SSAUnaryOpInstruction inst) {
    int def = inst.getDef();
    int use = inst.getUse(0);
    Value op = null;
    // TODO: change type
    TypeSignature type = UnknownTypeSignature.getInstance();
    if (symbolTable.isConstant(use)) {
      op = getConstant(use);
      type = op.getSignature();
    } else {
      op = getLocal(type, use);
    }
    Local left = getLocal(type, def);
    if (inst instanceof AssignInstruction) {
      return Jimple.newAssignStmt(
          left, op, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    } else {
      JNegExpr expr = Jimple.newNegExpr(op);
      return Jimple.newAssignStmt(
          left, expr, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    }
  }

  private IStmt convertPutInstruction(DebuggingInformation debugInfo, SSAPutInstruction inst) {
    FieldReference fieldRef = inst.getDeclaredField();
    TypeSignature fieldType = converter.convertType(inst.getDeclaredFieldType());
    String walaClassName = fieldRef.getDeclaringClass().getName().toString();
    JavaClassSignature classSig =
        sigFactory.getClassSignature(converter.convertClassNameFromWala(walaClassName));
    FieldSignature fieldSig =
        sigFactory.getFieldSignature(fieldRef.getName().toString(), classSig, fieldType.toString());
    Value fieldValue = null;
    if (inst.isStatic()) {
      fieldValue = Jimple.newStaticFieldRef(fieldSig);
    } else {
      int ref = inst.getRef();
      Local base = getLocal(classSig, ref);
      fieldValue = Jimple.newInstanceFieldRef(base, fieldSig);
    }
    Value value = null;
    int val = inst.getVal();
    if (symbolTable.isConstant(val)) {
      value = getConstant(val);
    } else {
      value = getLocal(fieldType, val);
    }
    return Jimple.newAssignStmt(
        fieldValue, value, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertNewInstruction(DebuggingInformation debugInfo, SSANewInstruction inst) {
    int result = inst.getDef();
    TypeSignature type = converter.convertType(inst.getNewSite().getDeclaredType());
    Value var = getLocal(type, result);
    Value rvalue = null;
    if (type instanceof ArrayTypeSignature) {
      int use = inst.getUse(0);
      Value size = null;
      if (symbolTable.isConstant(use)) {
        size = getConstant(use);
      } else {
        // TODO: size type unsure
        size = getLocal(PrimitiveTypeSignature.getIntSignature(), use);
      }
      rvalue = Jimple.newNewArrayExpr(type, size);
    } else {
      rvalue = Jimple.newNewExpr((ReferenceTypeSignature) type);
    }
    return Jimple.newAssignStmt(
        var, rvalue, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertComparisonInstruction(
      DebuggingInformation debugInfo, SSAComparisonInstruction inst) {
    // TODO
    return Jimple.newNopStmt(new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertInstanceofInstruction(
      DebuggingInformation debugInfo, SSAInstanceofInstruction inst) {
    int result = inst.getDef();
    int ref = inst.getRef();
    TypeSignature checkedType = converter.convertType(inst.getCheckedType());
    // TODO. how to get type of ref?
    Local op = getLocal(UnknownTypeSignature.getInstance(), ref);
    JInstanceOfExpr expr = Jimple.newInstanceOfExpr(op, checkedType);
    Value left = getLocal(UnknownTypeSignature.getInstance(), result);
    return Jimple.newAssignStmt(
        left, expr, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertConversionInstruction(
      DebuggingInformation debugInfo, SSAConversionInstruction inst) {
    TypeSignature fromType = converter.convertType(inst.getFromType());
    TypeSignature toType = converter.convertType(inst.getToType());
    int def = inst.getDef();
    int use = inst.getUse(0);
    Value lvalue = getLocal(toType, def);
    Value rvalue = null;
    if (symbolTable.isConstant(use)) {
      rvalue = getConstant(use);
    } else {
      rvalue = getLocal(fromType, use);
    }
    JCastExpr cast = Jimple.newCastExpr(rvalue, toType);
    return Jimple.newAssignStmt(
        lvalue, cast, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private IStmt convertInvokeInstruction(
      DebuggingInformation debugInfo, AstJavaInvokeInstruction invokeInst) {
    Value invoke = null;
    CallSiteReference callee = invokeInst.getCallSite();
    MethodReference target = invokeInst.getDeclaredTarget();
    String declaringClassSignature =
        converter.convertClassNameFromWala(target.getDeclaringClass().getName().toString());
    String returnType = converter.convertType(target.getReturnType()).toString();
    List<String> parameters = new ArrayList<>();
    List<TypeSignature> paraTypes = new ArrayList<>();
    List<Value> args = new ArrayList<>();
    for (int i = 0; i < target.getNumberOfParameters(); i++) {
      TypeSignature paraType = converter.convertType(target.getParameterType(i)); // note
      // the
      // parameters
      // do
      // not
      // include
      // "this"
      paraTypes.add(paraType);
      parameters.add(paraType.toString());
    }
    int i = 0;
    if (!callee.isStatic()) {
      i = 1; // non-static invoke this first use is thisRef.
    }
    for (; i < invokeInst.getNumberOfUses(); i++) {
      int use = invokeInst.getUse(i);
      Value arg = null;
      if (symbolTable.isConstant(use)) {
        arg = getConstant(use);
      } else {
        if (invokeInst.getNumberOfUses() > paraTypes.size()) {
          arg = getLocal(paraTypes.get(i - 1), use);
        } else {
          arg = getLocal(paraTypes.get(i), use);
        }
      }
      assert (arg != null);
      args.add(arg);
    }

    MethodSignature methodSig =
        converter
            .view
            .getSignatureFactory()
            .getMethodSignature(
                target.getName().toString(), declaringClassSignature, returnType, parameters);

    if (!callee.isStatic()) {
      int receiver = invokeInst.getReceiver();
      TypeSignature classType = converter.convertType(target.getDeclaringClass());
      Local base = getLocal(classType, receiver);
      if (callee.isSpecial()) {
        TypeSignature baseType = UnknownTypeSignature.getInstance();
        // TODO. baseType could be a problem.
        base = getLocal(baseType, receiver);
        invoke = Jimple.newSpecialInvokeExpr(base, methodSig, args); // constructor
      } else if (callee.isVirtual()) {
        invoke = Jimple.newVirtualInvokeExpr(base, methodSig, args);
      } else if (callee.isInterface()) {
        invoke = Jimple.newInterfaceInvokeExpr(base, methodSig, args);
      } else {
        throw new RuntimeException("Unsupported invoke instruction: " + callee.toString());
      }
    } else {
      invoke = Jimple.newStaticInvokeExpr(methodSig, args);
    }

    if (!invokeInst.hasDef()) {
      return Jimple.newInvokeStmt(
          invoke, new PositionInfo(debugInfo.getInstructionPosition(invokeInst.iindex), null));
    } else {
      TypeSignature type = converter.convertType(invokeInst.getDeclaredResultType());
      Local v = getLocal(type, invokeInst.getDef());
      return Jimple.newAssignStmt(
          v, invoke, new PositionInfo(debugInfo.getInstructionPosition(invokeInst.iindex), null));
    }
  }

  private IStmt convertBranchInstruction(
      DebuggingInformation debugInfo, SSAConditionalBranchInstruction condInst) {
    int val1 = condInst.getUse(0);
    int val2 = condInst.getUse(1);
    Value value1 = null;
    if (symbolTable.isZero(val1)) {
      value1 = IntConstant.getInstance(0);
    } else {
      value1 = getLocal(PrimitiveTypeSignature.getIntSignature(), val1);
    }
    Value value2 = null;
    if (symbolTable.isZero(val2)) {
      value2 = IntConstant.getInstance(0);
    } else {
      value2 = getLocal(PrimitiveTypeSignature.getIntSignature(), val1);
    }
    AbstractConditionExpr condition = null;
    IOperator op = condInst.getOperator();
    if (op.equals(Operator.EQ)) {
      condition = Jimple.newEqExpr(value1, value2);
    } else if (op.equals(Operator.NE)) {
      condition = Jimple.newNeExpr(value1, value2);
    } else if (op.equals(Operator.LT)) {
      condition = Jimple.newLtExpr(value1, value2);
    } else if (op.equals(Operator.GE)) {
      condition = Jimple.newGeExpr(value1, value2);
    } else if (op.equals(Operator.GT)) {
      condition = Jimple.newGtExpr(value1, value2);
    } else if (op.equals(Operator.LE)) {
      condition = Jimple.newLtExpr(value1, value2);
    } else {
      throw new RuntimeException("Unsupported conditional operator: " + op);
    }
    JStmtBox target = (JStmtBox) Jimple.newStmtBox(null);
    JIfStmt ifStmt =
        Jimple.newIfStmt(
            condition,
            target,
            new PositionInfo(debugInfo.getInstructionPosition(condInst.iindex), null));
    this.targetsOfIfStmts.put(ifStmt, condInst.getTarget());
    return ifStmt;
  }

  private IStmt convertBinaryOpInstruction(
      DebuggingInformation debugInfo, SSABinaryOpInstruction binOpInst) {
    int def = binOpInst.getDef();
    int val1 = binOpInst.getUse(0);
    int val2 = binOpInst.getUse(1);
    // TODO: only int type?
    TypeSignature type = PrimitiveTypeSignature.getIntSignature();
    Value result = getLocal(type, def);
    Value op1 = null;
    if (symbolTable.isConstant(val1)) {
      op1 = getConstant(val1);
      type = op1.getSignature();
    } else {
      op1 = getLocal(type, val1);
    }
    Value op2 = null;
    if (symbolTable.isConstant(val2)) {
      op2 = getConstant(val2);
      type = op2.getSignature();
    } else {
      op2 = getLocal(type, val2);
    }
    AbstractBinopExpr binExpr = null;
    IBinaryOpInstruction.IOperator operator = binOpInst.getOperator();
    if (operator.equals(IBinaryOpInstruction.Operator.ADD)) {
      binExpr = Jimple.newAddExpr(op1, op2);
    } else if (operator.equals(IBinaryOpInstruction.Operator.SUB)) {
      binExpr = Jimple.newSubExpr(op1, op2);
    } else if (operator.equals(IBinaryOpInstruction.Operator.MUL)) {
      binExpr = Jimple.newMulExpr(op1, op2);
    } else if (operator.equals(IBinaryOpInstruction.Operator.DIV)) {
      binExpr = Jimple.newDivExpr(op1, op2);
    } else if (operator.equals(IBinaryOpInstruction.Operator.REM)) {
      binExpr = Jimple.newRemExpr(op1, op2);
    } else if (operator.equals(IBinaryOpInstruction.Operator.AND)) {
      binExpr = Jimple.newAndExpr(op1, op2);
    } else if (operator.equals(IBinaryOpInstruction.Operator.OR)) {
      binExpr = Jimple.newOrExpr(op1, op2);
    } else if (operator.equals(IBinaryOpInstruction.Operator.XOR)) {
      binExpr = Jimple.newXorExpr(op1, op2);
    } else if (operator.equals(CAstBinaryOp.EQ)) {
      binExpr = Jimple.newEqExpr(op1, op2);
    } else if (operator.equals(CAstBinaryOp.NE)) {
      binExpr = Jimple.newNeExpr(op1, op2);
    } else if (operator.equals(CAstBinaryOp.LT)) {
      binExpr = Jimple.newLtExpr(op1, op2);
    } else if (operator.equals(CAstBinaryOp.GE)) {
      binExpr = Jimple.newGeExpr(op1, op2);
    } else if (operator.equals(CAstBinaryOp.GT)) {
      binExpr = Jimple.newGtExpr(op1, op2);
    } else if (operator.equals(CAstBinaryOp.LE)) {
      binExpr = Jimple.newLtExpr(op1, op2);
    } else if (operator.equals(IShiftInstruction.Operator.SHL)) {
      binExpr = Jimple.newShlExpr(op1, op2);
    } else if (operator.equals(IShiftInstruction.Operator.SHR)) {
      binExpr = Jimple.newShrExpr(op1, op2);
    } else if (operator.equals(IShiftInstruction.Operator.USHR)) {
      binExpr = Jimple.newUshrExpr(op1, op2);
    } else {
      throw new RuntimeException("Unsupported binary operator: " + operator.getClass());
    }
    Position[] operandPos = new Position[2];
    Position p1 = debugInfo.getOperandPosition(binOpInst.iindex, 0);
    operandPos[0] = p1;
    Position p2 = debugInfo.getOperandPosition(binOpInst.iindex, 1);
    operandPos[1] = p2;
    return Jimple.newAssignStmt(
        result,
        binExpr,
        new PositionInfo(debugInfo.getInstructionPosition(binOpInst.iindex), operandPos));
  }

  private IStmt convertReturnInstruction(
      DebuggingInformation debugInfo, SSAReturnInstruction inst) {
    int result = inst.getResult();
    if (inst.returnsVoid()) {
      // this is return void stmt
      return Jimple.newReturnVoidStmt(new PositionInfo(null, null));
    } else {
      Value ret;
      if (symbolTable.isConstant(result)) {
        ret = getConstant(result);
      } else {
        // TODO. how to get the type of result?
        ret = this.getLocal(UnknownTypeSignature.getInstance(), result);
      }
      return Jimple.newReturnStmt(
          ret, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
    }
  }

  private IStmt convertGoToInstruction(
      DebuggingInformation debugInfo, SSAGotoInstruction gotoInst) {
    JStmtBox target = (JStmtBox) Jimple.newStmtBox(null);
    JGotoStmt gotoStmt =
        Jimple.newGotoStmt(
            target, new PositionInfo(debugInfo.getInstructionPosition(gotoInst.iindex), null));
    this.targetsOfGotoStmts.put(gotoStmt, gotoInst.getTarget());
    return gotoStmt;
  }

  private IStmt convertGetInstruction(DebuggingInformation debugInfo, SSAGetInstruction inst) {
    int def = inst.getDef(0);
    FieldReference fieldRef = inst.getDeclaredField();
    TypeSignature fieldType = converter.convertType(inst.getDeclaredFieldType());
    String walaClassName = fieldRef.getDeclaringClass().getName().toString();
    JavaClassSignature classSig =
        sigFactory.getClassSignature(converter.convertClassNameFromWala(walaClassName));
    FieldSignature fieldSig =
        sigFactory.getFieldSignature(fieldRef.getName().toString(), classSig, fieldType.toString());
    Value rvalue = null;
    if (inst.isStatic()) {
      rvalue = Jimple.newStaticFieldRef(fieldSig);
    } else {
      int ref = inst.getRef();
      Local base = getLocal(classSig, ref);
      rvalue = Jimple.newInstanceFieldRef(base, fieldSig);
    }
    // TODO. get the position of ref
    Position p = debugInfo.getOperandPosition(inst.iindex, 0);
    // System.out.println("position: "+p);
    Value var = getLocal(fieldType, def);
    return Jimple.newAssignStmt(
        var, rvalue, new PositionInfo(debugInfo.getInstructionPosition(inst.iindex), null));
  }

  private Constant getConstant(int valueNumber) {
    Object value = symbolTable.getConstantValue(valueNumber);
    if (value instanceof Boolean) {
      if (value.equals(true)) {
        return IntConstant.getInstance(1);
      } else {
        return IntConstant.getInstance(0);
      }
    } else if (value instanceof Byte
        || value instanceof Char
        || value instanceof Short
        || value instanceof Integer) {
      return IntConstant.getInstance((int) value);
    } else if (symbolTable.isLongConstant(valueNumber)) {
      return LongConstant.getInstance((long) value);
    } else if (symbolTable.isDoubleConstant(valueNumber)) {
      return DoubleConstant.getInstance((double) value);
    } else if (symbolTable.isFloatConstant(valueNumber)) {
      return FloatConstant.getInstance((float) value);
    } else if (symbolTable.isStringConstant(valueNumber)) {
      return StringConstant.getInstance((String) value);
    } else if (symbolTable.isNullConstant(valueNumber)) {
      return NullConstant.getInstance();
    } else {
      throw new RuntimeException("Unsupported constant type: " + value.getClass().toString());
    }
  }

  private Local getLocal(TypeSignature type, int valueNumber) {
    if (locals.containsKey(valueNumber)) {
      return locals.get(valueNumber);
    }
    if (type.equals(methodSignature.getDeclClassSignature())) {
      if (!walaMethod.isStatic()) {
        return localGenerator.getThisLocal();
      }
    }
    if (symbolTable.isParameter(valueNumber)) {
      Local para = localGenerator.getParameterLocal(valueNumber - 1);
      if (para != null) {
        return para;
      }
    }
    if (!locals.containsKey(valueNumber)) {
      Local local = localGenerator.generateLocal(type);
      locals.put(valueNumber, local);
    }
    Local ret = locals.get(valueNumber);

    if (!ret.getSignature().equals(type)) {
      // ret.setType(ret.getType().merge(type));
      // TODO. re-implement merge.
      // throw new RuntimeException("Different types for same local
      // variable: "+ret.getType()+"<->"+type);
    }
    return ret;
  }

  /**
   * Test if the given stmt is the target stmt of {@link JIfStmt} or {@link JGotoStmt} and set it as
   * the target if it is the case.
   *
   * @param stmt the converted jimple stmt.
   * @param iindex the instruction index of the corresponding instruction in Wala.
   */
  protected void setTarget(IStmt stmt, int iindex) {
    if (this.targetsOfIfStmts.containsValue(iindex)) {
      for (JIfStmt ifStmt : this.targetsOfIfStmts.keySet()) {
        if (this.targetsOfIfStmts.get(ifStmt).equals(iindex)) {
          ifStmt.setTarget(stmt);
        }
      }
    }
    if (this.targetsOfGotoStmts.containsValue(iindex)) {
      for (JGotoStmt gotoStmt : this.targetsOfGotoStmts.keySet()) {
        if (this.targetsOfGotoStmts.get(gotoStmt).equals(iindex)) {
          gotoStmt.setTarget(stmt);
        }
      }
    }
    if (this.defaultOfLookUpSwitchStmts.containsValue(iindex)) {
      for (JLookupSwitchStmt lookupSwitch : this.defaultOfLookUpSwitchStmts.keySet()) {
        if (this.defaultOfLookUpSwitchStmts.get(lookupSwitch).equals(iindex)) {
          lookupSwitch.setDefaultTarget(stmt);
        }
      }
    }
    for (JLookupSwitchStmt lookupSwith : this.targetsOfLookUpSwitchStmts.keySet()) {
      if (this.targetsOfLookUpSwitchStmts.get(lookupSwith).contains(iindex)) {
        List<IStmt> targets = lookupSwith.getTargets();
        if (targets.contains(null)) { // targets only contains
          // placeholder
          targets = new ArrayList<>();
        }
        targets.add(stmt);
        lookupSwith.setTargets(targets);
      }
    }
  }
}
