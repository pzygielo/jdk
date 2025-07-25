/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.comp;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileManager;

import com.sun.source.tree.CaseTree;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Attribute.Compound;
import com.sun.tools.javac.code.Directive.ExportsDirective;
import com.sun.tools.javac.code.Directive.RequiresDirective;
import com.sun.tools.javac.code.Source.Feature;
import com.sun.tools.javac.comp.Annotate.AnnotationTypeMetadata;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import com.sun.tools.javac.resources.CompilerProperties.Warnings;
import com.sun.tools.javac.resources.CompilerProperties.LintWarnings;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.Error;
import com.sun.tools.javac.util.JCDiagnostic.Fragment;
import com.sun.tools.javac.util.JCDiagnostic.LintWarning;
import com.sun.tools.javac.util.List;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.DeferredAttr.DeferredAttrContext;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Flags.ANNOTATION;
import static com.sun.tools.javac.code.Flags.SYNCHRONIZED;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;
import static com.sun.tools.javac.code.Scope.LookupKind.RECURSIVE;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.code.TypeTag.WILDCARD;

import static com.sun.tools.javac.tree.JCTree.Tag.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementKindVisitor14;

/** Type checking helper class for the attribution phase.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Check {
    protected static final Context.Key<Check> checkKey = new Context.Key<>();

    // Flag bits indicating which item(s) chosen from a pair of items
    private static final int FIRST = 0x01;
    private static final int SECOND = 0x02;

    private final Names names;
    private final Log log;
    private final Resolve rs;
    private final Symtab syms;
    private final Enter enter;
    private final DeferredAttr deferredAttr;
    private final Infer infer;
    private final Types types;
    private final TypeAnnotations typeAnnotations;
    private final JCDiagnostic.Factory diags;
    private final JavaFileManager fileManager;
    private final Source source;
    private final Target target;
    private final Profile profile;
    private final Preview preview;
    private final boolean warnOnAnyAccessToMembers;

    public boolean disablePreviewCheck;

    // The set of lint options currently in effect. It is initialized
    // from the context, and then is set/reset as needed by Attr as it
    // visits all the various parts of the trees during attribution.
    Lint lint;

    // The method being analyzed in Attr - it is set/reset as needed by
    // Attr as it visits new method declarations.
    private MethodSymbol method;

    public static Check instance(Context context) {
        Check instance = context.get(checkKey);
        if (instance == null)
            instance = new Check(context);
        return instance;
    }

    @SuppressWarnings("this-escape")
    protected Check(Context context) {
        context.put(checkKey, this);

        names = Names.instance(context);
        log = Log.instance(context);
        rs = Resolve.instance(context);
        syms = Symtab.instance(context);
        enter = Enter.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        infer = Infer.instance(context);
        types = Types.instance(context);
        typeAnnotations = TypeAnnotations.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        Options options = Options.instance(context);
        lint = Lint.instance(context);
        fileManager = context.get(JavaFileManager.class);

        source = Source.instance(context);
        target = Target.instance(context);
        warnOnAnyAccessToMembers = options.isSet("warnOnAccessToMembers");

        disablePreviewCheck = false;

        Target target = Target.instance(context);
        syntheticNameChar = target.syntheticNameChar();

        profile = Profile.instance(context);
        preview = Preview.instance(context);

        deferredLintHandler = DeferredLintHandler.instance(context);

        allowModules = Feature.MODULES.allowedInSource(source);
        allowRecords = Feature.RECORDS.allowedInSource(source);
        allowSealed = Feature.SEALED_CLASSES.allowedInSource(source);
    }

    /** Character for synthetic names
     */
    char syntheticNameChar;

    /** A table mapping flat names of all compiled classes for each module in this run
     *  to their symbols; maintained from outside.
     */
    private Map<Pair<ModuleSymbol, Name>,ClassSymbol> compiled = new HashMap<>();

    /** A handler for deferred lint warnings.
     */
    private DeferredLintHandler deferredLintHandler;

    /** Are modules allowed
     */
    private final boolean allowModules;

    /** Are records allowed
     */
    private final boolean allowRecords;

    /** Are sealed classes allowed
     */
    private final boolean allowSealed;

    /** Whether to force suppression of deprecation and preview warnings.
     *  This happens when attributing import statements for JDK 9+.
     *  @see Feature#DEPRECATION_ON_IMPORT
     */
    private boolean importSuppression;

/* *************************************************************************
 * Errors and Warnings
 **************************************************************************/

    Lint setLint(Lint newLint) {
        Lint prev = lint;
        lint = newLint;
        return prev;
    }

    boolean setImportSuppression(boolean newImportSuppression) {
        boolean prev = importSuppression;
        importSuppression = newImportSuppression;
        return prev;
    }

    MethodSymbol setMethod(MethodSymbol newMethod) {
        MethodSymbol prev = method;
        method = newMethod;
        return prev;
    }

    /** Warn about deprecated symbol.
     *  @param pos        Position to be used for error reporting.
     *  @param sym        The deprecated symbol.
     */
    void warnDeprecated(DiagnosticPosition pos, Symbol sym) {
        LintWarning warningKey = null;
        if (sym.isDeprecatedForRemoval()) {
            if (!lint.isSuppressed(LintCategory.REMOVAL)) {
                if (sym.kind == MDL) {
                    warningKey = LintWarnings.HasBeenDeprecatedForRemovalModule(sym);
                } else {
                    warningKey = LintWarnings.HasBeenDeprecatedForRemoval(sym, sym.location());
                }
            }
        } else if (!lint.isSuppressed(LintCategory.DEPRECATION)) {
            if (sym.kind == MDL) {
                warningKey = LintWarnings.HasBeenDeprecatedModule(sym);
            } else {
                warningKey = LintWarnings.HasBeenDeprecated(sym, sym.location());
            }
        }
        if (warningKey != null)
            log.warning(pos, warningKey);
    }

    /** Log a preview warning.
     *  @param pos        Position to be used for error reporting.
     *  @param msg        A Warning describing the problem.
     */
    public void warnPreviewAPI(DiagnosticPosition pos, LintWarning warnKey) {
        if (!importSuppression && !lint.isSuppressed(LintCategory.PREVIEW))
            log.warning(pos, warnKey);
    }

    /** Log a preview warning.
     *  @param pos        Position to be used for error reporting.
     *  @param msg        A Warning describing the problem.
     */
    public void warnRestrictedAPI(DiagnosticPosition pos, Symbol sym) {
        lint.logIfEnabled(pos, LintWarnings.RestrictedMethod(sym.enclClass(), sym));
    }

    /** Warn about unchecked operation.
     *  @param pos        Position to be used for error reporting.
     *  @param msg        A string describing the problem.
     */
    public void warnUnchecked(DiagnosticPosition pos, LintWarning warnKey) {
        if (!lint.isSuppressed(LintCategory.UNCHECKED))
            log.warning(pos, warnKey);
    }

    /** Report a failure to complete a class.
     *  @param pos        Position to be used for error reporting.
     *  @param ex         The failure to report.
     */
    public Type completionError(DiagnosticPosition pos, CompletionFailure ex) {
        log.error(DiagnosticFlag.NON_DEFERRABLE, pos, Errors.CantAccess(ex.sym, ex.getDetailValue()));
        return syms.errType;
    }

    /** Report an error that wrong type tag was found.
     *  @param pos        Position to be used for error reporting.
     *  @param required   An internationalized string describing the type tag
     *                    required.
     *  @param found      The type that was found.
     */
    Type typeTagError(DiagnosticPosition pos, JCDiagnostic required, Object found) {
        // this error used to be raised by the parser,
        // but has been delayed to this point:
        if (found instanceof Type type && type.hasTag(VOID)) {
            log.error(pos, Errors.IllegalStartOfType);
            return syms.errType;
        }
        log.error(pos, Errors.TypeFoundReq(found, required));
        return types.createErrorType(found instanceof Type type ? type : syms.errType);
    }

    /** Report duplicate declaration error.
     */
    void duplicateError(DiagnosticPosition pos, Symbol sym) {
        if (!sym.type.isErroneous()) {
            Symbol location = sym.location();
            if (location.kind == MTH &&
                    ((MethodSymbol)location).isStaticOrInstanceInit()) {
                log.error(pos,
                          Errors.AlreadyDefinedInClinit(kindName(sym),
                                                        sym,
                                                        kindName(sym.location()),
                                                        kindName(sym.location().enclClass()),
                                                        sym.location().enclClass()));
            } else {
                /* dont error if this is a duplicated parameter of a generated canonical constructor
                 * as we should have issued an error for the duplicated fields
                 */
                if (location.kind != MTH ||
                        ((sym.owner.flags_field & GENERATEDCONSTR) == 0) ||
                        ((sym.owner.flags_field & RECORD) == 0)) {
                    log.error(pos,
                            Errors.AlreadyDefined(kindName(sym),
                                    sym,
                                    kindName(sym.location()),
                                    sym.location()));
                }
            }
        }
    }

    /** Report array/varargs duplicate declaration
     */
    void varargsDuplicateError(DiagnosticPosition pos, Symbol sym1, Symbol sym2) {
        if (!sym1.type.isErroneous() && !sym2.type.isErroneous()) {
            log.error(pos, Errors.ArrayAndVarargs(sym1, sym2, sym2.location()));
        }
    }

/* ************************************************************************
 * duplicate declaration checking
 *************************************************************************/

    /** Check that variable does not hide variable with same name in
     *  immediately enclosing local scope.
     *  @param pos           Position for error reporting.
     *  @param v             The symbol.
     *  @param s             The scope.
     */
    void checkTransparentVar(DiagnosticPosition pos, VarSymbol v, Scope s) {
        for (Symbol sym : s.getSymbolsByName(v.name)) {
            if (sym.owner != v.owner) break;
            if (sym.kind == VAR &&
                sym.owner.kind.matches(KindSelector.VAL_MTH) &&
                v.name != names.error) {
                duplicateError(pos, sym);
                return;
            }
        }
    }

    /** Check that a class or interface does not hide a class or
     *  interface with same name in immediately enclosing local scope.
     *  @param pos           Position for error reporting.
     *  @param c             The symbol.
     *  @param s             The scope.
     */
    void checkTransparentClass(DiagnosticPosition pos, ClassSymbol c, Scope s) {
        for (Symbol sym : s.getSymbolsByName(c.name)) {
            if (sym.owner != c.owner) break;
            if (sym.kind == TYP && !sym.type.hasTag(TYPEVAR) &&
                sym.owner.kind.matches(KindSelector.VAL_MTH) &&
                c.name != names.error) {
                duplicateError(pos, sym);
                return;
            }
        }
    }

    /** Check that class does not have the same name as one of
     *  its enclosing classes, or as a class defined in its enclosing scope.
     *  return true if class is unique in its enclosing scope.
     *  @param pos           Position for error reporting.
     *  @param name          The class name.
     *  @param s             The enclosing scope.
     */
    boolean checkUniqueClassName(DiagnosticPosition pos, Name name, Scope s) {
        for (Symbol sym : s.getSymbolsByName(name, NON_RECURSIVE)) {
            if (sym.kind == TYP && sym.name != names.error) {
                duplicateError(pos, sym);
                return false;
            }
        }
        for (Symbol sym = s.owner; sym != null; sym = sym.owner) {
            if (sym.kind == TYP && sym.name == name && sym.name != names.error &&
                    !sym.isImplicit()) {
                duplicateError(pos, sym);
                return true;
            }
        }
        return true;
    }

/* *************************************************************************
 * Class name generation
 **************************************************************************/


    private Map<Pair<Name, Name>, Integer> localClassNameIndexes = new HashMap<>();

    /** Return name of local class.
     *  This is of the form   {@code <enclClass> $ n <classname> }
     *  where
     *    enclClass is the flat name of the enclosing class,
     *    classname is the simple name of the local class
     */
    public Name localClassName(ClassSymbol c) {
        Name enclFlatname = c.owner.enclClass().flatname;
        String enclFlatnameStr = enclFlatname.toString();
        Pair<Name, Name> key = new Pair<>(enclFlatname, c.name);
        Integer index = localClassNameIndexes.get(key);
        for (int i = (index == null) ? 1 : index; ; i++) {
            Name flatname = names.fromString(enclFlatnameStr
                    + syntheticNameChar + i + c.name);
            if (getCompiled(c.packge().modle, flatname) == null) {
                localClassNameIndexes.put(key, i + 1);
                return flatname;
            }
        }
    }

    public void clearLocalClassNameIndexes(ClassSymbol c) {
        if (c.owner != null && c.owner.kind != NIL) {
            localClassNameIndexes.remove(new Pair<>(
                    c.owner.enclClass().flatname, c.name));
        }
    }

    public void newRound() {
        compiled.clear();
        localClassNameIndexes.clear();
    }

    public void putCompiled(ClassSymbol csym) {
        compiled.put(Pair.of(csym.packge().modle, csym.flatname), csym);
    }

    public ClassSymbol getCompiled(ClassSymbol csym) {
        return compiled.get(Pair.of(csym.packge().modle, csym.flatname));
    }

    public ClassSymbol getCompiled(ModuleSymbol msym, Name flatname) {
        return compiled.get(Pair.of(msym, flatname));
    }

    public void removeCompiled(ClassSymbol csym) {
        compiled.remove(Pair.of(csym.packge().modle, csym.flatname));
    }

