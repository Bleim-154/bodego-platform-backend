package com.bodego.platform.operations.domain;

import com.bodego.platform.operations.domain.model.aggregates.*;
import com.bodego.platform.operations.domain.model.valueobjects.MovementType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class StockControlReasonTests {
    @ParameterizedTest
    @ValueSource(ints = {255, 256, 500})
    void allThreeAggregatesRetainTheCompleteReason(int length) {
        String reason="á".repeat(length-1)+"Z";
        assertEquals(reason,new CorrectionRequest(1L,2L,reason).getReason());
        assertEquals(reason,new StockAdjustment(1L,2L,3L,10,7,reason).getReason());
        assertEquals(reason,new InventoryMovement(1L,3L,MovementType.EXIT,3,reason).getReason());
    }

    @Test void internalCallersCannotBypassBlankOr501CharacterLimits() {
        for(String invalid:new String[]{null," ","a".repeat(501)}) {
            assertThrows(IllegalArgumentException.class,()->new CorrectionRequest(1L,2L,invalid));
            assertThrows(IllegalArgumentException.class,()->new StockAdjustment(1L,2L,3L,10,7,invalid));
            assertThrows(IllegalArgumentException.class,()->new InventoryMovement(1L,3L,MovementType.EXIT,3,invalid));
        }
    }
}
