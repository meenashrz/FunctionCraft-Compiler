package main.visitor.codeGenerator;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.FunctionDeclaration;
import main.ast.nodes.declaration.MainDeclaration;
import main.ast.nodes.declaration.VarDeclaration;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.value.FunctionPointer;
import main.ast.nodes.expression.value.ListValue;
import main.ast.nodes.expression.value.primitive.BoolValue;
import main.ast.nodes.expression.value.primitive.IntValue;
import main.ast.nodes.expression.value.primitive.StringValue;
import main.ast.nodes.statement.*;
import main.ast.type.FptrType;
import main.ast.type.ListType;
import main.ast.type.NoType;
import main.ast.type.Type;
import main.ast.type.primitiveType.BoolType;
import main.ast.type.primitiveType.IntType;
import main.ast.type.primitiveType.StringType;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemNotFound;
import main.symbolTable.item.FunctionItem;
import main.visitor.Visitor;
import main.visitor.type.TypeChecker;

import java.io.*;
import java.util.*;

public class CodeGenerator extends Visitor<String> {
    private final String outputPath;
    private FileWriter mainFile;
    private final TypeChecker typeChecker;
    private final Set<String> visited;
    private FunctionItem curFunction;

    private final HashMap<String, Type> returnTypes = new HashMap<>();
    private final HashMap<String, Integer> slots = new HashMap<>();

    private Stack<String> loopStartLabels = new Stack<>();
    private Stack<String> loopEndLabels = new Stack<>();
    private int curLabel = 0;

    public CodeGenerator(TypeChecker typeChecker){
        this.typeChecker = typeChecker;
        this.visited = typeChecker.visited;
        outputPath = "./codeGenOutput/";
        prepareOutputFolder();
    }
    private int slotOf(String var) {
        if (!slots.containsKey(var)) {
            slots.put(var, slots.size() + 1);
            return slots.size();
        }
        return slots.get(var);
    }
    public String getFreshLabel(){
        String fresh = "Label_" + curLabel;
        curLabel++;
        return fresh;
    }
    public String getType(Type element){
        String type = "";
        switch (element){
            case StringType stringType -> type += "Ljava/lang/String;";
            case IntType intType -> type += "Ljava/lang/Integer;";
            case FptrType fptrType -> type += "LFptr;";
            case ListType listType -> type += "LList;";
            case BoolType boolType -> type += "Ljava/lang/Boolean;";
            case null, default -> {
                type += "V";
            }
        }
        return type;
    }
    private void prepareOutputFolder(){
        String jasminPath = "utilities/jarFiles/jasmin.jar";
        String listClassPath = "utilities/codeGenerationUtilityClasses/List.j";
        String fptrClassPath = "utilities/codeGenerationUtilityClasses/Fptr.j";
        try{
            File directory = new File(this.outputPath);
            File[] files = directory.listFiles();
            if(files != null)
                for (File file : files)
                    file.delete();
            directory.mkdir();
        }
        catch(SecurityException e){
            // ignore
        }
        copyFile(jasminPath, this.outputPath + "jasmin.jar");
        copyFile(listClassPath, this.outputPath + "List.j");
        copyFile(fptrClassPath, this.outputPath + "Fptr.j");

        try {
            String path = outputPath + "Main.j";
            File file = new File(path);
            file.createNewFile();
            mainFile = new FileWriter(path);
        } catch (IOException e){
            // ignore
        }
    }
    private void copyFile(String toBeCopied, String toBePasted){
        try {
            File readingFile = new File(toBeCopied);
            File writingFile = new File(toBePasted);
            InputStream readingFileStream = new FileInputStream(readingFile);
            OutputStream writingFileStream = new FileOutputStream(writingFile);
            byte[] buffer = new byte[1024];
            int readLength;
            while ((readLength = readingFileStream.read(buffer)) > 0)
                writingFileStream.write(buffer, 0, readLength);
            readingFileStream.close();
            writingFileStream.close();
        } catch (IOException e){
            // ignore
        }
    }
    private void addCommand(String command){
        try {
            command = String.join("\n\t\t", command.split("\n"));
            if(command.startsWith("Label_"))
                mainFile.write("\t" + command + "\n");
            else if(command.startsWith("."))
                mainFile.write(command + "\n");
            else
                mainFile.write("\t\t" + command + "\n");
            mainFile.flush();
        } catch (IOException e){
            // ignore
        }
    }
    private void handleMainClass(){
        String commands = """
                .method public static main([Ljava/lang/String;)V
                .limit stack 128
                .limit locals 128
                new Main
                invokespecial Main/<init>()V
                return
                .end method
                """;
        addCommand(commands);
    }

