package com.redhat.lightblue.metadata.rdbms;

import com.redhat.lightblue.metadata.parser.MetadataParser;
import com.redhat.lightblue.metadata.parser.PropertyParser;
import com.redhat.lightblue.util.Path;

import java.util.ArrayList;
import java.util.List;

public class RDBMSPropertyParserImpl<T> extends PropertyParser<T> {

    @Override
    public Object parse(String name, MetadataParser<T> p, T node) {
        if (!"rdbms".equals(name)) {
            throw com.redhat.lightblue.util.Error.get(RDBMSConstants.ERR_WRONG_ROOT_NODE_NAME, "Node name informed:"+name);
        }
        RDBMS rdbms = new RDBMS();
        rdbms.setDelete(parseOperation(p, p.getObjectProperty(node, "delete")));
        rdbms.setFetch(parseOperation(p, p.getObjectProperty(node, "fetch")));
        rdbms.setInsert(parseOperation(p, p.getObjectProperty(node, "insert")));
        rdbms.setSave(parseOperation(p, p.getObjectProperty(node, "save")));
        rdbms.setUpdate(parseOperation(p, p.getObjectProperty(node, "update")));

        return rdbms;
    }

    @Override
    public void convert(MetadataParser<T> p, T parent, Object object) {
        p.putObject(parent,"rdbms", convertRDBMS(p, (RDBMS) object));
    }

    private Object convertRDBMS(MetadataParser<T> p, RDBMS object) {
        T rdbms = p.newNode();
        p.putObject(rdbms, "delete", convertOperation(p, object.getDelete()));
        p.putObject(rdbms, "fetch", convertOperation(p, object.getFetch()));
        p.putObject(rdbms, "insert", convertOperation(p, object.getInsert()));
        p.putObject(rdbms, "save", convertOperation(p, object.getSave()));
        p.putObject(rdbms, "update", convertOperation(p, object.getUpdate()));
        return rdbms;
    }

    private Operation parseOperation(MetadataParser<T> p, T operation) {
        T b = p.getObjectProperty(operation, "bindings");
        Bindings bindings = transformToBindings(p, b);

        List<T> expressionsT = p.getObjectList(operation, "expressions");
        List<Expression> expressions = parseExpressions(p, expressionsT);

        final Operation s = new Operation();
        s.setBindings(bindings);
        s.setExpressionList(expressions);

        return s;
    }

    private Bindings transformToBindings(MetadataParser<T> p,T bindings) {
        final Bindings b = new Bindings();
        List<T> inRaw = p.getObjectList(bindings, "in");
        List<T> outRaw = p.getObjectList(bindings, "out");

        List<InOut> inList = transformToInOut(p, inRaw);
        List<InOut> outList = transformToInOut(p, outRaw);

        b.setInList(inList);
        b.setOutList(outList);

        return b;
    }

    private List<InOut> transformToInOut(MetadataParser<T> p, List<T> inRaw) {
        final ArrayList<InOut> result = new ArrayList<>();
        for (T t : inRaw) {
            InOut a = new InOut();
            a.setColumn(p.getStringProperty(t, "column"));
            a.setPath(new Path(p.getStringProperty(t, "path")));

            result.add(a);
        }
        return result;
    }

    private List<Expression> parseExpressions(MetadataParser<T> p, List<T> expressionsT) {
        final ArrayList<Expression> result = new ArrayList<>();
        for (T expression : expressionsT) {
            Expression e;
            String sql = p.getStringProperty(expression, "sql");
            T forS = p.getObjectProperty(expression, "$for");
            T foreachS = p.getObjectProperty(expression, "$foreach");
            T ifthen = p.getObjectProperty(expression, "$if");

            if(sql != null) {
                String datasource = p.getStringProperty(expression, "datasource");
                String type = p.getStringProperty(expression, "type");

                Statement statement = new Statement();
                statement.setSQL(sql);
                statement.setDatasource(datasource);
                statement.setType(type);

                e = statement;
            } else if(forS != null){
                String loopTimesS = p.getStringProperty(forS, "loopTimes");
                int loopTimes =  Integer.parseInt(loopTimesS);
                String loopCounterVariableName = p.getStringProperty(forS, "loopCounterVariableName");
                List<T> expressionsTforS = p.getObjectList(forS, "expressions");
                List<Expression> expressions = parseExpressions(p, expressionsTforS);

                For forLoop = new For();
                forLoop.setLoopTimes(loopTimes);
                forLoop.setLoopCounterVariableName(loopCounterVariableName);
                forLoop.setExpressions(expressions);

                e = forLoop;
            } else if(foreachS != null){
                String iterateOverPath = p.getStringProperty(foreachS, "iterateOverPath");
                List<T> expressionsTforS = p.getObjectList(foreachS, "expressions");
                List<Expression> expressions = parseExpressions(p, expressionsTforS);

                ForEach forLoop = new ForEach();
                forLoop.setIterateOverPath(new Path(iterateOverPath));
                forLoop.setExpressions(expressions);

                e = forLoop;
            } else if(ifthen != null) {
                //$if
                If If = parseIf(p, ifthen);

                //$then
                Then Then = parseThen(p,expression);

                //$elseIf
                List<ElseIf> elseIfList= parseElseIf(p,expression);

                //$else
                Else elseC = parseElse(p,expression);

                Conditional c  = new Conditional();
                c.setIf(If);
                c.setThen(Then);
                c.setElseIfList(elseIfList);
                c.setElse(elseC);

                e = c;
            } else {
                throw com.redhat.lightblue.util.Error.get(RDBMSConstants.ERR_WRONG_FIELD, "No valid field was set as expression ->"+expression.toString());
            }
            result.add(e);
        }
        return result;
    }

