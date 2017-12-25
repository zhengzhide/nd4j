package org.nd4j.linalg.api.ops.impl.controlflow;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import onnx.OnnxProto3;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.imports.converters.DifferentialFunctionClassHolder;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.CustomOpDescriptor;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.util.HashUtil;
import org.nd4j.weightinit.impl.ZeroInitScheme;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Equivalent to tensorflow's conditional op.
 * Runs one of 2 {@link SameDiff.SameDiffFunctionDefinition}
 * depending on a predicate {@link org.nd4j.autodiff.samediff.SameDiff.SameDiffConditional}
 *
 *
 * @author Adam Gibson
 */
@NoArgsConstructor
@Slf4j
public class If extends DifferentialFunction implements CustomOp {

    @Getter
    protected SameDiff loopBodyExecution,predicateExecution,falseBodyExecution;


    @Getter
    protected SameDiff.SameDiffConditional predicate;
    @Getter
    protected SameDiff.SameDiffFunctionDefinition trueBody,falseBody;

    @Getter
    protected String blockName,trueBodyName,falseBodyName;

    @Getter
    protected SDVariable[] inputVars;

    @Getter
    protected Boolean trueBodyExecuted = null;

    @Getter
    protected SDVariable targetBoolean;

    protected SDVariable dummyResult;

    @Getter
    @Setter
    protected SDVariable[] outputVars;

    public If(If ifStatement) {
        this.sameDiff = ifStatement.sameDiff;
        this.outputVars = ifStatement.outputVars;
        this.falseBodyExecution = ifStatement.falseBodyExecution;
        this.trueBodyExecuted = ifStatement.trueBodyExecuted;
        this.falseBody = ifStatement.falseBody;
        this.trueBodyExecuted = ifStatement.trueBodyExecuted;
        this.dummyResult = ifStatement.dummyResult;
        this.inputVars = ifStatement.inputVars;
        this.dummyResult =  this.sameDiff.var("dummyresult-" + UUID.randomUUID().toString(),new int[]{1,1},new ZeroInitScheme('f'));
        if(sameDiff.getShapeForVarName(dummyResult.getVarName()) == null)
            sameDiff.putShapeForVarName(dummyResult.getVarName(),new int[]{1,1});




    }

    @Builder
    public If(String blockName,
              SameDiff parent,
              SDVariable[] inputVars,
              SameDiff.SameDiffFunctionDefinition conditionBody,
              SameDiff.SameDiffConditional predicate,
              SameDiff.SameDiffFunctionDefinition trueBody,
              SameDiff.SameDiffFunctionDefinition falseBody) {

        this.sameDiff = parent;
        parent.putFunctionForId(getInstanceId(),this);
        this.inputVars = inputVars;
        this.predicate = predicate;

        parent.addArgsFor(inputVars,this);
        this.trueBody = trueBody;
        this.falseBody = falseBody;
        this.blockName = blockName;
        //need to add the op to the list of ops to be executed when running backwards
        this.dummyResult =  parent.var("dummyresult-" + UUID.randomUUID().toString(),new int[]{1,1},new ZeroInitScheme('f'));
        parent.addOutgoingFor(new SDVariable[]{dummyResult},this);

        //create a samediff sub graph for running just the execution
        //return a reference to the loop for referencing during actual execution
        SameDiff sameDiff = SameDiff.create();
        //store the reference to the result array and the same diff execution instance
        this.targetBoolean = predicate.eval(sameDiff,conditionBody, inputVars);
        this.predicateExecution = sameDiff;
        //store references to the loop body
        String trueBodyName = "true-body-" + UUID.randomUUID().toString();
        this.trueBodyName = trueBodyName;

        String falseBodyName = "false-body-" + UUID.randomUUID().toString();
        this.falseBodyName = trueBodyName;

        //running define function will setup a proper same diff instance
        this.loopBodyExecution = parent.defineFunction(trueBodyName,trueBody,inputVars);
        this.falseBodyExecution = parent.defineFunction(falseBodyName,falseBody,inputVars);
        parent.defineFunction(blockName,conditionBody,inputVars);
        parent.putSubFunction("predicate-eval-body-" + UUID.randomUUID().toString(),sameDiff);
        //get a reference to the actual loop body
        this.loopBodyExecution = parent.getFunction(trueBodyName);
    }


