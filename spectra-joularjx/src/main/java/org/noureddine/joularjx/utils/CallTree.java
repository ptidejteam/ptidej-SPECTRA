/* Copyright (c) 2021-2024, Adel Noureddine, Université de Pau et des Pays de l'Adour.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the
 * GNU General Public License v3.0 only (GPL-3.0-only)
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/gpl-3.0.en.html
 */

package org.noureddine.joularjx.utils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.*;
import java.io.File;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.utils.SourceRoot;

/**
 * A CallTree (or a stack trace) is a collection of StackTraceElements in a given order. This class provides methods in order to easily represent and manage such stack traces.
 */
public class CallTree {

    // The stack trace is stored in the form of a List of StackTraceElements.
    private List<StackTraceElement> callTree;
    private static final String SOURCE_ROOT = "src/main/java";
    private final StackTraceElement[] stackTraceElements;


    /**
     * Creates a new empty CallTree.
     */
    public CallTree() {
        this.callTree = new ArrayList<>();
        stackTraceElements = new StackTraceElement[0];
    }

    /**
     * Creates a new CallTree.
     * @param stackTrace a java array of StackTraceElement, representing a stack trace. This array will be automatically converted to a List.
     */
    public CallTree(StackTraceElement[] stackTrace) {
        this.callTree = Arrays.asList(stackTrace);
        this.stackTraceElements = stackTrace;
    }

    /**
     * Creates a new CallTree.
     * @param stackTrace a List of StackTraceElement, representing a stack trace
     */
    public CallTree(List<StackTraceElement> stackTrace) {
        this.callTree = stackTrace;
        stackTraceElements = new StackTraceElement[0];
    }

    /**
     * Sets the given stack trace.
     * @param stackTrace a java array of StackTraceElement, representing a stack trace.
     */
    public void setCallTree(StackTraceElement[] stackTrace) {
        this.callTree = Arrays.asList(stackTrace);
    }

    /**
     * Returns the call tree.
     * @return a List of StackTraceElement, representing a call tree.
     */
    public List<StackTraceElement> getCallTree() {
        return this.callTree;
    }

    @Override
    public String toString() {
        if (callTree.isEmpty()) return "null";

        StringBuilder res = new StringBuilder();

        StackTraceElement top = callTree.get(0);
        String resolvedTop = resolve(top.getClassName(), top.getMethodName(), top.getLineNumber());
        res.append(resolvedTop != null ? resolvedTop : top.getClassName() + "." + top.getMethodName());
        if (resolvedTop == null) {
            resolvedTop  = top.getClassName() + "." + top.getMethodName(); // Fallback
        }
        res.append(";");


        /*Appening elements to res String in reverse order. The least recent element (the bottom of the stack trace) will be written first, and the most recent one last.*/
        for (int i = callTree.size() - 1; i > 0; i--) {
            StackTraceElement element = callTree.get(i);
            res.append(element.getClassName()).append(".").append(element.getMethodName()).append(";");
        }

        //Removing the last ";"
        return res.substring(0, res.length()-1);

    }

    /**
     * Retrieves correct version of overloaded method with optional line number using JavaParser.
     */
    public static String resolve(String className, String methodName, int lineNumber) {
        // Convert class name to path
        String relativePath = className.replace(".", "/") + ".java";
        SourceRoot sourceRoot = new SourceRoot(Paths.get("src/main/java")); // or configurable
        CompilationUnit cu = sourceRoot.parse(relativePath.substring(0, relativePath.lastIndexOf("/")),
                relativePath.substring(relativePath.lastIndexOf("/") + 1));

        return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .flatMap(c -> {
                    List<CallableDeclaration<?>> methods = new ArrayList<>();
                    methods.addAll(c.getMethods());
                    methods.addAll(c.getConstructors());
                    return methods.stream();
                })
                .filter(m -> m.getNameAsString().equals(methodName))
                .filter(m -> m.getRange().isPresent()
                        && lineNumber >= m.getRange().get().begin.line
                        && lineNumber <= m.getRange().get().end.line)
                .map(m -> {
                    String classShort = m.findAncestor(ClassOrInterfaceDeclaration.class)
                            .map(ClassOrInterfaceDeclaration::getNameAsString).orElse("Unknown");
                    String pkg = m.findCompilationUnit().flatMap(CompilationUnit::getPackageDeclaration)
                            .map(pd -> pd.getNameAsString()).orElse("");
                    String params = m.getParameters().stream()
                            .map(p -> p.getType().asString())
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    String realName = m instanceof ConstructorDeclaration ? "<init>" : methodName;
                    return (pkg.isEmpty() ? classShort : pkg + "." + classShort) + "." + realName + "(" + params + ")";
                })
                .findFirst().orElse(null);

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.callTree == null) ? 0 : hashCode(this.callTree));
        return result;
    }

    private int hashCode(List<StackTraceElement> l) {
        int result = 1;
        for (StackTraceElement element : l) {
            result = 31 * result + hashCode(element);
        }
        return result;
    }

    private int hashCode(StackTraceElement e) {
        int result = 31 * e.getClassName().hashCode() + e.getMethodName().hashCode();
        if (e.getFileName() != null) {
            result = 31 * result + e.getFileName().hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        CallTree other = (CallTree) obj;
        if (callTree == null) {
            if (other.callTree != null)
                return false;
        } else if (!equals(this.callTree, other.callTree))
            return false;
        return true;
    }

    private boolean equals(List<StackTraceElement> callTree, List<StackTraceElement> other) {
        if (callTree == null ^ other == null) {
            return false;
        }

        if (callTree == null && other == null) {
            return true;
        }

        if (callTree.size() != other.size()) {
            return false;
        }

        for (int i = 0; i < callTree.size(); i++) {
            StackTraceElement e = callTree.get(i);
            if (!equals(e, other.get(i))) {
                return false;
            }
        }

        return true;
    }

    private boolean equals(StackTraceElement e, StackTraceElement other) {
        boolean result = true;

        if (e.getFileName() != null && other.getFileName() != null) {
            result = result && e.getFileName().equals(other.getFileName());
        }

        return result && e.getClassName().equals(other.getClassName()) && e.getMethodName().equals(other.getMethodName());
    }
}




