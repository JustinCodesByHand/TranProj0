# Tran Language Interpreter

A fully functional interpreter for **Tran**, a custom object-oriented programming language, written in Java. This project implements every stage of a language pipeline — from raw source text all the way to execution — demonstrating compiler theory fundamentals including lexical analysis, parsing, AST construction, and tree-walking interpretation.

---

## Table of Contents

- [Overview](#overview)
- [Language Features](#language-features)
- [Project Structure](#project-structure)
- [How to Build and Run](#how-to-build-and-run)
- [Tran Language Syntax](#tran-language-syntax)
- [Example Programs](#example-programs)
- [Testing](#testing)

---

## Overview

Tran is a statically-typed, indentation-sensitive language with support for classes, interfaces, constructors, loops, conditionals, and method calls. The interpreter is split into four clearly separated packages:

| Package | Responsibility |
|---|---|
| `Tran` | Lexer, Parser, Token definitions, and entry point |
| `AST` | Abstract Syntax Tree node classes |
| `Interpreter` | Tree-walking interpreter and built-in types |
| `Tests` | JUnit 5 tests and `.tran` example source files |

---

## Language Features

- **Classes** with member variables, methods, and multiple constructors
- **Interfaces** with method signature contracts
- **`implements`** keyword for interface-based polymorphism
- **`shared`** methods (similar to `static` in Java)
- **`private`** methods scoped to their class
- **`construct()`** constructors, including overloaded constructors
- **`loop`** for iteration
- **`if` / `else`** conditionals
- **`new`** keyword for object instantiation
- **Multiple return values** from a single method
- **Built-in `console.write()`** for output
- **Primitive types**: `number`, `string`, `boolean`, `character`
- **Indentation-based blocks** (whitespace-sensitive, like Python)
- **`{...}`** comment syntax

---

## Project Structure

```
TranProj0/
├── AST/                        # Abstract Syntax Tree nodes
│   ├── TranNode.java           # Root node (holds classes & interfaces)
│   ├── ClassNode.java
│   ├── InterfaceNode.java
│   ├── MethodDeclarationNode.java
│   ├── ConstructorNode.java
│   ├── IfNode.java / ElseNode.java / LoopNode.java
│   ├── MathOpNode.java / BooleanOpNode.java / CompareNode.java
│   └── ... (other expression & statement nodes)
│
├── Interpreter/                # Execution engine
│   ├── Interpreter.java        # Main tree-walking interpreter
│   ├── InterpreterDataType.java
│   ├── NumberIDT.java / StringIDT.java / BooleanIDT.java / CharIDT.java
│   ├── ObjectIDT.java / ReferenceIDT.java
│   └── ConsoleWrite.java       # Built-in console.write() method
│
├── Tran/                       # Front-end pipeline
│   ├── Lexer.java              # Tokenizer
│   ├── Parser.java             # Recursive-descent parser → AST
│   ├── Token.java              # Token type definitions
│   ├── TokenManager.java       # Token stream helper
│   ├── TextManager.java        # Character stream helper
│   ├── SyntaxErrorException.java
│   └── Main.java               # Entry point
│
└── Tests/                      # JUnit 5 test suite
    ├── LexerTests.java / LexerTests2.java
    ├── Parser1Tests.java / Parser2Tests.java / Parser3Tests.java
    ├── InterpreterTests.java
    ├── *.tran                  # Sample Tran source programs
    └── ...
```

---

## How to Build and Run

### Requirements

- Java 17 or higher
- JUnit 5 (for running tests)

### Compile

From the project root, compile all source files:

```bash
javac -d out AST/*.java Tran/*.java Interpreter/*.java
```

### Run

```bash
java -cp out Tran.Main
```

### Run Tests

If using an IDE (IntelliJ IDEA, Eclipse, VS Code with Java extensions), open the project and run the test classes in the `Tests/` directory directly. All tests use JUnit 5.

---

## Tran Language Syntax

### Classes and Members

```tran
class MyClass
    number x
    string name

    construct(number n)
        x = n
        name = ""
```

### Methods

```tran
    add(number a, number b) : number sum
        sum = a + b

    shared start()
        MyClass obj
        obj = new MyClass(10)
        console.write(obj.add(3, 4))
```

### Interfaces

```tran
interface Printable
    print()

class Document implements Printable
    print()
        console.write("printing...")
```

### Control Flow

```tran
    checkValue(number x)
        if x > 0
            console.write("positive")
        else
            console.write("non-positive")

    countUp(number n)
        number i
        loop i < n
            i = i + 1
            console.write(i)
```

### Multiple Return Values

```tran
    getData(number x) : number n, string t
        n = x
        t = "hello"

    shared start()
        number num
        string s
        num, s = Example.getData(45)
```

### Comments

Comments use curly braces and can appear inline:

```tran
    add(number a, number b) : number sum {adds two numbers}
        sum = a + b
```

---

## Example Programs

### Hello World

```tran
class Hello
    shared start()
        console.write("Hello, World!")
```

### Celsius to Fahrenheit Conversion

```tran
class celsius
    number temperature

    construct(number t)
        temperature = t

    toFahrenheit() : fahrenheit f
        number convert
        convert = (temperature * 9 / 5) + 32
        f = new fahrenheit(convert)

    get() : number c
        c = temperature

    shared start()
        celsius ten
        ten = new celsius(10)
        console.write("Celsius")
        console.write(ten.get())
        console.write("Fahrenheit")
        fahrenheit f
        f = ten.toFahrenheit()
        console.write(f.get())
```

---

## Testing

The `Tests/` directory contains a comprehensive JUnit 5 test suite covering:

- **Lexer tests** — tokenization of keywords, symbols, numbers, strings, indentation, and comments
- **Parser tests** — correct AST construction for all language constructs
- **Interpreter tests** — end-to-end execution of arithmetic, method calls, object creation, loops, conditionals, and more

Each `.tran` source file in `Tests/` has corresponding lexer and parser test classes (e.g., `Celsius.tran` → `CelsiusLexerTest.java` + `CelsiusParserTest.java`).