    private String createNewFptr(String name){
        String commands = "";
        commands += "new Fptr\n";
        commands += "dup\n";
        commands += "aload_0\n";
        commands += "ldc " + "\"" + name + "\"\n";
        commands += "invokespecial Fptr/<init>(Ljava/lang/Object;Ljava/lang/String;)V\n";
        return commands;
    }

    private String createArray(int tempVar, ArrayList<Expression> elements){
        ArrayList<String> commands = new ArrayList<>();
        commands.add("new java/util/ArrayList");
        commands.add("dup");
        commands.add("invokespecial java/util/ArrayList/<init>()V");
        commands.add("astore " + tempVar);
        for (Expression arg : elements) {
            commands.add("aload " + tempVar);
            Type elementType = arg.accept(typeChecker);

            if (elementType instanceof ListType) {
                commands.add("new List");
                commands.add("dup");
            }
            commands.add(arg.accept(this));

            if (elementType instanceof ListType) {
                commands.add("invokespecial List/<init>(LList;)V");
            }
            commands.add("invokevirtual java/util/ArrayList/add(Ljava/lang/Object;)Z");
            commands.add("pop");
        }
        return String.join("\n", commands);
    }

    @Override
    public String visit(Program program){
        String commands = """
                .class public Main
                .super java/lang/Object
                """;
        addCommand(commands);
        handleMainClass();

        for(String funcName : this.visited) {
            try {
                this.curFunction = (FunctionItem) SymbolTable.root.getItem(FunctionItem.START_KEY +
                        funcName);
                this.curFunction.getFunctionDeclaration().accept(this);
            } catch(ItemNotFound ignored) {}
        }

        program.getMain().accept(this);
        return null;
    }

    private FunctionItem getFunctionItem(FunctionDeclaration functionDeclaration) {
        FunctionItem functionItem = null;
        try {
            functionItem = (FunctionItem) SymbolTable.root.getItem(FunctionItem.START_KEY + functionDeclaration.getFunctionName().getName());
            SymbolTable.push(functionItem.getFunctionSymbolTable());
        } catch (ItemNotFound ignored) {
        }
        return functionItem;
    }

    @Override
    public String visit(FunctionDeclaration functionDeclaration){
        slots.clear();
        FunctionItem functionItem = getFunctionItem(functionDeclaration);

        String name = functionItem.getName();
        Type returnType = functionItem.getReturnType();
        returnTypes.put(name, returnType);
        ArrayList<String> argsTypeSign = new ArrayList<String>();

        for (Type argType : functionItem.getArgumentTypes()) {
            argsTypeSign.add(getType(argType));
        }
        for (VarDeclaration arg : functionDeclaration.getArgs()) {
            slotOf(arg.getName().getName());
        }

        ArrayList<String> bodyStmts = new ArrayList<String>();
        for (var stmt : functionDeclaration.getBody())
            bodyStmts.add(stmt.accept(this));

        StringBuilder commands = new StringBuilder();
        commands.append(".method public ");

        commands.append(name).append('(');
        for (String arg : argsTypeSign)
            commands.append(arg);

        commands.append(')')
                .append(getType(returnType))
                .append('\n')
                .append(".limit stack ")
                .append("128\n")
                .append(".limit locals ")
                .append("128\n");

        for (String stmt : bodyStmts)
            commands.append(stmt).append('\n');

        if ((returnType == null || returnType instanceof NoType) &&
                !String.join(" ", commands.toString().split("\n")).endsWith("return"))
            commands.append("return\n");
        commands.append(".end method\n\n\n");

        for (var comm : commands.toString().split("\n"))
            addCommand(comm);

        SymbolTable.pop();
        return null;
}
    @Override
    public String visit(MainDeclaration mainDeclaration){
        slots.clear();

        String commands = "";
        commands += ".method public <init>()V\n";
        commands += ".limit stack 128\n";
        commands += ".limit locals 128\n";
        commands += "aload_0\n";
        commands += "invokespecial java/lang/Object/<init>()V\n";
        addCommand(commands);
        commands = "";
        for (var statement : mainDeclaration.getBody()) {
            commands += statement.accept(this);
            commands += "\n";
        }
        commands += "return\n";
        commands += ".end method\n";

        addCommand(commands);
        return null;
    }
    public String visit(AccessExpression accessExpression){
        var commands = new ArrayList<String>();
        if (accessExpression.isFunctionCall()) {
            Identifier functionName = (Identifier) accessExpression.getAccessedExpression();
            if (!this.visited.contains(functionName.getName())) {
                commands.add("aload " + slotOf(functionName.getName()));
            } else {
                commands.add(createNewFptr(functionName.getName()));
            }
            int tempVar = slotOf(" " + functionName.getName());
            commands.add(createArray(tempVar, accessExpression.getArguments()));
            commands.add("aload " + tempVar);
            commands.add("invokevirtual Fptr/invoke(Ljava/util/ArrayList;)Ljava/lang/Object;");
            if (returnTypes.get(functionName.getName()) == null || returnTypes.get(functionName.getName()) instanceof NoType)
                commands.add("pop");
        }

        else {
            Type elementType = ((ListType) accessExpression.getAccessedExpression().accept(typeChecker)).getType();
            commands.add(accessExpression.getAccessedExpression().accept(this));
            if (!accessExpression.getDimentionalAccess().isEmpty()){
                var dimAccess = accessExpression.getDimentionalAccess().get(0);
                commands.add(dimAccess.accept(this));
                commands.add("invokevirtual java/lang/Integer/intValue()I");
                commands.add("invokevirtual List/getElement(I)Ljava/lang/Object;");
                if (elementType instanceof IntType)
                    commands.add("checkcast java/lang/Integer");
                else if (elementType instanceof BoolType)
                    commands.add("checkcast java/lang/Boolean");
                else if (elementType instanceof StringType)
                    commands.add("checkcast java/lang/String");
            }
        }
        return String.join("\n", commands);
    }
    @Override
    public String visit(AssignStatement assignStatement) {
        ArrayList<String> stmts = new ArrayList<>();

        int index = slotOf(assignStatement.getAssignedId().getName());
        if (assignStatement.isAccessList()) {
            stmts.add("aload " + index);
            stmts.add("checkcast List");
            stmts.add(assignStatement.getAccessListExpression().accept(this));
            stmts.add("invokevirtual java/lang/Integer/intValue()I");
            handleAssignOperator(stmts, assignStatement, true);
            stmts.add("invokevirtual List/setElement(ILjava/lang/Object;)V");
        } else {
            handleAssignOperator(stmts, assignStatement, false);
            stmts.add("astore " + index);
        }

        return String.join("\n", stmts);
    }

