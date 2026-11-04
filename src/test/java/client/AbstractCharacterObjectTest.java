package client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class AbstractCharacterObjectTest {
    private AbstractCharacterListener listener;
    private AbstractCharacterObject chr;

    @BeforeEach
    void createCharacter() {
        listener = mock(AbstractCharacterListener.class);
        chr = mock(AbstractCharacterObject.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
        chr.setListener(listener);
    }

    @Test
    void resetStrDexIntLukShouldReturnApSpentOnBaseStats() {
        chr.str = 50;
        chr.dex = 25;
        chr.int_ = 4;
        chr.luk = 10;
        chr.remainingAp = 3;
        chr.maxhp = 1000;
        chr.hpMpApUsed = 7;

        int refundedAp = chr.resetStrDexIntLuk();

        assertEquals(73, refundedAp);
        assertAll(
                () -> assertEquals(4, chr.getStr()),
                () -> assertEquals(4, chr.getDex()),
                () -> assertEquals(4, chr.getInt()),
                () -> assertEquals(4, chr.getLuk()),
                () -> assertEquals(76, chr.getRemainingAp()),
                () -> assertEquals(1000, chr.getMaxHp()),
                () -> assertEquals(7, chr.getHpMpApUsed())
        );
        verify(listener, times(1)).onAnnounceStatPoolUpdate();
    }

    @Test
    void resetStrDexIntLukShouldDoNothingWhenBaseStatsAreAtMinimum() {
        chr.str = 4;
        chr.dex = 4;
        chr.int_ = 4;
        chr.luk = 4;
        chr.remainingAp = 3;

        assertEquals(0, chr.resetStrDexIntLuk());
        assertEquals(3, chr.getRemainingAp());
        verify(listener, never()).onAnnounceStatPoolUpdate();
    }

    @Test
    void refundLockedSpShouldAddSpLockedToJobTier() {
        chr.remainingSp[0] = 2;

        chr.refundLockedSp(10, 2);

        assertEquals(12, chr.getRemainingSps()[0]);
        assertArrayEquals(new int[]{0, 10, 0, 0}, chr.getLockedSps());
        assertEquals(10, chr.getLockedSp(2));
        verify(listener, times(1)).onAnnounceStatPoolUpdate();
    }

    @Test
    void refundLockedSpShouldRejectInvalidJobTier() {
        assertThrows(IllegalArgumentException.class, () -> chr.refundLockedSp(5, 0));
        assertThrows(IllegalArgumentException.class, () -> chr.refundLockedSp(5, 5));
    }

    @Test
    void spendSkillPointShouldUseSpLockedToSkillTierFirst() {
        chr.remainingSp[0] = 12;
        chr.setLockedSps(new int[]{0, 10, 0, 0});

        assertTrue(chr.spendSkillPoint(2));

        assertEquals(11, chr.getRemainingSps()[0]);
        assertArrayEquals(new int[]{0, 9, 0, 0}, chr.getLockedSps());
    }

    @Test
    void spendSkillPointShouldUseFreeSpForOtherTiers() {
        chr.remainingSp[0] = 12;
        chr.setLockedSps(new int[]{0, 10, 0, 0});

        assertTrue(chr.spendSkillPoint(3));

        assertEquals(11, chr.getRemainingSps()[0]);
        assertArrayEquals(new int[]{0, 10, 0, 0}, chr.getLockedSps());
    }

    @Test
    void spendSkillPointShouldNotUseSpLockedToOtherTiers() {
        chr.remainingSp[0] = 10;
        chr.setLockedSps(new int[]{0, 10, 0, 0});

        assertFalse(chr.spendSkillPoint(3));
        assertFalse(chr.spendSkillPoint(0));

        assertEquals(10, chr.getRemainingSps()[0]);
        assertArrayEquals(new int[]{0, 10, 0, 0}, chr.getLockedSps());
        verify(listener, never()).onAnnounceStatPoolUpdate();
    }

    @Test
    void spendSkillPointShouldFailWithoutSp() {
        assertFalse(chr.spendSkillPoint(1));
    }

    @Test
    void lockedSpShouldBeTrimmedWhenRemainingSpDrops() {
        chr.remainingSp[0] = 12;
        chr.setLockedSps(new int[]{0, 5, 5, 0});

        chr.updateRemainingSp(6, 0);

        assertEquals(6, chr.getRemainingSps()[0]);
        assertArrayEquals(new int[]{0, 1, 5, 0}, chr.getLockedSps());
    }

    @Test
    void setLockedSpsShouldIgnoreInvalidValues() {
        chr.remainingSp[0] = 5;

        chr.setLockedSps(new int[]{-3, 4, 2, 1, 99});

        assertArrayEquals(new int[]{0, 2, 2, 1}, chr.getLockedSps());
    }
}
