package client.processor.stat;

import client.Character;
import client.Skill;
import constants.skills.Bowmaster;
import constants.skills.Hunter;
import constants.skills.Ranger;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignSPProcessorTest {
    private static final Predicate<Skill> HUNTER_SKILLS = skill -> skill.getId() / 10000 == 310;
    private static final Predicate<Skill> RANGER_SKILLS = skill -> skill.getId() / 10000 == 311;

    // Requirements as in Skill.wz
    private final Skill bowMastery = new Skill(Hunter.BOW_MASTERY);
    private final Skill bowBooster = new Skill(Hunter.BOW_BOOSTER);
    private final Skill puppet = new Skill(Ranger.PUPPET);
    private final Skill inferno = new Skill(Ranger.INFERNO);
    private final Skill silverHawk = skillRequiring(Ranger.SILVER_HAWK, Ranger.PUPPET, 5);
    private final Skill phoenix = skillRequiring(Bowmaster.PHOENIX, Ranger.SILVER_HAWK, 15);
    private final Skill bowExpert = skillRequiring(Bowmaster.BOW_EXPERT, Hunter.BOW_MASTERY, 20);

    private final Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();

    private static Skill skillRequiring(int skillId, int requiredSkillId, int requiredLevel) {
        Skill skill = new Skill(skillId);
        skill.addRequiredSkill(requiredSkillId, requiredLevel);
        return skill;
    }

    private void learn(Skill skill, int level) {
        skills.put(skill, new Character.SkillEntry((byte) level, 10, -1));
    }

    @Test
    void tierSkillsShouldBeFullyResetWhenNoLearnedSkillRequiresThem() {
        learn(bowMastery, 20);
        learn(bowBooster, 20);
        learn(bowExpert, 0);

        Map<Skill, Integer> resetLevels = AssignSPProcessor.getTierResetLevels(skills, HUNTER_SKILLS);

        assertEquals(Map.of(bowMastery, 0, bowBooster, 0), resetLevels);
    }

    @Test
    void requiredSkillShouldKeepRequiredLevelWhenHigherTierSkillIsLearned() {
        learn(bowMastery, 20);
        learn(bowBooster, 20);
        learn(bowExpert, 1);

        Map<Skill, Integer> resetLevels = AssignSPProcessor.getTierResetLevels(skills, HUNTER_SKILLS);

        assertEquals(Map.of(bowMastery, 20, bowBooster, 0), resetLevels);
    }

    @Test
    void keptSkillsShouldKeepTheirOwnRequiredSkills() {
        learn(puppet, 20);
        learn(inferno, 20);
        learn(silverHawk, 20);
        learn(phoenix, 1);

        Map<Skill, Integer> resetLevels = AssignSPProcessor.getTierResetLevels(skills, RANGER_SKILLS);

        assertEquals(Map.of(puppet, 5, inferno, 0, silverHawk, 15), resetLevels);
    }

    @Test
    void requirementChainShouldBeFullyResetWhenHigherTierSkillIsNotLearned() {
        learn(puppet, 20);
        learn(silverHawk, 20);
        learn(phoenix, 0);

        Map<Skill, Integer> resetLevels = AssignSPProcessor.getTierResetLevels(skills, RANGER_SKILLS);

        assertEquals(Map.of(puppet, 0, silverHawk, 0), resetLevels);
    }

    @Test
    void skillBelowRequiredLevelShouldKeepItsLevel() {
        learn(puppet, 3);
        learn(silverHawk, 10);
        learn(phoenix, 1);

        Map<Skill, Integer> resetLevels = AssignSPProcessor.getTierResetLevels(skills, RANGER_SKILLS);

        assertEquals(Map.of(puppet, 3, silverHawk, 10), resetLevels);
    }

    @Test
    void onlyLearnedTierSkillsShouldBeReset() {
        learn(bowMastery, 20);
        learn(bowBooster, 0);
        learn(puppet, 20);

        Map<Skill, Integer> resetLevels = AssignSPProcessor.getTierResetLevels(skills, HUNTER_SKILLS);

        assertEquals(Map.of(bowMastery, 0), resetLevels);
    }

    @Test
    void resetLevelsShouldBeOrderedBySkillId() {
        learn(silverHawk, 20);
        learn(inferno, 20);
        learn(puppet, 20);

        Map<Skill, Integer> resetLevels = AssignSPProcessor.getTierResetLevels(skills, RANGER_SKILLS);

        assertEquals(List.of(puppet, inferno, silverHawk), List.copyOf(resetLevels.keySet()));
    }

    @Test
    void requiredSkillLevelsShouldBeMet() {
        Map<Integer, Integer> levels = Map.of(Ranger.SILVER_HAWK, 15);

        assertTrue(AssignSPProcessor.hasRequiredSkillLevels(phoenix, skillId -> levels.getOrDefault(skillId, 0)));
        assertTrue(AssignSPProcessor.hasRequiredSkillLevels(inferno, skillId -> 0));
    }

    @Test
    void requiredSkillLevelsShouldNotBeMet() {
        Map<Integer, Integer> levels = Map.of(Ranger.SILVER_HAWK, 14);

        assertFalse(AssignSPProcessor.hasRequiredSkillLevels(phoenix, skillId -> levels.getOrDefault(skillId, 0)));
        assertFalse(AssignSPProcessor.hasRequiredSkillLevels(phoenix, skillId -> 0));
    }
}