    private void handleAssignOperator(ArrayList<String> stmts, AssignStatement assignStatement, boolean isList) {
        if (assignStatement.getAssignOperator() != AssignOperator.ASSIGN) {
            BinaryExpression binaryExpression = null;
            if (assignStatement.getAssignOperator() == AssignOperator.PLUS_ASSIGN) {
                binaryExpression = createBinaryExpression(assignStatement, BinaryOperator.PLUS, isList);
            } else if (assignStatement.getAssignOperator() == AssignOperator.MINUS_ASSIGN) {
                binaryExpression = createBinaryExpression(assignStatement, BinaryOperator.MINUS, isList);
            } else if (assignStatement.getAssignOperator() == AssignOperator.MULT_ASSIGN) {
                binaryExpression = createBinaryExpression(assignStatement, BinaryOperator.MULT, isList);
            } else if (assignStatement.getAssignOperator() == AssignOperator.DIVIDE_ASSIGN) {
                binaryExpression = createBinaryExpression(assignStatement, BinaryOperator.DIVIDE, isList);
            } else if (assignStatement.getAssignOperator() == AssignOperator.MOD_ASSIGN) {
                binaryExpression = createBinaryExpression(assignStatement, BinaryOperator.MOD, isList);
            }

        stmts.add(binaryExpression.accept(this));
        } else {
            stmts.add(assignStatement.getAssignExpression().accept(this));
        }
    }

    private BinaryExpression createBinaryExpression(AssignStatement assignStatement, BinaryOperator operator, boolean isList) {
        if (isList) {
            return new BinaryExpression(
                    new AccessExpression(assignStatement.getAssignedId(), assignStatement.getAccessListExpression()),
                    assignStatement.getAssignExpression(),
                    operator
            );
        } else {
            return new BinaryExpression(assignStatement.getAssignedId(), assignStatement.getAssignExpression(), operator);
        }
    }
    @Override
    public String visit(IfStatement ifStatement){
        SymbolTable.push(ifStatement.getSymbolTable());
        ArrayList<String> stmts = new ArrayList<>();
        stmts.add(ifStatement.getConditions().get(0).accept(this));

        String thenL = getFreshLabel();
        String elseL = getFreshLabel();
        String exitL = getFreshLabel();

        stmts.add("invokevirtual java/lang/Boolean/booleanValue()Z");

        stmts.add("ifeq" + " " + elseL);
        stmts.add(thenL + ":");
        for (var stmt : ifStatement.getThenBody())
            stmts.add(stmt.accept(this));
        if (!stmts.getLast().contains("return"))
            stmts.add("goto " + exitL);
        stmts.add(elseL + ":");
        for (var stmt : ifStatement.getElseBody())
            stmts.add(stmt.accept(this));

        stmts.add(exitL + ":");
        SymbolTable.pop();
        return String.join("\n",stmts);
    }

