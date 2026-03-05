package Interpreter;

import AST.*;
import org.junit.internal.Classes;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class Interpreter {
    TranNode top; //pass top of trees

    /** Constructor - get the interpreter ready to run. Set members from parameters and "prepare" the class.
     *
     * Store the tran node.
     * Add any built-in methods to the AST
     * @param top - the head of the AST
     */
    public Interpreter(TranNode top) {
        this.top = top;
        ClassNode classNode = new ClassNode();
        classNode.name = "console";
        top.Classes.add(classNode);
        ConsoleWrite consoleWrite= new ConsoleWrite();
        consoleWrite.name = "write";
        consoleWrite.isVariadic = true;
        consoleWrite.isShared = true;
        classNode.methods.add(consoleWrite);
    }

    /**
     * This is the public interface to the interpreter. After parsing, we will create an interpreter and call start to
     * start interpreting the code.
     *
     * Search the classes in Tran for a method that is "isShared", named "start", that is not private and has no parameters
     * Call "InterpretMethodCall" on that method, then return.
     * Throw an exception if no such method exists.
     */
    public void start() {
        // Search all classes for the start method
        for (ClassNode classNode : top.Classes) {
            for (MethodDeclarationNode method : classNode.methods) {

                if (method.isShared &&
                        method.name.equals("start") &&
                        !method.isPrivate &&
                        method.parameters.isEmpty()) {

                    // Found the start method - execute it with empty parameters
                    interpretMethodCall(Optional.empty(), method, new LinkedList<>());
                    return;
                }
            }
        }

        // no valid start method was found
        throw new RuntimeException("No valid 'start' method found. ");
    }

    //              Running Methods

    /**
     * Find the method (local to this class, shared (like Java's system.out.print), or a method on another class)
     * Evaluate the parameters to have a list of values
     * Use interpretMethodCall() to actually run the method.
     *
     * Call GetParameters() to get the parameter value list
     * Find the method. This is tricky - there are several cases:
     * someLocalMethod() - has NO object name. Look in "object"
     * console.write() - the objectName is a CLASS and the method is shared
     * bestStudent.getGPA() - the objectName is a local or a member
     *
     * Once you find the method, call InterpretMethodCall() on it. Return the list that it returns.
     * Throw an exception if we can't find a match.
     * @param object - the object we are inside right now (might be empty)
     * @param locals - the current local variables
     * @param mc - the method call
     * @return - the return values
     */
    private List<InterpreterDataType> findMethodForMethodCallAndRunIt(Optional<ObjectIDT> object, HashMap<String, InterpreterDataType> locals, MethodCallStatementNode mc) {
        // get evaluated parameter values
        List<InterpreterDataType> paramValues = getParameters(object, locals, mc);

        // Case 1 local method
        if (mc.objectName.isEmpty()) {
            if (object.isEmpty()) {
                throw new RuntimeException("Cannot call local method without an object context");
            }

            // find method in object class
            MethodDeclarationNode method = getMethodFromObject(object.get(), mc, paramValues);
            return interpretMethodCall(object, method, paramValues);
        }

        String objectName = mc.objectName.get();

        // case 2 Shared method
        Optional<ClassNode> classOptional = getClassByName(objectName);
        if (classOptional.isPresent()) {
            ClassNode classNode = classOptional.get();

            // find method in the class
            for (MethodDeclarationNode method : classNode.methods) {
                if (method.isShared && doesMatch(method, mc, paramValues)) {
                    return interpretMethodCall(Optional.empty(), method, paramValues);
                }
            }

            throw new RuntimeException("No matching shared methods found inside class " + objectName);
        }

        // case 3 method on other object
        InterpreterDataType targetObj = findVariable(object, locals, objectName);

        if (!(targetObj instanceof ObjectIDT || targetObj instanceof ReferenceIDT)) {
            throw new RuntimeException(objectName + " isn't an object");
        }

        ObjectIDT obj;
        if (targetObj instanceof ObjectIDT){
            obj = (ObjectIDT) targetObj;
        }
        else{
            obj = ((ReferenceIDT) targetObj).refersTo.get();
        }

        MethodDeclarationNode method = getMethodFromObject(obj, mc, paramValues);
        return interpretMethodCall(Optional.of(obj), method, paramValues);
    }

    /**
     * Run a "prepared" method (found, parameters evaluated)
     * This is split from findMethodForMethodCallAndRunIt() because there are a few cases where we don't need to do the finding:
     * in start() and dealing with loops with iterator objects, for example.
     *
     * Check to see if "m" is a built-in. If so, call Execute() on it and return
     * Make local variables, per "m"
     * If the number of passed in values doesn't match m's "expectations", throw
     * Add the parameters by name to locals.
     * Call InterpretStatementBlock
     * Build the return list - find the names from "m", then get the values for those names and add them to the list.
     * @param object - The object this method is being called on (might be empty for shared)
     * @param m - Which method is being called
     * @param values - The values to be passed in
     * @return the returned values from the method
    */
    private List<InterpreterDataType> interpretMethodCall(Optional<ObjectIDT> object, MethodDeclarationNode m, List<InterpreterDataType> values) {
        // handle built-in method
        if (m instanceof BuiltInMethodDeclarationNode) {
            BuiltInMethodDeclarationNode builtin = (BuiltInMethodDeclarationNode) m;
            return builtin.Execute(values);
        }

        // check parameter count match
        if (m.parameters.size() != values.size()) {
            throw new RuntimeException("Method parameter counts are mismatch");
        }

        // create local variables map
        HashMap<String, InterpreterDataType> locals = new HashMap<>();

        // parameters to locals
        for (int i = 0; i < values.size(); i++) {
            String name = m.parameters.get(i).name;
            InterpreterDataType val = values.get(i);
            locals.put(name, val);
        }

        // aad local variables from the method
        for (VariableDeclarationNode var : m.locals) {
            InterpreterDataType instance = instantiate(var.type);
            locals.put(var.name, instance);
        }

        // interpret statement block
        interpretStatementBlock(object, m.statements, locals);

        // build return list of return variables
        List<InterpreterDataType> returnValues = new LinkedList<>();
        for (VariableDeclarationNode returnVar : m.returns) {
            InterpreterDataType returnValue = findVariable(object, locals, returnVar.name);
            returnValues.add(returnValue);
        }

        return returnValues;
    }
    //              Running Constructors

    /**
     * This is a special case of the code for methods. Just different enough to make it worthwhile to split it out.
     *
     * Call GetParameters() to populate a list of IDT's
     * Call GetClassByName() to find the class for the constructor
     * If we didn't find the class, throw an exception
     * Find a constructor that is a good match - use DoesConstructorMatch()
     * Call InterpretConstructorCall() on the good match
     * @param callerObj - the object that we are inside when we called the constructor
     * @param locals - the current local variables (used to fill parameters)
     * @param mc  - the method call for this construction
     * @param newOne - the object that we just created that we are calling the constructor for
     */
    private void findConstructorAndRunIt(Optional<ObjectIDT> callerObj, HashMap<String, InterpreterDataType> locals, MethodCallStatementNode mc, ObjectIDT newOne) {
        // get evaluated parameter values for constructor call
        List<InterpreterDataType> paramValues = getParameters(callerObj, locals, mc);

        // get class definition for the constructor call
        Optional<ClassNode> classOpt = getClassByName(mc.methodName);
        if (classOpt.isEmpty()) {
            throw new RuntimeException("Constructor class is not found: " + mc.methodName);
        }
        ClassNode classNode = classOpt.get();

        // find matching constructor
        ConstructorNode matching = null;
        for (ConstructorNode c : classNode.constructors) {
            if (doesConstructorMatch(c, mc, paramValues)) {
                matching = c;
                break;
            }
        }

        if (matching == null) {
            throw new RuntimeException("No matching constructor for the class: " + mc.methodName);
        }

        // run the constructor
        interpretConstructorCall(newOne, matching, paramValues);
    }

    /**
     * Similar to interpretMethodCall, but "just different enough" - for example, constructors don't return anything.
     *
     * Creates local variables (as defined by the ConstructorNode), calls Instantiate() to do the creation
     * Checks to ensure that the right number of parameters were passed in, if not throw.
     * Adds the parameters (with the names from the ConstructorNode) to the locals.
     * Calls InterpretStatementBlock
     * @param object - the object that we allocated
     * @param c - which constructor is being called
     * @param values - the parameter values being passed to the constructor
     */
    private void interpretConstructorCall(ObjectIDT object, ConstructorNode c, List<InterpreterDataType> values) {
        // locals map for constructor
        HashMap<String, InterpreterDataType> locals = new HashMap<>();

        // check parameter count
        if (c.parameters.size() != values.size()) {
            throw new RuntimeException("Constructor parameter count mismatch. Expected " +
                    c.parameters.size() + " but received " + values.size());
        }

        // match parameters to names from constructor
        for (int i = 0; i < values.size(); i++) {
            String name = c.parameters.get(i).name;
            InterpreterDataType value = values.get(i);
            locals.put(name, value);
        }

        // declare constructor local variables
        for (VariableDeclarationNode var : c.locals) {
            InterpreterDataType instance = instantiate(var.type);
            locals.put(var.name, instance);
        }

        // set up fields in the object map
        for (MemberNode var : object.astNode.members) {
            InterpreterDataType instance = instantiate(var.declaration.type);
            object.members.put(var.declaration.name, instance);
        }

        // interpret constructor body
        interpretStatementBlock(Optional.of(object), c.statements, locals);
    }

    //              Running Instructions

    /**
     * Given a block (which could be from a method or an "if" or "loop" block, run each statement.
     * Blocks, by definition, do ever statement, so iterating over the statements makes sense.
     *
     * For each statement in statements:
     * check the type:
     *      For AssignmentNode, FindVariable() to get the target. Evaluate() the expression. Call Assign() on the target with the result of Evaluate()
     *      For MethodCallStatementNode, call doMethodCall(). Loop over the returned values and copy the into our local variables
     *      For LoopNode - there are 2 kinds.
     *          Setup:
     *          If this is a Loop over an iterator (an Object node whose class has "iterator" as an interface)
     *              Find the "getNext()" method; throw an exception if there isn't one
     *          Loop:
     *          While we are not done:
     *              if this is a boolean loop, Evaluate() to get true or false.
     *              if this is an iterator, call "getNext()" - it has 2 return values. The first is a boolean (was there another?), the second is a value
     *              If the loop has an assignment variable, populate it: for boolean loops, the true/false. For iterators, the "second value"
     *              If our answer from above is "true", InterpretStatementBlock() on the body of the loop.
     *       For If - Evaluate() the condition. If true, InterpretStatementBlock() on the if's statements. If not AND there is an else, InterpretStatementBlock on the else body.
     * @param object - the object that this statement block belongs to (used to get member variables and any members without an object)
     * @param statements - the statements to run
     * @param locals - the local variables
     */
    private void interpretStatementBlock(Optional<ObjectIDT> object, List<StatementNode> statements, HashMap<String, InterpreterDataType> locals) {

        for (StatementNode statement : statements) {
            // assignment, get the target and evaluate the expression, assign
            if (statement instanceof AssignmentNode assignmentNode) {
                InterpreterDataType target = findVariable(object, locals, assignmentNode.target.name);
                InterpreterDataType value = evaluate(locals, object, assignmentNode.expression);
                target.Assign(value);
            }

            // method call, run method and copy returned values into locals
            else if (statement instanceof MethodCallStatementNode methodCall) {
                List<InterpreterDataType> result = findMethodForMethodCallAndRunIt(object, locals, methodCall);
                for (int i = 0; i < methodCall.returnValues.size(); i++) {
                    String name = methodCall.returnValues.get(i).name;
                    locals.put(name, result.get(i)); // assume return names go into locals
                }
            }

            // if, evaluate condition and run correct block
            else if (statement instanceof IfNode ifNode) {
                InterpreterDataType condition = evaluate(locals, object, ifNode.condition);
                if (!(condition instanceof BooleanIDT)) {
                    throw new RuntimeException("If condition must evaluate to boolean");
                }
                if (((BooleanIDT) condition).Value) {
                    interpretStatementBlock(object, ifNode.statements, locals);
                } else if (ifNode.elseStatement.isPresent()) {
                    interpretStatementBlock(object, ifNode.elseStatement.get().statements, locals);
                }
            }
            // unknown statement type
            else {
                throw new RuntimeException("Unknown statement: " + statement.getClass().getSimpleName());
            }
        }

    }

    /**
     *  evaluate() processes everything that is an expression - math, variables, boolean expressions.
     *  There is a good bit of recursion in here, since math and comparisons have left and right sides that need to be evaluated.
     *
     * See the How To Write an Interpreter document for examples
     * For each possible ExpressionNode, do the work to resolve it:
     * BooleanLiteralNode - create a new BooleanLiteralNode with the same value
     *      - Same for all of the basic data types
     * BooleanOpNode - Evaluate() left and right, then perform either and/or on the results.
     * CompareNode - Evaluate() both sides. Do good comparison for each data type
     * MathOpNode - Evaluate() both sides. If they are both numbers, do the math using the built-in operators. Also handle String + String as concatenation (like Java)
     * MethodCallExpression - call doMethodCall() and return the first value
     * VariableReferenceNode - call findVariable()
     * @param locals the local variables
     * @param object - the current object we are running
     * @param expression - some expression to evaluate
     * @return a value
     */
    private InterpreterDataType evaluate(HashMap<String, InterpreterDataType> locals, Optional<ObjectIDT> object, ExpressionNode expression) {
        if (expression instanceof MathOpNode) {
            InterpreterDataType left = evaluate(locals, object, ((MathOpNode) expression).left);
            InterpreterDataType right = evaluate(locals, object, ((MathOpNode) expression).right);

            if (left instanceof NumberIDT && right instanceof NumberIDT) {
                float leftValue = ((NumberIDT) left).Value;
                float rightValue = ((NumberIDT) right).Value;

                switch (((MathOpNode) expression).op) {
                    case add: return new NumberIDT(leftValue + rightValue);
                    case subtract: return new NumberIDT(leftValue - rightValue);
                    case multiply: return new NumberIDT(leftValue * rightValue);
                    case divide: return new NumberIDT(leftValue / rightValue);
                    case modulo: return new NumberIDT(leftValue % rightValue);
                }
            }


            // string concatenation
            if (left instanceof StringIDT && right instanceof StringIDT &&
                    ((MathOpNode) expression).op == MathOpNode.MathOperations.add) {
                return new StringIDT(((StringIDT) left).Value + ((StringIDT) right).Value);
            }
        }

        if (expression instanceof CompareNode) {
            InterpreterDataType left = evaluate(locals, object, ((CompareNode) expression).left);
            InterpreterDataType right = evaluate(locals, object, ((CompareNode) expression).right);
            CompareNode.CompareOperations op = ((CompareNode) expression).op;

            if (left instanceof NumberIDT && right instanceof NumberIDT) {
                float leftValue = ((NumberIDT) left).Value;
                float rightValue = ((NumberIDT) right).Value;

                switch (op) {
                    case eq: return new BooleanIDT(leftValue == rightValue);
                    case ne: return new BooleanIDT(leftValue != rightValue);
                    case gt: return new BooleanIDT(leftValue > rightValue);
                    case ge: return new BooleanIDT(leftValue >= rightValue);
                    case lt: return new BooleanIDT(leftValue < rightValue);
                    case le: return new BooleanIDT(leftValue <= rightValue);
                }
            }

            if (left instanceof StringIDT && right instanceof StringIDT) {
                String leftValue = ((StringIDT) left).Value;
                String rightValue = ((StringIDT) right).Value;

                switch (op) {
                    case eq: return new BooleanIDT(leftValue.equals(rightValue));
                    case ne: return new BooleanIDT(!leftValue.equals(rightValue));
                }
            }
        }

        if (expression instanceof BooleanLiteralNode) {
            return new BooleanIDT(((BooleanLiteralNode) expression).value);
        }

        if (expression instanceof CharLiteralNode) {
            return new CharIDT(((CharLiteralNode) expression).value);
        }

        if (expression instanceof NumericLiteralNode) {
            return new NumberIDT(((NumericLiteralNode) expression).value);
        }

        if (expression instanceof StringLiteralNode) {
            return new StringIDT(((StringLiteralNode) expression).value);
        }


        if (expression instanceof VariableReferenceNode) {
            String name = ((VariableReferenceNode) expression).name;
            return findVariable(object, locals, name);
        }

        if (expression instanceof BooleanOpNode) {
            BooleanOpNode boolNode = (BooleanOpNode) expression;
            InterpreterDataType left = evaluate(locals, object, boolNode.left);
            InterpreterDataType right = evaluate(locals, object, boolNode.right);

            if (!(left instanceof BooleanIDT) || !(right instanceof BooleanIDT)) {
                throw new RuntimeException("Boolean operations require boolean operands");
            }

            boolean leftVal = ((BooleanIDT) left).Value;
            boolean rightVal = ((BooleanIDT) right).Value;

            switch (boolNode.op) {
                case and: return new BooleanIDT(leftVal && rightVal);
                case or: return new BooleanIDT(leftVal || rightVal);
                default: throw new RuntimeException("Unknown boolean operation");
            }
        }

        if (expression instanceof MethodCallExpressionNode) {
            MethodCallExpressionNode methodCall = (MethodCallExpressionNode) expression;
            MethodCallStatementNode callStatementNode = new MethodCallStatementNode();
            callStatementNode.methodName = methodCall.methodName;
            callStatementNode.objectName = methodCall.objectName;
            callStatementNode.parameters = methodCall.parameters;
            callStatementNode.returnValues = new LinkedList<>();

            List<InterpreterDataType> result = findMethodForMethodCallAndRunIt(object, locals, callStatementNode);
            if (result.isEmpty()) {
                throw new RuntimeException("Method must return at least one value");
            }
            return result.get(0);
        }

        if (expression instanceof NewNode) {
            NewNode newExpr = (NewNode) expression;
            Optional<ClassNode> classOptional = getClassByName(newExpr.className);
            if (classOptional.isEmpty()) {
                throw new RuntimeException("Class not found: " + newExpr.className);
            }


            ClassNode classNode = classOptional.get();
            ObjectIDT newObject = new ObjectIDT(classNode);

            MethodCallStatementNode constructorCall = new MethodCallStatementNode();
            constructorCall.methodName = newExpr.className;
            constructorCall.parameters = newExpr.parameters;

            findConstructorAndRunIt(object, locals, constructorCall, newObject);
            return newObject;
        }



        throw new IllegalArgumentException("Unknown expression type: " + expression.getClass().getSimpleName());
    }




    //              Utility Methods

    /**
     * Used when trying to find a match to a method call. Given a method declaration, does it match this methoc call?
     * We double check with the parameters, too, although in theory JUST checking the declaration to the call should be enough.
     *
     * Match names, parameter counts (both declared count vs method call and declared count vs value list), return counts.
     * If all of those match, consider the types (use TypeMatchToIDT).
     * If everything is OK, return true, else return false.
     * Note - if m is a built-in and isVariadic is true, skip all of the parameter validation.
     * @param m - the method declaration we are considering
     * @param mc - the method call we are trying to match
     * @param parameters - the parameter values for this method call
     * @return does this method match the method call?
     */
    private boolean doesMatch(MethodDeclarationNode m, MethodCallStatementNode mc, List<InterpreterDataType> parameters) {
        if (m.name.equals(mc.methodName)) { //check name


            if (m.parameters.size() == mc.parameters.size()){
                for(int i = 0; i < m.parameters.size(); i++){
                    typeMatchToIDT(m.parameters.get(i).type, parameters.get(i));

                }


                if (m.returns.size() == mc.returnValues.size()){
                    for(int i = 0; i < m.returns.size(); i++){
                        typeMatchToIDT(m.returns.get(i).type, parameters.get(i));
                    }
                }
            }
        return true;
        }

        return false;
    }

    /**
     * Very similar to DoesMatch() except simpler - there are no return values, the name will always match.
     * @param c - a particular constructor
     * @param mc - the method call
     * @param parameters - the parameter values
     * @return does this constructor match the method call?
     */
    private boolean doesConstructorMatch(ConstructorNode c, MethodCallStatementNode mc, List<InterpreterDataType> parameters) {
        for(int i = 0; i < c.parameters.size(); i++){
            typeMatchToIDT(c.parameters.get(i).type, parameters.get(i));


        }
        return true;
    }

    /**
     * Used when we call a method to get the list of values for the parameters.
     *
     * for each parameter in the method call, call Evaluate() on the parameter to get an IDT and add it to a list
     * @param object - the current object
     * @param locals - the local variables
     * @param mc - a method call
     * @return the list of method values
     */
    private List<InterpreterDataType> getParameters(Optional<ObjectIDT> object, HashMap<String,InterpreterDataType> locals, MethodCallStatementNode mc) {
        List<InterpreterDataType> paramValues = new LinkedList<>();
        for (ExpressionNode param : mc.parameters) {
            InterpreterDataType value = evaluate(locals, object, param);
            paramValues.add(value);
        }
        return paramValues;
    }

    /**
     * Used when we have an IDT and we want to see if it matches a type definition
     * Commonly, when someone is making a function call - do the parameter values match the method declaration?
     *
     * If the IDT is a simple type (boolean, number, etc) - does the string type match the name of that IDT ("boolean", etc)
     * If the IDT is an object, check to see if the name matches OR the class has an interface that matches
     * If the IDT is a reference, check the inner (refered to) type
     * @param type the name of a data type (parameter to a method)
     * @param idt the IDT someone is trying to pass to this method
     * @return is this OK?
     */
    private boolean typeMatchToIDT(String type, InterpreterDataType idt) {

        switch (type){
            case("string"):
                if(idt instanceof StringIDT){
                    return true;
                }
            case("number"):
                if (idt instanceof NumberIDT){
                    return true;
                }
            case("boolean"):
                if (idt instanceof BooleanIDT){
                    return true;
                }
            case("character"):
                if (idt instanceof CharIDT){
                    return true;
                }
                // check ref idt or object idt
            default:
                if (idt instanceof ObjectIDT) {
                    for (int i = 0; i < top.Classes.size(); i++) {
                        //gets name of classes, see if equal to type
                        if (top.Classes.get(i).name.equals(type)) {
                            return true;
                        }
                    }
                } else if (idt instanceof ReferenceIDT) {
                    for (int i = 0; i < top.Classes.size(); i++) {
                        //gets name of interface, see if equal to type
                        if (top.Classes.get(i).interfaces.equals(type)) {
                            return true;
                        }
                    }
                }

        }

        
        
// check if idt is instace of object, if true 2 sep for loops obj idt holds ast node, 
        // 1 that cks size for top.classes
        // one for top. interfaces, in loop check if interfaces .get(i) = type 
        throw new RuntimeException("Unable to resolve type " + type);
    }

    /**
     * Find a method in an object that is the right match for a method call (same name, parameters match, etc. Uses doesMatch() to do most of the work)
     *
     * Given a method call, we want to loop over the methods for that class, looking for a method that matches (use DoesMatch) or throw
     * @param object - an object that we want to find a method on
     * @param mc - the method call
     * @param parameters - the parameter value list
     * @return a method or throws an exception
     */
    private MethodDeclarationNode getMethodFromObject(ObjectIDT object, MethodCallStatementNode mc, List<InterpreterDataType> parameters) {

        for(int i = 0; i < object.astNode.methods.size(); i++){
            if(doesMatch(object.astNode.methods.get(i), mc, parameters)){

                return object.astNode.methods.get(i);
            }
        }
        //TODO fix exception retuen
        throw new RuntimeException("Unable to resolve method call " + mc);
    }

    /**
     * Find a class, given the name. Just loops over the TranNode's classes member, matching by name.
     *
     * Loop over each class in the top node, comparing names to find a match.
     * @param name Name of the class to find
     * @return either a class node or empty if that class doesn't exist
     */
    private Optional<ClassNode> getClassByName(String name) {

        for(int i = 0; i < top.Classes.size(); i++){
            ClassNode classNode = top.Classes.get(i);
            if (classNode.name.equals(name)) {
                return Optional.of(classNode);
            }
        }

        return Optional.empty();
    }

    /**
     * Given an execution environment (the current object, the current local variables), find a variable by name.
     *
     * @param name  - the variable that we are looking for
     * @param locals - the current method's local variables
     * @param object - the current object (so we can find members)
     * @return the IDT that we are looking for or throw an exception
     */
    private InterpreterDataType findVariable(Optional<ObjectIDT> object, HashMap<String, InterpreterDataType> locals, String name) {
        // check locals first
        if (locals.containsKey(name)) {
            return locals.get(name);
        }

        // check fields if inside an object
        if (object.isPresent() && object.get().members.containsKey(name)) {
            return object.get().members.get(name);
        }

        // if not found in locals or members, error
        throw new RuntimeException("Unable to find variable " + name);
    }


    /**
     * Given a string (the type name), make an IDT for it.
     *
     * @param type The name of the type (string, number, boolean, character). Defaults to ReferenceIDT if not one of those.
     * @return an IDT with default values (0 for number, "" for string, false for boolean, ' ' for character)
     */
    private InterpreterDataType instantiate(String type) {

        switch (type) {
            case "string":
                return new StringIDT("");
            case "number":
                return new NumberIDT(0);
            case "boolean":
                return new BooleanIDT(false);
            case "character":
                return new CharIDT(' ');
            default:
                return new ReferenceIDT();
        }
    }
}
