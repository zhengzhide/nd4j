package org.nd4j.linalg.api.ops.impl.controlflow;

import lombok.NoArgsConstructor;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ops.Op;

/**
 * While loop derivative
 * @author Adam Gibson
 */
@NoArgsConstructor
public class WhileDerivative extends While {
    private While delegate;

    public WhileDerivative(While delegate) {
        super(delegate);
        this.delegate = delegate;
    }



    @Override
    public SameDiff.SameDiffFunctionDefinition getTrueBody() {
        return delegate.trueBody;
    }

    @Override
    public String getTrueBodyName() {
        return delegate.getTrueBodyName();
    }

    @Override
    public SameDiff.SameDiffConditional getPredicate() {
        return delegate.getPredicate();
    }

    @Override
    public SameDiff getPredicateExecution() {
        return delegate.getPredicateExecution();
    }

    @Override
    public SDVariable[] getInputVars() {
        return delegate.getInputVars();
    }

    @Override
    public String getBlockName() {
        return delegate.getBlockName();
    }

    @Override
    public SameDiff getLoopBodyExecution() {
        return delegate.getLoopBodyExecution();
    }

    @Override
    public int getNumLooped() {
        return delegate.getNumLooped();
    }

    @Override
    public String opName() {
        return "while_bp";
    }

    @Override
    public Op.Type opType() {
        return Op.Type.CONDITIONAL;
    }
}