    @Override
    public String visit(PutStatement putStatement){
        ArrayList<String> stmts = new ArrayList<>();
        stmts.add("getstatic java/lang/System/out Ljava/io/PrintStream;");
        Type argType = putStatement.getExpression().accept(typeChecker);
        String commandsOfArg = putStatement.getExpression().accept(this);
        stmts.add(commandsOfArg);
        if (argType instanceof IntType) {
            stmts.add("checkcast java/lang/Integer");
            stmts.add("invokevirtual java/lang/Integer/intValue()I");
            stmts.add("invokevirtual java/io/PrintStream/println(I)V");
        }
        if (argType instanceof BoolType) {
            stmts.add("checkcast java/lang/Boolean");
            stmts.add("invokevirtual java/lang/Boolean/booleanValue()Z");
            stmts.add("invokevirtual java/io/PrintStream/println(Z)V");
        }

        if (argType instanceof StringType) {
            stmts.add("checkcast java/lang/String");
            stmts.add("invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V");
        }
        return String.join("\n", stmts);
    }

    @Override
    public String visit(ReturnStatement returnStatement){
        ArrayList<String> stmts = new ArrayList<>();
        if (!returnStatement.hasRetExpression()) {
            return "return";
        }
        stmts.add(returnStatement.getReturnExp().accept(this));

        stmts.add("areturn");
        return String.join("\n", stmts);
    }

    @Override
    public String visit(ExpressionStatement expressionStatement){
        return expressionStatement.getExpression().accept(this);
    }

    @Override
    public String visit(BinaryExpression binaryExpression) {
        ArrayList<String> stmts = new ArrayList<>();
        handleOperand(stmts, binaryExpression.getFirstOperand());
        handleOperand(stmts, binaryExpression.getSecondOperand());
        handleOperator(stmts, binaryExpression);
        return String.join("\n", stmts);
    }

    private void handleOperand(ArrayList<String> stmts, Expression operand) {
        stmts.add(operand.accept(this));
        if (operand.accept(typeChecker) instanceof IntType) {
            stmts.add("invokevirtual java/lang/Integer/intValue()I");
        } else if (operand.accept(typeChecker) instanceof BoolType) {
            stmts.add("invokevirtual java/lang/Boolean/booleanValue()Z");
        }
    }

    private void handleOperator(ArrayList<String> stmts, BinaryExpression binaryExpression) {
        switch (binaryExpression.getOperator()) {
            case PLUS -> {
                stmts.add("iadd");
                stmts.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            }
            case MINUS -> {
                stmts.add("isub");
                stmts.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            }
            case MULT -> {
                stmts.add("imul");
                stmts.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            }
            case DIVIDE -> {
                stmts.add("idiv");
                stmts.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            }
            case MOD ->{
                stmts.add("irem");
                stmts.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            }
            case EQUAL -> handleComparisonOperator(stmts, "if_icmpeq");
            case NOT_EQUAL -> handleComparisonOperator(stmts, "if_icmpne");
            case GREATER_THAN -> handleComparisonOperator(stmts, "if_icmpgt");
            case GREATER_EQUAL_THAN -> handleComparisonOperator(stmts, "if_icmpge");
            case LESS_THAN -> handleComparisonOperator(stmts, "if_icmplt");
            case LESS_EQUAL_THAN -> handleComparisonOperator(stmts, "if_icmple");
            default -> {}
        }
    }

