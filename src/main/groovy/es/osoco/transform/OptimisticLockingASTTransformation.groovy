/*
 * Copyright 2003-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package es.osoco.transform

import es.osoco.ast.OptimisticLocking

import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import org.springframework.dao.OptimisticLockingFailureException

import java.util.ArrayList
import java.util.Arrays
import java.util.List
import java.util.Map
import java.util.StringTokenizer

/**
 * This class provides the AST Transformation for the @{es.osoco.ast.OptimisticLocking} annotation.
 *
 * @author Rafael Luque (OSOCO)
 */
@GroovyASTTransformation(phase=CompilePhase.SEMANTIC_ANALYSIS)
public class OptimisticLockingASTTransformation implements ASTTransformation
{

    public void visit(ASTNode[] nodes, SourceUnit source) {
        // safeguards against unexpected AST input
        if (nodes.length != 2 || 
            !(nodes[0] instanceof AnnotationNode) || 
            !(nodes[1] instanceof AnnotatedNode)) {
            addError("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes), 
                 nodes[0], source);
        }

        AnnotationNode annotation = nodes[0]
        FieldNode field = nodes[1]

        ClosureExpression closure = field.initialValueExpression
        List statements = closure.code.statements
            
        def memberDefaultValuesMap = 
            [redirect: OptimisticLocking.DEFAULT_REDIRECT_ACTION,
             messageCode: OptimisticLocking.DEFAULT_FLASH_MESSAGE_CODE,
             params: OptimisticLocking.DEFAULT_REDIRECT_PARAMS]
        def (redirectValue, messageCodeValue, paramsValue) = memberDefaultValuesMap.collect { 
            member, defaultValue -> lookupMemberValue(annotation, member, defaultValue)
        }
        def paramList = tokenize(paramsValue)

        BlockStatement tryStatement = new BlockStatement()
        tryStatement.addStatements(statements)
        CatchStatement optimisticCatchStatement = 
            createOptimisticCatchStatement(redirectValue, messageCodeValue, paramList)
        Statement optimisticTryCatch = createTryCatchStatement(tryStatement, optimisticCatchStatement)
            
        statements.clear()
        statements.add(optimisticTryCatch)
    }

    private void addError(String msg, ASTNode expr, SourceUnit source) {
        int line = expr.lineNumber
        int col = columnNumber
        source.errorCollector.addErrorAndContinue(
            new SyntaxErrorMessage(new SyntaxException(msg + '\n', line, col), source))
    }

    private String lookupMemberValue(AnnotationNode annotation, String memberName, String defaultValue) {        
        Expression member = annotation.getMember(memberName)
        member?.text ?: defaultValue
    }

    private Statement createTryCatchStatement(Statement tryStatement, CatchStatement catchStatement) {
        TryCatchStatement tryCatch = new TryCatchStatement(tryStatement, EmptyStatement.INSTANCE)
        tryCatch.addCatch(catchStatement)
        tryCatch
    }

    private CatchStatement createOptimisticCatchStatement(
            String redirectValue, 
            String messageCodeValue, 
            List<String> params) {

        def code = new BlockStatement()

        Expression flashExpr = new PropertyExpression(new VariableExpression("flash"), "message")
        MapExpression messageMapExpr = new MapExpression()
        messageMapExpr.addMapEntryExpression(
            new ConstantExpression("code"), 
            new ConstantExpression(messageCodeValue))
        Expression messageCallExpr =
            new MethodCallExpression(
                new VariableExpression("this"), 
                "message", 
                new ArgumentListExpression(messageMapExpr))

        code.addStatement(
            new ExpressionStatement(
                new BinaryExpression(flashExpr, Token.newSymbol("=",-1,-1), messageCallExpr)))

        MapExpression redirectMapExpr = new MapExpression()
        redirectMapExpr.addMapEntryExpression(
            new ConstantExpression("action"), 
            new ConstantExpression(redirectValue))
        ListExpression paramListExpr = new ListExpression();
        params.each { paramListExpr.addExpression(new ConstantExpression(it)) }
        MethodCallExpression paramsSubMapExpr = new MethodCallExpression(
            new VariableExpression("params"),
            "subMap",
            new ArgumentListExpression(paramListExpr))
        redirectMapExpr.addMapEntryExpression(
            new ConstantExpression("params"), 
            paramsSubMapExpr)
        Expression redirectCallExpr =
            new MethodCallExpression(
                new VariableExpression("this"), 
                "redirect", 
                new ArgumentListExpression(redirectMapExpr))

        code.addStatement(new ExpressionStatement(redirectCallExpr))

        def catchStatement = 
            new CatchStatement(
                new Parameter(new ClassNode(OptimisticLockingFailureException), "olfe"),
                code)
    }

    private tokenize(String commaSeparatedValues) {
        commaSeparatedValues?.split(",")*.trim()
    }

}