    /**
     * Toggle whether the true body was executed
     * or the false body
     * @param trueBodyExecuted
     */
    public void exectedTrueOrFalse(boolean trueBodyExecuted)  {
        if(trueBodyExecuted)
            this.trueBodyExecuted = true;
        else
            this.trueBodyExecuted = false;
    }



    @Override
    public SDVariable[] outputVariables(String baseName) {
        return new SDVariable[]{dummyResult};
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        List<SDVariable> ret = new ArrayList<>();
        ret.addAll(Arrays.asList(new IfDerivative(this).outputVariables()));
        return ret;
    }

    @Override
    public String toString() {
        return opName();
    }

    @Override
    public String opName() {
        return "if";
    }

    @Override
    public long opHash() {
        return HashUtil.getLongHash(opName());
    }

    @Override
    public boolean isInplaceCall() {
        return false;
    }

    @Override
    public INDArray[] outputArguments() {
        return new INDArray[0];
    }

    @Override
    public INDArray[] inputArguments() {
        return new INDArray[0];
    }

    @Override
    public int[] iArgs() {
        return new int[0];
    }

    @Override
    public double[] tArgs() {
        return new double[0];
    }

    @Override
    public void addIArgument(int... arg) {

    }

    @Override
    public void removeIArgument(Integer arg) {

    }

    @Override
    public Integer getIArgument(int index) {
        return null;
    }

    @Override
    public int numIArguments() {
        return 0;
    }

    @Override
    public void addTArgument(double... arg) {

    }

    @Override
    public void removeTArgument(Double arg) {

    }

    @Override
    public Double getTArgument(int index) {
        return null;
    }

    @Override
    public int numTArguments() {
        return 0;
    }

    @Override
    public void addInputArgument(INDArray... arg) {

    }

    @Override
    public void removeInputArgument(INDArray arg) {

    }

    @Override
    public INDArray getInputArgument(int index) {
        return null;
    }

    @Override
    public int numInputArguments() {
        return 0;
    }

    @Override
    public void addOutputArgument(INDArray... arg) {

    }

    @Override
    public void removeOutputArgument(INDArray arg) {

    }

    @Override
    public INDArray getOutputArgument(int index) {
        return null;
    }

    @Override
    public int numOutputArguments() {
        return 0;
    }

    @Override
    public Op.Type opType() {
        return  Op.Type.CONDITIONAL;
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        doImport(nodeDef,initWith,attributesForNode,graph,new LinkedHashSet<String>(),new AtomicInteger(0));
    }


