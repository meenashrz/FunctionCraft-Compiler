grammar FunctionCraft;

function_craft
    : (function | comment)* main comment*
    ;

comment
    : SINGLELINECOMMENT
    | MULTILINECOMMENT
    ;

main:
    DEF
    MAIN
    LPAR
    RPAR
    body_function
    END
    ;

body_function
    : (statement | comment)*
    ;


function:
    DEF
    IDENTIFIER
    LPAR
    function_args
    RPAR
    body_function
    END
    ;

function_args:
    ((IDENTIFIER COMMA)*
    ((LBRACKET (IDENTIFIER ASSIGN primitive_val COMMA)* (IDENTIFIER ASSIGN primitive_val) RBRACKET)?
     | (IDENTIFIER)))?
    ;

lambda_function:
    ARROW
    LPAR
    function_args
    RPAR
    LBRACE
    body_function
    RBRACE
    ;

primitive_value
    : INT_VAL
    | FLOAT_VAL
    | STRING_VAL
    | BOOLEAN_VAL
    | lambda_function
    | list_primitive
    ;

list_primitive:
    LBRACKET (primitive_value COMMA)* primitive_value RBRACKET
    ;

list:
    LBRACKET
    (list_value COMMA)*
    list_value
    RBRACKET
    ;

list_value:
          primitive_value
          | IDENTIFIER
          | list
          ;

range:
    LPAR
    IDENTIFIER | INT_VAL
    DOUBLEDOT
    IDENTIFIER | INT_VAL
    RPAR
    ;

loop:
    LOOP
    DO
    loop_structure
    END
    ;

for:
    FOR
    IDENTIFIER
    IN
    list | IDENTIFIER | range
    loop_structure
    END
    ;

function_ptr:
    METHOD
    LPAR
    COLON
    IDENTIFIER
    RPAR


def f()

//statement
//    : (print
//    | returnf
//    | declaration
//    | assignment
//    | initialization
//    | initialization_array
//    | function_call
//    | predicate_def) SEMICOLON
//    | forloop
//    | implication
//    ;







// Keywords
END:       'end';
DEF:       'def';
IF:         'if';
ELSE:       'else';
ELSEIF:     'elseif';
TRUE:       'true';
FALSE:      'false';
CHOP:       'chop';
CHOMP:      'chomp';
PUSH:       'push';
MAIN:     'main';
RETURN:   'return';
PUTS:       'puts';
METHOD:     'method';
LEN:        'len';
PATTERN:     'patern';
MATCH:      'match';
NEXT:       'next';
BREAK:       'break';
LOOP:       'loop';
DO:         'do';
FOR:        'for';
IN:         'in';

// Types

INT:       'int';
FLOAT:     'float';
STRING:    'string';
BOOLEAN:   'bool';
LIST:      'list';
FUNCPTR:   'fptr';

// Type Values

ZERO:        '0';
INT_VAL:     [1-9][0-9]*;
FLOAT_VAL:   INT_VAL '.' [0-9]+ | '0.' [0-9]*;
STRING_VAL:  '"' ('\\' ["\\] | ~["\\\r\n])* '"';
BOOLEAN_VAL: 'true' | 'false';

// Parenthesis

LPAR: '(';
RPAR: ')';

// Brackets (array element access)

LBRACKET: '[';
RBRACKET: ']';

// Arithmetic Operators

PLUS:  '+';
MINUS: '-';
MULT:  '*';
DIV:   '/';
NEG:   '-';
INC:    '++';
DEC:    '--';

// Relational Operators

GEQ: '>=';
LEQ: '<=';
GTR: '>';
LES: '<';
EQL: '==';
NEQ: '!=';

// Logical Operators

AND: '&&';
OR:  '||';
NOT:  '!';

// Assignments

ASSIGN:     '=';
ADD_ASSIGN: '+=';
SUB_ASSIGN: '-=';
MUL_ASSIGN: '*=';
DIV_ASSIGN: '/=';
MOD_ASSIGN: '%=';

// Other Operators
APPEND:     '<<';


// Symbols

LBRACE:    '{';
RBRACE:    '}';
COMMA:     ',';
DOT:       '.';
DOUBLEDOT: '..';
COLON:     ':';
SEMICOLON: ';';
QUESTION:  '?';

// Other

IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;
//PREDICATE:  [A-Z][a-zA-Z0-9_]*;
ARROW:      '->';
SINGLELINECOMMENT:    '#' ~[\r\n]* -> skip;
MULTILINECOMMENT:     '=begin' .*? '=end' -> skip;
WS:         [ \t\r\n]+ -> skip;