package org.example;

import java.util.Arrays;
import java.util.logging.Logger;

public class TrafficLightControl {

    public enum TlTarget { T1_WEST, T2_EAST, T3_SOUTH, T4_NORTH }
    public enum OverrideMode { NONE, FORCE_RED, FORCE_GREEN }

    private final Logger log = Logging.log();

    private final OverrideMode[] overrides = {OverrideMode.NONE, OverrideMode.NONE, OverrideMode.NONE, OverrideMode.NONE};
    private String initialProgramId=null;
    private boolean everManualTouched=false;

    public void captureInitialProgramIfPossible(SumoConnector sumo) {
        if(initialProgramId!=null) return;
        initialProgramId = sumo.getTLProgram(CommonConfig.TL_ID);
        if(initialProgramId!=null) log.info("Captured initial TL program: "+initialProgramId);
    }

    public void forceSelected(TlTarget t, OverrideMode m){
        int idx = (t==TlTarget.T1_WEST)?0:(t==TlTarget.T2_EAST)?1:(t==TlTarget.T3_SOUTH)?2:3;
        overrides[idx]=m;
        log.info("TL override: "+t+" -> "+m);
    }

    public void resetToNormal(SumoConnector sumo){
        Arrays.fill(overrides, OverrideMode.NONE);
        log.info("TL override reset.");
        if(everManualTouched){
            if(initialProgramId!=null) sumo.setTLProgram(CommonConfig.TL_ID, initialProgramId);
            try { sumo.setTLPhase(CommonConfig.TL_ID, 0); } catch (Exception ignored) {}
            try { sumo.setTLPhaseDuration(CommonConfig.TL_ID, 0.0); } catch (Exception ignored) {}
            if(initialProgramId!=null) sumo.setTLProgram(CommonConfig.TL_ID, initialProgramId);
        }
    }

    public boolean anyOverrideActive(){
        for(OverrideMode m: overrides) if(m!=OverrideMode.NONE) return true;
        return false;
    }

    private static String forceIndicesTo(String state, int[] idxArr, char ch){
        if(state==null) return null;
        char[] a=state.toCharArray();
        for(int idx: idxArr) if(idx>=0 && idx<a.length) a[idx]=ch;
        return new String(a);
    }

    private String applyOverrides(String base){
        String out=base;
        for(int d=0; d<4; d++){
            if(overrides[d]==OverrideMode.FORCE_RED) out=forceIndicesTo(out, CommonConfig.DIR_IDX[d], 'r');
            else if(overrides[d]==OverrideMode.FORCE_GREEN) out=forceIndicesTo(out, CommonConfig.DIR_IDX[d], 'G');
        }
        return out;
    }

    public String computeAndApplyEffectiveState(SumoConnector sumo, String programState){
        String effectiveState=programState;
        if(anyOverrideActive()){
            effectiveState=applyOverrides(programState);
            if(effectiveState!=null && !effectiveState.equals(programState)){
                everManualTouched=true;
                try { sumo.setTLState(CommonConfig.TL_ID, effectiveState); } catch (Exception ignored) {}
            }
        }
        return effectiveState;
    }
}