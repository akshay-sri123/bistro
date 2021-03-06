package org.conceptoriented.bistro.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class OpLink implements Operation {

    Column column;

    public boolean isProj = false; // Either link-column or project-columns. Used in sub-classes and methods as a switch

    List<ColumnPath> valuePaths;

    List<Column> keyColumns = new ArrayList<>();

    List<BistroError> errors = new ArrayList<>();
    @Override
    public List<BistroError> getErrors() {
        return this.errors;
    }

    @Override
    public List<Element> getDependencies() {
        List<Element> deps = new ArrayList<>();

        deps.add(this.column.getInput()); // Columns depend on their input table

        if(this.isProj) {
            // Project column will itself populate the output table and hence does not depend on it
            // Project column however depends on the output table definition (e.g., where predicate could change) - therefore we add it as a workaround
            deps.add(this.column.getOutput());
        }
        else {
            // Link columns depend on the output table which must be populated before the link
            deps.add(this.column.getOutput());
        }

        if(this.valuePaths != null) {
            // Dependencies are in paths
            for (ColumnPath path : this.valuePaths) {
                for (Column col : path.columns) {
                    if (!deps.contains(col)) deps.add(col);
                }
            }
        }
        else {
            return null;
        }

        return deps;
    }

    @Override
    public void evaluate() {
        if(this.column.getOutput().getDefinitionType() == OperationType.RANGE) {
            this.evalRange();
        }
        else if(this.valuePaths != null) {
            this.evalPaths();
        }
    }

    protected void evalRange() {

        errors.clear(); // Clear state

        Table typeTable = this.column.getOutput();
        OpRange rangeZableDef = (OpRange)typeTable.definition;

        Table mainTable = this.column.getInput();

        // Currently we make full scan by re-evaluating all existing input ids
        Range mainRange = this.column.getInput().getIdRange();

        //
        // Prepare value paths/exprs for search/find
        //
        //List<List<ColumnPath>> rhsParamPaths = new ArrayList<>();
        //List<Object[]> rhsParamValues = new ArrayList<>();
        Object rhsResult;

        for(long i=mainRange.start; i < mainRange.end; i++) {

            // Retrieve the fact property value
            rhsResult = this.valuePaths.get(0).getValue(i);

            //
            // Find an element in the type table which corresponds to this value (can be null if not found and not added)
            //
            Object out = rangeZableDef.findRange(rhsResult, this.isProj);

            // Update output
            this.column.setValue(i, out);
        }
    }

    protected void evalPaths() {

        errors.clear(); // Clear state

        Table typeTable = this.column.getOutput();

        Table mainTable = this.column.getInput();

        //
        // Determine the scope of dirtiness
        //

        Range mainRange = mainTable.getIdRange();

        boolean fullScope = false;

        if(isProj) { // Currently project columns require full re-evaluation (because they actually popoulate a table)
            fullScope = true;
        }

        if(!fullScope) {
            if(this.column.getDefinitionChangedAt() > this.column.getChangedAt()) { // Definition has changes
                fullScope = true;
            }
        }

        if(!fullScope) { // Some column dependency has changes
            List<Element> deps = this.getDependencies();
            for(Element e : deps) {
                if(!(e instanceof Column)) continue;
                if(((Column)e).isChanged()) { // There is a column with some changes
                    fullScope = true;
                    break;
                }
            }
        }

        if(!fullScope) {
            if(typeTable.getDefinitionChangedAt() > this.column.getChangedAt()) { // Type table definition has changed
                fullScope = true;
            }
        }

        if(!fullScope) {
            mainRange = mainTable.getAddedRange();
        }

        //
        // Update dirty elements
        //

        // Prepare value paths/exprs for search/find
        //List<List<ColumnPath>> rhsParamPaths = new ArrayList<>();
        //List<Object[]> rhsParamValues = new ArrayList<>();
        List<Object> rhsResults = new ArrayList<>(); // Record of value paths used for search (produced by expressions and having same length as column list)

        // Initialize these lists for each key expression
        for(ColumnPath path : this.valuePaths) {
            //int paramCount = expr.getParameterPaths().size();

            //rhsParamPaths.add( expr.getParameterPaths() );
            //rhsParamValues.add( new Object[ paramCount ] );
            rhsResults.add(null);
        }

        for(long i=mainRange.start; i < mainRange.end; i++) {

            // Evaluate ALL child rhs expressions by producing an array/record of their results
            for(int keyNo = 0; keyNo < this.keyColumns.size(); keyNo++) {

                // Read one columnPath
                Object result = this.valuePaths.get(keyNo).getValue(i);

                rhsResults.set(keyNo, result);
            }

            //
            // Check if this record satisfies the where condition
            //
            boolean whereTrue = typeTable.isWhereTrue(rhsResults, keyColumns);
            if(typeTable.getExecutionErrors().size() > 0) {
                this.errors.addAll(typeTable.getExecutionErrors());
                return;
            }

            if(!whereTrue) continue;

            //
            // Find element in the type table which corresponds to these expression results (can be null if not found and not added)
            //
            Object out = typeTable.find(rhsResults, this.keyColumns, this.isProj);

            // Update output
            this.column.setValue(i, out);
        }
    }

    public OpLink(Column column, ColumnPath[] valuePaths, Column[] keyColumns) {
        this.column = column;

        this.valuePaths = Arrays.asList(valuePaths);

        this.keyColumns = Arrays.asList(keyColumns);
    }

    public OpLink(Column column, Column[] valueColumns, Column[] keyColumns) {
        this.column = column;

        List<ColumnPath> paths = new ArrayList<>();
        for(Column col : valueColumns) {
            paths.add(new ColumnPath(col));
        }

        this.valuePaths = paths;

        this.keyColumns = Arrays.asList(keyColumns);
    }

    public OpLink(Column column, ColumnPath valuePath) {
        this.column = column;

        this.valuePaths = Arrays.asList(valuePath);
    }
}