    private void handleComparisonOperator(ArrayList<String> stmts, String jumpInstruction) {
        String L1 = getFreshLabel();
        String exitL = getFreshLabel();
        stmts.add(jumpInstruction + " " + L1);
        stmts.add("ldc 0");
        stmts.add("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
        stmts.add("goto " + exitL);
        stmts.add(L1 + ":");
        stmts.add("ldc 1");
        stmts.add("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
        stmts.add(exitL + ":");
    }


    @Override
    public String visit(UnaryExpression unaryExpression){
        ArrayList<String> stmts = new ArrayList<>();
        handleOperand(stmts, unaryExpression.getExpression());
        switch (unaryExpression.getOperator()) {
            case MINUS -> {
                stmts.add("ineg");
            }
            case NOT -> {
                stmts.add("ldc 1");
                stmts.add("ixor");
            }
            case INC -> {
                stmts.add("ldc 1");
                stmts.add("iadd");
                stmts.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                stmts.add("astore " + slotOf(((Identifier)unaryExpression.getExpression()).getName()));
            }
            case DEC -> {
                stmts.add("ldc -1");
                stmts.add("iadd");
                stmts.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                stmts.add("astore " + slotOf(((Identifier)unaryExpression.getExpression()).getName()));
            }
        }
        Type type = unaryExpression.getExpression().accept(typeChecker);
        if (type instanceof BoolType)
            stmts.add("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
        return String.join("\n", stmts);
    }


    @Override
    public String visit(Identifier identifier){
        FunctionItem fsti = null;
        try {
            fsti = (FunctionItem) SymbolTable.root.getItem(FunctionItem.START_KEY + identifier.getName());
        } catch (ItemNotFound ignored) {
        }
        String command = "";
        if (fsti == null) {
            Type type = identifier.accept(typeChecker);
            String typeSign = "a";
            command += typeSign + "load " + slotOf(identifier.getName()) + "\n";
            if (type instanceof ListType)
                command += "checkcast List";
            if (type instanceof StringType)
                command += "checkcast java/lang/String";
            if (type instanceof IntType)
                command += "checkcast java/lang/Integer";
            if (type instanceof BoolType)
                command += "checkcast java/lang/Boolean";
        } else {
            command += createNewFptr(identifier.getName());
        }
        return command;
    }

    @Override
    public String visit(LoopDoStatement loopDoStatement){
        SymbolTable.push(loopDoStatement.getSymbolTable());
        ArrayList<String> stmts = new ArrayList<String>();

        String startL = getFreshLabel();
        String exitL = getFreshLabel();
        loopStartLabels.push(startL);
        loopEndLabels.push(exitL);
        stmts.add(startL + ":");

        for (Statement statement : loopDoStatement.getLoopBodyStmts())
            stmts.add(statement.accept(this));

        stmts.add("goto " + startL);
        stmts.add(exitL + ":");
        SymbolTable.pop();
        return String.join("\n",stmts);
    }
    @Override
    public String visit(BreakStatement breakStatement){
        ArrayList<String> stmts = new ArrayList<>();
        stmts.add("goto " + loopEndLabels.peek());
        loopEndLabels.pop();
        loopStartLabels.pop();
        return String.join("\n", stmts);
    }
    @Override
    public String visit(NextStatement nextStatement){
        ArrayList <String> stmts = new ArrayList<>();
        stmts.add("goto " + loopStartLabels.peek());
        return String.join("\n", stmts);
    }
    @Override
    public String visit(LenStatement lenStatement){
        Type type = lenStatement.getExpression().accept(typeChecker);
        ArrayList<String> stmts = new ArrayList<>();
        stmts.add(lenStatement.getExpression().accept(this));
        if (type instanceof StringType) {
            stmts.add("invokevirtual java/lang/String/length()I");
        }
        if (type instanceof ListType) {
            stmts.add("invokevirtual List/getSize()I");
        }
        stmts.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
        return String.join("\n", stmts);
    }

    @Override
    public String visit(ChopStatement chopStatement) {
        ArrayList<String> stmts = new ArrayList<>();

        stmts.add(chopStatement.getChopExpression().accept(this));
        stmts.add("dup");
        stmts.add("invokevirtual java/lang/String/length()I");
        stmts.add("ldc 1");
        stmts.add("isub");
        stmts.add("iconst_0");
        stmts.add("swap");
        stmts.add("invokevirtual java/lang/String/substring(II)Ljava/lang/String;");

        return String.join("\n", stmts);
    }


    @Override
    public String visit(FunctionPointer functionPointer){
        FptrType fptr = (FptrType) functionPointer.accept(typeChecker);
        return createNewFptr(fptr.getFunctionName());
    }
    @Override
    public String visit(ListValue listValue){
        ArrayList<String> stmts = new ArrayList<>();
        int tempvar = slotOf(" " + listValue.hashCode());
        stmts.add(createArray(tempvar, listValue.getElements()));
        stmts.add("new List");
        stmts.add("dup");
        stmts.add("aload " + tempvar);
        stmts.add("invokespecial List/<init>(Ljava/util/ArrayList;)V");

        return String.join("\n", stmts);
    }
    @Override
    public String visit(IntValue intValue){
        return "ldc " + intValue.getIntVal() + "\n"
                + "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;";
    }
    @Override
    public String visit(BoolValue boolValue){
        return "ldc " + (boolValue.getBool() ? "1" : "0") + "\n"
                + "invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;";
    }
    @Override
    public String visit(StringValue stringValue){
        return "ldc "  + stringValue.getStr();
    }
}
