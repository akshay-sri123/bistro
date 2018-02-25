package org.conceptoriented.bistro.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The logic of evaluation of calculate columns.
 */
public class ColumnDefinitionCalc implements ColumnDefinition {

    Column column;

    Expression expr;

    List<BistroError> errors = new ArrayList<>();

    @Override
    public List<BistroError> getErrors() {
        return this.errors;
    }

    @Override
    public List<Element> getDependencies() {
        List<ColumnPath> paths = this.expr.getParameterPaths();
        List<Column> cols = ColumnPath.getColumns(paths);
        List<Element> deps = new ArrayList<>();
        for(Column col : cols) deps.add(col);
        return deps;
    }

    @Override
    public void eval() {
        if(this.expr == null) { // Default
            this.column.setValue(); // Reset
            return;
        }

        errors.clear(); // Clear state

        Table mainTable = this.column.getInput(); // Loop/scan table

        Range mainRange = mainTable.getIdRange();

        // Get all necessary parameters and prepare (resolve) the corresponding data (function) objects for reading valuePaths
        List<ColumnPath> paramPaths = this.expr.getParameterPaths();
        Object[] paramValues = new Object[paramPaths.size() + 1]; // Will store valuePaths for all params and current output at the end
        Object result; // Will be written to output for each input

        for(long i=mainRange.start; i<mainRange.end; i++) {

            // Read all parameter valuePaths
            for(int p=0; p<paramPaths.size(); p++) {
                paramValues[p] = paramPaths.get(p).getValue(i);
            }

            // Evaluate
            try {
                result = this.expr.eval(paramValues);
            }
            catch(BistroError e) {
                this.errors.add(e);
                return;
            }
            catch(Exception e) {
                this.errors.add( new BistroError(BistroErrorCode.EVALUATION_ERROR, e.getMessage(), "") );
                return;
            }

            // Update output
            this.column.setValue(i, result);
        }

    }

    public ColumnDefinitionCalc(Column column, EvaluatorCalc lambda, ColumnPath[] paths) {
        this.column = column;
        this.expr = new Expr(lambda, paths);
    }

    public ColumnDefinitionCalc(Column column, EvaluatorCalc lambda, Column[] columns) {
        this.column = column;
        ColumnPath[] paths = new ColumnPath[columns.length];
        for (int i = 0; i < columns.length; i++) {
            paths[i] = new ColumnPath(columns[i]);
        }

        this.expr = new Expr(lambda, paths);
    }

    public ColumnDefinitionCalc(Column column, Expression expr) {
        this.column = column;
        this.expr = expr;
    }

}
