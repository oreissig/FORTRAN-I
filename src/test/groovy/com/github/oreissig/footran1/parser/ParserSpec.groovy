package com.github.oreissig.footran1.parser

import spock.lang.Stepwise
import spock.lang.Unroll

import com.github.oreissig.footran1.AbstractFootranSpec
import com.github.oreissig.footran1.AntlrSpec.SyntaxError

@Unroll
@Stepwise
class ParserSpec extends AbstractFootranSpec {
    
    def 'empty program parses successfully'() {
        when:
        input = ''
        
        then:
        program.card().empty
    }
    
    def 'card "#src" is recognized'(src, num, body) {
        when:
        input = src
        
        then:
        noParseError()
        cards.size() == 1
        cards[0].STMTNUM()?.text == num
        cards[0].statement().text == body
        
        where:
        src           | num  | body
        '      A=B'   | null | 'A=B'
        '      C=D\n' | null | 'C=D'
        '   23 X=Y'   | '23' | 'X=Y'
    }
    
    def 'multiple cards are parsed correctly'() {
        when:
        input = '''\
      A=B
C     FOO
      C=D'''
        
        then:
        noParseError()
        cards.size() == 2
        cards[0].statement().text == 'A=B'
        cards[1].statement().text == 'C=D'
    }
    
    def 'arithmetic formulas are parsed correctly (#var=#expr)'(var, expr) {
        when:
        input = card("$var=$expr")
        
        then:
        noParseError()
        def af = statement.arithmeticFormula()
        af != null
        def lhs = [af.variable(), af.subscript()].find()
        lhs.text == var
        af.expression().text == expr
        
        where:
        var    | expr
        'A'    | 'B'
        'FO0'  | '4711'
        'A(1)' | '42'   // subscript variable
        'FOOF' | 'BAR' // function candidate that is a non-subscripted var
    }
    
    def 'pixed point constants are parsed correctly (#src)'(src, sign, mag) {
        when:
        def e = parseExpression(src)
        
        then:
        noParseError()
        e.sign()?.text == sign
        def i = unary(e).ufixedConst()
        i.NUMBER().text == mag.toString()
        
        where:
        src      | sign | mag
        '3'      | null | 3
        '+1'     | '+'  | 1
        '-28987' | '-'  | 28987
        '0'      | null | 0
        '32767'  | null | 32767
        '-32767' | '-'  | 32767
    }
    
    def 'floating point constants are parsed correctly (#src)'(src, sign, integ, frac, expSign, expMag) {
        when:
        def e = parseExpression(src)
        
        then:
        noParseError()
        e.sign()?.text == sign
        def uf = unary(e).ufloatConst()
        uf.integer?.text == integ?.toString()
        def fraction = [uf.fraction, uf.fractionE].find()
        fraction?.text == frac?.toString()
        uf.expSign?.text == expSign
        uf.exponent?.NUMBER()?.text == expMag?.toString()
        
        where:
        src      | sign | integ | frac   | expSign | expMag
        '17.'    | null | 17    | null   | null    | null
        '+5.0'   | '+'  | 5     | 0      | null    | null
        '-.0003' | '-'  | null  | '0003' | null    | null
        // to be able to lex the E properly, it is part of the fraction token
        '5.0E3'  | null | 5     | '0E'   | null    | 3
        '5.0E+3' | null | 5     | '0E'   | '+'     | 3
        '5.0E-7' | null | 5     | '0E'   | '-'     | 7
        '0.0'    | null | 0     | 0      | null    | null
    }
    
    def 'subscript variables are parsed correctly (#var)'(var, dimensions, name, v, c) {
        when:
        input = card("$var=1")
        
        then:
        noParseError()
        def s = statement.arithmeticFormula().subscript()
        s.var.text == name
        s.subscriptExpression().size() == dimensions
        def e = s.subscriptExpression()[0]
        e.index?.text == v
        e.constant?.text == c
        
        where:
        var         | dimensions | name  | v    | c
        'A(I)'      | 1          | 'A'   | 'I'  | null
        'BLA(3)'    | 1          | 'BLA' | null | '3'
        'C(1,B)'    | 2          | 'C'   | null | '1'
        'D(1,B,C3)' | 3          | 'D'   | null | '1'
    }
    