    private  void doImport(NodeDef nodeDef,SameDiff initWith,Map<String,AttrValue> attributesForNode,GraphDef graph,Set<String> skipSet,AtomicInteger currIndex) {
        val uniqueId = java.util.UUID.randomUUID().toString();

        val scopeCondition = SameDiff.create();
        val trueBody = SameDiff.create();
        val falseBody = SameDiff.create();

        initWith.putSubFunction("condition-" + uniqueId,scopeCondition);
        initWith.putSubFunction("truebody-" + uniqueId,trueBody);
        initWith.putSubFunction("falsebody-" + uniqueId,falseBody);
        this.loopBodyExecution = trueBody;
        this.predicateExecution = scopeCondition;

        log.info("Adding 2 new scopes for WHILE {}");


        val nodes = graph.getNodeList();

        /**
         * Plan is simple:
         * 1) we read all declarations of variables used within loop
         * 2) we set up conditional scope
         * 3) we set up body scope
         * 4) ???
         * 5) PROFIT!
         */

        for (; currIndex.get() < nodes.size(); currIndex.incrementAndGet()) {
            val tfNode = nodes.get(currIndex.get());

            if (!tfNode.getOp().equalsIgnoreCase("enter")) {
                //skipSet.add(tfNode.getName());
                break;
            }

//            if (skipSet.contains(tfNode.getName()))
//                continue;

            skipSet.add(tfNode.getName());

            val vars = new SDVariable[tfNode.getInputCount()];
            for (int e = 0; e < tfNode.getInputCount(); e++) {
                val input = TFGraphMapper.getInstance().getNodeName(tfNode.getInput(e));
                vars[e] = initWith.getVariable(input) == null ? initWith.var(input,null,new ZeroInitScheme('f')) : initWith.getVariable(input);
                scopeCondition.var(vars[e]);
                trueBody.var(vars[e]);
            }

            this.inputVars = vars;
        }


        // now we're skipping Merge step, since we've already captured variables at Enter step
        int mergedCnt = 0;
        for (; currIndex.get() < nodes.size(); currIndex.incrementAndGet()) {
            val tfNode = nodes.get(currIndex.get());

            if (!tfNode.getOp().equalsIgnoreCase("merge")) {
                trueBody.var(TFGraphMapper.getInstance().getNodeName(tfNode.getName()),null,new ZeroInitScheme('f'));
                break;
            }

            skipSet.add(tfNode.getName());
            val var = trueBody.var(TFGraphMapper.getInstance().getNodeName(tfNode.getName()),null,new ZeroInitScheme('f'));
            scopeCondition.var(var);
            initWith.var(var);
            mergedCnt++;
        }


        // now, we're adding conditional scope
        for (; currIndex.get() < nodes.size(); currIndex.incrementAndGet()) {
            val tfNode = nodes.get(currIndex.get());

            // we're parsing up to condition
            if (tfNode.getOp().equalsIgnoreCase("LoopCond")) {
                skipSet.add(tfNode.getName());
                currIndex.incrementAndGet();
                break;
            }

            boolean isConst = tfNode.getOp().equalsIgnoreCase("const");
            boolean isVar = tfNode.getOp().startsWith("VariableV");
            boolean isPlaceholder = tfNode.getOp().startsWith("Placeholder");


            if (isConst || isVar || isPlaceholder) {
                val var = scopeCondition.var(tfNode.getName(),null,new ZeroInitScheme('f'));
                trueBody.var(var);
                initWith.var(var);
                log.info("Adding condition var [{}]", var.getVarName());

            }

            skipSet.add(tfNode.getName());
        }



        // time to skip some Switch calls
        int switchCnt = 0;
        for (; currIndex.get() < nodes.size(); currIndex.incrementAndGet()) {
            val tfNode = nodes.get(currIndex.get());

            // we're parsing up to condition
            if (!tfNode.getOp().equalsIgnoreCase("Switch"))
                break;

            switchCnt++;
            skipSet.add(tfNode.getName());
        }

        // now we're parsing Identity step
        int identityCnt = 0;
        for (; currIndex.get() < nodes.size(); currIndex.incrementAndGet()) {
            val tfNode = nodes.get(currIndex.get());


            if (!tfNode.getOp().equalsIgnoreCase("Identity")) {
                break;
            }


            val func = DifferentialFunctionClassHolder.getInstance().getInstance(TFGraphMapper.getInstance().getMappedOp(tfNode.getOp()).opName());
            func.initFromTensorFlow(tfNode,initWith,nodeDef.getAttrMap(),graph);
            func.setSameDiff(trueBody);
            val variables = new SDVariable[tfNode.getInputCount()];
            for(int i = 0; i < tfNode.getInputCount(); i++) {
                variables[i] = initWith.getVariable(TFGraphMapper.getInstance().getNodeName(tfNode.getInput(i)));
                scopeCondition.var(variables[i]);
                trueBody.var(variables[i]);
            }

            trueBody.addArgsFor(variables,func);
            skipSet.add(tfNode.getName());
        }


        // parsing body scope
        for (; currIndex.get() < nodes.size(); currIndex.incrementAndGet()) {
            val tfNode = nodes.get(currIndex.get());

            if (skipSet.contains(tfNode.getName())) {
                log.info("Skipping: {}", tfNode.getName());
                continue;
            }

            if (tfNode.getOp().equalsIgnoreCase("NextIteration")) {
//                skipSet.add(tfNode.getName());
                break;
            }

            if (skipSet.contains(tfNode.getName())) {
                log.info("Skipping: {}", tfNode.getName());
                continue;
            }



            boolean isConst = tfNode.getOp().equalsIgnoreCase("const");
            boolean isVar = tfNode.getOp().startsWith("VariableV");
            boolean isPlaceholder = tfNode.getOp().startsWith("Placeholder");


            if (isConst || isVar || isPlaceholder) {
                val var = trueBody.var(tfNode.getName(), null,new ZeroInitScheme('f'));
                log.info("Adding body var [{}]",var.getVarName());

            } else {
                log.info("starting on [{}]: {}", tfNode.getName(), tfNode.getOp());

                if (tfNode.getOp().equalsIgnoreCase("enter")) {
                    log.info("NEW LOOP ----------------------------------------");
                    val func = new If();
                    func.doImport(nodeDef,initWith,attributesForNode,graph,skipSet,currIndex);
                    func.setSameDiff(initWith);
                    log.info("END LOOP ----------------------------------------");
                } else {
                    val func = DifferentialFunctionClassHolder.getInstance().getInstance(TFGraphMapper.getInstance().getMappedOp(tfNode.getOp()).opName());

                    func.initFromTensorFlow(tfNode,initWith,nodeDef.getAttrMap(),graph);


                    func.setSameDiff(scopeCondition);

                    val variables = new SDVariable[tfNode.getInputCount()];
                    for(int i = 0; i < tfNode.getInputCount(); i++) {
                        val name = TFGraphMapper.getInstance().getNodeName(tfNode.getInput(i));
                        variables[i] = scopeCondition.getVariable(name);
                        if(variables[i] == null) {
                            if(trueBody.getVariable(name) == null)
                                variables[i] = scopeCondition.var(initWith.getVariable(name));
                            else if(trueBody.getVariable(name) != null)
                                variables[i] = trueBody.getVariable(name);
                            else
                                variables[i] = trueBody.var(name, Nd4j.scalar(1.0));
                        }
                    }

                    trueBody.addArgsFor(variables,func);


                }
            }

            skipSet.add(tfNode.getName());
        }


        val returnInputs = new ArrayList<SDVariable>();
        val returnOutputs = new ArrayList<SDVariable>();

        // mapping NextIterations, to Return op
        for (; currIndex.get() < nodes.size(); currIndex.incrementAndGet()) {
            val tfNode = nodes.get(currIndex.get());

            if (!tfNode.getOp().equalsIgnoreCase("NextIteration"))
                break;

            skipSet.add(tfNode.getName());

            val inputName = TFGraphMapper.getInstance().getNodeName(tfNode.getName());
            val input = initWith.getVariable(inputName) == null ? initWith.var(inputName,null,new ZeroInitScheme('f')) : initWith.getVariable(inputName) ;
            returnInputs.add(input);
        }


        this.outputVars = returnOutputs.toArray(new SDVariable[returnOutputs.size()]);
        this.inputVars = returnInputs.toArray(new SDVariable[returnInputs.size()]);
        initWith.addArgsFor(inputVars,this);
        initWith.addOutgoingFor(outputVars,this);

        // we should also map While/Exit to libnd4j while
        int exitCnt = 0;
        for (; currIndex.get() < nodes.size(); currIndex.incrementAndGet()) {
            val tfNode = nodes.get(currIndex.get());

            if (!tfNode.getOp().equalsIgnoreCase("Exit")) {
                //skipSet.add(tfNode.getName());
                break;
            }

            skipSet.add(tfNode.getName());
            val inputName = TFGraphMapper.getInstance().getNodeName(tfNode.getName());
            val input = initWith.getVariable(inputName) == null ? initWith.var(inputName,null,new ZeroInitScheme('f')) : initWith.getVariable(inputName) ;
        }


        log.info("-------------------------------------------");

    }

    @Override
    public void initFromOnnx(OnnxProto3.NodeProto node, SameDiff initWith, Map<String, OnnxProto3.AttributeProto> attributesForNode, OnnxProto3.GraphProto graph) {

    }



    @Override
    public List<int[]> calculateOutputShape() {
        return Arrays.asList(new int[]{1,1});
    }

    @Override
    public CustomOpDescriptor getDescriptor() {
        return null;
    }

    @Override
    public void assertValidForExecution() {

    }

    @Override
    public void populateInputsAndOutputsFromSameDiff() {

    }



    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " + opName());
    }

    @Override
    public String tensorflowName() {
            return "Cond";
    }
}