/* *************************************************************************
 * Type Checking
 **************************************************************************/

    /**
     * A check context is an object that can be used to perform compatibility
     * checks - depending on the check context, meaning of 'compatibility' might
     * vary significantly.
     */
    public interface CheckContext {
        /**
         * Is type 'found' compatible with type 'req' in given context
         */
        boolean compatible(Type found, Type req, Warner warn);
        /**
         * Report a check error
         */
        void report(DiagnosticPosition pos, JCDiagnostic details);
        /**
         * Obtain a warner for this check context
         */
        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req);

        public InferenceContext inferenceContext();

        public DeferredAttr.DeferredAttrContext deferredAttrContext();
    }

    /**
     * This class represent a check context that is nested within another check
     * context - useful to check sub-expressions. The default behavior simply
     * redirects all method calls to the enclosing check context leveraging
     * the forwarding pattern.
     */
    static class NestedCheckContext implements CheckContext {
        CheckContext enclosingContext;

        NestedCheckContext(CheckContext enclosingContext) {
            this.enclosingContext = enclosingContext;
        }

        public boolean compatible(Type found, Type req, Warner warn) {
            return enclosingContext.compatible(found, req, warn);
        }

        public void report(DiagnosticPosition pos, JCDiagnostic details) {
            enclosingContext.report(pos, details);
        }

        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req) {
            return enclosingContext.checkWarner(pos, found, req);
        }

        public InferenceContext inferenceContext() {
            return enclosingContext.inferenceContext();
        }

        public DeferredAttrContext deferredAttrContext() {
            return enclosingContext.deferredAttrContext();
        }
    }

    /**
     * Check context to be used when evaluating assignment/return statements
     */
    CheckContext basicHandler = new CheckContext() {
        public void report(DiagnosticPosition pos, JCDiagnostic details) {
            log.error(pos, Errors.ProbFoundReq(details));
        }
        public boolean compatible(Type found, Type req, Warner warn) {
            return types.isAssignable(found, req, warn);
        }

        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req) {
            return convertWarner(pos, found, req);
        }

        public InferenceContext inferenceContext() {
            return infer.emptyContext;
        }

        public DeferredAttrContext deferredAttrContext() {
            return deferredAttr.emptyDeferredAttrContext;
        }

        @Override
        public String toString() {
            return "CheckContext: basicHandler";
        }
    };

    /** Check that a given type is assignable to a given proto-type.
     *  If it is, return the type, otherwise return errType.
     *  @param pos        Position to be used for error reporting.
     *  @param found      The type that was found.
     *  @param req        The type that was required.
     */
    public Type checkType(DiagnosticPosition pos, Type found, Type req) {
        return checkType(pos, found, req, basicHandler);
    }

    Type checkType(final DiagnosticPosition pos, final Type found, final Type req, final CheckContext checkContext) {
        final InferenceContext inferenceContext = checkContext.inferenceContext();
        if (inferenceContext.free(req) || inferenceContext.free(found)) {
            inferenceContext.addFreeTypeListener(List.of(req, found),
                    solvedContext -> checkType(pos, solvedContext.asInstType(found), solvedContext.asInstType(req), checkContext));
        }
        if (req.hasTag(ERROR))
            return req;
        if (req.hasTag(NONE))
            return found;
        if (checkContext.compatible(found, req, checkContext.checkWarner(pos, found, req))) {
            return found;
        } else {
            if (found.isNumeric() && req.isNumeric()) {
                checkContext.report(pos, diags.fragment(Fragments.PossibleLossOfPrecision(found, req)));
                return types.createErrorType(found);
            }
            checkContext.report(pos, diags.fragment(Fragments.InconvertibleTypes(found, req)));
            return types.createErrorType(found);
        }
    }

    /** Check that a given type can be cast to a given target type.
     *  Return the result of the cast.
     *  @param pos        Position to be used for error reporting.
     *  @param found      The type that is being cast.
     *  @param req        The target type of the cast.
     */
    Type checkCastable(DiagnosticPosition pos, Type found, Type req) {
        return checkCastable(pos, found, req, basicHandler);
    }
    Type checkCastable(DiagnosticPosition pos, Type found, Type req, CheckContext checkContext) {
        if (types.isCastable(found, req, castWarner(pos, found, req))) {
            return req;
        } else {
            checkContext.report(pos, diags.fragment(Fragments.InconvertibleTypes(found, req)));
            return types.createErrorType(found);
        }
    }

    /** Check for redundant casts (i.e. where source type is a subtype of target type)
     * The problem should only be reported for non-292 cast
     */
    public void checkRedundantCast(Env<AttrContext> env, final JCTypeCast tree) {
        if (!tree.type.isErroneous()
                && types.isSameType(tree.expr.type, tree.clazz.type)
                && !(ignoreAnnotatedCasts && TreeInfo.containsTypeAnnotation(tree.clazz))
                && !is292targetTypeCast(tree)) {
            deferredLintHandler.report(_l -> {
                lint.logIfEnabled(tree.pos(), LintWarnings.RedundantCast(tree.clazz.type));
            });
        }
    }
    //where
        private boolean is292targetTypeCast(JCTypeCast tree) {
            boolean is292targetTypeCast = false;
            JCExpression expr = TreeInfo.skipParens(tree.expr);
            if (expr.hasTag(APPLY)) {
                JCMethodInvocation apply = (JCMethodInvocation)expr;
                Symbol sym = TreeInfo.symbol(apply.meth);
                is292targetTypeCast = sym != null &&
                    sym.kind == MTH &&
                    (sym.flags() & HYPOTHETICAL) != 0;
            }
            return is292targetTypeCast;
        }

        private static final boolean ignoreAnnotatedCasts = true;

    /** Check that a type is within some bounds.
     *
     *  Used in TypeApply to verify that, e.g., X in {@code V<X>} is a valid
     *  type argument.
     *  @param a             The type that should be bounded by bs.
     *  @param bound         The bound.
     */
    private boolean checkExtends(Type a, Type bound) {
         if (a.isUnbound()) {
             return true;
         } else if (!a.hasTag(WILDCARD)) {
             a = types.cvarUpperBound(a);
             return types.isSubtype(a, bound);
         } else if (a.isExtendsBound()) {
             return types.isCastable(bound, types.wildUpperBound(a), types.noWarnings);
         } else if (a.isSuperBound()) {
             return !types.notSoftSubtype(types.wildLowerBound(a), bound);
         }
         return true;
     }

    /** Check that type is different from 'void'.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkNonVoid(DiagnosticPosition pos, Type t) {
        if (t.hasTag(VOID)) {
            log.error(pos, Errors.VoidNotAllowedHere);
            return types.createErrorType(t);
        } else {
            return t;
        }
    }

    Type checkClassOrArrayType(DiagnosticPosition pos, Type t) {
        if (!t.hasTag(CLASS) && !t.hasTag(ARRAY) && !t.hasTag(ERROR)) {
            return typeTagError(pos,
                                diags.fragment(Fragments.TypeReqClassArray),
                                asTypeParam(t));
        } else {
            return t;
        }
    }

    /** Check that type is a class or interface type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkClassType(DiagnosticPosition pos, Type t) {
        if (!t.hasTag(CLASS) && !t.hasTag(ERROR)) {
            return typeTagError(pos,
                                diags.fragment(Fragments.TypeReqClass),
                                asTypeParam(t));
        } else {
            return t;
        }
    }
    //where
        private Object asTypeParam(Type t) {
            return (t.hasTag(TYPEVAR))
                                    ? diags.fragment(Fragments.TypeParameter(t))
                                    : t;
        }

    /** Check that type is a valid qualifier for a constructor reference expression
     */
    Type checkConstructorRefType(DiagnosticPosition pos, Type t) {
        t = checkClassOrArrayType(pos, t);
        if (t.hasTag(CLASS)) {
            if ((t.tsym.flags() & (ABSTRACT | INTERFACE)) != 0) {
                log.error(pos, Errors.AbstractCantBeInstantiated(t.tsym));
                t = types.createErrorType(t);
            } else if ((t.tsym.flags() & ENUM) != 0) {
                log.error(pos, Errors.EnumCantBeInstantiated);
                t = types.createErrorType(t);
            } else {
                t = checkClassType(pos, t, true);
            }
        } else if (t.hasTag(ARRAY)) {
            if (!types.isReifiable(((ArrayType)t).elemtype)) {
                log.error(pos, Errors.GenericArrayCreation);
                t = types.createErrorType(t);
            }
        }
        return t;
    }

    /** Check that type is a class or interface type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     *  @param noBounds    True if type bounds are illegal here.
     */
    Type checkClassType(DiagnosticPosition pos, Type t, boolean noBounds) {
        t = checkClassType(pos, t);
        if (noBounds && t.isParameterized()) {
            List<Type> args = t.getTypeArguments();
            while (args.nonEmpty()) {
                if (args.head.hasTag(WILDCARD))
                    return typeTagError(pos,
                                        diags.fragment(Fragments.TypeReqExact),
                                        args.head);
                args = args.tail;
            }
        }
        return t;
    }

    /** Check that type is a reference type, i.e. a class, interface or array type
     *  or a type variable.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkRefType(DiagnosticPosition pos, Type t) {
        if (t.isReference())
            return t;
        else
            return typeTagError(pos,
                                diags.fragment(Fragments.TypeReqRef),
                                t);
    }

    /** Check that each type is a reference type, i.e. a class, interface or array type
     *  or a type variable.
     *  @param trees         Original trees, used for error reporting.
     *  @param types         The types to be checked.
     */
    List<Type> checkRefTypes(List<JCExpression> trees, List<Type> types) {
        List<JCExpression> tl = trees;
        for (List<Type> l = types; l.nonEmpty(); l = l.tail) {
            l.head = checkRefType(tl.head.pos(), l.head);
            tl = tl.tail;
        }
        return types;
    }

    /** Check that type is a null or reference type.
     *  @param pos           Position to be used for error reporting.
     *  @param t             The type to be checked.
     */
    Type checkNullOrRefType(DiagnosticPosition pos, Type t) {
        if (t.isReference() || t.hasTag(BOT))
            return t;
        else
            return typeTagError(pos,
                                diags.fragment(Fragments.TypeReqRef),
                                t);
    }

    /** Check that flag set does not contain elements of two conflicting sets. s
     *  Return true if it doesn't.
     *  @param pos           Position to be used for error reporting.
     *  @param flags         The set of flags to be checked.
     *  @param set1          Conflicting flags set #1.
     *  @param set2          Conflicting flags set #2.
     */
    boolean checkDisjoint(DiagnosticPosition pos, long flags, long set1, long set2) {
        if ((flags & set1) != 0 && (flags & set2) != 0) {
            log.error(pos,
                      Errors.IllegalCombinationOfModifiers(asFlagSet(TreeInfo.firstFlag(flags & set1)),
                                                           asFlagSet(TreeInfo.firstFlag(flags & set2))));
            return false;
        } else
            return true;
    }

    /** Check that usage of diamond operator is correct (i.e. diamond should not
     * be used with non-generic classes or in anonymous class creation expressions)
     */
    Type checkDiamond(JCNewClass tree, Type t) {
        if (!TreeInfo.isDiamond(tree) ||
                t.isErroneous()) {
            return checkClassType(tree.clazz.pos(), t, true);
        } else {
            if (tree.def != null && !Feature.DIAMOND_WITH_ANONYMOUS_CLASS_CREATION.allowedInSource(source)) {
                log.error(DiagnosticFlag.SOURCE_LEVEL, tree.clazz.pos(),
                        Errors.CantApplyDiamond1(t, Feature.DIAMOND_WITH_ANONYMOUS_CLASS_CREATION.fragment(source.name)));
            }
            if (t.tsym.type.getTypeArguments().isEmpty()) {
                log.error(tree.clazz.pos(),
                          Errors.CantApplyDiamond1(t,
                                                   Fragments.DiamondNonGeneric(t)));
                return types.createErrorType(t);
            } else if (tree.typeargs != null &&
                    tree.typeargs.nonEmpty()) {
                log.error(tree.clazz.pos(),
                          Errors.CantApplyDiamond1(t,
                                                   Fragments.DiamondAndExplicitParams(t)));
                return types.createErrorType(t);
            } else {
                return t;
            }
        }
    }

    /** Check that the type inferred using the diamond operator does not contain
     *  non-denotable types such as captured types or intersection types.
     *  @param t the type inferred using the diamond operator
     *  @return  the (possibly empty) list of non-denotable types.
     */
    List<Type> checkDiamondDenotable(ClassType t) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type arg : t.allparams()) {
            if (!checkDenotable(arg)) {
                buf.append(arg);
            }
        }
        return buf.toList();
    }

    public boolean checkDenotable(Type t) {
        return denotableChecker.visit(t, null);
    }
        // where

        /** diamondTypeChecker: A type visitor that descends down the given type looking for non-denotable
         *  types. The visit methods return false as soon as a non-denotable type is encountered and true
         *  otherwise.
         */
        private static final Types.SimpleVisitor<Boolean, Void> denotableChecker = new Types.SimpleVisitor<Boolean, Void>() {
            @Override
            public Boolean visitType(Type t, Void s) {
                return true;
            }
            @Override
            public Boolean visitClassType(ClassType t, Void s) {
                if (t.isUnion() || t.isIntersection()) {
                    return false;
                }
                for (Type targ : t.allparams()) {
                    if (!visit(targ, s)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Boolean visitTypeVar(TypeVar t, Void s) {
                /* Any type variable mentioned in the inferred type must have been declared as a type parameter
                  (i.e cannot have been produced by inference (18.4))
                */
                return (t.tsym.flags() & SYNTHETIC) == 0;
            }

            @Override
            public Boolean visitCapturedType(CapturedType t, Void s) {
                /* Any type variable mentioned in the inferred type must have been declared as a type parameter
                  (i.e cannot have been produced by capture conversion (5.1.10))
                */
                return false;
            }

            @Override
            public Boolean visitArrayType(ArrayType t, Void s) {
                return visit(t.elemtype, s);
            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Void s) {
                return visit(t.type, s);
            }
        };

    void checkVarargsMethodDecl(Env<AttrContext> env, JCMethodDecl tree) {
        MethodSymbol m = tree.sym;
        boolean hasTrustMeAnno = m.attribute(syms.trustMeType.tsym) != null;
        Type varargElemType = null;
        if (m.isVarArgs()) {
            varargElemType = types.elemtype(tree.params.last().type);
        }
        if (hasTrustMeAnno && !isTrustMeAllowedOnMethod(m)) {
            if (varargElemType != null) {
                JCDiagnostic msg = Feature.PRIVATE_SAFE_VARARGS.allowedInSource(source) ?
                        diags.fragment(Fragments.VarargsTrustmeOnVirtualVarargs(m)) :
                        diags.fragment(Fragments.VarargsTrustmeOnVirtualVarargsFinalOnly(m));
                log.error(tree,
                          Errors.VarargsInvalidTrustmeAnno(syms.trustMeType.tsym,
                                                           msg));
            } else {
                log.error(tree,
                          Errors.VarargsInvalidTrustmeAnno(syms.trustMeType.tsym,
                                                           Fragments.VarargsTrustmeOnNonVarargsMeth(m)));
            }
        } else if (hasTrustMeAnno && varargElemType != null &&
                            types.isReifiable(varargElemType)) {
            lint.logIfEnabled(tree, LintWarnings.VarargsRedundantTrustmeAnno(
                                syms.trustMeType.tsym,
                                diags.fragment(Fragments.VarargsTrustmeOnReifiableVarargs(varargElemType))));
        }
        else if (!hasTrustMeAnno && varargElemType != null &&
                !types.isReifiable(varargElemType)) {
            warnUnchecked(tree.params.head.pos(), LintWarnings.UncheckedVarargsNonReifiableType(varargElemType));
        }
    }
    //where
        private boolean isTrustMeAllowedOnMethod(Symbol s) {
            return (s.flags() & VARARGS) != 0 &&
                (s.isConstructor() ||
                    (s.flags() & (STATIC | FINAL |
                                  (Feature.PRIVATE_SAFE_VARARGS.allowedInSource(source) ? PRIVATE : 0) )) != 0);
        }

    Type checkLocalVarType(DiagnosticPosition pos, Type t, Name name) {
        //check that resulting type is not the null type
        if (t.hasTag(BOT)) {
            log.error(pos, Errors.CantInferLocalVarType(name, Fragments.LocalCantInferNull));
            return types.createErrorType(t);
        } else if (t.hasTag(VOID)) {
            log.error(pos, Errors.CantInferLocalVarType(name, Fragments.LocalCantInferVoid));
            return types.createErrorType(t);
        }

        //upward project the initializer type
        return types.upward(t, types.captures(t)).baseType();
    }

    Type checkMethod(final Type mtype,
            final Symbol sym,
            final Env<AttrContext> env,
            final List<JCExpression> argtrees,
            final List<Type> argtypes,
            final boolean useVarargs,
            InferenceContext inferenceContext) {
        // System.out.println("call   : " + env.tree);
        // System.out.println("method : " + owntype);
        // System.out.println("actuals: " + argtypes);
        if (inferenceContext.free(mtype)) {
            inferenceContext.addFreeTypeListener(List.of(mtype),
                    solvedContext -> checkMethod(solvedContext.asInstType(mtype), sym, env, argtrees, argtypes, useVarargs, solvedContext));
            return mtype;
        }
        Type owntype = mtype;
        List<Type> formals = owntype.getParameterTypes();
        List<Type> nonInferred = sym.type.getParameterTypes();
        if (nonInferred.length() != formals.length()) nonInferred = formals;
        Type last = useVarargs ? formals.last() : null;
        if (sym.name == names.init && sym.owner == syms.enumSym) {
            formals = formals.tail.tail;
            nonInferred = nonInferred.tail.tail;
        }
        if ((sym.flags() & ANONCONSTR_BASED) != 0) {
            formals = formals.tail;
            nonInferred = nonInferred.tail;
        }
        List<JCExpression> args = argtrees;
        if (args != null) {
            //this is null when type-checking a method reference
            while (formals.head != last) {
                JCTree arg = args.head;
                Warner warn = convertWarner(arg.pos(), arg.type, nonInferred.head);
                assertConvertible(arg, arg.type, formals.head, warn);
                args = args.tail;
                formals = formals.tail;
                nonInferred = nonInferred.tail;
            }
            if (useVarargs) {
                Type varArg = types.elemtype(last);
                while (args.tail != null) {
                    JCTree arg = args.head;
                    Warner warn = convertWarner(arg.pos(), arg.type, varArg);
                    assertConvertible(arg, arg.type, varArg, warn);
                    args = args.tail;
                }
            } else if ((sym.flags() & (VARARGS | SIGNATURE_POLYMORPHIC)) == VARARGS) {
                // non-varargs call to varargs method
                Type varParam = owntype.getParameterTypes().last();
                Type lastArg = argtypes.last();
                if (types.isSubtypeUnchecked(lastArg, types.elemtype(varParam)) &&
                    !types.isSameType(types.erasure(varParam), types.erasure(lastArg)))
                    log.warning(argtrees.last().pos(),
                                Warnings.InexactNonVarargsCall(types.elemtype(varParam),varParam));
            }
        }
        if (useVarargs) {
            Type argtype = owntype.getParameterTypes().last();
            if (!types.isReifiable(argtype) &&
                (sym.baseSymbol().attribute(syms.trustMeType.tsym) == null ||
                 !isTrustMeAllowedOnMethod(sym))) {
                warnUnchecked(env.tree.pos(), LintWarnings.UncheckedGenericArrayCreation(argtype));
            }
            TreeInfo.setVarargsElement(env.tree, types.elemtype(argtype));
         }
         return owntype;
    }
    //where
    private void assertConvertible(JCTree tree, Type actual, Type formal, Warner warn) {
        if (types.isConvertible(actual, formal, warn))
            return;

        if (formal.isCompound()
            && types.isSubtype(actual, types.supertype(formal))
            && types.isSubtypeUnchecked(actual, types.interfaces(formal), warn))
            return;
    }

    /**
     * Check that type 't' is a valid instantiation of a generic class
     * (see JLS 4.5)
     *
     * @param t class type to be checked
     * @return true if 't' is well-formed
     */
    public boolean checkValidGenericType(Type t) {
        return firstIncompatibleTypeArg(t) == null;
    }
    //WHERE
        private Type firstIncompatibleTypeArg(Type type) {
            List<Type> formals = type.tsym.type.allparams();
            List<Type> actuals = type.allparams();
            List<Type> args = type.getTypeArguments();
            List<Type> forms = type.tsym.type.getTypeArguments();
            ListBuffer<Type> bounds_buf = new ListBuffer<>();

            // For matching pairs of actual argument types `a' and
            // formal type parameters with declared bound `b' ...
            while (args.nonEmpty() && forms.nonEmpty()) {
                // exact type arguments needs to know their
                // bounds (for upper and lower bound
                // calculations).  So we create new bounds where
                // type-parameters are replaced with actuals argument types.
                bounds_buf.append(types.subst(forms.head.getUpperBound(), formals, actuals));
                args = args.tail;
                forms = forms.tail;
            }

            args = type.getTypeArguments();
            List<Type> tvars_cap = types.substBounds(formals,
                                      formals,
                                      types.capture(type).allparams());
            while (args.nonEmpty() && tvars_cap.nonEmpty()) {
                // Let the actual arguments know their bound
                args.head.withTypeVar((TypeVar)tvars_cap.head);
                args = args.tail;
                tvars_cap = tvars_cap.tail;
            }

            args = type.getTypeArguments();
            List<Type> bounds = bounds_buf.toList();

            while (args.nonEmpty() && bounds.nonEmpty()) {
                Type actual = args.head;
                if (!isTypeArgErroneous(actual) &&
                        !bounds.head.isErroneous() &&
                        !checkExtends(actual, bounds.head)) {
                    return args.head;
                }
                args = args.tail;
                bounds = bounds.tail;
            }

            args = type.getTypeArguments();
            bounds = bounds_buf.toList();

            for (Type arg : types.capture(type).getTypeArguments()) {
                if (arg.hasTag(TYPEVAR) &&
                        arg.getUpperBound().isErroneous() &&
                        !bounds.head.isErroneous() &&
                        !isTypeArgErroneous(args.head)) {
                    return args.head;
                }
                bounds = bounds.tail;
                args = args.tail;
            }

            return null;
        }
        //where
        boolean isTypeArgErroneous(Type t) {
            return isTypeArgErroneous.visit(t);
        }

        Types.UnaryVisitor<Boolean> isTypeArgErroneous = new Types.UnaryVisitor<Boolean>() {
            public Boolean visitType(Type t, Void s) {
                return t.isErroneous();
            }
            @Override
            public Boolean visitTypeVar(TypeVar t, Void s) {
                return visit(t.getUpperBound());
            }
            @Override
            public Boolean visitCapturedType(CapturedType t, Void s) {
                return visit(t.getUpperBound()) ||
                        visit(t.getLowerBound());
            }
            @Override
            public Boolean visitWildcardType(WildcardType t, Void s) {
                return visit(t.type);
            }
        };

    /** Check that given modifiers are legal for given symbol and
     *  return modifiers together with any implicit modifiers for that symbol.
     *  Warning: we can't use flags() here since this method
     *  is called during class enter, when flags() would cause a premature
     *  completion.
     *  @param flags         The set of modifiers given in a definition.
     *  @param sym           The defined symbol.
     *  @param tree          The declaration
     */
    long checkFlags(long flags, Symbol sym, JCTree tree) {
        final DiagnosticPosition pos = tree.pos();
        long mask;
        long implicit = 0;

        switch (sym.kind) {
        case VAR:
            if (TreeInfo.isReceiverParam(tree))
                mask = ReceiverParamFlags;
            else if (sym.owner.kind != TYP)
                mask = LocalVarFlags;
            else if ((sym.owner.flags_field & INTERFACE) != 0)
                mask = implicit = InterfaceVarFlags;
            else
                mask = VarFlags;
            break;
        case MTH:
            if (sym.name == names.init) {
                if ((sym.owner.flags_field & ENUM) != 0) {
                    // enum constructors cannot be declared public or
                    // protected and must be implicitly or explicitly
                    // private
                    implicit = PRIVATE;
                    mask = PRIVATE;
                } else
                    mask = ConstructorFlags;
            }  else if ((sym.owner.flags_field & INTERFACE) != 0) {
                if ((sym.owner.flags_field & ANNOTATION) != 0) {
                    mask = AnnotationTypeElementMask;
                    implicit = PUBLIC | ABSTRACT;
                } else if ((flags & (DEFAULT | STATIC | PRIVATE)) != 0) {
                    mask = InterfaceMethodMask;
                    implicit = (flags & PRIVATE) != 0 ? 0 : PUBLIC;
                    if ((flags & DEFAULT) != 0) {
                        implicit |= ABSTRACT;
                    }
                } else {
                    mask = implicit = InterfaceMethodFlags;
                }
            } else if ((sym.owner.flags_field & RECORD) != 0) {
                mask = RecordMethodFlags;
            } else {
                mask = MethodFlags;
            }
            if ((flags & STRICTFP) != 0) {
                warnOnExplicitStrictfp(tree);
            }
            // Imply STRICTFP if owner has STRICTFP set.
            if (((flags|implicit) & Flags.ABSTRACT) == 0 ||
                ((flags) & Flags.DEFAULT) != 0)
                implicit |= sym.owner.flags_field & STRICTFP;
            break;
        case TYP:
            if (sym.owner.kind.matches(KindSelector.VAL_MTH) ||
                    (sym.isDirectlyOrIndirectlyLocal() && (flags & ANNOTATION) != 0)) {
                boolean implicitlyStatic = !sym.isAnonymous() &&
                        ((flags & RECORD) != 0 || (flags & ENUM) != 0 || (flags & INTERFACE) != 0);
                boolean staticOrImplicitlyStatic = (flags & STATIC) != 0 || implicitlyStatic;
                // local statics are allowed only if records are allowed too
                mask = staticOrImplicitlyStatic && allowRecords && (flags & ANNOTATION) == 0 ? StaticLocalFlags : LocalClassFlags;
                implicit = implicitlyStatic ? STATIC : implicit;
            } else if (sym.owner.kind == TYP) {
                // statics in inner classes are allowed only if records are allowed too
                mask = ((flags & STATIC) != 0) && allowRecords && (flags & ANNOTATION) == 0 ? ExtendedMemberStaticClassFlags : ExtendedMemberClassFlags;
                if (sym.owner.owner.kind == PCK ||
                    (sym.owner.flags_field & STATIC) != 0) {
                    mask |= STATIC;
                } else if (!allowRecords && ((flags & ENUM) != 0 || (flags & RECORD) != 0)) {
                    log.error(pos, Errors.StaticDeclarationNotAllowedInInnerClasses);
                }
                // Nested interfaces and enums are always STATIC (Spec ???)
                if ((flags & (INTERFACE | ENUM | RECORD)) != 0 ) implicit = STATIC;
            } else {
                mask = ExtendedClassFlags;
            }
            // Interfaces are always ABSTRACT
            if ((flags & INTERFACE) != 0) implicit |= ABSTRACT;

            if ((flags & ENUM) != 0) {
                // enums can't be declared abstract, final, sealed or non-sealed
                mask &= ~(ABSTRACT | FINAL | SEALED | NON_SEALED);
                implicit |= implicitEnumFinalFlag(tree);
            }
            if ((flags & RECORD) != 0) {
                // records can't be declared abstract
                mask &= ~ABSTRACT;
                implicit |= FINAL;
            }
            if ((flags & STRICTFP) != 0) {
                warnOnExplicitStrictfp(tree);
            }
            // Imply STRICTFP if owner has STRICTFP set.
            implicit |= sym.owner.flags_field & STRICTFP;
            break;
        default:
            throw new AssertionError();
        }
        long illegal = flags & ExtendedStandardFlags & ~mask;
        if (illegal != 0) {
            if ((illegal & INTERFACE) != 0) {
                log.error(pos, ((flags & ANNOTATION) != 0) ? Errors.AnnotationDeclNotAllowedHere : Errors.IntfNotAllowedHere);
                mask |= INTERFACE;
            }
            else {
                log.error(pos,
                        Errors.ModNotAllowedHere(asFlagSet(illegal)));
            }
        }
        else if ((sym.kind == TYP ||
                  // ISSUE: Disallowing abstract&private is no longer appropriate
                  // in the presence of inner classes. Should it be deleted here?
                  checkDisjoint(pos, flags,
                                ABSTRACT,
                                PRIVATE | STATIC | DEFAULT))
                 &&
                 checkDisjoint(pos, flags,
                                STATIC | PRIVATE,
                                DEFAULT)
                 &&
                 checkDisjoint(pos, flags,
                               ABSTRACT | INTERFACE,
                               FINAL | NATIVE | SYNCHRONIZED)
                 &&
                 checkDisjoint(pos, flags,
                               PUBLIC,
                               PRIVATE | PROTECTED)
                 &&
                 checkDisjoint(pos, flags,
                               PRIVATE,
                               PUBLIC | PROTECTED)
                 &&
                 checkDisjoint(pos, flags,
                               FINAL,
                               VOLATILE)
                 &&
                 (sym.kind == TYP ||
                  checkDisjoint(pos, flags,
                                ABSTRACT | NATIVE,
                                STRICTFP))
                 && checkDisjoint(pos, flags,
                                FINAL,
                           SEALED | NON_SEALED)
                 && checkDisjoint(pos, flags,
                                SEALED,
                           FINAL | NON_SEALED)
                 && checkDisjoint(pos, flags,
                                SEALED,
                                ANNOTATION)) {
            // skip
        }
        return flags & (mask | ~ExtendedStandardFlags) | implicit;
    }

    private void warnOnExplicitStrictfp(JCTree tree) {
        deferredLintHandler.push(tree);
        try {
            deferredLintHandler.report(_ -> lint.logIfEnabled(tree.pos(), LintWarnings.Strictfp));
        } finally {
            deferredLintHandler.pop();
        }
    }


    /** Determine if this enum should be implicitly final.
     *
     *  If the enum has no specialized enum constants, it is final.
     *
     *  If the enum does have specialized enum constants, it is
     *  <i>not</i> final.
     */
    private long implicitEnumFinalFlag(JCTree tree) {
        if (!tree.hasTag(CLASSDEF)) return 0;
        class SpecialTreeVisitor extends JCTree.Visitor {
            boolean specialized;
            SpecialTreeVisitor() {
                this.specialized = false;
            }

            @Override
            public void visitTree(JCTree tree) { /* no-op */ }

            @Override
            public void visitVarDef(JCVariableDecl tree) {
                if ((tree.mods.flags & ENUM) != 0) {
                    if (tree.init instanceof JCNewClass newClass && newClass.def != null) {
                        specialized = true;
                    }
                }
            }
        }

        SpecialTreeVisitor sts = new SpecialTreeVisitor();
        JCClassDecl cdef = (JCClassDecl) tree;
        for (JCTree defs: cdef.defs) {
            defs.accept(sts);
            if (sts.specialized) return allowSealed ? SEALED : 0;
        }
        return FINAL;
    }

/* *************************************************************************
 * Type Validation
 **************************************************************************/

    /** Validate a type expression. That is,
     *  check that all type arguments of a parametric type are within
     *  their bounds. This must be done in a second phase after type attribution
     *  since a class might have a subclass as type parameter bound. E.g:
     *
     *  <pre>{@code
     *  class B<A extends C> { ... }
     *  class C extends B<C> { ... }
     *  }</pre>
     *
     *  and we can't make sure that the bound is already attributed because
     *  of possible cycles.
     *
     * Visitor method: Validate a type expression, if it is not null, catching
     *  and reporting any completion failures.
     */
    void validate(JCTree tree, Env<AttrContext> env) {
        validate(tree, env, true);
    }
    void validate(JCTree tree, Env<AttrContext> env, boolean checkRaw) {
        new Validator(env).validateTree(tree, checkRaw, true);
    }

    /** Visitor method: Validate a list of type expressions.
     */
    void validate(List<? extends JCTree> trees, Env<AttrContext> env) {
        for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
            validate(l.head, env);
    }

    /** A visitor class for type validation.
     */
    class Validator extends JCTree.Visitor {

        boolean checkRaw;
        boolean isOuter;
        Env<AttrContext> env;

        Validator(Env<AttrContext> env) {
            this.env = env;
        }

        @Override
        public void visitTypeArray(JCArrayTypeTree tree) {
            validateTree(tree.elemtype, checkRaw, isOuter);
        }

        @Override
        public void visitTypeApply(JCTypeApply tree) {
            if (tree.type.hasTag(CLASS)) {
                List<JCExpression> args = tree.arguments;
                List<Type> forms = tree.type.tsym.type.getTypeArguments();

                Type incompatibleArg = firstIncompatibleTypeArg(tree.type);
                if (incompatibleArg != null) {
                    for (JCTree arg : tree.arguments) {
                        if (arg.type == incompatibleArg) {
                            log.error(arg, Errors.NotWithinBounds(incompatibleArg, forms.head));
                        }
                        forms = forms.tail;
                     }
                 }

                forms = tree.type.tsym.type.getTypeArguments();

                boolean is_java_lang_Class = tree.type.tsym.flatName() == names.java_lang_Class;

                // For matching pairs of actual argument types `a' and
                // formal type parameters with declared bound `b' ...
                while (args.nonEmpty() && forms.nonEmpty()) {
                    validateTree(args.head,
                            !(isOuter && is_java_lang_Class),
                            false);
                    args = args.tail;
                    forms = forms.tail;
                }

                // Check that this type is either fully parameterized, or
                // not parameterized at all.
                if (tree.type.getEnclosingType().isRaw())
                    log.error(tree.pos(), Errors.ImproperlyFormedTypeInnerRawParam);
                if (tree.clazz.hasTag(SELECT))
                    visitSelectInternal((JCFieldAccess)tree.clazz);
            }
        }

        @Override
        public void visitTypeParameter(JCTypeParameter tree) {
            validateTrees(tree.bounds, true, isOuter);
            checkClassBounds(tree.pos(), tree.type);
        }

        @Override
        public void visitWildcard(JCWildcard tree) {
            if (tree.inner != null)
                validateTree(tree.inner, true, isOuter);
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            if (tree.type.hasTag(CLASS)) {
                visitSelectInternal(tree);

                // Check that this type is either fully parameterized, or
                // not parameterized at all.
                if (tree.selected.type.isParameterized() && tree.type.tsym.type.getTypeArguments().nonEmpty())
                    log.error(tree.pos(), Errors.ImproperlyFormedTypeParamMissing);
            }
        }

        public void visitSelectInternal(JCFieldAccess tree) {
            if (tree.type.tsym.isStatic() &&
                tree.selected.type.isParameterized()) {
                // The enclosing type is not a class, so we are
                // looking at a static member type.  However, the
                // qualifying expression is parameterized.
                log.error(tree.pos(), Errors.CantSelectStaticClassFromParamType);
            } else {
                // otherwise validate the rest of the expression
                tree.selected.accept(this);
            }
        }

        @Override
        public void visitAnnotatedType(JCAnnotatedType tree) {
            tree.underlyingType.accept(this);
        }

        @Override
        public void visitTypeIdent(JCPrimitiveTypeTree that) {
            if (that.type.hasTag(TypeTag.VOID)) {
                log.error(that.pos(), Errors.VoidNotAllowedHere);
            }
            super.visitTypeIdent(that);
        }

        /** Default visitor method: do nothing.
         */
        @Override
        public void visitTree(JCTree tree) {
        }

        public void validateTree(JCTree tree, boolean checkRaw, boolean isOuter) {
            if (tree != null) {
                boolean prevCheckRaw = this.checkRaw;
                this.checkRaw = checkRaw;
                this.isOuter = isOuter;

                try {
                    tree.accept(this);
                    if (checkRaw)
                        checkRaw(tree, env);
                } catch (CompletionFailure ex) {
                    completionError(tree.pos(), ex);
                } finally {
                    this.checkRaw = prevCheckRaw;
                }
            }
        }

        public void validateTrees(List<? extends JCTree> trees, boolean checkRaw, boolean isOuter) {
            for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
                validateTree(l.head, checkRaw, isOuter);
        }
    }

    void checkRaw(JCTree tree, Env<AttrContext> env) {
        if (tree.type.hasTag(CLASS) &&
            !TreeInfo.isDiamond(tree) &&
            !withinAnonConstr(env) &&
            tree.type.isRaw()) {
            lint.logIfEnabled(tree.pos(), LintWarnings.RawClassUse(tree.type, tree.type.tsym.type));
        }
    }
    //where
        private boolean withinAnonConstr(Env<AttrContext> env) {
            return env.enclClass.name.isEmpty() &&
                    env.enclMethod != null && env.enclMethod.name == names.init;
        }

/* *************************************************************************
 * Exception checking
 **************************************************************************/

    /* The following methods treat classes as sets that contain
     * the class itself and all their subclasses
     */

    /** Is given type a subtype of some of the types in given list?
     */
    boolean subset(Type t, List<Type> ts) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (types.isSubtype(t, l.head)) return true;
        return false;
    }

    /** Is given type a subtype or supertype of
     *  some of the types in given list?
     */
    boolean intersects(Type t, List<Type> ts) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (types.isSubtype(t, l.head) || types.isSubtype(l.head, t)) return true;
        return false;
    }

    /** Add type set to given type list, unless it is a subclass of some class
     *  in the list.
     */
    List<Type> incl(Type t, List<Type> ts) {
        return subset(t, ts) ? ts : excl(t, ts).prepend(t);
    }

    /** Remove type set from type set list.
     */
    List<Type> excl(Type t, List<Type> ts) {
        if (ts.isEmpty()) {
            return ts;
        } else {
            List<Type> ts1 = excl(t, ts.tail);
            if (types.isSubtype(ts.head, t)) return ts1;
            else if (ts1 == ts.tail) return ts;
            else return ts1.prepend(ts.head);
        }
    }

    /** Form the union of two type set lists.
     */
    List<Type> union(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = ts1;
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            ts = incl(l.head, ts);
        return ts;
    }

    /** Form the difference of two type lists.
     */
    List<Type> diff(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = ts1;
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            ts = excl(l.head, ts);
        return ts;
    }

    /** Form the intersection of two type lists.
     */
    public List<Type> intersect(List<Type> ts1, List<Type> ts2) {
        List<Type> ts = List.nil();
        for (List<Type> l = ts1; l.nonEmpty(); l = l.tail)
            if (subset(l.head, ts2)) ts = incl(l.head, ts);
        for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
            if (subset(l.head, ts1)) ts = incl(l.head, ts);
        return ts;
    }

    /** Is exc an exception symbol that need not be declared?
     */
    boolean isUnchecked(ClassSymbol exc) {
        return
            exc.kind == ERR ||
            exc.isSubClass(syms.errorType.tsym, types) ||
            exc.isSubClass(syms.runtimeExceptionType.tsym, types);
    }

    /** Is exc an exception type that need not be declared?
     */
    boolean isUnchecked(Type exc) {
        return
            (exc.hasTag(TYPEVAR)) ? isUnchecked(types.supertype(exc)) :
            (exc.hasTag(CLASS)) ? isUnchecked((ClassSymbol)exc.tsym) :
            exc.hasTag(BOT);
    }

    boolean isChecked(Type exc) {
        return !isUnchecked(exc);
    }

    /** Same, but handling completion failures.
     */
    boolean isUnchecked(DiagnosticPosition pos, Type exc) {
        try {
            return isUnchecked(exc);
        } catch (CompletionFailure ex) {
            completionError(pos, ex);
            return true;
        }
    }

    /** Is exc handled by given exception list?
     */
    boolean isHandled(Type exc, List<Type> handled) {
        return isUnchecked(exc) || subset(exc, handled);
    }

    /** Return all exceptions in thrown list that are not in handled list.
     *  @param thrown     The list of thrown exceptions.
     *  @param handled    The list of handled exceptions.
     */
    List<Type> unhandled(List<Type> thrown, List<Type> handled) {
        List<Type> unhandled = List.nil();
        for (List<Type> l = thrown; l.nonEmpty(); l = l.tail)
            if (!isHandled(l.head, handled)) unhandled = unhandled.prepend(l.head);
        return unhandled;
    }

/* *************************************************************************
 * Overriding/Implementation checking
 **************************************************************************/

    /** The level of access protection given by a flag set,
     *  where PRIVATE is highest and PUBLIC is lowest.
     */
    static int protection(long flags) {
        switch ((short)(flags & AccessFlags)) {
        case PRIVATE: return 3;
        case PROTECTED: return 1;
        default:
        case PUBLIC: return 0;
        case 0: return 2;
        }
    }

    /** A customized "cannot override" error message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Fragment cannotOverride(MethodSymbol m, MethodSymbol other) {
        Symbol mloc = m.location();
        Symbol oloc = other.location();

        if ((other.owner.flags() & INTERFACE) == 0)
            return Fragments.CantOverride(m, mloc, other, oloc);
        else if ((m.owner.flags() & INTERFACE) == 0)
            return Fragments.CantImplement(m, mloc, other, oloc);
        else
            return Fragments.ClashesWith(m, mloc, other, oloc);
    }

    /** A customized "override" warning message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Fragment uncheckedOverrides(MethodSymbol m, MethodSymbol other) {
        Symbol mloc = m.location();
        Symbol oloc = other.location();

        if ((other.owner.flags() & INTERFACE) == 0)
            return Fragments.UncheckedOverride(m, mloc, other, oloc);
        else if ((m.owner.flags() & INTERFACE) == 0)
            return Fragments.UncheckedImplement(m, mloc, other, oloc);
        else
            return Fragments.UncheckedClashWith(m, mloc, other, oloc);
    }

    /** A customized "override" warning message.
     *  @param m      The overriding method.
     *  @param other  The overridden method.
     *  @return       An internationalized string.
     */
    Fragment varargsOverrides(MethodSymbol m, MethodSymbol other) {
        Symbol mloc = m.location();
        Symbol oloc = other.location();

        if ((other.owner.flags() & INTERFACE) == 0)
            return Fragments.VarargsOverride(m, mloc, other, oloc);
        else  if ((m.owner.flags() & INTERFACE) == 0)
            return Fragments.VarargsImplement(m, mloc, other, oloc);
        else
            return Fragments.VarargsClashWith(m, mloc, other, oloc);
    }

    /** Check that this method conforms with overridden method 'other'.
     *  where `origin' is the class where checking started.
     *  Complications:
     *  (1) Do not check overriding of synthetic methods
     *      (reason: they might be final).
     *      todo: check whether this is still necessary.
     *  (2) Admit the case where an interface proxy throws fewer exceptions
     *      than the method it implements. Augment the proxy methods with the
     *      undeclared exceptions in this case.
     *  (3) When generics are enabled, admit the case where an interface proxy
     *      has a result type
     *      extended by the result type of the method it implements.
     *      Change the proxies result type to the smaller type in this case.
     *
     *  @param tree         The tree from which positions
     *                      are extracted for errors.
     *  @param m            The overriding method.
     *  @param other        The overridden method.
     *  @param origin       The class of which the overriding method
     *                      is a member.
     */
    void checkOverride(JCTree tree,
                       MethodSymbol m,
                       MethodSymbol other,
                       ClassSymbol origin) {
        // Don't check overriding of synthetic methods or by bridge methods.
        if ((m.flags() & (SYNTHETIC|BRIDGE)) != 0 || (other.flags() & SYNTHETIC) != 0) {
            return;
        }

        // Error if static method overrides instance method (JLS 8.4.8.2).
        if ((m.flags() & STATIC) != 0 &&
                   (other.flags() & STATIC) == 0) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree),
                      Errors.OverrideStatic(cannotOverride(m, other)));
            m.flags_field |= BAD_OVERRIDE;
            return;
        }

        // Error if instance method overrides static or final
        // method (JLS 8.4.8.1).
        if ((other.flags() & FINAL) != 0 ||
                 (m.flags() & STATIC) == 0 &&
                 (other.flags() & STATIC) != 0) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree),
                      Errors.OverrideMeth(cannotOverride(m, other),
                                          asFlagSet(other.flags() & (FINAL | STATIC))));
            m.flags_field |= BAD_OVERRIDE;
            return;
        }

        if ((m.owner.flags() & ANNOTATION) != 0) {
            // handled in validateAnnotationMethod
            return;
        }

        // Error if overriding method has weaker access (JLS 8.4.8.3).
        if (protection(m.flags()) > protection(other.flags())) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree),
                      (other.flags() & AccessFlags) == 0 ?
                              Errors.OverrideWeakerAccess(cannotOverride(m, other),
                                                          "package") :
                              Errors.OverrideWeakerAccess(cannotOverride(m, other),
                                                          asFlagSet(other.flags() & AccessFlags)));
            m.flags_field |= BAD_OVERRIDE;
            return;
        }

        if (shouldCheckPreview(m, other, origin)) {
            checkPreview(TreeInfo.diagnosticPositionFor(m, tree),
                         m, origin.type, other);
        }

        Type mt = types.memberType(origin.type, m);
        Type ot = types.memberType(origin.type, other);
        // Error if overriding result type is different
        // (or, in the case of generics mode, not a subtype) of
        // overridden result type. We have to rename any type parameters
        // before comparing types.
        List<Type> mtvars = mt.getTypeArguments();
        List<Type> otvars = ot.getTypeArguments();
        Type mtres = mt.getReturnType();
        Type otres = types.subst(ot.getReturnType(), otvars, mtvars);

        overrideWarner.clear();
        boolean resultTypesOK =
            types.returnTypeSubstitutable(mt, ot, otres, overrideWarner);
        if (!resultTypesOK) {
            if ((m.flags() & STATIC) != 0 && (other.flags() & STATIC) != 0) {
                log.error(TreeInfo.diagnosticPositionFor(m, tree),
                          Errors.OverrideIncompatibleRet(Fragments.CantHide(m, m.location(), other,
                                        other.location()), mtres, otres));
                m.flags_field |= BAD_OVERRIDE;
            } else {
                log.error(TreeInfo.diagnosticPositionFor(m, tree),
                          Errors.OverrideIncompatibleRet(cannotOverride(m, other), mtres, otres));
                m.flags_field |= BAD_OVERRIDE;
            }
            return;
        } else if (overrideWarner.hasNonSilentLint(LintCategory.UNCHECKED)) {
            warnUnchecked(TreeInfo.diagnosticPositionFor(m, tree),
                    LintWarnings.OverrideUncheckedRet(uncheckedOverrides(m, other), mtres, otres));
        }

        // Error if overriding method throws an exception not reported
        // by overridden method.
        List<Type> otthrown = types.subst(ot.getThrownTypes(), otvars, mtvars);
        List<Type> unhandledErased = unhandled(mt.getThrownTypes(), types.erasure(otthrown));
        List<Type> unhandledUnerased = unhandled(mt.getThrownTypes(), otthrown);
        if (unhandledErased.nonEmpty()) {
            log.error(TreeInfo.diagnosticPositionFor(m, tree),
                      Errors.OverrideMethDoesntThrow(cannotOverride(m, other), unhandledUnerased.head));
            m.flags_field |= BAD_OVERRIDE;
            return;
        }
        else if (unhandledUnerased.nonEmpty()) {
            warnUnchecked(TreeInfo.diagnosticPositionFor(m, tree),
                          LintWarnings.OverrideUncheckedThrown(cannotOverride(m, other), unhandledUnerased.head));
            return;
        }

        // Optional warning if varargs don't agree
        if ((((m.flags() ^ other.flags()) & Flags.VARARGS) != 0)) {
            lint.logIfEnabled(TreeInfo.diagnosticPositionFor(m, tree),
                        ((m.flags() & Flags.VARARGS) != 0)
                        ? LintWarnings.OverrideVarargsMissing(varargsOverrides(m, other))
                        : LintWarnings.OverrideVarargsExtra(varargsOverrides(m, other)));
        }

        // Warn if instance method overrides bridge method (compiler spec ??)
        if ((other.flags() & BRIDGE) != 0) {
            log.warning(TreeInfo.diagnosticPositionFor(m, tree),
                        Warnings.OverrideBridge(uncheckedOverrides(m, other)));
        }

        // Warn if a deprecated method overridden by a non-deprecated one.
        if (!isDeprecatedOverrideIgnorable(other, origin)) {
            Lint prevLint = setLint(lint.augment(m));
            try {
                checkDeprecated(() -> TreeInfo.diagnosticPositionFor(m, tree), m, other);
            } finally {
                setLint(prevLint);
            }
        }
    }
    // where
        private boolean shouldCheckPreview(MethodSymbol m, MethodSymbol other, ClassSymbol origin) {
            if (m.owner != origin ||
                //performance - only do the expensive checks when the overridden method is a Preview API:
                ((other.flags() & PREVIEW_API) == 0 &&
                 (other.owner.flags() & PREVIEW_API) == 0)) {
                return false;
            }

            for (Symbol s : types.membersClosure(origin.type, false).getSymbolsByName(m.name)) {
                if (m != s && m.overrides(s, origin, types, false)) {
                    //only produce preview warnings or errors if "m" immediatelly overrides "other"
                    //without intermediate overriding methods:
                    return s == other;
                }
            }

            return false;
        }
        private boolean isDeprecatedOverrideIgnorable(MethodSymbol m, ClassSymbol origin) {
            // If the method, m, is defined in an interface, then ignore the issue if the method
            // is only inherited via a supertype and also implemented in the supertype,
            // because in that case, we will rediscover the issue when examining the method
            // in the supertype.
            // If the method, m, is not defined in an interface, then the only time we need to
            // address the issue is when the method is the supertype implementation: any other
            // case, we will have dealt with when examining the supertype classes
            ClassSymbol mc = m.enclClass();
            Type st = types.supertype(origin.type);
            if (!st.hasTag(CLASS))
                return true;
            MethodSymbol stimpl = m.implementation((ClassSymbol)st.tsym, types, false);

            if (mc != null && ((mc.flags() & INTERFACE) != 0)) {
                List<Type> intfs = types.interfaces(origin.type);
                return (intfs.contains(mc.type) ? false : (stimpl != null));
            }
            else
                return (stimpl != m);
        }


    // used to check if there were any unchecked conversions
    Warner overrideWarner = new Warner();

    /** Check that a class does not inherit two concrete methods
     *  with the same signature.
     *  @param pos          Position to be used for error reporting.
     *  @param site         The class type to be checked.
     */
    public void checkCompatibleConcretes(DiagnosticPosition pos, Type site) {
        Type sup = types.supertype(site);
        if (!sup.hasTag(CLASS)) return;

        for (Type t1 = sup;
             t1.hasTag(CLASS) && t1.tsym.type.isParameterized();
             t1 = types.supertype(t1)) {
            for (Symbol s1 : t1.tsym.members().getSymbols(NON_RECURSIVE)) {
                if (s1.kind != MTH ||
                    (s1.flags() & (STATIC|SYNTHETIC|BRIDGE)) != 0 ||
                    !s1.isInheritedIn(site.tsym, types) ||
                    ((MethodSymbol)s1).implementation(site.tsym,
                                                      types,
                                                      true) != s1)
                    continue;
                Type st1 = types.memberType(t1, s1);
                int s1ArgsLength = st1.getParameterTypes().length();
                if (st1 == s1.type) continue;

                for (Type t2 = sup;
                     t2.hasTag(CLASS);
                     t2 = types.supertype(t2)) {
                    for (Symbol s2 : t2.tsym.members().getSymbolsByName(s1.name)) {
                        if (s2 == s1 ||
                            s2.kind != MTH ||
                            (s2.flags() & (STATIC|SYNTHETIC|BRIDGE)) != 0 ||
                            s2.type.getParameterTypes().length() != s1ArgsLength ||
                            !s2.isInheritedIn(site.tsym, types) ||
                            ((MethodSymbol)s2).implementation(site.tsym,
                                                              types,
                                                              true) != s2)
                            continue;
                        Type st2 = types.memberType(t2, s2);
                        if (types.overrideEquivalent(st1, st2))
                            log.error(pos,
                                      Errors.ConcreteInheritanceConflict(s1, t1, s2, t2, sup));
                    }
                }
            }
        }
    }

    /** Check that classes (or interfaces) do not each define an abstract
     *  method with same name and arguments but incompatible return types.
     *  @param pos          Position to be used for error reporting.
     *  @param t1           The first argument type.
     *  @param t2           The second argument type.
     */
    public boolean checkCompatibleAbstracts(DiagnosticPosition pos,
                                            Type t1,
                                            Type t2,
                                            Type site) {
        if ((site.tsym.flags() & COMPOUND) != 0) {
            // special case for intersections: need to eliminate wildcards in supertypes
            t1 = types.capture(t1);
            t2 = types.capture(t2);
        }
        return firstIncompatibility(pos, t1, t2, site) == null;
    }

    /** Return the first method which is defined with same args
     *  but different return types in two given interfaces, or null if none
     *  exists.
     *  @param t1     The first type.
     *  @param t2     The second type.
     *  @param site   The most derived type.
     *  @return symbol from t2 that conflicts with one in t1.
     */
    private Symbol firstIncompatibility(DiagnosticPosition pos, Type t1, Type t2, Type site) {
        Map<TypeSymbol,Type> interfaces1 = new HashMap<>();
        closure(t1, interfaces1);
        Map<TypeSymbol,Type> interfaces2;
        if (t1 == t2)
            interfaces2 = interfaces1;
        else
            closure(t2, interfaces1, interfaces2 = new HashMap<>());

        for (Type t3 : interfaces1.values()) {
            for (Type t4 : interfaces2.values()) {
                Symbol s = firstDirectIncompatibility(pos, t3, t4, site);
                if (s != null) return s;
            }
        }
        return null;
    }

    /** Compute all the supertypes of t, indexed by type symbol. */
    private void closure(Type t, Map<TypeSymbol,Type> typeMap) {
        if (!t.hasTag(CLASS)) return;
        if (typeMap.put(t.tsym, t) == null) {
            closure(types.supertype(t), typeMap);
            for (Type i : types.interfaces(t))
                closure(i, typeMap);
        }
    }

    /** Compute all the supertypes of t, indexed by type symbol (except those in typesSkip). */
    private void closure(Type t, Map<TypeSymbol,Type> typesSkip, Map<TypeSymbol,Type> typeMap) {
        if (!t.hasTag(CLASS)) return;
        if (typesSkip.get(t.tsym) != null) return;
        if (typeMap.put(t.tsym, t) == null) {
            closure(types.supertype(t), typesSkip, typeMap);
            for (Type i : types.interfaces(t))
                closure(i, typesSkip, typeMap);
        }
    }

    /** Return the first method in t2 that conflicts with a method from t1. */
    private Symbol firstDirectIncompatibility(DiagnosticPosition pos, Type t1, Type t2, Type site) {
        for (Symbol s1 : t1.tsym.members().getSymbols(NON_RECURSIVE)) {
            Type st1 = null;
            if (s1.kind != MTH || !s1.isInheritedIn(site.tsym, types) ||
                    (s1.flags() & SYNTHETIC) != 0) continue;
            Symbol impl = ((MethodSymbol)s1).implementation(site.tsym, types, false);
            if (impl != null && (impl.flags() & ABSTRACT) == 0) continue;
            for (Symbol s2 : t2.tsym.members().getSymbolsByName(s1.name)) {
                if (s1 == s2) continue;
                if (s2.kind != MTH || !s2.isInheritedIn(site.tsym, types) ||
                        (s2.flags() & SYNTHETIC) != 0) continue;
                if (st1 == null) st1 = types.memberType(t1, s1);
                Type st2 = types.memberType(t2, s2);
                if (types.overrideEquivalent(st1, st2)) {
                    List<Type> tvars1 = st1.getTypeArguments();
                    List<Type> tvars2 = st2.getTypeArguments();
                    Type rt1 = st1.getReturnType();
                    Type rt2 = types.subst(st2.getReturnType(), tvars2, tvars1);
                    boolean compat =
                        types.isSameType(rt1, rt2) ||
                        !rt1.isPrimitiveOrVoid() &&
                        !rt2.isPrimitiveOrVoid() &&
                        (types.covariantReturnType(rt1, rt2, types.noWarnings) ||
                         types.covariantReturnType(rt2, rt1, types.noWarnings)) ||
                         checkCommonOverriderIn(s1,s2,site);
                    if (!compat) {
                        if (types.isSameType(t1, t2)) {
                            log.error(pos, Errors.IncompatibleDiffRetSameType(t1,
                                    s2.name, types.memberType(t2, s2).getParameterTypes()));
                        } else {
                            log.error(pos, Errors.TypesIncompatible(t1, t2,
                                    Fragments.IncompatibleDiffRet(s2.name, types.memberType(t2, s2).getParameterTypes())));
                        }
                        return s2;
                    }
                } else if (checkNameClash((ClassSymbol)site.tsym, s1, s2) &&
                        !checkCommonOverriderIn(s1, s2, site)) {
                    log.error(pos, Errors.NameClashSameErasureNoOverride(
                            s1.name, types.memberType(site, s1).asMethodType().getParameterTypes(), s1.location(),
                            s2.name, types.memberType(site, s2).asMethodType().getParameterTypes(), s2.location()));
                    return s2;
                }
            }
        }
        return null;
    }
    //WHERE
    boolean checkCommonOverriderIn(Symbol s1, Symbol s2, Type site) {
        Map<TypeSymbol,Type> supertypes = new HashMap<>();
        Type st1 = types.memberType(site, s1);
        Type st2 = types.memberType(site, s2);
        closure(site, supertypes);
        for (Type t : supertypes.values()) {
            for (Symbol s3 : t.tsym.members().getSymbolsByName(s1.name)) {
                if (s3 == s1 || s3 == s2 || s3.kind != MTH || (s3.flags() & (BRIDGE|SYNTHETIC)) != 0) continue;
                Type st3 = types.memberType(site,s3);
                if (types.overrideEquivalent(st3, st1) &&
                        types.overrideEquivalent(st3, st2) &&
                        types.returnTypeSubstitutable(st3, st1) &&
                        types.returnTypeSubstitutable(st3, st2)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Check that a given method conforms with any method it overrides.
     *  @param tree         The tree from which positions are extracted
     *                      for errors.
     *  @param m            The overriding method.
     */
    void checkOverride(Env<AttrContext> env, JCMethodDecl tree, MethodSymbol m) {
        ClassSymbol origin = (ClassSymbol)m.owner;
        if ((origin.flags() & ENUM) != 0 && names.finalize.equals(m.name)) {
            if (m.overrides(syms.enumFinalFinalize, origin, types, false)) {
                log.error(tree.pos(), Errors.EnumNoFinalize);
                return;
            }
        }
        if (allowRecords && origin.isRecord()) {
            // let's find out if this is a user defined accessor in which case the @Override annotation is acceptable
            Optional<? extends RecordComponent> recordComponent = origin.getRecordComponents().stream()
                    .filter(rc -> rc.accessor == tree.sym && (rc.accessor.flags_field & GENERATED_MEMBER) == 0).findFirst();
            if (recordComponent.isPresent()) {
                return;
            }
        }

        for (Type t = origin.type; t.hasTag(CLASS);
             t = types.supertype(t)) {
            if (t != origin.type) {
                checkOverride(tree, t, origin, m);
            }
            for (Type t2 : types.interfaces(t)) {
                checkOverride(tree, t2, origin, m);
            }
        }

        final boolean explicitOverride = m.attribute(syms.overrideType.tsym) != null;
        // Check if this method must override a super method due to being annotated with @Override
        // or by virtue of being a member of a diamond inferred anonymous class. Latter case is to
        // be treated "as if as they were annotated" with @Override.
        boolean mustOverride = explicitOverride ||
                (env.info.isAnonymousDiamond && !m.isConstructor() && !m.isPrivate());
        if (mustOverride && !isOverrider(m)) {
            DiagnosticPosition pos = tree.pos();
            for (JCAnnotation a : tree.getModifiers().annotations) {
                if (a.annotationType.type.tsym == syms.overrideType.tsym) {
                    pos = a.pos();
                    break;
                }
            }
            log.error(pos,
                      explicitOverride ? (m.isStatic() ? Errors.StaticMethodsCannotBeAnnotatedWithOverride : Errors.MethodDoesNotOverrideSuperclass) :
                                Errors.AnonymousDiamondMethodDoesNotOverrideSuperclass(Fragments.DiamondAnonymousMethodsImplicitlyOverride));
        }
    }

    void checkOverride(JCTree tree, Type site, ClassSymbol origin, MethodSymbol m) {
        TypeSymbol c = site.tsym;
        for (Symbol sym : c.members().getSymbolsByName(m.name)) {
            if (m.overrides(sym, origin, types, false)) {
                if ((sym.flags() & ABSTRACT) == 0) {
                    checkOverride(tree, m, (MethodSymbol)sym, origin);
                }
            }
        }
    }

    private Predicate<Symbol> equalsHasCodeFilter = s -> MethodSymbol.implementation_filter.test(s) &&
            (s.flags() & BAD_OVERRIDE) == 0;

    public void checkClassOverrideEqualsAndHashIfNeeded(DiagnosticPosition pos,
            ClassSymbol someClass) {
        /* At present, annotations cannot possibly have a method that is override
         * equivalent with Object.equals(Object) but in any case the condition is
         * fine for completeness.
         */
        if (someClass == (ClassSymbol)syms.objectType.tsym ||
            someClass.isInterface() || someClass.isEnum() ||
            (someClass.flags() & ANNOTATION) != 0 ||
            (someClass.flags() & ABSTRACT) != 0) return;
        //anonymous inner classes implementing interfaces need especial treatment
        if (someClass.isAnonymous()) {
            List<Type> interfaces =  types.interfaces(someClass.type);
            if (interfaces != null && !interfaces.isEmpty() &&
                interfaces.head.tsym == syms.comparatorType.tsym) return;
        }
        checkClassOverrideEqualsAndHash(pos, someClass);
    }

    private void checkClassOverrideEqualsAndHash(DiagnosticPosition pos,
            ClassSymbol someClass) {
        if (lint.isEnabled(LintCategory.OVERRIDES)) {
            MethodSymbol equalsAtObject = (MethodSymbol)syms.objectType
                    .tsym.members().findFirst(names.equals);
            MethodSymbol hashCodeAtObject = (MethodSymbol)syms.objectType
                    .tsym.members().findFirst(names.hashCode);
            MethodSymbol equalsImpl = types.implementation(equalsAtObject,
                    someClass, false, equalsHasCodeFilter);
            boolean overridesEquals = equalsImpl != null &&
                                      equalsImpl.owner == someClass;
            boolean overridesHashCode = types.implementation(hashCodeAtObject,
                someClass, false, equalsHasCodeFilter) != hashCodeAtObject;

            if (overridesEquals && !overridesHashCode) {
                log.warning(pos,
                            LintWarnings.OverrideEqualsButNotHashcode(someClass));
            }
        }
    }

    public void checkHasMain(DiagnosticPosition pos, ClassSymbol c) {
        boolean found = false;

        for (Symbol sym : c.members().getSymbolsByName(names.main)) {
            if (sym.kind == MTH && (sym.flags() & PRIVATE) == 0) {
                MethodSymbol meth = (MethodSymbol)sym;
                if (!types.isSameType(meth.getReturnType(), syms.voidType)) {
                    continue;
                }
                if (meth.params.isEmpty()) {
                    found = true;
                    break;
                }
                if (meth.params.size() != 1) {
                    continue;
                }
                if (!types.isSameType(meth.params.head.type, types.makeArrayType(syms.stringType))) {
                    continue;
                }

                found = true;
                break;
            }
        }

        if (!found) {
            log.error(pos, Errors.ImplicitClassDoesNotHaveMainMethod);
        }
    }

    public void checkModuleName (JCModuleDecl tree) {
        Name moduleName = tree.sym.name;
        Assert.checkNonNull(moduleName);
        if (lint.isEnabled(LintCategory.MODULE)) {
            JCExpression qualId = tree.qualId;
            while (qualId != null) {
                Name componentName;
                DiagnosticPosition pos;
                switch (qualId.getTag()) {
                    case SELECT:
                        JCFieldAccess selectNode = ((JCFieldAccess) qualId);
                        componentName = selectNode.name;
                        pos = selectNode.pos();
                        qualId = selectNode.selected;
                        break;
                    case IDENT:
                        componentName = ((JCIdent) qualId).name;
                        pos = qualId.pos();
                        qualId = null;
                        break;
                    default:
                        throw new AssertionError("Unexpected qualified identifier: " + qualId.toString());
                }
                if (componentName != null) {
                    String moduleNameComponentString = componentName.toString();
                    int nameLength = moduleNameComponentString.length();
                    if (nameLength > 0 && Character.isDigit(moduleNameComponentString.charAt(nameLength - 1))) {
                        log.warning(pos, LintWarnings.PoorChoiceForModuleName(componentName));
                    }
                }
            }
        }
    }

    private boolean checkNameClash(ClassSymbol origin, Symbol s1, Symbol s2) {
        ClashFilter cf = new ClashFilter(origin.type);
        return (cf.test(s1) &&
                cf.test(s2) &&
                types.hasSameArgs(s1.erasure(types), s2.erasure(types)));
    }


    /** Check that all abstract members of given class have definitions.
     *  @param pos          Position to be used for error reporting.
     *  @param c            The class.
     */
    void checkAllDefined(DiagnosticPosition pos, ClassSymbol c) {
        MethodSymbol undef = types.firstUnimplementedAbstract(c);
        if (undef != null) {
            MethodSymbol undef1 =
                new MethodSymbol(undef.flags(), undef.name,
                                 types.memberType(c.type, undef), undef.owner);
            log.error(pos,
                      Errors.DoesNotOverrideAbstract(c, undef1, undef1.location()));
        }
    }

    void checkNonCyclicDecl(JCClassDecl tree) {
        CycleChecker cc = new CycleChecker();
        cc.scan(tree);
        if (!cc.errorFound && !cc.partialCheck) {
            tree.sym.flags_field |= ACYCLIC;
        }
    }

    class CycleChecker extends TreeScanner {

        Set<Symbol> seenClasses = new HashSet<>();
        boolean errorFound = false;
        boolean partialCheck = false;

        private void checkSymbol(DiagnosticPosition pos, Symbol sym) {
            if (sym != null && sym.kind == TYP) {
                Env<AttrContext> classEnv = enter.getEnv((TypeSymbol)sym);
                if (classEnv != null) {
                    DiagnosticSource prevSource = log.currentSource();
                    try {
                        log.useSource(classEnv.toplevel.sourcefile);
                        scan(classEnv.tree);
                    }
                    finally {
                        log.useSource(prevSource.getFile());
                    }
                } else if (sym.kind == TYP) {
                    checkClass(pos, sym, List.nil());
                }
            } else if (sym == null || sym.kind != PCK) {
                //not completed yet
                partialCheck = true;
            }
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            super.visitSelect(tree);
            checkSymbol(tree.pos(), tree.sym);
        }

        @Override
        public void visitIdent(JCIdent tree) {
            checkSymbol(tree.pos(), tree.sym);
        }

        @Override
        public void visitTypeApply(JCTypeApply tree) {
            scan(tree.clazz);
        }

        @Override
        public void visitTypeArray(JCArrayTypeTree tree) {
            scan(tree.elemtype);
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            List<JCTree> supertypes = List.nil();
            if (tree.getExtendsClause() != null) {
                supertypes = supertypes.prepend(tree.getExtendsClause());
            }
            if (tree.getImplementsClause() != null) {
                for (JCTree intf : tree.getImplementsClause()) {
                    supertypes = supertypes.prepend(intf);
                }
            }
            checkClass(tree.pos(), tree.sym, supertypes);
        }

        void checkClass(DiagnosticPosition pos, Symbol c, List<JCTree> supertypes) {
            if ((c.flags_field & ACYCLIC) != 0)
                return;
            if (seenClasses.contains(c)) {
                errorFound = true;
                log.error(pos, Errors.CyclicInheritance(c));
                seenClasses.stream()
                  .filter(s -> !s.type.isErroneous())
                  .filter(ClassSymbol.class::isInstance)
                  .map(ClassSymbol.class::cast)
                  .forEach(Check.this::handleCyclic);
            } else if (!c.type.isErroneous()) {
                try {
                    seenClasses.add(c);
                    if (c.type.hasTag(CLASS)) {
                        if (supertypes.nonEmpty()) {
                            scan(supertypes);
                        }
                        else {
                            ClassType ct = (ClassType)c.type;
                            if (ct.supertype_field == null ||
                                    ct.interfaces_field == null) {
                                //not completed yet
                                partialCheck = true;
                                return;
                            }
                            checkSymbol(pos, ct.supertype_field.tsym);
                            for (Type intf : ct.interfaces_field) {
                                checkSymbol(pos, intf.tsym);
                            }
                        }
                        if (c.owner.kind == TYP) {
                            checkSymbol(pos, c.owner);
                        }
                    }
                } finally {
                    seenClasses.remove(c);
                }
            }
        }
    }

    /** Check for cyclic references. Issue an error if the
     *  symbol of the type referred to has a LOCKED flag set.
     *
     *  @param pos      Position to be used for error reporting.
     *  @param t        The type referred to.
     */
    void checkNonCyclic(DiagnosticPosition pos, Type t) {
        checkNonCyclicInternal(pos, t);
    }


    void checkNonCyclic(DiagnosticPosition pos, TypeVar t) {
        checkNonCyclic1(pos, t, List.nil());
    }

    private void checkNonCyclic1(DiagnosticPosition pos, Type t, List<TypeVar> seen) {
        final TypeVar tv;
        if  (t.hasTag(TYPEVAR) && (t.tsym.flags() & UNATTRIBUTED) != 0)
            return;
        if (seen.contains(t)) {
            tv = (TypeVar)t;
            tv.setUpperBound(types.createErrorType(t));
            log.error(pos, Errors.CyclicInheritance(t));
        } else if (t.hasTag(TYPEVAR)) {
            tv = (TypeVar)t;
            seen = seen.prepend(tv);
            for (Type b : types.getBounds(tv))
                checkNonCyclic1(pos, b, seen);
        }
    }

    /** Check for cyclic references. Issue an error if the
     *  symbol of the type referred to has a LOCKED flag set.
     *
     *  @param pos      Position to be used for error reporting.
     *  @param t        The type referred to.
     *  @return        True if the check completed on all attributed classes
     */
    private boolean checkNonCyclicInternal(DiagnosticPosition pos, Type t) {
        boolean complete = true; // was the check complete?
        //- System.err.println("checkNonCyclicInternal("+t+");");//DEBUG
        Symbol c = t.tsym;
        if ((c.flags_field & ACYCLIC) != 0) return true;

        if ((c.flags_field & LOCKED) != 0) {
            log.error(pos, Errors.CyclicInheritance(c));
            handleCyclic((ClassSymbol)c);
        } else if (!c.type.isErroneous()) {
            try {
                c.flags_field |= LOCKED;
                if (c.type.hasTag(CLASS)) {
                    ClassType clazz = (ClassType)c.type;
                    if (clazz.interfaces_field != null)
                        for (List<Type> l=clazz.interfaces_field; l.nonEmpty(); l=l.tail)
                            complete &= checkNonCyclicInternal(pos, l.head);
                    if (clazz.supertype_field != null) {
                        Type st = clazz.supertype_field;
                        if (st != null && st.hasTag(CLASS))
                            complete &= checkNonCyclicInternal(pos, st);
                    }
                    if (c.owner.kind == TYP)
                        complete &= checkNonCyclicInternal(pos, c.owner.type);
                }
            } finally {
                c.flags_field &= ~LOCKED;
            }
        }
        if (complete)
            complete = ((c.flags_field & UNATTRIBUTED) == 0) && c.isCompleted();
        if (complete) c.flags_field |= ACYCLIC;
        return complete;
    }

    /** Handle finding an inheritance cycle on a class by setting
     *  the class' and its supertypes' types to the error type.
     **/
    private void handleCyclic(ClassSymbol c) {
        for (List<Type> l=types.interfaces(c.type); l.nonEmpty(); l=l.tail)
            l.head = types.createErrorType((ClassSymbol)l.head.tsym, Type.noType);
        Type st = types.supertype(c.type);
        if (st.hasTag(CLASS))
            ((ClassType)c.type).supertype_field = types.createErrorType((ClassSymbol)st.tsym, Type.noType);
        c.type = types.createErrorType(c, c.type);
        c.flags_field |= ACYCLIC;
    }

    /** Check that all methods which implement some
     *  method conform to the method they implement.
     *  @param tree         The class definition whose members are checked.
     */
    void checkImplementations(JCClassDecl tree) {
        checkImplementations(tree, tree.sym, tree.sym);
    }
    //where
        /** Check that all methods which implement some
         *  method in `ic' conform to the method they implement.
         */
        void checkImplementations(JCTree tree, ClassSymbol origin, ClassSymbol ic) {
            for (List<Type> l = types.closure(ic.type); l.nonEmpty(); l = l.tail) {
                ClassSymbol lc = (ClassSymbol)l.head.tsym;
                if ((lc.flags() & ABSTRACT) != 0) {
                    for (Symbol sym : lc.members().getSymbols(NON_RECURSIVE)) {
                        if (sym.kind == MTH &&
                            (sym.flags() & (STATIC|ABSTRACT)) == ABSTRACT) {
                            MethodSymbol absmeth = (MethodSymbol)sym;
                            MethodSymbol implmeth = absmeth.implementation(origin, types, false);
                            if (implmeth != null && implmeth != absmeth &&
                                (implmeth.owner.flags() & INTERFACE) ==
                                (origin.flags() & INTERFACE)) {
                                // don't check if implmeth is in a class, yet
                                // origin is an interface. This case arises only
                                // if implmeth is declared in Object. The reason is
                                // that interfaces really don't inherit from
                                // Object it's just that the compiler represents
                                // things that way.
                                checkOverride(tree, implmeth, absmeth, origin);
                            }
                        }
                    }
                }
            }
        }

    /** Check that all abstract methods implemented by a class are
     *  mutually compatible.
     *  @param pos          Position to be used for error reporting.
     *  @param c            The class whose interfaces are checked.
     */
    void checkCompatibleSupertypes(DiagnosticPosition pos, Type c) {
        List<Type> supertypes = types.interfaces(c);
        Type supertype = types.supertype(c);
        if (supertype.hasTag(CLASS) &&
            (supertype.tsym.flags() & ABSTRACT) != 0)
            supertypes = supertypes.prepend(supertype);
        for (List<Type> l = supertypes; l.nonEmpty(); l = l.tail) {
            if (!l.head.getTypeArguments().isEmpty() &&
                !checkCompatibleAbstracts(pos, l.head, l.head, c))
                return;
            for (List<Type> m = supertypes; m != l; m = m.tail)
                if (!checkCompatibleAbstracts(pos, l.head, m.head, c))
                    return;
        }
        checkCompatibleConcretes(pos, c);
    }

    /** Check that all non-override equivalent methods accessible from 'site'
     *  are mutually compatible (JLS 8.4.8/9.4.1).
     *
     *  @param pos  Position to be used for error reporting.
     *  @param site The class whose methods are checked.
     *  @param sym  The method symbol to be checked.
     */
    void checkOverrideClashes(DiagnosticPosition pos, Type site, MethodSymbol sym) {
         ClashFilter cf = new ClashFilter(site);
        //for each method m1 that is overridden (directly or indirectly)
        //by method 'sym' in 'site'...

        ArrayList<Symbol> symbolsByName = new ArrayList<>();
        types.membersClosure(site, false).getSymbolsByName(sym.name, cf).forEach(symbolsByName::add);
        for (Symbol m1 : symbolsByName) {
            if (!sym.overrides(m1, site.tsym, types, false)) {
                continue;
            }

            //...check each method m2 that is a member of 'site'
            for (Symbol m2 : symbolsByName) {
                if (m2 == m1) continue;
                //if (i) the signature of 'sym' is not a subsignature of m1 (seen as
                //a member of 'site') and (ii) m1 has the same erasure as m2, issue an error
                if (!types.isSubSignature(sym.type, types.memberType(site, m2)) &&
                        types.hasSameArgs(m2.erasure(types), m1.erasure(types))) {
                    sym.flags_field |= CLASH;
                    if (m1 == sym) {
                        log.error(pos, Errors.NameClashSameErasureNoOverride(
                            m1.name, types.memberType(site, m1).asMethodType().getParameterTypes(), m1.location(),
                            m2.name, types.memberType(site, m2).asMethodType().getParameterTypes(), m2.location()));
                    } else {
                        ClassType ct = (ClassType)site;
                        String kind = ct.isInterface() ? "interface" : "class";
                        log.error(pos, Errors.NameClashSameErasureNoOverride1(
                            kind,
                            ct.tsym.name,
                            m1.name,
                            types.memberType(site, m1).asMethodType().getParameterTypes(),
                            m1.location(),
                            m2.name,
                            types.memberType(site, m2).asMethodType().getParameterTypes(),
                            m2.location()));
                    }
                    return;
                }
            }
        }
    }

    /** Check that all static methods accessible from 'site' are
     *  mutually compatible (JLS 8.4.8).
     *
     *  @param pos  Position to be used for error reporting.
     *  @param site The class whose methods are checked.
     *  @param sym  The method symbol to be checked.
     */
    void checkHideClashes(DiagnosticPosition pos, Type site, MethodSymbol sym) {
        ClashFilter cf = new ClashFilter(site);
        //for each method m1 that is a member of 'site'...
        for (Symbol s : types.membersClosure(site, true).getSymbolsByName(sym.name, cf)) {
            //if (i) the signature of 'sym' is not a subsignature of m1 (seen as
            //a member of 'site') and (ii) 'sym' has the same erasure as m1, issue an error
            if (!types.isSubSignature(sym.type, types.memberType(site, s))) {
                if (types.hasSameArgs(s.erasure(types), sym.erasure(types))) {
                    log.error(pos,
                              Errors.NameClashSameErasureNoHide(sym, sym.location(), s, s.location()));
                    return;
                }
            }
         }
     }

     //where
     private class ClashFilter implements Predicate<Symbol> {

         Type site;

         ClashFilter(Type site) {
             this.site = site;
         }

         boolean shouldSkip(Symbol s) {
             return (s.flags() & CLASH) != 0 &&
                s.owner == site.tsym;
         }

         @Override
         public boolean test(Symbol s) {
             return s.kind == MTH &&
                     (s.flags() & SYNTHETIC) == 0 &&
                     !shouldSkip(s) &&
                     s.isInheritedIn(site.tsym, types) &&
                     !s.isConstructor();
         }
     }

    void checkDefaultMethodClashes(DiagnosticPosition pos, Type site) {
        DefaultMethodClashFilter dcf = new DefaultMethodClashFilter(site);
        for (Symbol m : types.membersClosure(site, false).getSymbols(dcf)) {
            Assert.check(m.kind == MTH);
            List<MethodSymbol> prov = types.interfaceCandidates(site, (MethodSymbol)m);
            if (prov.size() > 1) {
                ListBuffer<Symbol> abstracts = new ListBuffer<>();
                ListBuffer<Symbol> defaults = new ListBuffer<>();
                for (MethodSymbol provSym : prov) {
                    if ((provSym.flags() & DEFAULT) != 0) {
                        defaults = defaults.append(provSym);
                    } else if ((provSym.flags() & ABSTRACT) != 0) {
                        abstracts = abstracts.append(provSym);
                    }
                    if (defaults.nonEmpty() && defaults.size() + abstracts.size() >= 2) {
                        //strong semantics - issue an error if two sibling interfaces
                        //have two override-equivalent defaults - or if one is abstract
                        //and the other is default
                        Fragment diagKey;
                        Symbol s1 = defaults.first();
                        Symbol s2;
                        if (defaults.size() > 1) {
                            s2 = defaults.toList().tail.head;
                            diagKey = Fragments.IncompatibleUnrelatedDefaults(Kinds.kindName(site.tsym), site,
                                    m.name, types.memberType(site, m).getParameterTypes(),
                                    s1.location(), s2.location());

                        } else {
                            s2 = abstracts.first();
                            diagKey = Fragments.IncompatibleAbstractDefault(Kinds.kindName(site.tsym), site,
                                    m.name, types.memberType(site, m).getParameterTypes(),
                                    s1.location(), s2.location());
                        }
                        log.error(pos, Errors.TypesIncompatible(s1.location().type, s2.location().type, diagKey));
                        break;
                    }
                }
            }
        }
    }

    //where
     private class DefaultMethodClashFilter implements Predicate<Symbol> {

         Type site;

         DefaultMethodClashFilter(Type site) {
             this.site = site;
         }

         @Override
         public boolean test(Symbol s) {
             return s.kind == MTH &&
                     (s.flags() & DEFAULT) != 0 &&
                     s.isInheritedIn(site.tsym, types) &&
                     !s.isConstructor();
         }
     }

    /** Report warnings for potentially ambiguous method declarations in the given site. */
    void checkPotentiallyAmbiguousOverloads(JCClassDecl tree, Type site) {

        // Skip if warning not enabled
        if (!lint.isEnabled(LintCategory.OVERLOADS))
            return;

        // Gather all of site's methods, including overridden methods, grouped by name (except Object methods)
        List<java.util.List<MethodSymbol>> methodGroups = methodsGroupedByName(site,
            new PotentiallyAmbiguousFilter(site), ArrayList::new);

        // Build the predicate that determines if site is responsible for an ambiguity
        BiPredicate<MethodSymbol, MethodSymbol> responsible = buildResponsiblePredicate(site, methodGroups);

        // Now remove overridden methods from each group, leaving only site's actual members
        methodGroups.forEach(list -> removePreempted(list, (m1, m2) -> m1.overrides(m2, site.tsym, types, false)));

        // Allow site's own declared methods (only) to apply @SuppressWarnings("overloads")
        methodGroups.forEach(list -> list.removeIf(
            m -> m.owner == site.tsym && !lint.augment(m).isEnabled(LintCategory.OVERLOADS)));

        // Warn about ambiguous overload method pairs for which site is responsible
        methodGroups.forEach(list -> compareAndRemove(list, (m1, m2) -> {

            // See if this is an ambiguous overload for which "site" is responsible
            if (!potentiallyAmbiguousOverload(site, m1, m2) || !responsible.test(m1, m2))
                return 0;

            // Locate the warning at one of the methods, if possible
            DiagnosticPosition pos =
                m1.owner == site.tsym ? TreeInfo.diagnosticPositionFor(m1, tree) :
                m2.owner == site.tsym ? TreeInfo.diagnosticPositionFor(m2, tree) :
                tree.pos();

            // Log the warning
            log.warning(pos,
                LintWarnings.PotentiallyAmbiguousOverload(
                    m1.asMemberOf(site, types), m1.location(),
                    m2.asMemberOf(site, types), m2.location()));

            // Don't warn again for either of these two methods
            return FIRST | SECOND;
        }));
    }

    /** Build a predicate that determines, given two methods that are members of the given class,
     *  whether the class should be held "responsible" if the methods are potentially ambiguous.
     *
     *  Sometimes ambiguous methods are unavoidable because they're inherited from a supertype.
     *  For example, any subtype of Spliterator.OfInt will have ambiguities for both
     *  forEachRemaining() and tryAdvance() (in both cases the overloads are IntConsumer and
     *  Consumer&lt;? super Integer&gt;). So we only want to "blame" a class when that class is
     *  itself responsible for creating the ambiguity. We declare that a class C is "responsible"
     *  for the ambiguity between two methods m1 and m2 if there is no direct supertype T of C
     *  such that m1 and m2, or some overrides thereof, both exist in T and are ambiguous in T.
     *  As an optimization, we first check if either method is declared in C and does not override
     *  any other methods; in this case the class is definitely responsible.
     */
    BiPredicate<MethodSymbol, MethodSymbol> buildResponsiblePredicate(Type site,
        List<? extends Collection<MethodSymbol>> methodGroups) {

        // Define the "overrides" predicate
        BiPredicate<MethodSymbol, MethodSymbol> overrides = (m1, m2) -> m1.overrides(m2, site.tsym, types, false);

        // Map each method declared in site to a list of the supertype method(s) it directly overrides
        HashMap<MethodSymbol, ArrayList<MethodSymbol>> overriddenMethodsMap = new HashMap<>();
        methodGroups.forEach(list -> {
            for (MethodSymbol m : list) {

                // Skip methods not declared in site
                if (m.owner != site.tsym)
                    continue;

                // Gather all supertype methods overridden by m, directly or indirectly
                ArrayList<MethodSymbol> overriddenMethods = list.stream()
                  .filter(m2 -> m2 != m && overrides.test(m, m2))
                  .collect(Collectors.toCollection(ArrayList::new));

                // Eliminate non-direct overrides
                removePreempted(overriddenMethods, overrides);

                // Add to map
                overriddenMethodsMap.put(m, overriddenMethods);
            }
        });

        // Build the predicate
        return (m1, m2) -> {

            // Get corresponding supertype methods (if declared in site)
            java.util.List<MethodSymbol> overriddenMethods1 = overriddenMethodsMap.get(m1);
            java.util.List<MethodSymbol> overriddenMethods2 = overriddenMethodsMap.get(m2);

            // Quick check for the case where a method was added by site itself
            if (overriddenMethods1 != null && overriddenMethods1.isEmpty())
                return true;
            if (overriddenMethods2 != null && overriddenMethods2.isEmpty())
                return true;

            // Get each method's corresponding method(s) from supertypes of site
            java.util.List<MethodSymbol> supertypeMethods1 = overriddenMethods1 != null ?
              overriddenMethods1 : Collections.singletonList(m1);
            java.util.List<MethodSymbol> supertypeMethods2 = overriddenMethods2 != null ?
              overriddenMethods2 : Collections.singletonList(m2);

            // See if we can blame some direct supertype instead
            return types.directSupertypes(site).stream()
              .filter(stype -> stype != syms.objectType)
              .map(stype -> stype.tsym.type)                // view supertype in its original form
              .noneMatch(stype -> {
                for (MethodSymbol sm1 : supertypeMethods1) {
                    if (!types.isSubtype(types.erasure(stype), types.erasure(sm1.owner.type)))
                        continue;
                    for (MethodSymbol sm2 : supertypeMethods2) {
                        if (!types.isSubtype(types.erasure(stype), types.erasure(sm2.owner.type)))
                            continue;
                        if (potentiallyAmbiguousOverload(stype, sm1, sm2))
                            return true;
                    }
                }
                return false;
            });
        };
    }

    /** Gather all of site's methods, including overridden methods, grouped and sorted by name,
     *  after applying the given filter.
     */
    <C extends Collection<MethodSymbol>> List<C> methodsGroupedByName(Type site,
            Predicate<Symbol> filter, Supplier<? extends C> groupMaker) {
        Iterable<Symbol> symbols = types.membersClosure(site, false).getSymbols(filter, RECURSIVE);
        return StreamSupport.stream(symbols.spliterator(), false)
          .map(MethodSymbol.class::cast)
          .collect(Collectors.groupingBy(m -> m.name, Collectors.toCollection(groupMaker)))
          .entrySet()
          .stream()
          .sorted(Comparator.comparing(e -> e.getKey().toString()))
          .map(Map.Entry::getValue)
          .collect(List.collector());
    }

    /** Compare elements in a list pair-wise in order to remove some of them.
     *  @param list mutable list of items
     *  @param comparer returns flag bit(s) to remove FIRST and/or SECOND
     */
    <T> void compareAndRemove(java.util.List<T> list, ToIntBiFunction<? super T, ? super T> comparer) {
        for (int index1 = 0; index1 < list.size() - 1; index1++) {
            T item1 = list.get(index1);
            for (int index2 = index1 + 1; index2 < list.size(); index2++) {
                T item2 = list.get(index2);
                int flags = comparer.applyAsInt(item1, item2);
                if ((flags & SECOND) != 0)
                    list.remove(index2--);          // remove item2
                if ((flags & FIRST) != 0) {
                    list.remove(index1--);          // remove item1
                    break;
                }
            }
        }
    }

    /** Remove elements in a list that are preempted by some other element in the list.
     *  @param list mutable list of items
     *  @param preempts decides if one item preempts another, causing the second one to be removed
     */
    <T> void removePreempted(java.util.List<T> list, BiPredicate<? super T, ? super T> preempts) {
        compareAndRemove(list, (item1, item2) -> {
            int flags = 0;
            if (preempts.test(item1, item2))
                flags |= SECOND;
            if (preempts.test(item2, item1))
                flags |= FIRST;
            return flags;
        });
    }

    /** Filters method candidates for the "potentially ambiguous method" check */
    class PotentiallyAmbiguousFilter extends ClashFilter {

        PotentiallyAmbiguousFilter(Type site) {
            super(site);
        }

        @Override
        boolean shouldSkip(Symbol s) {
            return s.owner.type.tsym == syms.objectType.tsym || super.shouldSkip(s);
        }
    }

    /**
      * Report warnings for potentially ambiguous method declarations. Two declarations
      * are potentially ambiguous if they feature two unrelated functional interface
      * in same argument position (in which case, a call site passing an implicit
      * lambda would be ambiguous). This assumes they already have the same name.
      */
    boolean potentiallyAmbiguousOverload(Type site, MethodSymbol msym1, MethodSymbol msym2) {
        Assert.check(msym1.name == msym2.name);
        if (msym1 == msym2)
            return false;
        Type mt1 = types.memberType(site, msym1);
        Type mt2 = types.memberType(site, msym2);
        //if both generic methods, adjust type variables
        if (mt1.hasTag(FORALL) && mt2.hasTag(FORALL) &&
                types.hasSameBounds((ForAll)mt1, (ForAll)mt2)) {
            mt2 = types.subst(mt2, ((ForAll)mt2).tvars, ((ForAll)mt1).tvars);
        }
        //expand varargs methods if needed
        int maxLength = Math.max(mt1.getParameterTypes().length(), mt2.getParameterTypes().length());
        List<Type> args1 = rs.adjustArgs(mt1.getParameterTypes(), msym1, maxLength, true);
        List<Type> args2 = rs.adjustArgs(mt2.getParameterTypes(), msym2, maxLength, true);
        //if arities don't match, exit
        if (args1.length() != args2.length())
            return false;
        boolean potentiallyAmbiguous = false;
        while (args1.nonEmpty() && args2.nonEmpty()) {
            Type s = args1.head;
            Type t = args2.head;
            if (!types.isSubtype(t, s) && !types.isSubtype(s, t)) {
                if (types.isFunctionalInterface(s) && types.isFunctionalInterface(t) &&
                        types.findDescriptorType(s).getParameterTypes().length() > 0 &&
                        types.findDescriptorType(s).getParameterTypes().length() ==
                        types.findDescriptorType(t).getParameterTypes().length()) {
                    potentiallyAmbiguous = true;
                } else {
                    return false;
                }
            }
            args1 = args1.tail;
            args2 = args2.tail;
        }
        return potentiallyAmbiguous;
    }

    // Apply special flag "-XDwarnOnAccessToMembers" which turns on just this particular warning for all types of access
    void checkAccessFromSerializableElement(final JCTree tree, boolean isLambda) {
        final Lint prevLint = setLint(warnOnAnyAccessToMembers ? lint.enable(LintCategory.SERIAL) : lint);
        try {
            if (warnOnAnyAccessToMembers || isLambda)
                checkAccessFromSerializableElementInner(tree, isLambda);
        } finally {
            setLint(prevLint);
        }
    }

    private void checkAccessFromSerializableElementInner(final JCTree tree, boolean isLambda) {
        if (lint.isEnabled(LintCategory.SERIAL)) {
            Symbol sym = TreeInfo.symbol(tree);
            if (!sym.kind.matches(KindSelector.VAL_MTH)) {
                return;
            }

            if (sym.kind == VAR) {
                if ((sym.flags() & PARAMETER) != 0 ||
                    sym.isDirectlyOrIndirectlyLocal() ||
                    sym.name == names._this ||
                    sym.name == names._super) {
                    return;
                }
            }

            if (!types.isSubtype(sym.owner.type, syms.serializableType) &&
                isEffectivelyNonPublic(sym)) {
                if (isLambda) {
                    if (belongsToRestrictedPackage(sym)) {
                        log.warning(tree.pos(),
                                    LintWarnings.AccessToMemberFromSerializableLambda(sym));
                    }
                } else {
                    log.warning(tree.pos(),
                                LintWarnings.AccessToMemberFromSerializableElement(sym));
                }
            }
        }
    }

    private boolean isEffectivelyNonPublic(Symbol sym) {
        if (sym.packge() == syms.rootPackage) {
            return false;
        }

        while (sym.kind != PCK) {
            if ((sym.flags() & PUBLIC) == 0) {
                return true;
            }
            sym = sym.owner;
        }
        return false;
    }

    private boolean belongsToRestrictedPackage(Symbol sym) {
        String fullName = sym.packge().fullname.toString();
        return fullName.startsWith("java.") ||
                fullName.startsWith("javax.") ||
                fullName.startsWith("sun.") ||
                fullName.contains(".internal.");
    }

    /** Check that class c does not implement directly or indirectly
     *  the same parameterized interface with two different argument lists.
     *  @param pos          Position to be used for error reporting.
     *  @param type         The type whose interfaces are checked.
     */
    void checkClassBounds(DiagnosticPosition pos, Type type) {
        checkClassBounds(pos, new HashMap<TypeSymbol,Type>(), type);
    }
//where
        /** Enter all interfaces of type `type' into the hash table `seensofar'
         *  with their class symbol as key and their type as value. Make
         *  sure no class is entered with two different types.
         */
        void checkClassBounds(DiagnosticPosition pos,
                              Map<TypeSymbol,Type> seensofar,
                              Type type) {
            if (type.isErroneous()) return;
            for (List<Type> l = types.interfaces(type); l.nonEmpty(); l = l.tail) {
                Type it = l.head;
                if (type.hasTag(CLASS) && !it.hasTag(CLASS)) continue; // JLS 8.1.5

                Type oldit = seensofar.put(it.tsym, it);
                if (oldit != null) {
                    List<Type> oldparams = oldit.allparams();
                    List<Type> newparams = it.allparams();
                    if (!types.containsTypeEquivalent(oldparams, newparams))
                        log.error(pos,
                                  Errors.CantInheritDiffArg(it.tsym,
                                                            Type.toString(oldparams),
                                                            Type.toString(newparams)));
                }
                checkClassBounds(pos, seensofar, it);
            }
            Type st = types.supertype(type);
            if (type.hasTag(CLASS) && !st.hasTag(CLASS)) return; // JLS 8.1.4
            if (st != Type.noType) checkClassBounds(pos, seensofar, st);
        }

    /** Enter interface into into set.
     *  If it existed already, issue a "repeated interface" error.
     */
    void checkNotRepeated(DiagnosticPosition pos, Type it, Set<Symbol> its) {
        if (its.contains(it.tsym))
            log.error(pos, Errors.RepeatedInterface);
        else {
            its.add(it.tsym);
        }
    }

/* *************************************************************************
 * Check annotations
 **************************************************************************/

    /**
     * Recursively validate annotations values
     */
    void validateAnnotationTree(JCTree tree) {
        class AnnotationValidator extends TreeScanner {
            @Override
            public void visitAnnotation(JCAnnotation tree) {
                if (!tree.type.isErroneous() && tree.type.tsym.isAnnotationType()) {
                    super.visitAnnotation(tree);
                    validateAnnotation(tree);
                }
            }
        }
        tree.accept(new AnnotationValidator());
    }

    /**
     *  {@literal
     *  Annotation types are restricted to primitives, String, an
     *  enum, an annotation, Class, Class<?>, Class<? extends
     *  Anything>, arrays of the preceding.
     *  }
     */
    void validateAnnotationType(JCTree restype) {
        // restype may be null if an error occurred, so don't bother validating it
        if (restype != null) {
            validateAnnotationType(restype.pos(), restype.type);
        }
    }

    void validateAnnotationType(DiagnosticPosition pos, Type type) {
        if (type.isPrimitive()) return;
        if (types.isSameType(type, syms.stringType)) return;
        if ((type.tsym.flags() & Flags.ENUM) != 0) return;
        if ((type.tsym.flags() & Flags.ANNOTATION) != 0) return;
        if (types.cvarLowerBound(type).tsym == syms.classType.tsym) return;
        if (types.isArray(type) && !types.isArray(types.elemtype(type))) {
            validateAnnotationType(pos, types.elemtype(type));
            return;
        }
        log.error(pos, Errors.InvalidAnnotationMemberType);
    }

    /**
     * "It is also a compile-time error if any method declared in an
     * annotation type has a signature that is override-equivalent to
     * that of any public or protected method declared in class Object
     * or in the interface annotation.Annotation."
     *
     * @jls 9.6 Annotation Types
     */
    void validateAnnotationMethod(DiagnosticPosition pos, MethodSymbol m) {
        for (Type sup = syms.annotationType; sup.hasTag(CLASS); sup = types.supertype(sup)) {
            Scope s = sup.tsym.members();
            for (Symbol sym : s.getSymbolsByName(m.name)) {
                if (sym.kind == MTH &&
                    (sym.flags() & (PUBLIC | PROTECTED)) != 0 &&
                    types.overrideEquivalent(m.type, sym.type))
                    log.error(pos, Errors.IntfAnnotationMemberClash(sym, sup));
            }
        }
    }

    /** Check the annotations of a symbol.
     */
    public void validateAnnotations(List<JCAnnotation> annotations, JCTree declarationTree, Symbol s) {
        for (JCAnnotation a : annotations)
            validateAnnotation(a, declarationTree, s);
    }

    /** Check the type annotations.
     */
    public void validateTypeAnnotations(List<JCAnnotation> annotations, Symbol s, boolean isTypeParameter) {
        for (JCAnnotation a : annotations)
            validateTypeAnnotation(a, s, isTypeParameter);
    }

    /** Check an annotation of a symbol.
     */
    private void validateAnnotation(JCAnnotation a, JCTree declarationTree, Symbol s) {
        /** NOTE: if annotation processors are present, annotation processing rounds can happen after this method,
         *  this can impact in particular records for which annotations are forcibly propagated.
         */
        validateAnnotationTree(a);
        boolean isRecordMember = ((s.flags_field & RECORD) != 0 || s.enclClass() != null && s.enclClass().isRecord());

        boolean isRecordField = (s.flags_field & RECORD) != 0 &&
                declarationTree.hasTag(VARDEF) &&
                s.owner.kind == TYP;

        if (isRecordField) {
            // first we need to check if the annotation is applicable to records
            Name[] targets = getTargetNames(a);
            boolean appliesToRecords = false;
            for (Name target : targets) {
                appliesToRecords =
                                target == names.FIELD ||
                                target == names.PARAMETER ||
                                target == names.METHOD ||
                                target == names.TYPE_USE ||
                                target == names.RECORD_COMPONENT;
                if (appliesToRecords) {
                    break;
                }
            }
            if (!appliesToRecords) {
                log.error(a.pos(), Errors.AnnotationTypeNotApplicable);
            } else {
                /* lets now find the annotations in the field that are targeted to record components and append them to
                 * the corresponding record component
                 */
                ClassSymbol recordClass = (ClassSymbol) s.owner;
                RecordComponent rc = recordClass.getRecordComponent((VarSymbol)s);
                SymbolMetadata metadata = rc.getMetadata();
                if (metadata == null || metadata.isEmpty()) {
                    /* if not is empty then we have already been here, which is the case if multiple annotations are applied
                     * to the record component declaration
                     */
                    rc.appendAttributes(s.getRawAttributes().stream().filter(anno ->
                            Arrays.stream(getTargetNames(anno.type.tsym)).anyMatch(name -> name == names.RECORD_COMPONENT)
                    ).collect(List.collector()));

                    JCVariableDecl fieldAST = (JCVariableDecl) declarationTree;
                    for (JCAnnotation fieldAnnot : fieldAST.mods.annotations) {
                        for (JCAnnotation rcAnnot : rc.declarationFor().mods.annotations) {
                            if (rcAnnot.pos == fieldAnnot.pos) {
                                rcAnnot.setType(fieldAnnot.type);
                                break;
                            }
                        }
                    }

                    /* At this point, we used to carry over any type annotations from the VARDEF to the record component, but
                     * that is problematic, since we get here only when *some* annotation is applied to the SE5 (declaration)
                     * annotation location, inadvertently failing to carry over the type annotations when the VarDef has no
                     * annotations in the SE5 annotation location.
                     *
                     * Now type annotations are assigned to record components in a method that would execute irrespective of
                     * whether there are SE5 annotations on a VarDef viz com.sun.tools.javac.code.TypeAnnotations.TypeAnnotationPositions.visitVarDef
                     */
                }
            }
        }

        /* the section below is tricky. Annotations applied to record components are propagated to the corresponding
         * record member so if an annotation has target: FIELD, it is propagated to the corresponding FIELD, if it has
         * target METHOD, it is propagated to the accessor and so on. But at the moment when method members are generated
         * there is no enough information to propagate only the right annotations. So all the annotations are propagated
         * to all the possible locations.
         *
         * At this point we need to remove all the annotations that are not in place before going on with the annotation
         * party. On top of the above there is the issue that there is no AST representing record components, just symbols
         * so the corresponding field has been holding all the annotations and it's metadata has been modified as if it
         * was both a field and a record component.
         *
         * So there are two places where we need to trim annotations from: the metadata of the symbol and / or the modifiers
         * in the AST. Whatever is in the metadata will be written to the class file, whatever is in the modifiers could
         * be see by annotation processors.
         *
         * The metadata contains both type annotations and declaration annotations. At this point of the game we don't
         * need to care about type annotations, they are all in the right place. But we could need to remove declaration
         * annotations. So for declaration annotations if they are not applicable to the record member, excluding type
         * annotations which are already correct, then we will remove it. For the AST modifiers if the annotation is not
         * applicable either as type annotation and or declaration annotation, only in that case it will be removed.
         *
         * So it could be that annotation is removed as a declaration annotation but it is kept in the AST modifier for
         * further inspection by annotation processors.
         *
         * For example:
         *
         *     import java.lang.annotation.*;
         *
         *     @Target({ElementType.TYPE_USE, ElementType.RECORD_COMPONENT})
         *     @Retention(RetentionPolicy.RUNTIME)
         *     @interface Anno { }
         *
         *     record R(@Anno String s) {}
         *
         * at this point we will have for the case of the generated field:
         *   - @Anno in the modifier
         *   - @Anno as a type annotation
         *   - @Anno as a declaration annotation
         *
         * the last one should be removed because the annotation has not FIELD as target but it was applied as a
         * declaration annotation because the field was being treated both as a field and as a record component
         * as we have already copied the annotations to the record component, now the field doesn't need to hold
         * annotations that are not intended for it anymore. Still @Anno has to be kept in the AST's modifiers as it
         * is applicable as a type annotation to the type of the field.
         */

        if (a.type.tsym.isAnnotationType()) {
            Optional<Set<Name>> applicableTargetsOp = getApplicableTargets(a, s);
            if (!applicableTargetsOp.isEmpty()) {
                Set<Name> applicableTargets = applicableTargetsOp.get();
                boolean notApplicableOrIsTypeUseOnly = applicableTargets.isEmpty() ||
                        applicableTargets.size() == 1 && applicableTargets.contains(names.TYPE_USE);
                boolean isCompGeneratedRecordElement = isRecordMember && (s.flags_field & Flags.GENERATED_MEMBER) != 0;
                boolean isCompRecordElementWithNonApplicableDeclAnno = isCompGeneratedRecordElement && notApplicableOrIsTypeUseOnly;

                if (applicableTargets.isEmpty() || isCompRecordElementWithNonApplicableDeclAnno) {
                    if (isCompRecordElementWithNonApplicableDeclAnno) {
                            /* so we have found an annotation that is not applicable to a record member that was generated by the
                             * compiler. This was intentionally done at TypeEnter, now is the moment strip away the annotations
                             * that are not applicable to the given record member
                             */
                        JCModifiers modifiers = TreeInfo.getModifiers(declarationTree);
                            /* lets first remove the annotation from the modifier if it is not applicable, we have to check again as
                             * it could be a type annotation
                             */
                        if (modifiers != null && applicableTargets.isEmpty()) {
                            ListBuffer<JCAnnotation> newAnnotations = new ListBuffer<>();
                            for (JCAnnotation anno : modifiers.annotations) {
                                if (anno != a) {
                                    newAnnotations.add(anno);
                                }
                            }
                            modifiers.annotations = newAnnotations.toList();
                        }
                        // now lets remove it from the symbol
                        s.getMetadata().removeDeclarationMetadata(a.attribute);
                    } else {
                        log.error(a.pos(), Errors.AnnotationTypeNotApplicable);
                    }
                }
                /* if we are seeing the @SafeVarargs annotation applied to a compiler generated accessor,
                 * then this is an error as we know that no compiler generated accessor will be a varargs
                 * method, better to fail asap
                 */
                if (isCompGeneratedRecordElement && !isRecordField && a.type.tsym == syms.trustMeType.tsym && declarationTree.hasTag(METHODDEF)) {
                    log.error(a.pos(), Errors.VarargsInvalidTrustmeAnno(syms.trustMeType.tsym, Fragments.VarargsTrustmeOnNonVarargsAccessor(s)));
                }
            }
        }

        if (a.annotationType.type.tsym == syms.functionalInterfaceType.tsym) {
            if (s.kind != TYP) {
                log.error(a.pos(), Errors.BadFunctionalIntfAnno);
            } else if (!s.isInterface() || (s.flags() & ANNOTATION) != 0) {
                log.error(a.pos(), Errors.BadFunctionalIntfAnno1(Fragments.NotAFunctionalIntf(s)));
            }
        }
    }

    public void validateTypeAnnotation(JCAnnotation a, Symbol s, boolean isTypeParameter) {
        Assert.checkNonNull(a.type);
        // we just want to validate that the anotation doesn't have any wrong target
        if (s != null) getApplicableTargets(a, s);
        validateAnnotationTree(a);

        if (a.hasTag(TYPE_ANNOTATION) &&
                !a.annotationType.type.isErroneous() &&
                !isTypeAnnotation(a, isTypeParameter)) {
            log.error(a.pos(), Errors.AnnotationTypeNotApplicableToType(a.type));
        }
    }

    /**
     * Validate the proposed container 'repeatable' on the
     * annotation type symbol 's'. Report errors at position
     * 'pos'.
     *
     * @param s The (annotation)type declaration annotated with a @Repeatable
     * @param repeatable the @Repeatable on 's'
     * @param pos where to report errors
     */
    public void validateRepeatable(TypeSymbol s, Attribute.Compound repeatable, DiagnosticPosition pos) {
        Assert.check(types.isSameType(repeatable.type, syms.repeatableType));

        Type t = null;
        List<Pair<MethodSymbol,Attribute>> l = repeatable.values;
        if (!l.isEmpty()) {
            Assert.check(l.head.fst.name == names.value);
            if (l.head.snd instanceof Attribute.Class) {
                t = ((Attribute.Class)l.head.snd).getValue();
            }
        }

        if (t == null) {
            // errors should already have been reported during Annotate
            return;
        }

        validateValue(t.tsym, s, pos);
        validateRetention(t.tsym, s, pos);
        validateDocumented(t.tsym, s, pos);
        validateInherited(t.tsym, s, pos);
        validateTarget(t.tsym, s, pos);
        validateDefault(t.tsym, pos);
    }

    private void validateValue(TypeSymbol container, TypeSymbol contained, DiagnosticPosition pos) {
        Symbol sym = container.members().findFirst(names.value);
        if (sym != null && sym.kind == MTH) {
            MethodSymbol m = (MethodSymbol) sym;
            Type ret = m.getReturnType();
            if (!(ret.hasTag(ARRAY) && types.isSameType(((ArrayType)ret).elemtype, contained.type))) {
                log.error(pos,
                          Errors.InvalidRepeatableAnnotationValueReturn(container,
                                                                        ret,
                                                                        types.makeArrayType(contained.type)));
            }
        } else {
            log.error(pos, Errors.InvalidRepeatableAnnotationNoValue(container));
        }
    }

    private void validateRetention(TypeSymbol container, TypeSymbol contained, DiagnosticPosition pos) {
        Attribute.RetentionPolicy containerRetention = types.getRetention(container);
        Attribute.RetentionPolicy containedRetention = types.getRetention(contained);

        boolean error = false;
        switch (containedRetention) {
        case RUNTIME:
            if (containerRetention != Attribute.RetentionPolicy.RUNTIME) {
                error = true;
            }
            break;
        case CLASS:
            if (containerRetention == Attribute.RetentionPolicy.SOURCE)  {
                error = true;
            }
        }
        if (error ) {
            log.error(pos,
                      Errors.InvalidRepeatableAnnotationRetention(container,
                                                                  containerRetention.name(),
                                                                  contained,
                                                                  containedRetention.name()));
        }
    }

    private void validateDocumented(Symbol container, Symbol contained, DiagnosticPosition pos) {
        if (contained.attribute(syms.documentedType.tsym) != null) {
            if (container.attribute(syms.documentedType.tsym) == null) {
                log.error(pos, Errors.InvalidRepeatableAnnotationNotDocumented(container, contained));
            }
        }
    }

    private void validateInherited(Symbol container, Symbol contained, DiagnosticPosition pos) {
        if (contained.attribute(syms.inheritedType.tsym) != null) {
            if (container.attribute(syms.inheritedType.tsym) == null) {
                log.error(pos, Errors.InvalidRepeatableAnnotationNotInherited(container, contained));
            }
        }
    }

    private void validateTarget(TypeSymbol container, TypeSymbol contained, DiagnosticPosition pos) {
        // The set of targets the container is applicable to must be a subset
        // (with respect to annotation target semantics) of the set of targets
        // the contained is applicable to. The target sets may be implicit or
        // explicit.

        Set<Name> containerTargets;
        Attribute.Array containerTarget = getAttributeTargetAttribute(container);
        if (containerTarget == null) {
            containerTargets = getDefaultTargetSet();
        } else {
            containerTargets = new HashSet<>();
            for (Attribute app : containerTarget.values) {
                if (!(app instanceof Attribute.Enum attributeEnum)) {
                    continue; // recovery
                }
                containerTargets.add(attributeEnum.value.name);
            }
        }

        Set<Name> containedTargets;
        Attribute.Array containedTarget = getAttributeTargetAttribute(contained);
        if (containedTarget == null) {
            containedTargets = getDefaultTargetSet();
        } else {
            containedTargets = new HashSet<>();
            for (Attribute app : containedTarget.values) {
                if (!(app instanceof Attribute.Enum attributeEnum)) {
                    continue; // recovery
                }
                containedTargets.add(attributeEnum.value.name);
            }
        }

        if (!isTargetSubsetOf(containerTargets, containedTargets)) {
            log.error(pos, Errors.InvalidRepeatableAnnotationIncompatibleTarget(container, contained));
        }
    }

    /* get a set of names for the default target */
    private Set<Name> getDefaultTargetSet() {
        if (defaultTargets == null) {
            defaultTargets = Set.of(defaultTargetMetaInfo());
        }

        return defaultTargets;
    }
    private Set<Name> defaultTargets;


    /** Checks that s is a subset of t, with respect to ElementType
     * semantics, specifically {ANNOTATION_TYPE} is a subset of {TYPE},
     * and {TYPE_USE} covers the set {ANNOTATION_TYPE, TYPE, TYPE_USE,
     * TYPE_PARAMETER}.
     */
    private boolean isTargetSubsetOf(Set<Name> s, Set<Name> t) {
        // Check that all elements in s are present in t
        for (Name n2 : s) {
            boolean currentElementOk = false;
            for (Name n1 : t) {
                if (n1 == n2) {
                    currentElementOk = true;
                    break;
                } else if (n1 == names.TYPE && n2 == names.ANNOTATION_TYPE) {
                    currentElementOk = true;
                    break;
                } else if (n1 == names.TYPE_USE &&
                        (n2 == names.TYPE ||
                         n2 == names.ANNOTATION_TYPE ||
                         n2 == names.TYPE_PARAMETER)) {
                    currentElementOk = true;
                    break;
                }
            }
            if (!currentElementOk)
                return false;
        }
        return true;
    }

    private void validateDefault(Symbol container, DiagnosticPosition pos) {
        // validate that all other elements of containing type has defaults
        Scope scope = container.members();
        for(Symbol elm : scope.getSymbols()) {
            if (elm.name != names.value &&
                elm.kind == MTH &&
                ((MethodSymbol)elm).defaultValue == null) {
                log.error(pos,
                          Errors.InvalidRepeatableAnnotationElemNondefault(container, elm));
            }
        }
    }

    /** Is s a method symbol that overrides a method in a superclass? */
    boolean isOverrider(Symbol s) {
        if (s.kind != MTH || s.isStatic())
            return false;
        MethodSymbol m = (MethodSymbol)s;
        TypeSymbol owner = (TypeSymbol)m.owner;
        for (Type sup : types.closure(owner.type)) {
            if (sup == owner.type)
                continue; // skip "this"
            Scope scope = sup.tsym.members();
            for (Symbol sym : scope.getSymbolsByName(m.name)) {
                if (!sym.isStatic() && m.overrides(sym, owner, types, true))
                    return true;
            }
        }
        return false;
    }

    /** Is the annotation applicable to types? */
    protected boolean isTypeAnnotation(JCAnnotation a, boolean isTypeParameter) {
        List<Attribute> targets = typeAnnotations.annotationTargets(a.annotationType.type.tsym);
        return (targets == null) ?
                (Feature.NO_TARGET_ANNOTATION_APPLICABILITY.allowedInSource(source) && isTypeParameter) :
                targets.stream()
                        .anyMatch(attr -> isTypeAnnotation(attr, isTypeParameter));
    }
    //where
        boolean isTypeAnnotation(Attribute a, boolean isTypeParameter) {
            Attribute.Enum e = (Attribute.Enum)a;
            return (e.value.name == names.TYPE_USE ||
                    (isTypeParameter && e.value.name == names.TYPE_PARAMETER));
        }

    /** Is the annotation applicable to the symbol? */
    Name[] getTargetNames(JCAnnotation a) {
        return getTargetNames(a.annotationType.type.tsym);
    }

    public Name[] getTargetNames(TypeSymbol annoSym) {
        Attribute.Array arr = getAttributeTargetAttribute(annoSym);
        Name[] targets;
        if (arr == null) {
            targets = defaultTargetMetaInfo();
        } else {
            // TODO: can we optimize this?
            targets = new Name[arr.values.length];
            for (int i=0; i<arr.values.length; ++i) {
                Attribute app = arr.values[i];
                if (!(app instanceof Attribute.Enum attributeEnum)) {
                    return new Name[0];
                }
                targets[i] = attributeEnum.value.name;
            }
        }
        return targets;
    }

    boolean annotationApplicable(JCAnnotation a, Symbol s) {
        Optional<Set<Name>> targets = getApplicableTargets(a, s);
        /* the optional could be empty if the annotation is unknown in that case
         * we return that it is applicable and if it is erroneous that should imply
         * an error at the declaration site
         */
        return targets.isEmpty() || targets.isPresent() && !targets.get().isEmpty();
    }

    Optional<Set<Name>> getApplicableTargets(JCAnnotation a, Symbol s) {
        Attribute.Array arr = getAttributeTargetAttribute(a.annotationType.type.tsym);
        Name[] targets;
        Set<Name> applicableTargets = new HashSet<>();

        if (arr == null) {
            targets = defaultTargetMetaInfo();
        } else {
            // TODO: can we optimize this?
            targets = new Name[arr.values.length];
            for (int i=0; i<arr.values.length; ++i) {
                Attribute app = arr.values[i];
                if (!(app instanceof Attribute.Enum attributeEnum)) {
                    // recovery
                    return Optional.empty();
                }
                targets[i] = attributeEnum.value.name;
            }
        }
        for (Name target : targets) {
            if (target == names.TYPE) {
                if (s.kind == TYP)
                    applicableTargets.add(names.TYPE);
            } else if (target == names.FIELD) {
                if (s.kind == VAR && s.owner.kind != MTH)
                    applicableTargets.add(names.FIELD);
            } else if (target == names.RECORD_COMPONENT) {
                if (s.getKind() == ElementKind.RECORD_COMPONENT) {
                    applicableTargets.add(names.RECORD_COMPONENT);
                }
            } else if (target == names.METHOD) {
                if (s.kind == MTH && !s.isConstructor())
                    applicableTargets.add(names.METHOD);
            } else if (target == names.PARAMETER) {
                if (s.kind == VAR &&
                    (s.owner.kind == MTH && (s.flags() & PARAMETER) != 0)) {
                    applicableTargets.add(names.PARAMETER);
                }
            } else if (target == names.CONSTRUCTOR) {
                if (s.kind == MTH && s.isConstructor())
                    applicableTargets.add(names.CONSTRUCTOR);
            } else if (target == names.LOCAL_VARIABLE) {
                if (s.kind == VAR && s.owner.kind == MTH &&
                      (s.flags() & PARAMETER) == 0) {
                    applicableTargets.add(names.LOCAL_VARIABLE);
                }
            } else if (target == names.ANNOTATION_TYPE) {
                if (s.kind == TYP && (s.flags() & ANNOTATION) != 0) {
                    applicableTargets.add(names.ANNOTATION_TYPE);
                }
            } else if (target == names.PACKAGE) {
                if (s.kind == PCK)
                    applicableTargets.add(names.PACKAGE);
            } else if (target == names.TYPE_USE) {
                if (s.kind == VAR && s.owner.kind == MTH && s.type.hasTag(NONE)) {
                    //cannot type annotate implicitly typed locals
                    continue;
                } else if (s.kind == TYP || s.kind == VAR ||
                        (s.kind == MTH && !s.isConstructor() &&
                                !s.type.getReturnType().hasTag(VOID)) ||
                        (s.kind == MTH && s.isConstructor())) {
                    applicableTargets.add(names.TYPE_USE);
                }
            } else if (target == names.TYPE_PARAMETER) {
                if (s.kind == TYP && s.type.hasTag(TYPEVAR))
                    applicableTargets.add(names.TYPE_PARAMETER);
            } else if (target == names.MODULE) {
                if (s.kind == MDL)
                    applicableTargets.add(names.MODULE);
            } else {
                log.error(a, Errors.AnnotationUnrecognizedAttributeName(a.type, target));
                return Optional.empty(); // Unknown ElementType
            }
        }
        return Optional.of(applicableTargets);
    }

    Attribute.Array getAttributeTargetAttribute(TypeSymbol s) {
        Attribute.Compound atTarget = s.getAnnotationTypeMetadata().getTarget();
        if (atTarget == null) return null; // ok, is applicable
        Attribute atValue = atTarget.member(names.value);
        return (atValue instanceof Attribute.Array attributeArray) ? attributeArray : null;
    }

    private Name[] dfltTargetMeta;
    private Name[] defaultTargetMetaInfo() {
        if (dfltTargetMeta == null) {
            ArrayList<Name> defaultTargets = new ArrayList<>();
            defaultTargets.add(names.PACKAGE);
            defaultTargets.add(names.TYPE);
            defaultTargets.add(names.FIELD);
            defaultTargets.add(names.METHOD);
            defaultTargets.add(names.CONSTRUCTOR);
            defaultTargets.add(names.ANNOTATION_TYPE);
            defaultTargets.add(names.LOCAL_VARIABLE);
            defaultTargets.add(names.PARAMETER);
            if (allowRecords) {
              defaultTargets.add(names.RECORD_COMPONENT);
            }
            if (allowModules) {
              defaultTargets.add(names.MODULE);
            }
            dfltTargetMeta = defaultTargets.toArray(new Name[0]);
        }
        return dfltTargetMeta;
    }

    /** Check an annotation value.
     *
     * @param a The annotation tree to check
     * @return true if this annotation tree is valid, otherwise false
     */
    public boolean validateAnnotationDeferErrors(JCAnnotation a) {
        boolean res = false;
        final Log.DiagnosticHandler diagHandler = log.new DiscardDiagnosticHandler();
        try {
            res = validateAnnotation(a);
        } finally {
            log.popDiagnosticHandler(diagHandler);
        }
        return res;
    }

    private boolean validateAnnotation(JCAnnotation a) {
        boolean isValid = true;
        AnnotationTypeMetadata metadata = a.annotationType.type.tsym.getAnnotationTypeMetadata();

        // collect an inventory of the annotation elements
        Set<MethodSymbol> elements = metadata.getAnnotationElements();

        // remove the ones that are assigned values
        for (JCTree arg : a.args) {
            if (!arg.hasTag(ASSIGN)) continue; // recovery
            JCAssign assign = (JCAssign)arg;
            Symbol m = TreeInfo.symbol(assign.lhs);
            if (m == null || m.type.isErroneous()) continue;
            if (!elements.remove(m)) {
                isValid = false;
                log.error(assign.lhs.pos(),
                          Errors.DuplicateAnnotationMemberValue(m.name, a.type));
            }
        }

        // all the remaining ones better have default values
        List<Name> missingDefaults = List.nil();
        Set<MethodSymbol> membersWithDefault = metadata.getAnnotationElementsWithDefault();
        for (MethodSymbol m : elements) {
            if (m.type.isErroneous())
                continue;

            if (!membersWithDefault.contains(m))
                missingDefaults = missingDefaults.append(m.name);
        }
        missingDefaults = missingDefaults.reverse();
        if (missingDefaults.nonEmpty()) {
            isValid = false;
            Error errorKey = (missingDefaults.size() > 1)
                    ? Errors.AnnotationMissingDefaultValue1(a.type, missingDefaults)
                    : Errors.AnnotationMissingDefaultValue(a.type, missingDefaults);
            log.error(a.pos(), errorKey);
        }

        return isValid && validateTargetAnnotationValue(a);
    }

    /* Validate the special java.lang.annotation.Target annotation */
    boolean validateTargetAnnotationValue(JCAnnotation a) {
        // special case: java.lang.annotation.Target must not have
        // repeated values in its value member
        if (a.annotationType.type.tsym != syms.annotationTargetType.tsym ||
                a.args.tail == null)
            return true;

        boolean isValid = true;
        if (!a.args.head.hasTag(ASSIGN)) return false; // error recovery
        JCAssign assign = (JCAssign) a.args.head;
        Symbol m = TreeInfo.symbol(assign.lhs);
        if (m.name != names.value) return false;
        JCTree rhs = assign.rhs;
        if (!rhs.hasTag(NEWARRAY)) return false;
        JCNewArray na = (JCNewArray) rhs;
        Set<Symbol> targets = new HashSet<>();
        for (JCTree elem : na.elems) {
            if (!targets.add(TreeInfo.symbol(elem))) {
                isValid = false;
                log.error(elem.pos(), Errors.RepeatedAnnotationTarget);
            }
        }
        return isValid;
    }

    void checkDeprecatedAnnotation(DiagnosticPosition pos, Symbol s) {
        if (lint.isEnabled(LintCategory.DEP_ANN) && s.isDeprecatableViaAnnotation() &&
            (s.flags() & DEPRECATED) != 0 &&
            !syms.deprecatedType.isErroneous() &&
            s.attribute(syms.deprecatedType.tsym) == null) {
            log.warning(pos, LintWarnings.MissingDeprecatedAnnotation);
        }
        // Note: @Deprecated has no effect on local variables, parameters and package decls.
        if (lint.isEnabled(LintCategory.DEPRECATION) && !s.isDeprecatableViaAnnotation()) {
            if (!syms.deprecatedType.isErroneous() && s.attribute(syms.deprecatedType.tsym) != null) {
                log.warning(pos,
                            LintWarnings.DeprecatedAnnotationHasNoEffect(Kinds.kindName(s)));
            }
        }
    }

    void checkDeprecated(final DiagnosticPosition pos, final Symbol other, final Symbol s) {
        checkDeprecated(() -> pos, other, s);
    }

    void checkDeprecated(Supplier<DiagnosticPosition> pos, final Symbol other, final Symbol s) {
        if (!importSuppression
                && (s.isDeprecatedForRemoval() || s.isDeprecated() && !other.isDeprecated())
                && (s.outermostClass() != other.outermostClass() || s.outermostClass() == null)
                && s.kind != Kind.PCK) {
            deferredLintHandler.report(_l -> warnDeprecated(pos.get(), s));
        }
    }

    void checkSunAPI(final DiagnosticPosition pos, final Symbol s) {
        if ((s.flags() & PROPRIETARY) != 0) {
            deferredLintHandler.report(_l -> {
                log.warning(pos, Warnings.SunProprietary(s));
            });
        }
    }

    void checkProfile(final DiagnosticPosition pos, final Symbol s) {
        if (profile != Profile.DEFAULT && (s.flags() & NOT_IN_PROFILE) != 0) {
            log.error(pos, Errors.NotInProfile(s, profile));
        }
    }

    void checkPreview(DiagnosticPosition pos, Symbol other, Symbol s) {
        checkPreview(pos, other, Type.noType, s);
    }

    void checkPreview(DiagnosticPosition pos, Symbol other, Type site, Symbol s) {
        boolean sIsPreview;
        Symbol previewSymbol;
        if ((s.flags() & PREVIEW_API) != 0) {
            sIsPreview = true;
            previewSymbol=  s;
        } else if ((s.kind == Kind.MTH || s.kind == Kind.VAR) &&
                   site.tsym != null &&
                   (site.tsym.flags() & PREVIEW_API) == 0 &&
                   (s.owner.flags() & PREVIEW_API) != 0) {
            //calling a method, or using a field, whose owner is a preview, but
            //using a site that is not a preview. Also produce an error or warning:
            sIsPreview = true;
            previewSymbol = s.owner;
        } else {
            sIsPreview = false;
            previewSymbol = null;
        }
        if (sIsPreview && !preview.participatesInPreview(syms, other, s) && !disablePreviewCheck) {
            if ((previewSymbol.flags() & PREVIEW_REFLECTIVE) == 0) {
                if (!preview.isEnabled()) {
                    log.error(pos, Errors.IsPreview(s));
                } else {
                    preview.markUsesPreview(pos);
                    warnPreviewAPI(pos, LintWarnings.IsPreview(s));
                }
            } else {
                warnPreviewAPI(pos, LintWarnings.IsPreviewReflective(s));
            }
        }
        if (preview.declaredUsingPreviewFeature(s)) {
            if (preview.isEnabled()) {
                //for preview disabled do presumably so not need to do anything?
                //If "s" is compiled from source, then there was an error for it already;
                //if "s" is from classfile, there already was an error for the classfile.
                preview.markUsesPreview(pos);
                warnPreviewAPI(pos, LintWarnings.DeclaredUsingPreview(kindName(s), s));
            }
        }
    }

    void checkRestricted(DiagnosticPosition pos, Symbol s) {
        if (s.kind == MTH && (s.flags() & RESTRICTED) != 0) {
            deferredLintHandler.report(_l -> warnRestrictedAPI(pos, s));
        }
    }

/* *************************************************************************
 * Check for recursive annotation elements.
 **************************************************************************/

    /** Check for cycles in the graph of annotation elements.
     */
    void checkNonCyclicElements(JCClassDecl tree) {
        if ((tree.sym.flags_field & ANNOTATION) == 0) return;
        Assert.check((tree.sym.flags_field & LOCKED) == 0);
        try {
            tree.sym.flags_field |= LOCKED;
            for (JCTree def : tree.defs) {
                if (!def.hasTag(METHODDEF)) continue;
                JCMethodDecl meth = (JCMethodDecl)def;
                checkAnnotationResType(meth.pos(), meth.restype.type);
            }
        } finally {
            tree.sym.flags_field &= ~LOCKED;
            tree.sym.flags_field |= ACYCLIC_ANN;
        }
    }

    void checkNonCyclicElementsInternal(DiagnosticPosition pos, TypeSymbol tsym) {
        if ((tsym.flags_field & ACYCLIC_ANN) != 0)
            return;
        if ((tsym.flags_field & LOCKED) != 0) {
            log.error(pos, Errors.CyclicAnnotationElement(tsym));
            return;
        }
        try {
            tsym.flags_field |= LOCKED;
            for (Symbol s : tsym.members().getSymbols(NON_RECURSIVE)) {
                if (s.kind != MTH)
                    continue;
                checkAnnotationResType(pos, ((MethodSymbol)s).type.getReturnType());
            }
        } finally {
            tsym.flags_field &= ~LOCKED;
            tsym.flags_field |= ACYCLIC_ANN;
        }
    }

    void checkAnnotationResType(DiagnosticPosition pos, Type type) {
        switch (type.getTag()) {
        case CLASS:
            if ((type.tsym.flags() & ANNOTATION) != 0)
                checkNonCyclicElementsInternal(pos, type.tsym);
            break;
        case ARRAY:
            checkAnnotationResType(pos, types.elemtype(type));
            break;
        default:
            break; // int etc
        }
    }

/* *************************************************************************
 * Check for cycles in the constructor call graph.
 **************************************************************************/

    /** Check for cycles in the graph of constructors calling other
     *  constructors.
     */
    void checkCyclicConstructors(JCClassDecl tree) {
        // use LinkedHashMap so we generate errors deterministically
        Map<Symbol,Symbol> callMap = new LinkedHashMap<>();

        // enter each constructor this-call into the map
        for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
            if (!TreeInfo.isConstructor(l.head))
                continue;
            JCMethodDecl meth = (JCMethodDecl)l.head;
            JCMethodInvocation app = TreeInfo.findConstructorCall(meth);
            if (app != null && TreeInfo.name(app.meth) == names._this) {
                callMap.put(meth.sym, TreeInfo.symbol(app.meth));
            } else {
                meth.sym.flags_field |= ACYCLIC;
            }
        }

        // Check for cycles in the map
        Symbol[] ctors = new Symbol[0];
        ctors = callMap.keySet().toArray(ctors);
        for (Symbol caller : ctors) {
            checkCyclicConstructor(tree, caller, callMap);
        }
    }

    /** Look in the map to see if the given constructor is part of a
     *  call cycle.
     */
    private void checkCyclicConstructor(JCClassDecl tree, Symbol ctor,
                                        Map<Symbol,Symbol> callMap) {
        if (ctor != null && (ctor.flags_field & ACYCLIC) == 0) {
            if ((ctor.flags_field & LOCKED) != 0) {
                log.error(TreeInfo.diagnosticPositionFor(ctor, tree, false, t -> t.hasTag(IDENT)),
                          Errors.RecursiveCtorInvocation);
            } else {
                ctor.flags_field |= LOCKED;
                checkCyclicConstructor(tree, callMap.remove(ctor), callMap);
                ctor.flags_field &= ~LOCKED;
            }
            ctor.flags_field |= ACYCLIC;
        }
    }

/* *************************************************************************
 * Verify the proper placement of super()/this() calls.
 *
 *    - super()/this() may only appear in constructors
 *    - There must be at most one super()/this() call per constructor
 *    - The super()/this() call, if any, must be a top-level statement in the
 *      constructor, i.e., not nested inside any other statement or block
 *    - There must be no return statements prior to the super()/this() call
 **************************************************************************/

    void checkSuperInitCalls(JCClassDecl tree) {
        new SuperThisChecker().check(tree);
    }

    private class SuperThisChecker extends TreeScanner {

        // Match this scan stack: 1=JCMethodDecl, 2=JCExpressionStatement, 3=JCMethodInvocation
        private static final int MATCH_SCAN_DEPTH = 3;

        private boolean constructor;        // is this method a constructor?
        private boolean firstStatement;     // at the first statement in method?
        private JCReturn earlyReturn;       // first return prior to the super()/init(), if any
        private Name initCall;              // whichever of "super" or "init" we've seen already
        private int scanDepth;              // current scan recursion depth in method body

        public void check(JCClassDecl classDef) {
            scan(classDef.defs);
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            Assert.check(!constructor);
            Assert.check(earlyReturn == null);
            Assert.check(initCall == null);
            Assert.check(scanDepth == 1);

            // Initialize state for this method
            constructor = TreeInfo.isConstructor(tree);
            try {

                // Scan method body
                if (tree.body != null) {
                    firstStatement = true;
                    for (List<JCStatement> l = tree.body.stats; l.nonEmpty(); l = l.tail) {
                        scan(l.head);
                        firstStatement = false;
                    }
                }

                // Verify no 'return' seen prior to an explicit super()/this() call
                if (constructor && earlyReturn != null && initCall != null)
                    log.error(earlyReturn.pos(), Errors.ReturnBeforeSuperclassInitialized);
            } finally {
                firstStatement = false;
                constructor = false;
                earlyReturn = null;
                initCall = null;
            }
        }

        @Override
        public void scan(JCTree tree) {
            scanDepth++;
            try {
                super.scan(tree);
            } finally {
                scanDepth--;
            }
        }

        @Override
        public void visitApply(JCMethodInvocation apply) {
            do {

                // Is this a super() or this() call?
                Name methodName = TreeInfo.name(apply.meth);
                if (methodName != names._super && methodName != names._this)
                    break;

                // super()/this() calls must only appear in a constructor
                if (!constructor) {
                    log.error(apply.pos(), Errors.CallMustOnlyAppearInCtor);
                    break;
                }

                // super()/this() calls must be a top level statement
                if (scanDepth != MATCH_SCAN_DEPTH) {
                    log.error(apply.pos(), Errors.CtorCallsNotAllowedHere);
                    break;
                }

                // super()/this() calls must not appear more than once
                if (initCall != null) {
                    log.error(apply.pos(), Errors.RedundantSuperclassInit);
                    break;
                }

                // If super()/this() isn't first, require flexible constructors feature
                if (!firstStatement)
                    preview.checkSourceLevel(apply.pos(), Feature.FLEXIBLE_CONSTRUCTORS);

                // We found a legitimate super()/this() call; remember it
                initCall = methodName;
            } while (false);

            // Proceed
            super.visitApply(apply);
        }

        @Override
        public void visitReturn(JCReturn tree) {
            if (constructor && initCall == null && earlyReturn == null)
                earlyReturn = tree;             // we have seen a return but not (yet) a super()/this()
            super.visitReturn(tree);
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            // don't descend any further
        }

        @Override
        public void visitLambda(JCLambda tree) {
            final boolean constructorPrev = constructor;
            final boolean firstStatementPrev = firstStatement;
            final JCReturn earlyReturnPrev = earlyReturn;
            final Name initCallPrev = initCall;
            final int scanDepthPrev = scanDepth;
            constructor = false;
            firstStatement = false;
            earlyReturn = null;
            initCall = null;
            scanDepth = 0;
            try {
                super.visitLambda(tree);
            } finally {
                constructor = constructorPrev;
                firstStatement = firstStatementPrev;
                earlyReturn = earlyReturnPrev;
                initCall = initCallPrev;
                scanDepth = scanDepthPrev;
            }
        }
    }

/* *************************************************************************
 * Miscellaneous
 **************************************************************************/

    /**
     *  Check for division by integer constant zero
     *  @param pos           Position for error reporting.
     *  @param operator      The operator for the expression
     *  @param operand       The right hand operand for the expression
     */
    void checkDivZero(final DiagnosticPosition pos, Symbol operator, Type operand) {
        if (operand.constValue() != null
            && operand.getTag().isSubRangeOf(LONG)
            && ((Number) (operand.constValue())).longValue() == 0) {
            int opc = ((OperatorSymbol)operator).opcode;
            if (opc == ByteCodes.idiv || opc == ByteCodes.imod
                || opc == ByteCodes.ldiv || opc == ByteCodes.lmod) {
                deferredLintHandler.report(_ -> lint.logIfEnabled(pos, LintWarnings.DivZero));
            }
        }
    }

    /**
     *  Check for possible loss of precission
     *  @param pos           Position for error reporting.
     *  @param found    The computed type of the tree
     *  @param req  The computed type of the tree
     */
    void checkLossOfPrecision(final DiagnosticPosition pos, Type found, Type req) {
        if (found.isNumeric() && req.isNumeric() && !types.isAssignable(found, req)) {
            deferredLintHandler.report(_ ->
                lint.logIfEnabled(pos, LintWarnings.PossibleLossOfPrecision(found, req)));
        }
    }

    /**
     * Check for empty statements after if
     */
    void checkEmptyIf(JCIf tree) {
        if (tree.thenpart.hasTag(SKIP) && tree.elsepart == null) {
            lint.logIfEnabled(tree.thenpart.pos(), LintWarnings.EmptyIf);
        }
    }

    /** Check that symbol is unique in given scope.
     *  @param pos           Position for error reporting.
     *  @param sym           The symbol.
     *  @param s             The scope.
     */
    boolean checkUnique(DiagnosticPosition pos, Symbol sym, Scope s) {
        if (sym.type.isErroneous())
            return true;
        if (sym.owner.name == names.any) return false;
        for (Symbol byName : s.getSymbolsByName(sym.name, NON_RECURSIVE)) {
            if (sym != byName &&
                    (byName.flags() & CLASH) == 0 &&
                    sym.kind == byName.kind &&
                    sym.name != names.error &&
                    (sym.kind != MTH ||
                     types.hasSameArgs(sym.type, byName.type) ||
                     types.hasSameArgs(types.erasure(sym.type), types.erasure(byName.type)))) {
                if ((sym.flags() & VARARGS) != (byName.flags() & VARARGS)) {
                    sym.flags_field |= CLASH;
                    varargsDuplicateError(pos, sym, byName);
                    return true;
                } else if (sym.kind == MTH && !types.hasSameArgs(sym.type, byName.type, false)) {
                    duplicateErasureError(pos, sym, byName);
                    sym.flags_field |= CLASH;
                    return true;
                } else if ((sym.flags() & MATCH_BINDING) != 0 &&
                           (byName.flags() & MATCH_BINDING) != 0 &&
                           (byName.flags() & MATCH_BINDING_TO_OUTER) == 0) {
                    if (!sym.type.isErroneous()) {
                        log.error(pos, Errors.MatchBindingExists);
                        sym.flags_field |= CLASH;
                    }
                    return false;
                } else {
                    duplicateError(pos, byName);
                    return false;
                }
            }
        }
        return true;
    }

    /** Report duplicate declaration error.
     */
    void duplicateErasureError(DiagnosticPosition pos, Symbol sym1, Symbol sym2) {
        if (!sym1.type.isErroneous() && !sym2.type.isErroneous()) {
            log.error(pos, Errors.NameClashSameErasure(sym1, sym2));
        }
    }

    /**Check that types imported through the ordinary imports don't clash with types imported
     * by other (static or ordinary) imports. Note that two static imports may import two clashing
     * types without an error on the imports.
     * @param toplevel       The toplevel tree for which the test should be performed.
     */
    void checkImportsUnique(JCCompilationUnit toplevel) {
        WriteableScope ordinallyImportedSoFar = WriteableScope.create(toplevel.packge);
        WriteableScope staticallyImportedSoFar = WriteableScope.create(toplevel.packge);
        WriteableScope topLevelScope = toplevel.toplevelScope;

        for (JCTree def : toplevel.defs) {
            if (!def.hasTag(IMPORT))
                continue;

            JCImport imp = (JCImport) def;

            if (imp.importScope == null)
                continue;

            for (Symbol sym : imp.importScope.getSymbols(sym -> sym.kind == TYP)) {
                if (imp.isStatic()) {
                    checkUniqueImport(imp.pos(), ordinallyImportedSoFar, staticallyImportedSoFar, topLevelScope, sym, true);
                    staticallyImportedSoFar.enter(sym);
                } else {
                    checkUniqueImport(imp.pos(), ordinallyImportedSoFar, staticallyImportedSoFar, topLevelScope, sym, false);
                    ordinallyImportedSoFar.enter(sym);
                }
            }

            imp.importScope = null;
        }
    }

    /** Check that single-type import is not already imported or top-level defined,
     *  but make an exception for two single-type imports which denote the same type.
     *  @param pos                     Position for error reporting.
     *  @param ordinallyImportedSoFar  A Scope containing types imported so far through
     *                                 ordinary imports.
     *  @param staticallyImportedSoFar A Scope containing types imported so far through
     *                                 static imports.
     *  @param topLevelScope           The current file's top-level Scope
     *  @param sym                     The symbol.
     *  @param staticImport            Whether or not this was a static import
     */
    private boolean checkUniqueImport(DiagnosticPosition pos, Scope ordinallyImportedSoFar,
                                      Scope staticallyImportedSoFar, Scope topLevelScope,
                                      Symbol sym, boolean staticImport) {
        Predicate<Symbol> duplicates = candidate -> candidate != sym && !candidate.type.isErroneous();
        Symbol ordinaryClashing = ordinallyImportedSoFar.findFirst(sym.name, duplicates);
        Symbol staticClashing = null;
        if (ordinaryClashing == null && !staticImport) {
            staticClashing = staticallyImportedSoFar.findFirst(sym.name, duplicates);
        }
        if (ordinaryClashing != null || staticClashing != null) {
            if (ordinaryClashing != null)
                log.error(pos, Errors.AlreadyDefinedSingleImport(ordinaryClashing));
            else
                log.error(pos, Errors.AlreadyDefinedStaticSingleImport(staticClashing));
            return false;
        }
        Symbol clashing = topLevelScope.findFirst(sym.name, duplicates);
        if (clashing != null) {
            log.error(pos, Errors.AlreadyDefinedThisUnit(clashing));
            return false;
        }
        return true;
    }

    /** Check that a qualified name is in canonical form (for import decls).
     */
    public void checkCanonical(JCTree tree) {
        if (!isCanonical(tree))
            log.error(tree.pos(),
                      Errors.ImportRequiresCanonical(TreeInfo.symbol(tree)));
    }
        // where
        private boolean isCanonical(JCTree tree) {
            while (tree.hasTag(SELECT)) {
                JCFieldAccess s = (JCFieldAccess) tree;
                if (s.sym.owner.getQualifiedName() != TreeInfo.symbol(s.selected).getQualifiedName())
                    return false;
                tree = s.selected;
            }
            return true;
        }

    /** Check that an auxiliary class is not accessed from any other file than its own.
     */
    void checkForBadAuxiliaryClassAccess(DiagnosticPosition pos, Env<AttrContext> env, ClassSymbol c) {
        if ((c.flags() & AUXILIARY) != 0 &&
            rs.isAccessible(env, c) &&
            !fileManager.isSameFile(c.sourcefile, env.toplevel.sourcefile))
        {
            lint.logIfEnabled(pos,
                        LintWarnings.AuxiliaryClassAccessedFromOutsideOfItsSourceFile(c, c.sourcefile));
        }
    }

    /**
     * Check for a default constructor in an exported package.
     */
    void checkDefaultConstructor(ClassSymbol c, DiagnosticPosition pos) {
        if (lint.isEnabled(LintCategory.MISSING_EXPLICIT_CTOR) &&
            ((c.flags() & (ENUM | RECORD)) == 0) &&
            !c.isAnonymous() &&
            ((c.flags() & (PUBLIC | PROTECTED)) != 0) &&
            Feature.MODULES.allowedInSource(source)) {
            NestingKind nestingKind = c.getNestingKind();
            switch (nestingKind) {
                case ANONYMOUS,
                     LOCAL -> {return;}
                case TOP_LEVEL -> {;} // No additional checks needed
                case MEMBER -> {
                    // For nested member classes, all the enclosing
                    // classes must be public or protected.
                    Symbol owner = c.owner;
                    while (owner != null && owner.kind == TYP) {
                        if ((owner.flags() & (PUBLIC | PROTECTED)) == 0)
                            return;
                        owner = owner.owner;
                    }
                }
            }

            // Only check classes in named packages exported by its module
            PackageSymbol pkg = c.packge();
            if (!pkg.isUnnamed()) {
                ModuleSymbol modle = pkg.modle;
                for (ExportsDirective exportDir : modle.exports) {
                    // Report warning only if the containing
                    // package is unconditionally exported
                    if (exportDir.packge.equals(pkg)) {
                        if (exportDir.modules == null || exportDir.modules.isEmpty()) {
                            // Warning may be suppressed by
                            // annotations; check again for being
                            // enabled in the deferred context.
                            deferredLintHandler.report(_ ->
                                lint.logIfEnabled(pos, LintWarnings.MissingExplicitCtor(c, pkg, modle)));
                        } else {
                            return;
                        }
                    }
                }
            }
        }
        return;
    }

    private class ConversionWarner extends Warner {
        final String uncheckedKey;
        final Type found;
        final Type expected;
        public ConversionWarner(DiagnosticPosition pos, String uncheckedKey, Type found, Type expected) {
            super(pos);
            this.uncheckedKey = uncheckedKey;
            this.found = found;
            this.expected = expected;
        }

        @Override
        public void warn(LintCategory lint) {
            boolean warned = this.warned;
            super.warn(lint);
            if (warned) return; // suppress redundant diagnostics
            switch (lint) {
                case UNCHECKED:
                    Check.this.warnUnchecked(pos(), LintWarnings.ProbFoundReq(diags.fragment(uncheckedKey), found, expected));
                    break;
                case VARARGS:
                    if (method != null &&
                            method.attribute(syms.trustMeType.tsym) != null &&
                            isTrustMeAllowedOnMethod(method) &&
                            !types.isReifiable(method.type.getParameterTypes().last())) {
                        Check.this.lint.logIfEnabled(pos(), LintWarnings.VarargsUnsafeUseVarargsParam(method.params.last()));
                    }
                    break;
                default:
                    throw new AssertionError("Unexpected lint: " + lint);
            }
        }
    }

    public Warner castWarner(DiagnosticPosition pos, Type found, Type expected) {
        return new ConversionWarner(pos, "unchecked.cast.to.type", found, expected);
    }

    public Warner convertWarner(DiagnosticPosition pos, Type found, Type expected) {
        return new ConversionWarner(pos, "unchecked.assign", found, expected);
    }

    public void checkFunctionalInterface(JCClassDecl tree, ClassSymbol cs) {
        Compound functionalType = cs.attribute(syms.functionalInterfaceType.tsym);

        if (functionalType != null) {
            try {
                types.findDescriptorSymbol((TypeSymbol)cs);
            } catch (Types.FunctionDescriptorLookupError ex) {
                DiagnosticPosition pos = tree.pos();
                for (JCAnnotation a : tree.getModifiers().annotations) {
                    if (a.annotationType.type.tsym == syms.functionalInterfaceType.tsym) {
                        pos = a.pos();
                        break;
                    }
                }
                log.error(pos, Errors.BadFunctionalIntfAnno1(ex.getDiagnostic()));
            }
        }
    }

    public void checkImportsResolvable(final JCCompilationUnit toplevel) {
        for (final JCImportBase impBase : toplevel.getImports()) {
            if (!(impBase instanceof JCImport imp))
                continue;
            if (!imp.staticImport || !imp.qualid.hasTag(SELECT))
                continue;
            final JCFieldAccess select = imp.qualid;
            final Symbol origin;
            if (select.name == names.asterisk || (origin = TreeInfo.symbol(select.selected)) == null || origin.kind != TYP)
                continue;

            TypeSymbol site = (TypeSymbol) TreeInfo.symbol(select.selected);
            if (!checkTypeContainsImportableElement(site, site, toplevel.packge, select.name, new HashSet<Symbol>())) {
                log.error(imp.pos(),
                          Errors.CantResolveLocation(KindName.STATIC,
                                                     select.name,
                                                     null,
                                                     null,
                                                     Fragments.Location(kindName(site),
                                                                        site,
                                                                        null)));
            }
        }
    }

    // Check that packages imported are in scope (JLS 7.4.3, 6.3, 6.5.3.1, 6.5.3.2)
    public void checkImportedPackagesObservable(final JCCompilationUnit toplevel) {
        OUTER: for (JCImportBase impBase : toplevel.getImports()) {
            if (impBase instanceof JCImport imp && !imp.staticImport &&
                TreeInfo.name(imp.qualid) == names.asterisk) {
                TypeSymbol tsym = imp.qualid.selected.type.tsym;
                if (tsym.kind == PCK && tsym.members().isEmpty() &&
                    !(Feature.IMPORT_ON_DEMAND_OBSERVABLE_PACKAGES.allowedInSource(source) && tsym.exists())) {
                    log.error(DiagnosticFlag.RESOLVE_ERROR, imp.qualid.selected.pos(), Errors.DoesntExist(tsym));
                }
            }
        }
    }

    private boolean checkTypeContainsImportableElement(TypeSymbol tsym, TypeSymbol origin, PackageSymbol packge, Name name, Set<Symbol> processed) {
        if (tsym == null || !processed.add(tsym))
            return false;

            // also search through inherited names
        if (checkTypeContainsImportableElement(types.supertype(tsym.type).tsym, origin, packge, name, processed))
            return true;

        for (Type t : types.interfaces(tsym.type))
            if (checkTypeContainsImportableElement(t.tsym, origin, packge, name, processed))
                return true;

        for (Symbol sym : tsym.members().getSymbolsByName(name)) {
            if (sym.isStatic() &&
                importAccessible(sym, packge) &&
                sym.isMemberOf(origin, types)) {
                return true;
            }
        }

        return false;
    }

    // is the sym accessible everywhere in packge?
    public boolean importAccessible(Symbol sym, PackageSymbol packge) {
        try {
            int flags = (int)(sym.flags() & AccessFlags);
            switch (flags) {
            default:
            case PUBLIC:
                return true;
            case PRIVATE:
                return false;
            case 0:
            case PROTECTED:
                return sym.packge() == packge;
            }
        } catch (ClassFinder.BadClassFile err) {
            throw err;
        } catch (CompletionFailure ex) {
            return false;
        }
    }

    public void checkLeaksNotAccessible(Env<AttrContext> env, JCClassDecl check) {
        JCCompilationUnit toplevel = env.toplevel;

        if (   toplevel.modle == syms.unnamedModule
            || toplevel.modle == syms.noModule
            || (check.sym.flags() & COMPOUND) != 0) {
            return ;
        }

        ExportsDirective currentExport = findExport(toplevel.packge);

        if (   currentExport == null //not exported
            || currentExport.modules != null) //don't check classes in qualified export
            return ;

        new TreeScanner() {
            Lint lint = env.info.lint;
            boolean inSuperType;

            @Override
            public void visitBlock(JCBlock tree) {
            }
            @Override
            public void visitMethodDef(JCMethodDecl tree) {
                if (!isAPISymbol(tree.sym))
                    return;
                Lint prevLint = lint;
                try {
                    lint = lint.augment(tree.sym);
                    if (lint.isEnabled(LintCategory.EXPORTS)) {
                        super.visitMethodDef(tree);
                    }
                } finally {
                    lint = prevLint;
                }
            }
            @Override
            public void visitVarDef(JCVariableDecl tree) {
                if (!isAPISymbol(tree.sym) && tree.sym.owner.kind != MTH)
                    return;
                Lint prevLint = lint;
                try {
                    lint = lint.augment(tree.sym);
                    if (lint.isEnabled(LintCategory.EXPORTS)) {
                        scan(tree.mods);
                        scan(tree.vartype);
                    }
                } finally {
                    lint = prevLint;
                }
            }
            @Override
            public void visitClassDef(JCClassDecl tree) {
                if (tree != check)
                    return ;

                if (!isAPISymbol(tree.sym))
                    return ;

                Lint prevLint = lint;
                try {
                    lint = lint.augment(tree.sym);
                    if (lint.isEnabled(LintCategory.EXPORTS)) {
                        scan(tree.mods);
                        scan(tree.typarams);
                        try {
                            inSuperType = true;
                            scan(tree.extending);
                            scan(tree.implementing);
                        } finally {
                            inSuperType = false;
                        }
                        scan(tree.defs);
                    }
                } finally {
                    lint = prevLint;
                }
            }
            @Override
            public void visitTypeApply(JCTypeApply tree) {
                scan(tree.clazz);
                boolean oldInSuperType = inSuperType;
                try {
                    inSuperType = false;
                    scan(tree.arguments);
                } finally {
                    inSuperType = oldInSuperType;
                }
            }
            @Override
            public void visitIdent(JCIdent tree) {
                Symbol sym = TreeInfo.symbol(tree);
                if (sym.kind == TYP && !sym.type.hasTag(TYPEVAR)) {
                    checkVisible(tree.pos(), sym, toplevel.packge, inSuperType);
                }
            }

            @Override
            public void visitSelect(JCFieldAccess tree) {
                Symbol sym = TreeInfo.symbol(tree);
                Symbol sitesym = TreeInfo.symbol(tree.selected);
                if (sym.kind == TYP && sitesym.kind == PCK) {
                    checkVisible(tree.pos(), sym, toplevel.packge, inSuperType);
                } else {
                    super.visitSelect(tree);
                }
            }

            @Override
            public void visitAnnotation(JCAnnotation tree) {
                if (tree.attribute.type.tsym.getAnnotation(java.lang.annotation.Documented.class) != null)
                    super.visitAnnotation(tree);
            }

        }.scan(check);
    }
        //where:
        private ExportsDirective findExport(PackageSymbol pack) {
            for (ExportsDirective d : pack.modle.exports) {
                if (d.packge == pack)
                    return d;
            }

            return null;
        }
        private boolean isAPISymbol(Symbol sym) {
            while (sym.kind != PCK) {
                if ((sym.flags() & Flags.PUBLIC) == 0 && (sym.flags() & Flags.PROTECTED) == 0) {
                    return false;
                }
                sym = sym.owner;
            }
            return true;
        }
        private void checkVisible(DiagnosticPosition pos, Symbol what, PackageSymbol inPackage, boolean inSuperType) {
            if (!isAPISymbol(what) && !inSuperType) { //package private/private element
                log.warning(pos, LintWarnings.LeaksNotAccessible(kindName(what), what, what.packge().modle));
                return ;
            }

            PackageSymbol whatPackage = what.packge();
            ExportsDirective whatExport = findExport(whatPackage);
            ExportsDirective inExport = findExport(inPackage);

            if (whatExport == null) { //package not exported:
                log.warning(pos, LintWarnings.LeaksNotAccessibleUnexported(kindName(what), what, what.packge().modle));
                return ;
            }

            if (whatExport.modules != null) {
                if (inExport.modules == null || !whatExport.modules.containsAll(inExport.modules)) {
                    log.warning(pos, LintWarnings.LeaksNotAccessibleUnexportedQualified(kindName(what), what, what.packge().modle));
                }
            }

            if (whatPackage.modle != inPackage.modle && whatPackage.modle != syms.java_base) {
                //check that relativeTo.modle requires transitive what.modle, somehow:
                List<ModuleSymbol> todo = List.of(inPackage.modle);

                while (todo.nonEmpty()) {
                    ModuleSymbol current = todo.head;
                    todo = todo.tail;
                    if (current == whatPackage.modle)
                        return ; //OK
                    if ((current.flags() & Flags.AUTOMATIC_MODULE) != 0)
                        continue; //for automatic modules, don't look into their dependencies
                    for (RequiresDirective req : current.requires) {
                        if (req.isTransitive()) {
                            todo = todo.prepend(req.module);
                        }
                    }
                }

                log.warning(pos, LintWarnings.LeaksNotAccessibleNotRequiredTransitive(kindName(what), what, what.packge().modle));
            }
        }

    void checkModuleExists(final DiagnosticPosition pos, ModuleSymbol msym) {
        if (msym.kind != MDL) {
            deferredLintHandler.report(_ ->
                lint.logIfEnabled(pos, LintWarnings.ModuleNotFound(msym)));
        }
    }

    void checkPackageExistsForOpens(final DiagnosticPosition pos, PackageSymbol packge) {
        if (packge.members().isEmpty() &&
            ((packge.flags() & Flags.HAS_RESOURCE) == 0)) {
            deferredLintHandler.report(_ ->
                lint.logIfEnabled(pos, LintWarnings.PackageEmptyOrNotFound(packge)));
        }
    }

    void checkModuleRequires(final DiagnosticPosition pos, final RequiresDirective rd) {
        if ((rd.module.flags() & Flags.AUTOMATIC_MODULE) != 0) {
            deferredLintHandler.report(_ -> {
                if (rd.isTransitive() && lint.isEnabled(LintCategory.REQUIRES_TRANSITIVE_AUTOMATIC)) {
                    log.warning(pos, LintWarnings.RequiresTransitiveAutomatic);
                } else {
                    lint.logIfEnabled(pos, LintWarnings.RequiresAutomatic);
                }
            });
        }
    }

    /**
     * Verify the case labels conform to the constraints. Checks constraints related
     * combinations of patterns and other labels.
     *
     * @param cases the cases that should be checked.
     */
    void checkSwitchCaseStructure(List<JCCase> cases) {
        for (List<JCCase> l = cases; l.nonEmpty(); l = l.tail) {
            JCCase c = l.head;
            if (c.labels.head instanceof JCConstantCaseLabel constLabel) {
                if (TreeInfo.isNull(constLabel.expr)) {
                    if (c.labels.tail.nonEmpty()) {
                        if (c.labels.tail.head instanceof JCDefaultCaseLabel defLabel) {
                            if (c.labels.tail.tail.nonEmpty()) {
                                log.error(c.labels.tail.tail.head.pos(), Errors.InvalidCaseLabelCombination);
                            }
                        } else {
                            log.error(c.labels.tail.head.pos(), Errors.InvalidCaseLabelCombination);
                        }
                    }
                } else {
                    for (JCCaseLabel label : c.labels.tail) {
                        if (!(label instanceof JCConstantCaseLabel) || TreeInfo.isNullCaseLabel(label)) {
                            log.error(label.pos(), Errors.InvalidCaseLabelCombination);
                            break;
                        }
                    }
                }
            } else if (c.labels.tail.nonEmpty()) {
                var patterCaseLabels = c.labels.stream().filter(ll -> ll instanceof JCPatternCaseLabel).map(cl -> (JCPatternCaseLabel)cl);
                var allUnderscore = patterCaseLabels.allMatch(pcl -> !hasBindings(pcl.getPattern()));

                if (!allUnderscore) {
                    log.error(c.labels.tail.head.pos(), Errors.FlowsThroughFromPattern);
                }

                boolean allPatternCaseLabels = c.labels.stream().allMatch(p -> p instanceof JCPatternCaseLabel);

                if (allPatternCaseLabels) {
                    preview.checkSourceLevel(c.labels.tail.head.pos(), Feature.UNNAMED_VARIABLES);
                }

                for (JCCaseLabel label : c.labels.tail) {
                    if (label instanceof JCConstantCaseLabel) {
                        log.error(label.pos(), Errors.InvalidCaseLabelCombination);
                        break;
                    }
                }
            }
        }

        boolean isCaseStatementGroup = cases.nonEmpty() &&
                                       cases.head.caseKind == CaseTree.CaseKind.STATEMENT;

        if (isCaseStatementGroup) {
            boolean previousCompletessNormally = false;
            for (List<JCCase> l = cases; l.nonEmpty(); l = l.tail) {
                JCCase c = l.head;
                if (previousCompletessNormally &&
                    c.stats.nonEmpty() &&
                    c.labels.head instanceof JCPatternCaseLabel patternLabel &&
                    (hasBindings(patternLabel.pat) || hasBindings(c.guard))) {
                    log.error(c.labels.head.pos(), Errors.FlowsThroughToPattern);
                } else if (c.stats.isEmpty() &&
                           c.labels.head instanceof JCPatternCaseLabel patternLabel &&
                           (hasBindings(patternLabel.pat) || hasBindings(c.guard)) &&
                           hasStatements(l.tail)) {
                    log.error(c.labels.head.pos(), Errors.FlowsThroughFromPattern);
                }
                previousCompletessNormally = c.completesNormally;
            }
        }
    }

    boolean hasBindings(JCTree p) {
        boolean[] bindings = new boolean[1];

        new TreeScanner() {
            @Override
            public void visitBindingPattern(JCBindingPattern tree) {
                bindings[0] |= !tree.var.sym.isUnnamedVariable();
                super.visitBindingPattern(tree);
            }
        }.scan(p);

        return bindings[0];
    }

    boolean hasStatements(List<JCCase> cases) {
        for (List<JCCase> l = cases; l.nonEmpty(); l = l.tail) {
            if (l.head.stats.nonEmpty()) {
                return true;
            }
        }

        return false;
    }
    void checkSwitchCaseLabelDominated(JCCaseLabel unconditionalCaseLabel, List<JCCase> cases) {
        List<Pair<JCCase, JCCaseLabel>> caseLabels = List.nil();
        boolean seenDefault = false;
        boolean seenDefaultLabel = false;
        boolean warnDominatedByDefault = false;
        boolean unconditionalFound = false;

        for (List<JCCase> l = cases; l.nonEmpty(); l = l.tail) {
            JCCase c = l.head;
            for (JCCaseLabel label : c.labels) {
                if (label.hasTag(DEFAULTCASELABEL)) {
                    seenDefault = true;
                    seenDefaultLabel |=
                            TreeInfo.isNullCaseLabel(c.labels.head);
                    continue;
                }
                if (TreeInfo.isNullCaseLabel(label)) {
                    if (seenDefault) {
                        log.error(label.pos(), Errors.PatternDominated);
                    }
                    continue;
                }
                if (seenDefault && !warnDominatedByDefault) {
                    if (label.hasTag(PATTERNCASELABEL) ||
                        (label instanceof JCConstantCaseLabel && seenDefaultLabel)) {
                        log.error(label.pos(), Errors.PatternDominated);
                        warnDominatedByDefault = true;
                    }
                }
                Type currentType = labelType(label);
                for (Pair<JCCase, JCCaseLabel> caseAndLabel : caseLabels) {
                    JCCase testCase = caseAndLabel.fst;
                    JCCaseLabel testCaseLabel = caseAndLabel.snd;
                    Type testType = labelType(testCaseLabel);
                    boolean dominated = false;
                    if (types.isUnconditionallyExact(currentType, testType) &&
                        !currentType.hasTag(ERROR) && !testType.hasTag(ERROR)) {
                        //the current label is potentially dominated by the existing (test) label, check:
                        if (label instanceof JCConstantCaseLabel) {
                            dominated |= !(testCaseLabel instanceof JCConstantCaseLabel) &&
                                         TreeInfo.unguardedCase(testCase);
                        } else if (label instanceof JCPatternCaseLabel patternCL &&
                                   testCaseLabel instanceof JCPatternCaseLabel testPatternCaseLabel &&
                                   (testCase.equals(c) || TreeInfo.unguardedCase(testCase))) {
                            dominated = patternDominated(testPatternCaseLabel.pat,
                                                         patternCL.pat);
                        }
                    }

                    if (dominated) {
                        log.error(label.pos(), Errors.PatternDominated);
                    }
                }
                caseLabels = caseLabels.prepend(Pair.of(c, label));
            }
        }
    }
        //where:
        private Type labelType(JCCaseLabel label) {
            return types.erasure(switch (label.getTag()) {
                case PATTERNCASELABEL -> ((JCPatternCaseLabel) label).pat.type;
                case CONSTANTCASELABEL -> ((JCConstantCaseLabel) label).expr.type;
                default -> throw Assert.error("Unexpected tree kind: " + label.getTag());
            });
        }
        private boolean patternDominated(JCPattern existingPattern, JCPattern currentPattern) {
            Type existingPatternType = types.erasure(existingPattern.type);
            Type currentPatternType = types.erasure(currentPattern.type);
            if (!types.isUnconditionallyExact(currentPatternType, existingPatternType)) {
                return false;
            }
            if (currentPattern instanceof JCBindingPattern ||
                currentPattern instanceof JCAnyPattern) {
                return existingPattern instanceof JCBindingPattern ||
                       existingPattern instanceof JCAnyPattern;
            } else if (currentPattern instanceof JCRecordPattern currentRecordPattern) {
                if (existingPattern instanceof JCBindingPattern ||
                    existingPattern instanceof JCAnyPattern) {
                    return true;
                } else if (existingPattern instanceof JCRecordPattern existingRecordPattern) {
                    List<JCPattern> existingNested = existingRecordPattern.nested;
                    List<JCPattern> currentNested = currentRecordPattern.nested;
                    if (existingNested.size() != currentNested.size()) {
                        return false;
                    }
                    while (existingNested.nonEmpty()) {
                        if (!patternDominated(existingNested.head, currentNested.head)) {
                            return false;
                        }
                        existingNested = existingNested.tail;
                        currentNested = currentNested.tail;
                    }
                    return true;
                } else {
                    Assert.error("Unknown pattern: " + existingPattern.getTag());
                }
            } else {
                Assert.error("Unknown pattern: " + currentPattern.getTag());
            }
            return false;
        }

    /** check if a type is a subtype of Externalizable, if that is available. */
    boolean isExternalizable(Type t) {
        try {
            syms.externalizableType.complete();
        } catch (CompletionFailure e) {
            return false;
        }
        return types.isSubtype(t, syms.externalizableType);
    }

    /**
     * Check structure of serialization declarations.
     */
    public void checkSerialStructure(JCClassDecl tree, ClassSymbol c) {
        (new SerialTypeVisitor()).visit(c, tree);
    }

    /**
     * This visitor will warn if a serialization-related field or
     * method is declared in a suspicious or incorrect way. In
     * particular, it will warn for cases where the runtime
     * serialization mechanism will silently ignore a mis-declared
     * entity.
     *
     * Distinguished serialization-related fields and methods:
     *
     * Methods:
     *
     * private void writeObject(ObjectOutputStream stream) throws IOException
     * ANY-ACCESS-MODIFIER Object writeReplace() throws ObjectStreamException
     *
     * private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException
     * private void readObjectNoData() throws ObjectStreamException
     * ANY-ACCESS-MODIFIER Object readResolve() throws ObjectStreamException
     *
     * Fields:
     *
     * private static final long serialVersionUID
     * private static final ObjectStreamField[] serialPersistentFields
     *
     * Externalizable: methods defined on the interface
     * public void writeExternal(ObjectOutput) throws IOException
     * public void readExternal(ObjectInput) throws IOException
     */
    private class SerialTypeVisitor extends ElementKindVisitor14<Void, JCClassDecl> {
        SerialTypeVisitor() {
            this.lint = Check.this.lint;
        }

        private static final Set<String> serialMethodNames =
            Set.of("writeObject", "writeReplace",
                   "readObject",  "readObjectNoData",
                   "readResolve");

        private static final Set<String> serialFieldNames =
            Set.of("serialVersionUID", "serialPersistentFields");

        // Type of serialPersistentFields
        private final Type OSF_TYPE = new Type.ArrayType(syms.objectStreamFieldType, syms.arrayClass);

        Lint lint;

        @Override
        public Void defaultAction(Element e, JCClassDecl p) {
            throw new IllegalArgumentException(Objects.requireNonNullElse(e.toString(), ""));
        }

        @Override
        public Void visitType(TypeElement e, JCClassDecl p) {
            runUnderLint(e, p, (symbol, param) -> super.visitType(symbol, param));
            return null;
        }

        @Override
        public Void visitTypeAsClass(TypeElement e,
                                     JCClassDecl p) {
            // Anonymous classes filtered out by caller.

            ClassSymbol c = (ClassSymbol)e;

            checkCtorAccess(p, c);

            // Check for missing serialVersionUID; check *not* done
            // for enums or records.
            VarSymbol svuidSym = null;
            for (Symbol sym : c.members().getSymbolsByName(names.serialVersionUID)) {
                if (sym.kind == VAR) {
                    svuidSym = (VarSymbol)sym;
                    break;
                }
            }

            if (svuidSym == null) {
                log.warning(p.pos(), LintWarnings.MissingSVUID(c));
            }

            // Check for serialPersistentFields to gate checks for
            // non-serializable non-transient instance fields
            boolean serialPersistentFieldsPresent =
                    c.members()
                     .getSymbolsByName(names.serialPersistentFields, sym -> sym.kind == VAR)
                     .iterator()
                     .hasNext();

            // Check declarations of serialization-related methods and
            // fields
            for(Symbol el : c.getEnclosedElements()) {
                runUnderLint(el, p, (enclosed, tree) -> {
                    String name = null;
                    switch(enclosed.getKind()) {
                    case FIELD -> {
                        if (!serialPersistentFieldsPresent) {
                            var flags = enclosed.flags();
                            if ( ((flags & TRANSIENT) == 0) &&
                                 ((flags & STATIC) == 0)) {
                                Type varType = enclosed.asType();
                                if (!canBeSerialized(varType)) {
                                    // Note per JLS arrays are
                                    // serializable even if the
                                    // component type is not.
                                    log.warning(
                                            TreeInfo.diagnosticPositionFor(enclosed, tree),
                                                LintWarnings.NonSerializableInstanceField);
                                } else if (varType.hasTag(ARRAY)) {
                                    ArrayType arrayType = (ArrayType)varType;
                                    Type elementType = arrayType.elemtype;
                                    while (elementType.hasTag(ARRAY)) {
                                        arrayType = (ArrayType)elementType;
                                        elementType = arrayType.elemtype;
                                    }
                                    if (!canBeSerialized(elementType)) {
                                        log.warning(
                                                TreeInfo.diagnosticPositionFor(enclosed, tree),
                                                    LintWarnings.NonSerializableInstanceFieldArray(elementType));
                                    }
                                }
                            }
                        }

                        name = enclosed.getSimpleName().toString();
                        if (serialFieldNames.contains(name)) {
                            VarSymbol field = (VarSymbol)enclosed;
                            switch (name) {
                            case "serialVersionUID"       ->  checkSerialVersionUID(tree, e, field);
                            case "serialPersistentFields" ->  checkSerialPersistentFields(tree, e, field);
                            default -> throw new AssertionError();
                            }
                        }
                    }

                    // Correctly checking the serialization-related
                    // methods is subtle. For the methods declared to be
                    // private or directly declared in the class, the
                    // enclosed elements of the class can be checked in
                    // turn. However, writeReplace and readResolve can be
                    // declared in a superclass and inherited. Note that
                    // the runtime lookup walks the superclass chain
                    // looking for writeReplace/readResolve via
                    // Class.getDeclaredMethod. This differs from calling
                    // Elements.getAllMembers(TypeElement) as the latter
                    // will also pull in default methods from
                    // superinterfaces. In other words, the runtime checks
                    // (which long predate default methods on interfaces)
                    // do not admit the possibility of inheriting methods
                    // this way, a difference from general inheritance.

                    // The current implementation just checks the enclosed
                    // elements and does not directly check the inherited
                    // methods. If all the types are being checked this is
                    // less of a concern; however, there are cases that
                    // could be missed. In particular, readResolve and
                    // writeReplace could, in principle, by inherited from
                    // a non-serializable superclass and thus not checked
                    // even if compiled with a serializable child class.
                    case METHOD -> {
                        var method = (MethodSymbol)enclosed;
                        name = method.getSimpleName().toString();
                        if (serialMethodNames.contains(name)) {
                            switch (name) {
                            case "writeObject"      -> checkWriteObject(tree, e, method);
                            case "writeReplace"     -> checkWriteReplace(tree,e, method);
                            case "readObject"       -> checkReadObject(tree,e, method);
                            case "readObjectNoData" -> checkReadObjectNoData(tree, e, method);
                            case "readResolve"      -> checkReadResolve(tree, e, method);
                            default ->  throw new AssertionError();
                            }
                        }
                    }
                    }
                });
            }

            return null;
        }

        boolean canBeSerialized(Type type) {
            return type.isPrimitive() || rs.isSerializable(type);
        }

        /**
         * Check that Externalizable class needs a public no-arg
         * constructor.
         *
         * Check that a Serializable class has access to the no-arg
         * constructor of its first nonserializable superclass.
         */
        private void checkCtorAccess(JCClassDecl tree, ClassSymbol c) {
            if (isExternalizable(c.type)) {
                for(var sym : c.getEnclosedElements()) {
                    if (sym.isConstructor() &&
                        ((sym.flags() & PUBLIC) == PUBLIC)) {
                        if (((MethodSymbol)sym).getParameters().isEmpty()) {
                            return;
                        }
                    }
                }
                log.warning(tree.pos(),
                            LintWarnings.ExternalizableMissingPublicNoArgCtor);
            } else {
                // Approximate access to the no-arg constructor up in
                // the superclass chain by checking that the
                // constructor is not private. This may not handle
                // some cross-package situations correctly.
                Type superClass = c.getSuperclass();
                // java.lang.Object is *not* Serializable so this loop
                // should terminate.
                while (rs.isSerializable(superClass) ) {
                    try {
                        superClass = (Type)((TypeElement)(((DeclaredType)superClass)).asElement()).getSuperclass();
                    } catch(ClassCastException cce) {
                        return ; // Don't try to recover
                    }
                }
                // Non-Serializable superclass
                try {
                    ClassSymbol supertype = ((ClassSymbol)(((DeclaredType)superClass).asElement()));
                    for(var sym : supertype.getEnclosedElements()) {
                        if (sym.isConstructor()) {
                            MethodSymbol ctor = (MethodSymbol)sym;
                            if (ctor.getParameters().isEmpty()) {
                                if (((ctor.flags() & PRIVATE) == PRIVATE) ||
                                    // Handle nested classes and implicit this$0
                                    (supertype.getNestingKind() == NestingKind.MEMBER &&
                                     ((supertype.flags() & STATIC) == 0)))
                                    log.warning(tree.pos(),
                                                LintWarnings.SerializableMissingAccessNoArgCtor(supertype.getQualifiedName()));
                            }
                        }
                    }
                } catch (ClassCastException cce) {
                    return ; // Don't try to recover
                }
                return;
            }
        }

        private void checkSerialVersionUID(JCClassDecl tree, Element e, VarSymbol svuid) {
            // To be effective, serialVersionUID must be marked static
            // and final, but private is recommended. But alas, in
            // practice there are many non-private serialVersionUID
            // fields.
             if ((svuid.flags() & (STATIC | FINAL)) !=
                 (STATIC | FINAL)) {
                 log.warning(
                         TreeInfo.diagnosticPositionFor(svuid, tree),
                             LintWarnings.ImproperSVUID((Symbol)e));
             }

             // check svuid has type long
             if (!svuid.type.hasTag(LONG)) {
                 log.warning(
                         TreeInfo.diagnosticPositionFor(svuid, tree),
                             LintWarnings.LongSVUID((Symbol)e));
             }

             if (svuid.getConstValue() == null)
                 log.warning(
                         TreeInfo.diagnosticPositionFor(svuid, tree),
                             LintWarnings.ConstantSVUID((Symbol)e));
        }

        private void checkSerialPersistentFields(JCClassDecl tree, Element e, VarSymbol spf) {
            // To be effective, serialPersisentFields must be private, static, and final.
             if ((spf.flags() & (PRIVATE | STATIC | FINAL)) !=
                 (PRIVATE | STATIC | FINAL)) {
                 log.warning(
                         TreeInfo.diagnosticPositionFor(spf, tree),
                             LintWarnings.ImproperSPF);
             }

             if (!types.isSameType(spf.type, OSF_TYPE)) {
                 log.warning(
                         TreeInfo.diagnosticPositionFor(spf, tree),
                             LintWarnings.OSFArraySPF);
             }

            if (isExternalizable((Type)(e.asType()))) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(spf, tree),
                            LintWarnings.IneffectualSerialFieldExternalizable);
            }

            // Warn if serialPersistentFields is initialized to a
            // literal null.
            JCTree spfDecl = TreeInfo.declarationFor(spf, tree);
            if (spfDecl != null && spfDecl.getTag() == VARDEF) {
                JCVariableDecl variableDef = (JCVariableDecl) spfDecl;
                JCExpression initExpr = variableDef.init;
                 if (initExpr != null && TreeInfo.isNull(initExpr)) {
                     log.warning(initExpr.pos(),
                                 LintWarnings.SPFNullInit);
                 }
            }
        }

        private void checkWriteObject(JCClassDecl tree, Element e, MethodSymbol method) {
            // The "synchronized" modifier is seen in the wild on
            // readObject and writeObject methods and is generally
            // innocuous.

            // private void writeObject(ObjectOutputStream stream) throws IOException
            checkPrivateNonStaticMethod(tree, method);
            checkReturnType(tree, e, method, syms.voidType);
            checkOneArg(tree, e, method, syms.objectOutputStreamType);
            checkExceptions(tree, e, method, syms.ioExceptionType);
            checkExternalizable(tree, e, method);
        }

        private void checkWriteReplace(JCClassDecl tree, Element e, MethodSymbol method) {
            // ANY-ACCESS-MODIFIER Object writeReplace() throws
            // ObjectStreamException

            // Excluding abstract, could have a more complicated
            // rule based on abstract-ness of the class
            checkConcreteInstanceMethod(tree, e, method);
            checkReturnType(tree, e, method, syms.objectType);
            checkNoArgs(tree, e, method);
            checkExceptions(tree, e, method, syms.objectStreamExceptionType);
        }

        private void checkReadObject(JCClassDecl tree, Element e, MethodSymbol method) {
            // The "synchronized" modifier is seen in the wild on
            // readObject and writeObject methods and is generally
            // innocuous.

            // private void readObject(ObjectInputStream stream)
            //   throws IOException, ClassNotFoundException
            checkPrivateNonStaticMethod(tree, method);
            checkReturnType(tree, e, method, syms.voidType);
            checkOneArg(tree, e, method, syms.objectInputStreamType);
            checkExceptions(tree, e, method, syms.ioExceptionType, syms.classNotFoundExceptionType);
            checkExternalizable(tree, e, method);
        }

        private void checkReadObjectNoData(JCClassDecl tree, Element e, MethodSymbol method) {
            // private void readObjectNoData() throws ObjectStreamException
            checkPrivateNonStaticMethod(tree, method);
            checkReturnType(tree, e, method, syms.voidType);
            checkNoArgs(tree, e, method);
            checkExceptions(tree, e, method, syms.objectStreamExceptionType);
            checkExternalizable(tree, e, method);
        }

        private void checkReadResolve(JCClassDecl tree, Element e, MethodSymbol method) {
            // ANY-ACCESS-MODIFIER Object readResolve()
            // throws ObjectStreamException

            // Excluding abstract, could have a more complicated
            // rule based on abstract-ness of the class
            checkConcreteInstanceMethod(tree, e, method);
            checkReturnType(tree,e, method, syms.objectType);
            checkNoArgs(tree, e, method);
            checkExceptions(tree, e, method, syms.objectStreamExceptionType);
        }

        private void checkWriteExternalRecord(JCClassDecl tree, Element e, MethodSymbol method, boolean isExtern) {
            //public void writeExternal(ObjectOutput) throws IOException
            checkExternMethodRecord(tree, e, method, syms.objectOutputType, isExtern);
        }

        private void checkReadExternalRecord(JCClassDecl tree, Element e, MethodSymbol method, boolean isExtern) {
            // public void readExternal(ObjectInput) throws IOException
            checkExternMethodRecord(tree, e, method, syms.objectInputType, isExtern);
         }

        private void checkExternMethodRecord(JCClassDecl tree, Element e, MethodSymbol method, Type argType,
                                             boolean isExtern) {
            if (isExtern && isExternMethod(tree, e, method, argType)) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.IneffectualExternalizableMethodRecord(method.getSimpleName().toString()));
            }
        }

        void checkPrivateNonStaticMethod(JCClassDecl tree, MethodSymbol method) {
            var flags = method.flags();
            if ((flags & PRIVATE) == 0) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.SerialMethodNotPrivate(method.getSimpleName()));
            }

            if ((flags & STATIC) != 0) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.SerialMethodStatic(method.getSimpleName()));
            }
        }

        /**
         * Per section 1.12 "Serialization of Enum Constants" of
         * the serialization specification, due to the special
         * serialization handling of enums, any writeObject,
         * readObject, writeReplace, and readResolve methods are
         * ignored as are serialPersistentFields and
         * serialVersionUID fields.
         */
        @Override
        public Void visitTypeAsEnum(TypeElement e,
                                    JCClassDecl p) {
            boolean isExtern = isExternalizable((Type)e.asType());
            for(Element el : e.getEnclosedElements()) {
                runUnderLint(el, p, (enclosed, tree) -> {
                    String name = enclosed.getSimpleName().toString();
                    switch(enclosed.getKind()) {
                    case FIELD -> {
                        var field = (VarSymbol)enclosed;
                        if (serialFieldNames.contains(name)) {
                            log.warning(
                                    TreeInfo.diagnosticPositionFor(field, tree),
                                        LintWarnings.IneffectualSerialFieldEnum(name));
                        }
                    }

                    case METHOD -> {
                        var method = (MethodSymbol)enclosed;
                        if (serialMethodNames.contains(name)) {
                            log.warning(
                                    TreeInfo.diagnosticPositionFor(method, tree),
                                        LintWarnings.IneffectualSerialMethodEnum(name));
                        }

                        if (isExtern) {
                            switch(name) {
                            case "writeExternal" -> checkWriteExternalEnum(tree, e, method);
                            case "readExternal"  -> checkReadExternalEnum(tree, e, method);
                            }
                        }
                    }

                    // Also perform checks on any class bodies of enum constants, see JLS 8.9.1.
                    case ENUM_CONSTANT -> {
                        var field = (VarSymbol)enclosed;
                        JCVariableDecl decl = (JCVariableDecl) TreeInfo.declarationFor(field, p);
                        if (decl.init instanceof JCNewClass nc && nc.def != null) {
                            ClassSymbol enumConstantType = nc.def.sym;
                            visitTypeAsEnum(enumConstantType, p);
                        }
                    }

                    }});
            }
            return null;
        }

        private void checkWriteExternalEnum(JCClassDecl tree, Element e, MethodSymbol method) {
            //public void writeExternal(ObjectOutput) throws IOException
            checkExternMethodEnum(tree, e, method, syms.objectOutputType);
        }

        private void checkReadExternalEnum(JCClassDecl tree, Element e, MethodSymbol method) {
             // public void readExternal(ObjectInput) throws IOException
            checkExternMethodEnum(tree, e, method, syms.objectInputType);
         }

        private void checkExternMethodEnum(JCClassDecl tree, Element e, MethodSymbol method, Type argType) {
            if (isExternMethod(tree, e, method, argType)) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.IneffectualExternMethodEnum(method.getSimpleName().toString()));
            }
        }

        private boolean isExternMethod(JCClassDecl tree, Element e, MethodSymbol method, Type argType) {
            long flags = method.flags();
            Type rtype = method.getReturnType();

            // Not necessary to check throws clause in this context
            return (flags & PUBLIC) != 0 && (flags & STATIC) == 0 &&
                types.isSameType(syms.voidType, rtype) &&
                hasExactlyOneArgWithType(tree, e, method, argType);
        }

        /**
         * Most serialization-related fields and methods on interfaces
         * are ineffectual or problematic.
         */
        @Override
        public Void visitTypeAsInterface(TypeElement e,
                                         JCClassDecl p) {
            for(Element el : e.getEnclosedElements()) {
                runUnderLint(el, p, (enclosed, tree) -> {
                    String name = null;
                    switch(enclosed.getKind()) {
                    case FIELD -> {
                        var field = (VarSymbol)enclosed;
                        name = field.getSimpleName().toString();
                        switch(name) {
                        case "serialPersistentFields" -> {
                            log.warning(
                                    TreeInfo.diagnosticPositionFor(field, tree),
                                        LintWarnings.IneffectualSerialFieldInterface);
                        }

                        case "serialVersionUID" -> {
                            checkSerialVersionUID(tree, e, field);
                        }
                        }
                    }

                    case METHOD -> {
                        var method = (MethodSymbol)enclosed;
                        name = enclosed.getSimpleName().toString();
                        if (serialMethodNames.contains(name)) {
                            switch (name) {
                            case
                                "readObject",
                                "readObjectNoData",
                                "writeObject"      -> checkPrivateMethod(tree, e, method);

                            case
                                "writeReplace",
                                "readResolve"      -> checkDefaultIneffective(tree, e, method);

                            default ->  throw new AssertionError();
                            }

                        }
                    }}
                });
            }

            return null;
        }

        private void checkPrivateMethod(JCClassDecl tree,
                                        Element e,
                                        MethodSymbol method) {
            if ((method.flags() & PRIVATE) == 0) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.NonPrivateMethodWeakerAccess);
            }
        }

        private void checkDefaultIneffective(JCClassDecl tree,
                                             Element e,
                                             MethodSymbol method) {
            if ((method.flags() & DEFAULT) == DEFAULT) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.DefaultIneffective);

            }
        }

        @Override
        public Void visitTypeAsAnnotationType(TypeElement e,
                                              JCClassDecl p) {
            // Per the JLS, annotation types are not serializeable
            return null;
        }

        /**
         * From the Java Object Serialization Specification, 1.13
         * Serialization of Records:
         *
         * "The process by which record objects are serialized or
         * externalized cannot be customized; any class-specific
         * writeObject, readObject, readObjectNoData, writeExternal,
         * and readExternal methods defined by record classes are
         * ignored during serialization and deserialization. However,
         * a substitute object to be serialized or a designate
         * replacement may be specified, by the writeReplace and
         * readResolve methods, respectively. Any
         * serialPersistentFields field declaration is
         * ignored. Documenting serializable fields and data for
         * record classes is unnecessary, since there is no variation
         * in the serial form, other than whether a substitute or
         * replacement object is used. The serialVersionUID of a
         * record class is 0L unless explicitly declared. The
         * requirement for matching serialVersionUID values is waived
         * for record classes."
         */
        @Override
        public Void visitTypeAsRecord(TypeElement e,
                                      JCClassDecl p) {
            boolean isExtern = isExternalizable((Type)e.asType());
            for(Element el : e.getEnclosedElements()) {
                runUnderLint(el, p, (enclosed, tree) -> {
                    String name = enclosed.getSimpleName().toString();
                    switch(enclosed.getKind()) {
                    case FIELD -> {
                        var field = (VarSymbol)enclosed;
                        switch(name) {
                        case "serialPersistentFields" -> {
                            log.warning(
                                    TreeInfo.diagnosticPositionFor(field, tree),
                                        LintWarnings.IneffectualSerialFieldRecord);
                        }

                        case "serialVersionUID" -> {
                            // Could generate additional warning that
                            // svuid value is not checked to match for
                            // records.
                            checkSerialVersionUID(tree, e, field);
                        }}
                    }

                    case METHOD -> {
                        var method = (MethodSymbol)enclosed;
                        switch(name) {
                        case "writeReplace" -> checkWriteReplace(tree, e, method);
                        case "readResolve"  -> checkReadResolve(tree, e, method);

                        case "writeExternal" -> checkWriteExternalRecord(tree, e, method, isExtern);
                        case "readExternal"  -> checkReadExternalRecord(tree, e, method, isExtern);

                        default -> {
                            if (serialMethodNames.contains(name)) {
                                log.warning(
                                        TreeInfo.diagnosticPositionFor(method, tree),
                                            LintWarnings.IneffectualSerialMethodRecord(name));
                            }
                        }}
                    }}});
            }
            return null;
        }

        void checkConcreteInstanceMethod(JCClassDecl tree,
                                         Element enclosing,
                                         MethodSymbol method) {
            if ((method.flags() & (STATIC | ABSTRACT)) != 0) {
                    log.warning(
                            TreeInfo.diagnosticPositionFor(method, tree),
                                LintWarnings.SerialConcreteInstanceMethod(method.getSimpleName()));
            }
        }

        private void checkReturnType(JCClassDecl tree,
                                     Element enclosing,
                                     MethodSymbol method,
                                     Type expectedReturnType) {
            // Note: there may be complications checking writeReplace
            // and readResolve since they return Object and could, in
            // principle, have covariant overrides and any synthetic
            // bridge method would not be represented here for
            // checking.
            Type rtype = method.getReturnType();
            if (!types.isSameType(expectedReturnType, rtype)) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.SerialMethodUnexpectedReturnType(method.getSimpleName(),
                                                                      rtype, expectedReturnType));
            }
        }

        private void checkOneArg(JCClassDecl tree,
                                 Element enclosing,
                                 MethodSymbol method,
                                 Type expectedType) {
            String name = method.getSimpleName().toString();

            var parameters= method.getParameters();

            if (parameters.size() != 1) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.SerialMethodOneArg(method.getSimpleName(), parameters.size()));
                return;
            }

            Type parameterType = parameters.get(0).asType();
            if (!types.isSameType(parameterType, expectedType)) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.SerialMethodParameterType(method.getSimpleName(),
                                                               expectedType,
                                                               parameterType));
            }
        }

        private boolean hasExactlyOneArgWithType(JCClassDecl tree,
                                                 Element enclosing,
                                                 MethodSymbol method,
                                                 Type expectedType) {
            var parameters = method.getParameters();
            return (parameters.size() == 1) &&
                types.isSameType(parameters.get(0).asType(), expectedType);
        }


        private void checkNoArgs(JCClassDecl tree, Element enclosing, MethodSymbol method) {
            var parameters = method.getParameters();
            if (!parameters.isEmpty()) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(parameters.get(0), tree),
                            LintWarnings.SerialMethodNoArgs(method.getSimpleName()));
            }
        }

        private void checkExternalizable(JCClassDecl tree, Element enclosing, MethodSymbol method) {
            // If the enclosing class is externalizable, warn for the method
            if (isExternalizable((Type)enclosing.asType())) {
                log.warning(
                        TreeInfo.diagnosticPositionFor(method, tree),
                            LintWarnings.IneffectualSerialMethodExternalizable(method.getSimpleName()));
            }
            return;
        }

        private void checkExceptions(JCClassDecl tree,
                                     Element enclosing,
                                     MethodSymbol method,
                                     Type... declaredExceptions) {
            for (Type thrownType: method.getThrownTypes()) {
                // For each exception in the throws clause of the
                // method, if not an Error and not a RuntimeException,
                // check if the exception is a subtype of a declared
                // exception from the throws clause of the
                // serialization method in question.
                if (types.isSubtype(thrownType, syms.runtimeExceptionType) ||
                    types.isSubtype(thrownType, syms.errorType) ) {
                    continue;
                } else {
                    boolean declared = false;
                    for (Type declaredException : declaredExceptions) {
                        if (types.isSubtype(thrownType, declaredException)) {
                            declared = true;
                            continue;
                        }
                    }
                    if (!declared) {
                        log.warning(
                                TreeInfo.diagnosticPositionFor(method, tree),
                                    LintWarnings.SerialMethodUnexpectedException(method.getSimpleName(),
                                                                             thrownType));
                    }
                }
            }
            return;
        }

        private <E extends Element> Void runUnderLint(E symbol, JCClassDecl p, BiConsumer<E, JCClassDecl> task) {
            Lint prevLint = lint;
            try {
                lint = lint.augment((Symbol) symbol);

                if (lint.isEnabled(LintCategory.SERIAL)) {
                    task.accept(symbol, p);
                }

                return null;
            } finally {
                lint = prevLint;
            }
        }

    }

    void checkRequiresIdentity(JCTree tree, Lint lint) {
        switch (tree) {
            case JCClassDecl classDecl -> {
                Type st = types.supertype(classDecl.sym.type);
                if (st != null &&
                        // no need to recheck j.l.Object, shortcut,
                        st.tsym != syms.objectType.tsym &&
                        // this one could be null, no explicit extends
                        classDecl.extending != null) {
                    checkIfIdentityIsExpected(classDecl.extending.pos(), st, lint);
                }
                for (JCExpression intrface: classDecl.implementing) {
                    checkIfIdentityIsExpected(intrface.pos(), intrface.type, lint);
                }
                for (JCTypeParameter tp : classDecl.typarams) {
                    checkIfIdentityIsExpected(tp.pos(), tp.type, lint);
                }
            }
            case JCVariableDecl variableDecl -> {
                if (variableDecl.vartype != null &&
                        (variableDecl.sym.flags_field & RECORD) == 0 ||
                        (variableDecl.sym.flags_field & ~(Flags.PARAMETER | RECORD | GENERATED_MEMBER)) != 0) {
                    /* we don't want to warn twice so if this variable is a compiler generated parameter of
                     * a canonical record constructor, we don't want to issue a warning as we will warn the
                     * corresponding compiler generated private record field anyways
                     */
                    checkIfIdentityIsExpected(variableDecl.vartype.pos(), variableDecl.vartype.type, lint);
                }
            }
            case JCTypeCast typeCast -> checkIfIdentityIsExpected(typeCast.clazz.pos(), typeCast.clazz.type, lint);
            case JCBindingPattern bindingPattern -> {
                if (bindingPattern.var.vartype != null) {
                    checkIfIdentityIsExpected(bindingPattern.var.vartype.pos(), bindingPattern.var.vartype.type, lint);
                }
            }
            case JCMethodDecl methodDecl -> {
                for (JCTypeParameter tp : methodDecl.typarams) {
                    checkIfIdentityIsExpected(tp.pos(), tp.type, lint);
                }
                if (methodDecl.restype != null && !methodDecl.restype.type.hasTag(VOID)) {
                    checkIfIdentityIsExpected(methodDecl.restype.pos(), methodDecl.restype.type, lint);
                }
            }
            case JCMemberReference mref -> {
                checkIfIdentityIsExpected(mref.expr.pos(), mref.target, lint);
                checkIfTypeParamsRequiresIdentity(mref.sym.getMetadata(), mref.typeargs, lint);
            }
            case JCPolyExpression poly
                when (poly instanceof JCNewClass || poly instanceof JCMethodInvocation) -> {
                if (poly instanceof JCNewClass newClass) {
                    checkIfIdentityIsExpected(newClass.clazz.pos(), newClass.clazz.type, lint);
                }
                List<JCExpression> argExps = poly instanceof JCNewClass ?
                        ((JCNewClass)poly).args :
                        ((JCMethodInvocation)poly).args;
                Symbol msym = TreeInfo.symbolFor(poly);
                if (msym != null) {
                    if (!argExps.isEmpty() && msym instanceof MethodSymbol ms && ms.params != null) {
                        VarSymbol lastParam = ms.params.head;
                        for (VarSymbol param: ms.params) {
                            if ((param.flags_field & REQUIRES_IDENTITY) != 0 && argExps.head.type.isValueBased()) {
                                lint.logIfEnabled(argExps.head.pos(), LintWarnings.AttemptToUseValueBasedWhereIdentityExpected);
                            }
                            lastParam = param;
                            argExps = argExps.tail;
                        }
                        while (argExps != null && !argExps.isEmpty() && lastParam != null) {
                            if ((lastParam.flags_field & REQUIRES_IDENTITY) != 0 && argExps.head.type.isValueBased()) {
                                lint.logIfEnabled(argExps.head.pos(), LintWarnings.AttemptToUseValueBasedWhereIdentityExpected);
                            }
                            argExps = argExps.tail;
                        }
                    }
                    checkIfTypeParamsRequiresIdentity(
                            msym.getMetadata(),
                            poly instanceof JCNewClass ?
                                ((JCNewClass)poly).typeargs :
                                ((JCMethodInvocation)poly).typeargs,
                            lint);
                }
            }
            default -> throw new AssertionError("unexpected tree " + tree);
        }
    }

    /** Check if a type required an identity class
     */
    private boolean checkIfIdentityIsExpected(DiagnosticPosition pos, Type t, Lint lint) {
        if (t != null &&
                lint != null &&
                lint.isEnabled(LintCategory.IDENTITY)) {
            RequiresIdentityVisitor requiresIdentityVisitor = new RequiresIdentityVisitor();
            // we need to avoid recursion due to self referencing type vars or captures, this is why we need a set
            requiresIdentityVisitor.visit(t, new HashSet<>());
            if (requiresIdentityVisitor.requiresWarning) {
                lint.logIfEnabled(pos, LintWarnings.AttemptToUseValueBasedWhereIdentityExpected);
                return true;
            }
        }
        return false;
    }

    // where
    private class RequiresIdentityVisitor extends Types.SimpleVisitor<Void, Set<Type>> {
        boolean requiresWarning = false;

        @Override
        public Void visitType(Type t, Set<Type> seen) {
            return null;
        }

        @Override
        public Void visitWildcardType(WildcardType t, Set<Type> seen) {
            return visit(t.type, seen);
        }

        @Override
        public Void visitTypeVar(TypeVar t, Set<Type> seen) {
            if (seen.add(t)) {
                visit(t.getUpperBound(), seen);
            }
            return null;
        }

        @Override
        public Void visitCapturedType(CapturedType t, Set<Type> seen) {
            if (seen.add(t)) {
                visit(t.getUpperBound(), seen);
                visit(t.getLowerBound(), seen);
            }
            return null;
        }

        @Override
        public Void visitArrayType(ArrayType t, Set<Type> seen) {
            return visit(t.elemtype, seen);
        }

        @Override
        public Void visitClassType(ClassType t, Set<Type> seen) {
            if (t != null && t.tsym != null) {
                SymbolMetadata sm = t.tsym.getMetadata();
                if (sm != null && !t.getTypeArguments().isEmpty()) {
                    if (sm.getTypeAttributes().stream()
                            .filter(ta -> isRequiresIdentityAnnotation(ta.type.tsym) &&
                                    t.getTypeArguments().get(ta.position.parameter_index) != null &&
                                    t.getTypeArguments().get(ta.position.parameter_index).isValueBased()).findAny().isPresent()) {
                        requiresWarning = true;
                        return null;
                    }
                }
            }
            visit(t.getEnclosingType(), seen);
            for (Type targ : t.getTypeArguments()) {
                visit(targ, seen);
            }
            return null;
        }
    } // RequiresIdentityVisitor

    private void checkIfTypeParamsRequiresIdentity(SymbolMetadata sm,
                                                     List<JCExpression> typeParamTrees,
                                                     Lint lint) {
        if (typeParamTrees != null && !typeParamTrees.isEmpty()) {
            for (JCExpression targ : typeParamTrees) {
                checkIfIdentityIsExpected(targ.pos(), targ.type, lint);
            }
            if (sm != null)
                sm.getTypeAttributes().stream()
                        .filter(ta -> isRequiresIdentityAnnotation(ta.type.tsym) &&
                                typeParamTrees.get(ta.position.parameter_index).type != null &&
                                typeParamTrees.get(ta.position.parameter_index).type.isValueBased())
                        .forEach(ta -> lint.logIfEnabled(typeParamTrees.get(ta.position.parameter_index).pos(),
                                CompilerProperties.LintWarnings.AttemptToUseValueBasedWhereIdentityExpected));
        }
    }

    private boolean isRequiresIdentityAnnotation(TypeSymbol annoType) {
        return annoType == syms.requiresIdentityType.tsym ||
               annoType.flatName() == syms.requiresIdentityInternalType.tsym.flatName();
    }
}