    // subscript expressions = factor * index +/- summand
    def 'subscript expressions are parsed correctly (#expr)'(expr, factor, index, sign, summand) {
        when:
        input = card("A($expr)=1")
        
        then:
        noParseError()
        def e = statement.arithmeticFormula().subscript().subscriptExpression()[0]
        e.factor?.text == factor
        e.index?.text == index
        e.sign()?.text == sign
        e.summand?.text == summand
        
        where:
        expr     | factor | index | sign | summand
        'MU+2'   | null   | 'MU'  | '+'  | '2'
        'MU-2'   | null   | 'MU'  | '-'  | '2'
        '5*J'    | '5'    | 'J'   | null | null
        '5*J+2'  | '5'    | 'J'   | '+'  | '2'
        '5*J-2'  | '5'    | 'J'   | '-'  | '2'
    }
    
    def 'function calls are parsed correctly (#src)'(src, function, params) {
        when:
        def e = parseExpression(src)
        
        then:
        noParseError()
        def f = unary(e).functionCall()
        f.function.text == function
        f.expression()*.text == params
        
        where:
        src                     | function | params
        'FOOF(1)'               | 'FOOF'   | ['1']
        'BARF(1,2,3,4)'         | 'BARF'   | ['1','2','3','4']
        'BAZF(ABC,DEF)'         | 'BAZF'   | ['ABC','DEF']
        'BZRF(BARF(BAZF(FOO)))' | 'BZRF'   | ['BARF(BAZF(FOO))']
    }
    
    def '#name are valid expressions (#src)'(src, member, name) {
        when:
        def e = parseExpression(src)
        
        then:
        noParseError()
        unary(e)."$member"().text == src
        
        where:
        src       | member         | name
        'ABC'     | 'variable'     | 'variables'
        'A(X)'    | 'subscript'    | 'subscripts'
        'SINF(X)' | 'functionCall' | 'function calls'
        '23'      | 'ufixedConst'  | 'fixed point constants'
        '42.'     | 'ufloatConst'  | 'floating point constants'
    }
    
    def 'expressions can be nested'() {
        when:
        def e = parseExpression('(A)')
        
        then:
        noParseError()
        unary(e).expression().text == 'A'
    }
    
    def 'non-matching parenthesis are caught ("#src")'(src, msg) {
        when:
        parseExpression(src)
        
        then:
        SyntaxError e = thrown()
        e.msg.startsWith msg
        
        where:
        src              | msg
        'FOOF(BARF(1)'   | "missing ')'"
        'FOOF(BARF(1)))' | "extraneous input ')'"
    }
    
    def 'expressions can be signed (#src)'(src, sign) {
        when:
        def e = parseExpression(src)
        
        then:
        noParseError()
        e.sign()?.text == sign
        unary(e).text == 'A'
        
        where:
        src  | sign
        'A'  | null
        '+A' | '+'
        '-A' | '-'
    }
    
    def 'expressions can have at most one sign'() {
        when:
        input = card("A=+-B")
        program
        
        then:
        SyntaxError e = thrown()
        e.msg.startsWith("extraneous input '-'")
    }
    
    def 'additive operations are parsed correctly (#src)'(src,op,summands) {
        when:
        def e = parseExpression(src)
        
        then:
        noParseError()
        def sum = e.sum()
        sum.sum()*.text == summands
        sum.sign().text == op
        !sum.product()
        
        where:
        src     | op  | summands
        'A+3'   | '+' | ['A','3']
        'B-4'   | '-' | ['B','4']
        '+A+3'  | '+' | ['A','3']
        'A+B+C' | '+' | ['A+B','C']
        '1-2+3' | '+' | ['1-2','3']
    }
    
    def 'multiplicative operations are parsed correctly (#src)'(src,op,factors) {
        when:
        def e = parseExpression(src)
        
        then:
        noParseError()
        def product = e.sum().product()
        product.product()*.text == factors
        product.mulOp().text == op
        !product.power()
        
        where:
        src     | op  | factors
        'A*3'   | '*' | ['A','3']
        'B/4'   | '/' | ['B','4']
        '+A*3'  | '*' | ['A','3']
        'A*B*C' | '*' | ['A*B','C']
        '1/2*3' | '*' | ['1/2','3']
    }
    