    private List<ElseIf> parseElseIf(MetadataParser<T> p, T expression) {
        List<T> elseIfs = p.getObjectList(expression, "$elseIf");
        List<ElseIf> elseIfList = new ArrayList<>();
        for(T ei : elseIfs){
            T eiIfT = p.getObjectProperty(ei, "$if");
            If eiIf = parseIf(p, eiIfT);

            T eiThenT = p.getObjectProperty(ei, "$then");
            Then eiThen = parseThen(p,eiThenT);

            ElseIf elseIf = new ElseIf();
            elseIf.setIf(eiIf);
            elseIf.setThen(eiThen);
            elseIfList.add(elseIf);
        }
        return elseIfList;
    }

    private If parseIf(MetadataParser<T> p, T ifT) {
        If x = null;
        List<T> orArray = p.getObjectList(ifT, "$or");
        if (orArray != null) {
             x = parseIfs(p, orArray, new IfOr());
        } else {
            List<T> anyArray = p.getObjectList(ifT, "$any");
            if (anyArray != null) {
                 x = parseIfs(p, anyArray, new IfOr());
            } else {
                List<T> andArray = p.getObjectList(ifT, "$and");
                if (andArray != null) {
                     x = parseIfs(p, andArray, new IfAnd());
                } else {
                    List<T> allArray = p.getObjectList(ifT, "$all");
                    if (allArray != null) {
                         x = parseIfs(p, allArray, new IfAnd());
                    } else {
                        T notIfT = p.getObjectProperty(ifT, "$not");
                        if (notIfT != null) {
                            If y = parseIf(p, notIfT);
                            x = new IfNot();
                            x.getConditions().add(y);
                        } else {
                            T pathEmpty = p.getObjectProperty(ifT, "$path-empty");
                            if (pathEmpty != null) {
                                x = new IfPathEmpty();
                                String path1 = p.getStringProperty(pathEmpty, "path1");
                                ((IfPathEmpty) x).setPath1(new Path(path1));
                            } else {
                                T pathpath = p.getObjectProperty(ifT, "$path-check-path");
                                if (pathpath != null) {
                                    x = new IfPathPath();
                                    String conditional = p.getStringProperty(pathpath, "conditional");
                                    String path1 = p.getStringProperty(pathpath, "path1");
                                    String path2 = p.getStringProperty(pathpath, "path2");
                                    ((IfPathPath) x).setPath1(new Path(path1));
                                    ((IfPathPath) x).setPath2(new Path(path2));
                                    ((IfPathPath) x).setConditional(conditional);
                                } else {
                                    T pathvalue = p.getObjectProperty(ifT, "$path-check-value");
                                    if (pathvalue != null) {
                                        x = new IfPathValue();
                                        String conditional = p.getStringProperty(pathvalue, "conditional");
                                        String path1 = p.getStringProperty(pathvalue, "path1");
                                        String value2 = p.getStringProperty(pathvalue, "value2");
                                        ((IfPathValue) x).setPath1(new Path(path1));
                                        ((IfPathValue) x).setValue2(value2);
                                        ((IfPathValue) x).setConditional(conditional);
                                    } else {
                                        T pathvalues = p.getObjectProperty(ifT, "$path-check-values");
                                        if (pathvalues != null) {
                                            x = new IfPathValues();
                                            String conditional = p.getStringProperty(pathvalues, "conditional");
                                            String path1 = p.getStringProperty(pathvalues, "path1");
                                            List<String> values2 = p.getStringList(pathvalues, "values2");

                                            ((IfPathValues) x).setPath1(new Path(path1));
                                            ((IfPathValues) x).setValues2(values2);
                                            ((IfPathValues) x).setConditional(conditional);
                                        } else {
                                            T pathregex = p.getObjectProperty(ifT, "$path-regex");
                                            if (pathregex != null) {
                                                x = new IfPathRegex();
                                                String path = p.getStringProperty(pathregex, "path");
                                                String regex = p.getStringProperty(pathregex, "regex");
                                                String caseInsensitive = p.getStringProperty(pathregex, "case_insensitive");
                                                String multiline = p.getStringProperty(pathregex, "multiline");
                                                String extended = p.getStringProperty(pathregex, "extended");
                                                String dotall = p.getStringProperty(pathregex, "dotall");
                                                ((IfPathRegex) x).setPath(new Path(path));
                                                ((IfPathRegex) x).setRegex(regex);
                                                ((IfPathRegex) x).setCaseInsensitive(Boolean.parseBoolean(caseInsensitive));
                                                ((IfPathRegex) x).setMultiline(Boolean.parseBoolean(multiline));
                                                ((IfPathRegex) x).setExtended(Boolean.parseBoolean(extended));
                                                ((IfPathRegex) x).setDotall(Boolean.parseBoolean(dotall));
                                            } else {
                                                throw com.redhat.lightblue.util.Error.get(RDBMSConstants.ERR_WRONG_FIELD, "No valid if field was set ->"+ifT.toString());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    return x;
    }

    private <Z extends If> Z parseIfs(MetadataParser<T> p, List<T> orArray, Z ifC) {
        List<If> l = new ArrayList<>();
        for (T t : orArray){
            If eiIf = parseIf(p, t);
            l.add(eiIf);
        }
        ifC.setConditions(l);
        return ifC;
    }

    private Then parseThenOrElse(MetadataParser<T> p, T t, String name, Then then) {
        String loopOperator = p.getStringProperty(t, name);
        if(loopOperator != null) {
            then.setLoopOperator(loopOperator);
        } else {
            List<T> expressionsT= p.getObjectList(t, name);
            List<Expression> expressions = parseExpressions(p, expressionsT);
            then.setExpressions(expressions);
        }

        return then;
    }

    private Then parseThen(MetadataParser<T> p, T parentThenT) {
        return parseThenOrElse(p, parentThenT, "$then", new Else());
    }

    private Else parseElse(MetadataParser<T> p, T parentElseT) {
        return (Else) parseThenOrElse(p, parentElseT, "$else", new Else());
    }



    private Object convertOperation(MetadataParser<T> p, Operation o) {
        if(o == null){
            throw com.redhat.lightblue.util.Error.get(RDBMSConstants.ERR_FIELD_REQ, "No operation informed");
        }
        T oT = p.newNode();
        if(o.getBindings() != null) {
            p.putObject(oT, "bindings", convertBindings(p, o.getBindings()));
        }
        Object expressions = p.newArrayField(oT, "expressions");
        convertExpressions(p, o.getExpressionList(), expressions);
        return oT;
    }

    private Object convertBindings(MetadataParser<T> p, Bindings bindings) {
        boolean bIn = bindings.getInList() == null || bindings.getInList().size() == 0;
        boolean bOut = bindings.getOutList() == null || bindings.getOutList().size() == 0;
        if(bIn && bOut){
            throw com.redhat.lightblue.util.Error.get(RDBMSConstants.ERR_FIELD_REQ, "No fields found for binding");
        }
        T bT = p.newNode();
        if(!bIn) {
            Object arri = p.newArrayField(bT, "in");
            for (InOut x : bindings.getInList()) {
                p.addObjectToArray(arri, convertInOut(p, x));
            }
        }
        if(!bOut) {
            Object arro = p.newArrayField(bT, "out");
            for (InOut x : bindings.getOutList()) {
                p.addObjectToArray(arro, convertInOut(p, x));
            }
        }
        return bT;
    }

    private Object convertInOut(MetadataParser<T> p, InOut x) {
        T ioT = p.newNode();

        p.putString(ioT,"column",x.getColumn());
        p.putString(ioT,"path",x.getPath().toString());

        return ioT;
    }

    private void convertExpressions(MetadataParser<T> p, List<Expression> expressionList, Object expressions) {
        if(expressionList == null || expressionList.size() == 0){
            throw com.redhat.lightblue.util.Error.get(RDBMSConstants.ERR_FIELD_REQ, "Expressions not informed");
        }
        for (Expression expression : expressionList) {
            expression.convert(p,expressions);
        }
    }
}