    def 'exponential operations are parsed correctly (A ** B)'() {
        when:
        def e = parseExpression("A**B")
        
        then:
        noParseError()
        def power = e.sum().product().power()
        power.unaryExpression()*.text == ['A','B']
    }
    
    def 'Hierarchy of operations is correct'() {
        when:
        def root = parseExpression('A+B/C+D**E*F-G').sum()
        
        then:
        /*
         * A+B/C+D**E*F-G
         * -----a------|b
         * -c---|----d-
         * e|-f- -g--|h
         *   i|j k|-l
         */
        def a = root.sum(0)
        def b = root.sum(1)
        def c = a.sum(0)
        def d = a.sum(1).product()
        def e = c.sum(0)
        def f = c.sum(1).product()
        def g = d.product(0).power()
        def h = d.product(1)
        def i = f.product(0)
        def j = f.product(1)
        def k = g.unaryExpression(0)
        def l = g.unaryExpression(1)
        
        [e,i,j,k,l,h,b]*.text == 'ABCDEFG'.split('')
        root.sign().MINUS()
        a.sign().PLUS()
        c.sign().PLUS()
        d.mulOp().MUL()
        f.mulOp().DIV()
        g.POWER()
    }
    
    def 'unconditional goto can be parsed (#num)'(num) {
        when:
        input = card("GO TO $num")
        
        then:
        noParseError()
        def goTo = statement.uncondGoto()
        goTo.statementNumber().text == num.toString()
        
        where:
        num << [1, 23, 32767]
    }
    
    def 'assigned goto can be parsed'() {
        when:
        input = card('GO TO N, (7,12,19)')
        
        then:
        noParseError()
        def goTo = statement.assignedGoto()
        goTo.variable().text == 'N'
        goTo.statementNumber()*.text == ['7','12','19']
    }
    
    def 'assign statement can be parsed'() {
        when:
        input = card('ASSIGN 12 TO N')
        
        then:
        noParseError()
        def a = statement.assign()
        a.statementNumber().text == '12'
        a.variable().text == 'N'
    }
    
    def 'computed goto can be parsed'() {
        when:
        input = card('GO TO (30,40,50,60), I')
        
        then:
        noParseError()
        def goTo = statement.computedGoto()
        goTo.statementNumber()*.text == ['30','40','50','60']
        goTo.variable().text == 'I'
    }
    
    def 'if statements can be parsed (#src)'(src, cond, lt, eq, gt) {
        when:
        input = card(src)
        
        then:
        noParseError()
        def i = statement.ifStatement()
        i.condition.text == cond
        i.lessThan.text == lt.toString()
        i.equal.text == eq.toString()
        i.greaterThan.text == gt.toString()
        
        where:
        src                   | cond     | lt | eq | gt
        'IF(3) 1,2,3'         | '3'      | 1  | 2  | 3
        'IF(I) 1,23,32767'    | 'I'      | 1  | 23 | 32767
        'IF(A(J,K)) 10,20,30' | 'A(J,K)' | 10 | 20 | 30
    }
    
    def 'sense light statements can be parsed (#i)'(i) {
        when:
        input = card("SENSE LIGHT $i")
        
        then:
        noParseError()
        def s = statement.senseLight()
        s.light.text == i.toString()
        
        where:
        i << (0..4).asList()
    }
    
    def 'if sense light statements can be parsed (#i)'(i) {
        when:
        input = card("IF (SENSE LIGHT $i) 30, 40")
        
        then:
        noParseError()
        def isl = statement.ifSenseLight()
        isl.light.text == i.toString()
        isl.on.text == '30'
        isl.off.text == '40'
        
        where:
        i << (1..4).asList()
    }
    
    def 'if sense switch statements can be parsed (#i)'(i) {
        when:
        input = card("IF (SENSE SWITCH $i) 30, 40")
        
        then:
        noParseError()
        def iss = statement.ifSenseSwitch()
        iss.senseSwitch.text == i.toString()
        iss.down.text == '30'
        iss.up.text == '40'
        
        where:
        i << (1..6).asList()
    }
    
    def 'if #flag can be parsed'(flag, rule) {
        when:
        input = card("IF ${flag.toUpperCase()} 30, 40")
        
        then:
        noParseError()
        def i = statement."$rule"()
        i.on.text == '30'
        i.off.text == '40'
        
        where:
        flag                   | rule
        'accumulator overflow' | 'ifAccumulatorOverflow'
        'quotient overflow'    | 'ifQuotientOverflow'
        'divide check'         | 'ifDivideCheck'
    }
    
    def 'do loop can be parsed (#first,#last,#step)'(src,first,last,step) {
        when:
        input = card(src)
        
        then:
        noParseError()
        def d = statement.doLoop()
        d.range.text == '30'
        d.index.text == 'I'
        d.first.text == first
        d.last.text == last
        d.step?.text == step
        
        where:
        src             | first | last | step
        'DO 30 I=1,10'  | '1'   | '10' | null
        'DO 30 I=1,M,3' | '1'   | 'M'  | '3'
        'DO 30 I=A,2,C' | 'A'   | '2'  | 'C'
    }
    
    def '#src statements can be parsed'(src, rule) {
        when:
        input = card(src)
        
        then:
        noParseError()
        statement."$rule"().text == src
        
        where:
        src        | rule
        'CONTINUE' | 'continueStmt'
        'PAUSE'    | 'pause'
        'STOP'     | 'stop'
    }
    
    def '#src statement may state a return code'(src) {
        when:
        input = card("$src 77777")
        
        then:
        noParseError()
        def rule = src.toLowerCase()
        statement."$rule"().consoleOutput.text == '77777'
        
        where:
        src << ['PAUSE', 'STOP']
    }
    
    def 'dimension statement can be parsed'() {
        when:
        input = card('DIMENSION A(10),B(5,15),C(3,4,5)')
        
        then:
        noParseError()
        def allocs = statement.dimension().allocation()
        allocs*.var*.text == ['A','B','C']
        allocs[0].ufixedConst()*.text == ['10']
        allocs[1].ufixedConst()*.text == ['5','15']
        allocs[2].ufixedConst()*.text == ['3','4','5']
    }
    
    def 'equivalence statement can be parsed'() {
        when:
        input = card('EQUIVALENCE (A,B(1),C(5)), (D(17),E(3)), (FOOF,BARF)')
        
        then:
        noParseError()
        def groups = statement.equivalence().group()
        groups.size() == 3
        def g1 = groups[0].quantity()
        g1*.VAR_ID()*.text == ['A','B','C']
        g1*.location*.text == [null,'1','5']
        def g2 = groups[1].quantity()
        g2*.VAR_ID()*.text == ['D','E']
        g2*.location*.text == ['17','3']
        def g3 = groups[2].quantity()
        g3*.FUNC_CANDIDATE()*.text == ['FOOF', 'BARF']
    }
    
    def 'frequency statement can be parsed'() {
        when:
        input = card('FREQUENCY 30(1,2,1), 40(11), 50(1,7,1,1)')
        
        then:
        noParseError()
        def est = statement.frequency().estimate()
        est.size() == 3
        est*.statementNumber()*.text == ['30','40','50']
        est[0].ufixedConst()*.text == ['1','2','1']
        est[1].ufixedConst()*.text == ['11']
        est[2].ufixedConst()*.text == ['1','7','1','1']
    }
    
    def 'READ DRUM can be parsed'(drum,start,list) {
        when:
        input = card("READ DRUM $drum, $start, ${list.join(',')}")
        
        then:
        noParseError()
        def read = statement.readDrum()
        read.drum.text == drum
        read.word.text == start
        read.variable()*.text == list
        
        where:
        drum | start  | list
        '2'  | '1000' | ['A', 'B', 'C', 'D']
        'I'  | 'J'    | ['A', 'B', 'C', 'D']
    }
    
    def 'WRITE DRUM can be parsed'(drum,start,list) {
        when:
        input = card("WRITE DRUM $drum, $start, ${list.join(',')}")
        
        then:
        noParseError()
        def write = statement.writeDrum()
        write.drum.text == drum
        write.word.text == start
        write.variable()*.text == list
        
        where:
        drum | start  | list
        '2'  | '1000' | ['A', 'B', 'C', 'D']
        'I'  | 'J'    | ['A', 'B', 'C', 'D']
    }
}
